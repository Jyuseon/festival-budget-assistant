package com.festival.budgetassist.estimate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.festival.budgetassist.festival.domain.BudgetStatus;
import com.festival.budgetassist.festival.domain.FestivalRecord;
import com.festival.budgetassist.festival.domain.FestivalType;
import com.festival.budgetassist.festival.domain.Region;
import com.festival.budgetassist.festival.domain.VenueType;
import com.festival.budgetassist.festival.repository.FestivalRecordRepository;

/**
 * 안내서 9장 전체를 조합하는 오케스트레이터.
 *
 * <p>흐름: 코드 검증 → 연도별 예산-확정 모집단 조회 → 계층형 fallback으로 후보 수집
 * ({@link CandidateSelector}) → 유사도 계산({@link SimilarityCalculator}) → 임계값/표본수 제한
 * → 기간 보정({@link DurationAdjuster}) → 해당 유형 모집단 기준 winsorize → 가중 통계
 * ({@link WeightedStatistics}) → 신뢰도({@link ConfidenceCalculator}) → 예비비 반영 추천 예산.</p>
 *
 * <p>모든 매직넘버는 {@link AlgorithmConfig}에서만 가져온다.</p>
 */
@Service
public class BudgetEstimatorService {

    private final FestivalRecordRepository festivalRecordRepository;
    private final CandidateSelector candidateSelector;
    private final SimilarityCalculator similarityCalculator;
    private final DurationAdjuster durationAdjuster;
    private final ConfidenceCalculator confidenceCalculator;
    private final AlgorithmConfig config;

    @Value("${festival.calculation-trace.enabled:false}")
    private boolean calculationTraceEnabled;

    BudgetEstimatorService(FestivalRecordRepository festivalRecordRepository,
                            CandidateSelector candidateSelector,
                            SimilarityCalculator similarityCalculator,
                            DurationAdjuster durationAdjuster,
                            ConfidenceCalculator confidenceCalculator,
                            AlgorithmConfig config) {
        this.festivalRecordRepository = festivalRecordRepository;
        this.candidateSelector = candidateSelector;
        this.similarityCalculator = similarityCalculator;
        this.durationAdjuster = durationAdjuster;
        this.confidenceCalculator = confidenceCalculator;
        this.config = config;
    }

    public BudgetEstimateResponse estimate(BudgetEstimateRequest request) {
        Region region = parseRegion(request.regionCode());
        FestivalType festivalType = parseFestivalType(request.festivalType());
        VenueType venueType = parseVenueType(request.venueType());
        int durationDays = request.durationDays();
        String district = blankToNull(request.district());

        int datasetYear = festivalRecordRepository.findMaxDatasetYear()
                .orElseThrow(() -> new IllegalStateException("적재된 데이터가 없습니다. 먼저 CLI로 Import를 실행하세요."));

        List<FestivalRecord> yearPool = festivalRecordRepository.findByDatasetYearAndBudgetStatus(datasetYear, BudgetStatus.CONFIRMED);

        List<String> trace = calculationTraceEnabled ? new ArrayList<>() : null;

        CandidateSelectionResult selection = candidateSelector.select(yearPool, region, district, festivalType, venueType);
        trace(trace, "1. 후보 축제 선정: %d건 (fallback=%s)".formatted(selection.candidates().size(), selection.level()));
        if (trace != null) {
            for (LevelContribution contribution : selection.levelBreakdown()) {
                trace(trace, "   %s: +%d건 (누적 %d건)".formatted(
                        contribution.level(), contribution.added(), contribution.cumulativeTotal()));
            }
        }

        // 이상치 완화 기준값: 같은 축제 유형의 "전체" 모집단(선정된 후보군이 아니라 연도 전체)에서 구한다.
        double[] typePopulationLogBudgets = yearPool.stream()
                .filter(r -> r.getFestivalType() == festivalType)
                .mapToDouble(r -> Math.log(r.getTotalBudgetKrw()))
                .toArray();
        double lowerLogBound = typePopulationLogBudgets.length > 0
                ? WeightedStatistics.quantile(typePopulationLogBudgets, config.getWinsorizeLowerPercentile())
                : Double.NEGATIVE_INFINITY;
        double upperLogBound = typePopulationLogBudgets.length > 0
                ? WeightedStatistics.quantile(typePopulationLogBudgets, config.getWinsorizeUpperPercentile())
                : Double.POSITIVE_INFINITY;
        trace(trace, "2. 이상치 완화 기준: 동일 유형 모집단 %d건의 예산(log) P%.0f~P%.0f = %,.0f~%,.0f원".formatted(
                typePopulationLogBudgets.length,
                config.getWinsorizeLowerPercentile() * 100, config.getWinsorizeUpperPercentile() * 100,
                Math.exp(lowerLogBound), Math.exp(upperLogBound)));

        List<ScoredCandidate> scored = new ArrayList<>();
        for (FestivalRecord candidate : selection.candidates()) {
            SimilarityScore score = similarityCalculator.compute(region, district, festivalType, venueType, durationDays, candidate);
            double adjustedBudget = durationAdjuster.adjust(candidate.getTotalBudgetKrw(), candidate.getDurationDays(), durationDays);
            double winsorizedBudget = Math.exp(WeightedStatistics.clip(Math.log(adjustedBudget), lowerLogBound, upperLogBound));
            scored.add(new ScoredCandidate(candidate, score, adjustedBudget, winsorizedBudget));
        }

        List<ScoredCandidate> finalSample = scored.stream()
                .filter(c -> c.score().similarity() >= config.getSimilarityMinThreshold())
                .sorted(Comparator.comparingDouble((ScoredCandidate c) -> c.score().weight()).reversed())
                .limit(config.getMaxSampleCount())
                .toList();
        trace(trace, "3. 유사도 임계값(%.2f) 적용 및 최대 %d건 제한 후 최종 표본: %d건".formatted(
                config.getSimilarityMinThreshold(), config.getMaxSampleCount(), finalSample.size()));
        trace(trace, "4. 기간 보정 적용 (elasticity=%.2f, 비율 clamp %.1f~%.1f)".formatted(
                config.getDurationElasticity(), config.getDurationRatioClampMin(), config.getDurationRatioClampMax()));

        int sampleCount = finalSample.size();

        if (sampleCount == 0) {
            return emptyResponse(datasetYear, selection.level(), trace);
        }

        double[] values = finalSample.stream().mapToDouble(ScoredCandidate::winsorizedBudgetKrw).toArray();
        double[] weights = finalSample.stream().mapToDouble(c -> c.score().weight()).toArray();
        double[] similarities = finalSample.stream().mapToDouble(c -> c.score().similarity()).toArray();
        double[] hasDurationFlags = finalSample.stream().mapToDouble(c -> c.record().getDurationDays() != null ? 1.0 : 0.0).toArray();

        double weightedAverage = WeightedStatistics.weightedMean(values, weights);
        double estimated = WeightedStatistics.weightedGeometricMean(values, weights);
        double p25 = WeightedStatistics.weightedQuantile(values, weights, 0.25);
        double p50 = WeightedStatistics.weightedQuantile(values, weights, 0.50);
        double p60 = WeightedStatistics.weightedQuantile(values, weights, config.getRecommendedBasePercentile());
        double p75 = WeightedStatistics.weightedQuantile(values, weights, 0.75);

        trace(trace, "5. 가중 산술평균: %,.0f원".formatted(weightedAverage));
        trace(trace, "6. 가중 기하평균(추정 예산): %,.0f원".formatted(estimated));
        trace(trace, "7. 가중 P60: %,.0f원".formatted(p60));

        double similarityScoreAvg = WeightedStatistics.weightedMean(similarities, weights);
        double completenessScore = WeightedStatistics.weightedMean(hasDurationFlags, weights);
        ConfidenceResult confidence = confidenceCalculator.calculate(sampleCount, similarityScoreAvg, p25, p50, p75, completenessScore);
        trace(trace, "8. 신뢰도(legacy): %.1f점 (%s) [표본점수=%.2f, 유사도점수=%.2f, 안정성점수=%.2f, 완전성점수=%.2f]".formatted(
                confidence.score(), confidence.label(),
                confidence.sampleScore(), confidence.similarityScore(), confidence.stabilityScore(), confidence.completenessScore()));

        // v1.1 후보 공식(비교 전용) - 대표 confidence 필드에는 반영하지 않고 breakdown에만 노출한다.
        ConfidenceV11Result confidenceV11 = confidenceCalculator.calculateV11(
                sampleCount, weights, similarityScoreAvg, p25, p75, completenessScore, selection.level());
        trace(trace, "8b. 신뢰도(v1.1 후보): %.1f점 (%s) [effectiveN=%.1f, 표본점수=%.2f, 안정성점수=%.2f, scope점수=%.2f]".formatted(
                confidenceV11.score(), confidenceV11.label(),
                confidenceV11.effectiveSampleSize(), confidenceV11.effectiveSampleScore(),
                confidenceV11.stabilityScore(), confidenceV11.scopeScore()));

        // v1.2 후보 공식(비교 전용) - v1.1과 같은 표본/안정성/완전성 점수를 재사용하되 scope를 뺀다.
        ConfidenceV12Result confidenceV12 = confidenceCalculator.calculateV12(
                sampleCount, confidenceV11.effectiveSampleScore(), similarityScoreAvg, confidenceV11.stabilityScore(), completenessScore);
        trace(trace, "8c. 신뢰도(v1.2 후보): %.1f점 (%s) [scope 제외, 나머지는 v1.1과 동일 요소 재사용]".formatted(
                confidenceV12.score(), confidenceV12.label()));

        List<ScopeWeightShare> scopeWeightBreakdown = buildScopeWeightBreakdown(finalSample, selection.originLevelByCandidateId());
        if (trace != null && !scopeWeightBreakdown.isEmpty()) {
            trace(trace, "8d. fallback 단계별 최종 weight 점유율(설명용, confidence에는 미반영):");
            for (ScopeWeightShare share : scopeWeightBreakdown) {
                trace(trace, "   %s: %.1f%%".formatted(share.label(), share.weightSharePercent()));
            }
        }

        double recommendedBase = Math.max(estimated, p60);
        double contingencyRate = config.getContingencyBaseRate()
                + (1 - confidence.score() / 100.0) * config.getContingencyMaxExtraRate();
        double recommendedBudget = recommendedBase * (1 + contingencyRate);
        trace(trace, "9. 예비비율: %.2f%%".formatted(contingencyRate * 100));
        trace(trace, "10. 최종 추천 예산: %,.0f원".formatted(recommendedBudget));

        List<String> basis = buildBasis(finalSample, festivalType, region, venueType);
        List<String> warnings = buildWarnings(datasetYear, sampleCount, venueType);
        List<SimilarFestivalDto> similarFestivals = buildSimilarFestivals(finalSample);
        ConfidenceBreakdown confidenceBreakdown = calculationTraceEnabled
                ? new ConfidenceBreakdown(
                        confidence.sampleScore(), confidence.similarityScore(), confidence.stabilityScore(), confidence.completenessScore(),
                        sampleCount, confidenceV11.effectiveSampleSize(), confidenceV11.effectiveSampleScore(),
                        confidenceV11.stabilityScore(), confidenceV11.scopeScore(), confidenceV11.score(), confidenceV11.level(),
                        confidenceV12.score(), confidenceV12.level())
                : null;

        return new BudgetEstimateResponse(
                datasetYear,
                config.getVersion(),
                Math.round(weightedAverage),
                Math.round(estimated),
                Math.round(recommendedBudget),
                new BudgetRange(Math.round(p25), Math.round(p75)),
                Math.round(p50),
                Math.round(p60),
                sampleCount,
                new ConfidenceInfo(confidence.score(), confidence.level(), confidence.label()),
                selection.level().name(),
                selection.level().getLabel(),
                basis,
                warnings,
                similarFestivals,
                trace,
                confidenceBreakdown,
                calculationTraceEnabled ? scopeWeightBreakdown : List.of()
        );
    }

    private BudgetEstimateResponse emptyResponse(int datasetYear, FallbackLevel level, List<String> trace) {
        trace(trace, "후보가 없어 추정을 계산할 수 없습니다.");
        return new BudgetEstimateResponse(
                datasetYear,
                config.getVersion(),
                0, 0, 0,
                new BudgetRange(0, 0),
                0, 0,
                0,
                new ConfidenceInfo(0, "LOW", "데이터 부족"),
                level.name(),
                level.getLabel(),
                List.of(),
                List.of("해당 조건에 참고할 데이터가 없습니다.", disclaimerText(datasetYear)),
                List.of(),
                trace,
                null,
                List.of()
        );
    }

    private List<String> buildBasis(List<ScoredCandidate> finalSample, FestivalType festivalType, Region region, VenueType venueType) {
        long sameType = finalSample.stream().filter(c -> c.record().getFestivalType() == festivalType).count();
        long sameRegion = finalSample.stream().filter(c -> c.record().getRegion() == region).count();
        long sameVenue = finalSample.stream().filter(c -> c.record().getVenueType() == venueType).count();
        long hasDuration = finalSample.stream().filter(c -> c.record().getDurationDays() != null).count();
        return List.of(
                "동일 축제 유형 %d건".formatted(sameType),
                "동일 광역지역 %d건".formatted(sameRegion),
                "동일 장소유형 %d건".formatted(sameVenue),
                "유효 개최기간 데이터 %d건".formatted(hasDuration)
        );
    }

    private List<String> buildWarnings(int datasetYear, int sampleCount, VenueType requestedVenueType) {
        List<String> warnings = new ArrayList<>();
        warnings.add(disclaimerText(datasetYear));
        if (sampleCount < config.getConfidenceInsufficientSampleThreshold()) {
            warnings.add("표본이 매우 부족합니다(%d건). 참고용으로만 사용하세요.".formatted(sampleCount));
        } else if (sampleCount < config.getMinSampleCount()) {
            warnings.add("표본 수(%d건)가 권장 최소 표본(%d건)보다 적어 추정 신뢰도가 낮을 수 있습니다.".formatted(sampleCount, config.getMinSampleCount()));
        }
        if (requestedVenueType == VenueType.UNDECIDED) {
            warnings.add("개최 장소 유형을 아직 정하지 않아 추정 정확도가 떨어질 수 있습니다.");
        }
        return warnings;
    }

    private String disclaimerText(int datasetYear) {
        return "본 결과는 %d년 계획 예산 데이터를 기반으로 한 참고 추정치입니다.".formatted(datasetYear);
    }

    /**
     * 최종 표본의 weight 합계 중 각 fallback 단계에서 처음 들어온 후보들이 차지하는 비율.
     * confidence 점수에는 쓰지 않는다(v1.2부터 scope를 뺐다) - 설명용 정보로만 노출한다.
     */
    private List<ScopeWeightShare> buildScopeWeightBreakdown(List<ScoredCandidate> finalSample,
                                                               Map<Long, FallbackLevel> originLevelByCandidateId) {
        double totalWeight = finalSample.stream().mapToDouble(c -> c.score().weight()).sum();
        if (totalWeight <= 0) {
            return List.of();
        }

        Map<FallbackLevel, Double> weightByLevel = new LinkedHashMap<>();
        for (ScoredCandidate candidate : finalSample) {
            FallbackLevel origin = originLevelByCandidateId.get(candidate.record().getId());
            if (origin == null) {
                continue; // 방어적 - 정상 흐름에서는 항상 존재해야 함
            }
            weightByLevel.merge(origin, candidate.score().weight(), Double::sum);
        }

        List<ScopeWeightShare> result = new ArrayList<>();
        for (FallbackLevel level : FallbackLevel.values()) {
            Double weight = weightByLevel.get(level);
            if (weight == null) {
                continue;
            }
            result.add(new ScopeWeightShare(level.name(), scopeWeightLabel(level), weight / totalWeight * 100));
        }
        return result;
    }

    private String scopeWeightLabel(FallbackLevel level) {
        return switch (level) {
            case SAME_DISTRICT_TYPE_VENUE -> "동일 시군구";
            case SAME_REGION_TYPE_VENUE -> "동일 광역지역 추가";
            case NATIONWIDE_TYPE_VENUE -> "전국(유형+장소) 추가";
            case SAME_REGION_TYPE -> "동일 광역지역(장소 무관) 추가";
            case NATIONWIDE_TYPE -> "전국(유형만) 추가";
            case GLOBAL_SIMILARITY -> "전체 후보 추가";
        };
    }

    private List<SimilarFestivalDto> buildSimilarFestivals(List<ScoredCandidate> finalSample) {
        return finalSample.stream()
                .limit(10)
                .map(c -> new SimilarFestivalDto(
                        c.record().getFestivalName(),
                        c.record().getRegionName(),
                        c.record().getAdministrativeDistrict(),
                        c.record().getFestivalType().getDisplayName(),
                        c.record().getVenueType().getDisplayName(),
                        c.record().getDurationDays(),
                        c.record().getTotalBudgetKrw(),
                        Math.round(c.adjustedBudgetKrw()),
                        c.score().festivalTypeScore(),
                        c.score().regionScore(),
                        c.score().venueTypeScore(),
                        c.score().durationScore(),
                        c.score().similarity(),
                        c.score().weight()
                ))
                .toList();
    }

    private void trace(List<String> trace, String message) {
        if (trace != null) {
            trace.add(message);
        }
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private Region parseRegion(String code) {
        try {
            return Region.valueOf(code);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("인식할 수 없는 지역 코드입니다: " + code);
        }
    }

    private FestivalType parseFestivalType(String code) {
        try {
            return FestivalType.valueOf(code);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("인식할 수 없는 축제 유형 코드입니다: " + code);
        }
    }

    private VenueType parseVenueType(String code) {
        try {
            return VenueType.valueOf(code);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("인식할 수 없는 개최 장소 유형 코드입니다: " + code);
        }
    }
}