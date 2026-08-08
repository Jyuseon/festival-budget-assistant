package com.festival.budgetassist.multiyear.experimental;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.festival.budgetassist.festival.domain.FestivalType;
import com.festival.budgetassist.festival.domain.Region;
import com.festival.budgetassist.festival.domain.VenueType;
import com.festival.budgetassist.multiyear.backtest.MultiYearBacktestService;
import com.festival.budgetassist.multiyear.backtest.MultiYearPlanningEstimateResult;
import com.festival.budgetassist.multiyear.backtest.MultiYearPlanningYearWeightShare;
import com.festival.budgetassist.multiyear.backtest.MultiYearPredictionCandidate;
import com.festival.budgetassist.multiyear.backtest.MultiYearPredictionResult;
import com.festival.budgetassist.multiyear.backtest.ReferenceDataPolicy;
import com.festival.budgetassist.multiyear.domain.MultiYearDatasetPublicationStatus;
import com.festival.budgetassist.multiyear.domain.MultiYearDatasetPublicationStatusValue;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;
import com.festival.budgetassist.multiyear.repository.MultiYearDatasetPublicationStatusRepository;
import com.festival.budgetassist.multiyear.repository.MultiYearFestivalRecordRepository;

/**
 * "다년도 실험 분석" 영역 전용 application service - festivalSeries v1 baseline S0 backtest
 * 계산식을 실제 사용자 입력 1건에 그대로 재사용해 즉석 예측을 만든다.
 *
 * <p><b>production을 대체하지 않는다</b>: 이 서비스는 {@code multiyear} 패키지 하위에만 있고,
 * {@code estimate} 패키지(production {@code BudgetEstimatorService}/{@code CandidateSelector}/
 * {@code SimilarityCalculator}/{@code ConfidenceCalculator}/{@code WeightedStatistics}/
 * {@code DurationAdjuster})는 전혀 import하지 않는다 - 두 계산 경로는 완전히 분리된 채로
 * 나란히 존재한다.</p>
 *
 * <p><b>두 경로, 완전한 하위호환</b>: {@code request.planningYear() == null}이면 기존과 100%
 * 동일하게 {@link MultiYearBacktestService#predictForQuery}(V0/baseline S0, targetYear=2026
 * 고정)를 호출한다 - 이미 {@code /budget-assistant}에 연결된 화면은 이 필드를 보내지 않으므로
 * 결과가 전혀 바뀌지 않는다. {@code planningYear}가 주어지면 새로 일반화된 {@link
 * MultiYearBacktestService#estimateForPlanning}(선정 전략 {@code MultiYearCandidateSelectorV1},
 * planningYear/{@link ReferenceDataPolicy} 지원)로 라우팅한다 - 두 경로 모두 CPI/series
 * correction/recency/COVID는 OFF/NONE으로 고정이다.</p>
 */
@Service
public class MultiYearExperimentalEstimateService {

    private static final String MODEL_BASELINE_S0 = "MULTIYEAR_BASELINE_S0";
    private static final String MODEL_CANDIDATE_SELECTOR_V1 = "MULTIYEAR_PLANNING_V1";

    private final MultiYearFestivalRecordRepository recordRepository;
    private final MultiYearBacktestService backtestService;
    private final MultiYearDatasetPublicationStatusRepository publicationStatusRepository;

    MultiYearExperimentalEstimateService(MultiYearFestivalRecordRepository recordRepository,
                                          MultiYearBacktestService backtestService,
                                          MultiYearDatasetPublicationStatusRepository publicationStatusRepository) {
        this.recordRepository = recordRepository;
        this.backtestService = backtestService;
        this.publicationStatusRepository = publicationStatusRepository;
    }

    /**
     * {@code /budget-assistant} 다년도 계획예산 분석 UI가 "기획연도" 선택지를 채우는 데 쓰는
     * 메타데이터 - planningYear 상수를 하드코딩하지 않고 DB의 최신 datasetYear를 기준으로
     * 계산한다(사용자 요청). 데이터가 아예 없으면 빈 목록을 반환한다.
     */
    public MultiYearPlanningMetadataResponse planningMetadata() {
        Integer maxDatasetYear = recordRepository.findMaxDatasetYear();
        if (maxDatasetYear == null) {
            return new MultiYearPlanningMetadataResponse(List.of(), null, List.of());
        }
        List<Integer> availableYears = List.of(maxDatasetYear, maxDatasetYear + 1);
        List<Integer> publishedYears = publicationStatusRepository.findAll().stream()
                .filter(s -> s.getStatus() == MultiYearDatasetPublicationStatusValue.PUBLISHED_PLAN_COMPLETE)
                .map(MultiYearDatasetPublicationStatus::getDatasetYear)
                .sorted()
                .toList();
        return new MultiYearPlanningMetadataResponse(availableYears, maxDatasetYear, publishedYears);
    }

    public MultiYearExperimentalEstimateResponse estimate(MultiYearExperimentalEstimateRequest request) {
        Region region = parseRegion(request.regionCode());
        FestivalType festivalType = parseFestivalType(request.festivalType());
        VenueType venueType = parseVenueType(request.venueType());
        String district = blankToNull(request.district());

        if (request.planningYear() == null) {
            return estimateLegacy(region, district, festivalType, venueType, request.durationDays());
        }
        return estimatePlanning(region, district, festivalType, venueType, request.durationDays(),
                request.planningYear(), request.referenceDataPolicy());
    }

    /** 기존 그대로 - V0/baseline S0, targetYear=2026 고정. 결과가 이전과 byte-identical해야 한다. */
    private MultiYearExperimentalEstimateResponse estimateLegacy(Region region, String district, FestivalType festivalType,
                                                                   VenueType venueType, Integer durationDays) {
        int targetYear = backtestService.predictionTargetYear();
        // leakage-safe + 성능: targetYear(2026) 이상 연도는 DB 쿼리 단계에서부터 제외한다(지시사항 17절) -
        // predictForQuery 내부의 MultiYearBacktestDatasetBuilder가 다시 한 번 같은 기준으로 걸러내므로
        // 이 사전 필터링이 leakage를 만들 위험은 없다(중복 안전장치일 뿐).
        List<MultiYearFestivalRecord> trainingCandidates = recordRepository.findByDatasetYearLessThan(targetYear);

        MultiYearPredictionResult result = backtestService.predictForQuery(
                region, district, Set.of(festivalType), venueType, durationDays, trainingCandidates);

        List<MultiYearSimilarFestivalDto> topSimilar = result.topCandidates().stream().map(this::toSimilarFestivalDto).toList();
        return new MultiYearExperimentalEstimateResponse(
                MODEL_BASELINE_S0, result.targetYear(), result.trainingYearFrom(), result.trainingYearTo(),
                result.estimatedBudgetKrw(), result.weightedAverageBudgetKrw(), result.recommendedBudgetKrw(),
                result.p25Krw(), result.p50Krw(), result.p75Krw(),
                result.sampleCount(), result.distinctYearsUsed(), result.earliestSourceYear(), result.latestSourceYear(),
                result.fallbackLevel(), result.averageSimilarity(), result.dataQualityV3(),
                MultiYearExperimentSettingsDto.BASELINE_S0, topSimilar,
                null, null, null, null
        );
    }

    /** planningYear 일반화 - MultiYearCandidateSelectorV1(V4 Hybrid), ReferenceDataPolicy 지원. */
    private MultiYearExperimentalEstimateResponse estimatePlanning(Region region, String district, FestivalType festivalType,
                                                                     VenueType venueType, Integer durationDays,
                                                                     int planningYear, String referenceDataPolicyRaw) {
        ReferenceDataPolicy requestedPolicy = parseReferenceDataPolicy(referenceDataPolicyRaw);
        // planningYear까지(포함) 미리 걸러 온다 - estimateForPlanning 내부의 MultiYearReferenceYearFilter가
        // 정확한 정책(< 또는 <=)을 다시 적용하므로 이 사전 필터링이 leakage를 만들지 않는다.
        List<MultiYearFestivalRecord> referenceCandidates = recordRepository.findByDatasetYearLessThanEqual(planningYear);

        MultiYearPlanningEstimateResult result = backtestService.estimateForPlanning(
                region, district, Set.of(festivalType), venueType, durationDays,
                planningYear, requestedPolicy, referenceCandidates);

        List<MultiYearSimilarFestivalDto> topSimilar = result.topCandidates().stream().map(this::toSimilarFestivalDto).toList();
        List<MultiYearPlanningYearWeightShareDto> yearWeightBreakdown = result.yearWeightBreakdown().stream()
                .map(this::toYearWeightShareDto)
                .toList();

        return new MultiYearExperimentalEstimateResponse(
                MODEL_CANDIDATE_SELECTOR_V1, result.planningYear(), result.referenceYearFrom(), result.referenceYearTo(),
                result.estimatedBudgetKrw(), result.weightedAverageBudgetKrw(), result.recommendedBudgetKrw(),
                result.p25Krw(), result.p50Krw(), result.p75Krw(),
                result.sampleCount(), result.distinctYearsUsed(), result.earliestSourceYear(), result.latestSourceYear(),
                result.fallbackLevel(), result.averageSimilarity(), result.dataQualityV3(),
                MultiYearExperimentSettingsDto.BASELINE_S0, topSimilar,
                result.requestedReferenceDataPolicy().name(), result.appliedReferenceDataPolicy().name(),
                result.effectiveYearCount(), yearWeightBreakdown
        );
    }

    private MultiYearSimilarFestivalDto toSimilarFestivalDto(MultiYearPredictionCandidate c) {
        return new MultiYearSimilarFestivalDto(
                c.sourceYear(), c.festivalName(), c.region(), c.district(), c.festivalType(), c.venueType(),
                c.durationDays(), c.originalBudgetKrw(), c.durationAdjustedBudgetKrw(), c.similarity(), c.finalWeight(),
                c.fallbackStage()
        );
    }

    private MultiYearPlanningYearWeightShareDto toYearWeightShareDto(MultiYearPlanningYearWeightShare y) {
        return new MultiYearPlanningYearWeightShareDto(y.year(), y.candidateCount(), y.weightShare());
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

    /** null/공백이면 HISTORICAL_ONLY(가장 안전한 기본값)로 취급한다. */
    private ReferenceDataPolicy parseReferenceDataPolicy(String raw) {
        if (raw == null || raw.isBlank()) {
            return ReferenceDataPolicy.HISTORICAL_ONLY;
        }
        try {
            return ReferenceDataPolicy.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("인식할 수 없는 referenceDataPolicy입니다: " + raw);
        }
    }
}