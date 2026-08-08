package com.festival.budgetassist.multiyear.backtest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.festival.budgetassist.estimate.AlgorithmConfig;
import com.festival.budgetassist.estimate.FallbackLevel;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;
import com.festival.budgetassist.multiyear.repository.MultiYearFestivalRecordRepository;

/**
 * leakage-safe 다년도 baseline backtest 오케스트레이터.
 *
 * <p>production {@link com.festival.budgetassist.estimate.BudgetEstimatorService}와 같은
 * 구조(계층형 fallback 후보 선정 -> 유사도 -> 기간보정 -> winsorize -> 가중통계 -> legacy
 * confidence -> 예비비 반영 추천예산)를 그대로 다년도 데이터에 적용하되, 이번 baseline
 * 단계에서는 추가 보정(물가/recency/COVID/series 중복/series cap/새 duration elasticity/
 * confidence threshold 변경)을 전혀 넣지 않는다(지시사항 3절). production
 * {@code BudgetEstimatorService}/{@code CandidateSelector}/{@code SimilarityCalculator}/
 * {@code ConfidenceCalculator}/{@code WeightedStatistics}/{@code DurationAdjuster} 코드는
 * 전혀 건드리지 않고 import하지도 않는다 - {@link AlgorithmConfig}(읽기 전용 공개 설정 Bean)만
 * 공유해서 같은 가중치/threshold 값을 쓴다.</p>
 *
 * <p>후보 선정({@link #selectFinalSample})과 통계 집계({@link #aggregate})를 분리해 둔 것은
 * {@link MultiYearSeriesCorrectionBacktestService}(festivalSeries 중복 보정 S0/S1/S2 비교
 * 실험)가 "정확히 같은 candidate selection 결과"를 재사용하면서 최종 weight만 다르게 넣어
 * 재집계할 수 있게 하기 위해서다 - 두 메서드는 package-private이라 같은 패키지 안에서만
 * 재사용된다.</p>
 */
@Service
public class MultiYearBacktestService {

    private final MultiYearFestivalRecordRepository recordRepository;
    private final MultiYearBacktestDatasetBuilder datasetBuilder;
    private final MultiYearCandidateSelector candidateSelector;
    private final MultiYearSimilarityCalculator similarityCalculator;
    private final MultiYearDataQualityV3Calculator v3Calculator;
    private final AnnualPriceIndexProvider priceIndexProvider;
    private final AlgorithmConfig config;

    MultiYearBacktestService(MultiYearFestivalRecordRepository recordRepository,
                              MultiYearBacktestDatasetBuilder datasetBuilder,
                              MultiYearCandidateSelector candidateSelector,
                              MultiYearSimilarityCalculator similarityCalculator,
                              MultiYearDataQualityV3Calculator v3Calculator,
                              AnnualPriceIndexProvider priceIndexProvider,
                              AlgorithmConfig config) {
        this.recordRepository = recordRepository;
        this.datasetBuilder = datasetBuilder;
        this.candidateSelector = candidateSelector;
        this.similarityCalculator = similarityCalculator;
        this.v3Calculator = v3Calculator;
        this.priceIndexProvider = priceIndexProvider;
        this.config = config;
    }

    /** {@link #runFold(List, MultiYearBacktestFold)}의 공개 진입점 - DB 전체를 한 번 읽어 fold별로 재사용한다. */
    public List<MultiYearFoldResult> runAllFolds() {
        List<MultiYearFestivalRecord> allRecords = recordRepository.findAll();
        List<MultiYearFoldResult> results = new ArrayList<>();
        for (MultiYearBacktestFold fold : MultiYearBacktestFold.all()) {
            results.add(runFold(allRecords, fold));
        }
        return results;
    }

    /**
     * @param allRecords DB의 전체 multi_year_festival_record (fold 안에서 leakage-safe하게
     *                    {@code datasetYear < targetYear}(training)/{@code == targetYear}(평가)로만 나뉜다)
     */
    MultiYearFoldResult runFold(List<MultiYearFestivalRecord> allRecords, MultiYearBacktestFold fold) {
        MultiYearBacktestDataset dataset = datasetBuilder.build(allRecords, fold);

        List<MultiYearBacktestPrediction> predictions = new ArrayList<>();
        int noFinalSample = 0;

        for (MultiYearFestivalRecord target : dataset.evalTargets()) {
            MultiYearBacktestPrediction prediction = predictOne(target, dataset.trainingPool());
            if (prediction == null) {
                noFinalSample++;
            } else {
                predictions.add(prediction);
            }
        }

        return new MultiYearFoldResult(fold, predictions, dataset.trainingPool().size(), dataset.evalTargets().size(),
                noFinalSample, dataset.trainingExcludedLowQuality(), dataset.trainingExcludedMissingFeature(),
                dataset.evalExcludedLowQuality(), dataset.evalExcludedMissingFeature());
    }

    private MultiYearBacktestPrediction predictOne(MultiYearFestivalRecord target, List<MultiYearFestivalRecord> trainingPool) {
        FinalSample fs = selectFinalSample(target, trainingPool);
        if (fs == null) {
            return null;
        }
        double[] weights = fs.finalSample().stream().mapToDouble(c -> c.score().weight()).toArray();
        return aggregate(target, fs, weights);
    }

    /** inflation 미적용 - 기존 baseline/series-correction 실험이 쓰는 기본 경로(하위호환, 동작 동일). */
    FinalSample selectFinalSample(MultiYearFestivalRecord target, List<MultiYearFestivalRecord> trainingPool) {
        return selectFinalSample(target, trainingPool, false);
    }

    /**
     * 후보 선정 단계(계층형 fallback -> 유사도 -> [물가보정] -> 기간보정 -> winsorize -> threshold+상위
     * N건) - series correction/inflation 여부와 무관하게 항상 같은 후보가 선정돼야 한다(지시사항,
     * "series correction/inflation 때문에 CandidateSelector 자체가 다른 후보를 뽑도록 하지 마").
     * similarity는 budget과 무관한 feature(유형/지역/장소/기간)만으로 계산되므로 이 조건은 자동으로
     * 만족된다 - inflation은 오직 각 candidate의 "budget 값"에만 영향을 주고, 정렬/상한 컷 기준인
     * {@code score.weight()}(=similarity^2)는 전혀 건드리지 않는다.
     *
     * <p><b>적용 순서</b>: 물가보정을 기간보정보다 먼저 적용한다. 두 보정 모두 budget에 대한
     * 곱셈 스케일링이라 순서 자체는 최종 candidate 값에 영향이 없지만(교환법칙), winsorize
     * 모집단(같은 유형의 training 전체 log-budget 분포)은 반드시 "같은 처리를 거친" 값으로
     * 만들어야 한다 - 그래서 물가보정 켜짐 상태에서는 winsorize 모집단도 물가보정된 budget으로
     * 다시 계산한다(그렇지 않으면 인플레이션으로 전체적으로 커진 candidate 값을, 인플레이션
     * 적용 전 기준으로 만들어진 낮은 상한에 부당하게 clip하게 된다).</p>
     *
     * @param applyInflation true면 candidate의 실제 budget을 target의 datasetYear 가격 수준으로
     *                        환산한 뒤(inflationAdjustedBudget = raw * CPI(targetYear)/CPI(candidateYear))
     *                        이후 단계를 진행한다. CPI(candidateYear)는 항상 training 기간(따라서
     *                        targetYear보다 이른 연도)의 값만 참조한다 - target year 이후 CPI는
     *                        구조적으로 조회 자체가 불가능하다(트레이닝 풀 자체가 이미 그렇게 필터링됨).
     */
    FinalSample selectFinalSample(MultiYearFestivalRecord target, List<MultiYearFestivalRecord> trainingPool, boolean applyInflation) {
        MultiYearBacktestQuery query = MultiYearBacktestQuery.from(target);
        int targetYear = target.getDatasetYear();

        MultiYearCandidateSelectionResult selection = candidateSelector.select(trainingPool, query);
        if (selection.candidates().isEmpty()) {
            return null;
        }

        // winsorize 기준값: 같은 축제유형(overlap) "전체" training 모집단(선정된 후보군이 아니라
        // training 전체)에서 구한다 - production과 동일한 설계, training 기간만 쓰므로 leakage 없음.
        // applyInflation=true면 이 모집단도 물가보정된 값으로 통일한다(위 Javadoc 설명).
        double[] typePopulationLogBudgets = trainingPool.stream()
                .filter(r -> MultiYearFeatureResolver.typesOverlap(query.typeTokens(), MultiYearFeatureResolver.resolveTypeTokens(r)))
                .mapToDouble(r -> Math.log(inflationAdjustedBudget(r, targetYear, applyInflation)))
                .toArray();
        double lowerLogBound = typePopulationLogBudgets.length > 0
                ? MultiYearBacktestMath.quantile(typePopulationLogBudgets, config.getWinsorizeLowerPercentile())
                : Double.NEGATIVE_INFINITY;
        double upperLogBound = typePopulationLogBudgets.length > 0
                ? MultiYearBacktestMath.quantile(typePopulationLogBudgets, config.getWinsorizeUpperPercentile())
                : Double.POSITIVE_INFINITY;

        List<MultiYearScoredCandidate> scored = new ArrayList<>();
        for (MultiYearFestivalRecord candidate : selection.candidates()) {
            MultiYearSimilarityScore score = similarityCalculator.compute(query, candidate);
            double candidateBudgetKrw = inflationAdjustedBudget(candidate, targetYear, applyInflation);
            double adjustedBudget = query.durationDays() != null
                    ? durationAdjust(candidateBudgetKrw, candidate.getDurationDays(), query.durationDays())
                    : candidateBudgetKrw;
            double winsorizedBudget = Math.exp(MultiYearBacktestMath.clip(Math.log(adjustedBudget), lowerLogBound, upperLogBound));
            FallbackLevel origin = selection.originLevelByRecordId().get(candidate.getId());
            scored.add(new MultiYearScoredCandidate(candidate, score, adjustedBudget, winsorizedBudget, origin));
        }

        List<MultiYearScoredCandidate> finalSample = scored.stream()
                .filter(c -> c.score().similarity() >= config.getSimilarityMinThreshold())
                .sorted(Comparator.comparingDouble((MultiYearScoredCandidate c) -> c.score().weight()).reversed())
                .limit(config.getMaxSampleCount())
                .toList();

        if (finalSample.isEmpty()) {
            return null;
        }
        return new FinalSample(query, selection, finalSample);
    }

    /**
     * candidate의 원본 budget(원)을 targetYear 가격 수준으로 환산한다(지시사항 1절: {@code
     * inflationAdjustedBudget = originalBudget * CPI(Y)/CPI(t)}). {@code applyInflation=false}면
     * 원본 그대로 반환한다(A/B 변형과 완전히 동일한 값). CPI 테이블에 해당 연도가 없으면(이번
     * 2017~2026 데이터셋에서는 발생하지 않지만 방어적으로) 보정 없이 원본을 그대로 쓴다.
     */
    private double inflationAdjustedBudget(MultiYearFestivalRecord record, int targetYear, boolean applyInflation) {
        long raw = MultiYearFeatureResolver.budgetKrw(record);
        if (!applyInflation) {
            return raw;
        }
        return raw * inflationFactor(targetYear, record.getDatasetYear());
    }

    /** CPI(targetYear)/CPI(candidateYear). 어느 한쪽이라도 CPI 테이블에 없으면 1.0(보정 없음)을 반환한다. */
    private double inflationFactor(int targetYear, int candidateYear) {
        Optional<AnnualPriceIndex> targetCpi = priceIndexProvider.get(targetYear);
        Optional<AnnualPriceIndex> candidateCpi = priceIndexProvider.get(candidateYear);
        if (targetCpi.isEmpty() || candidateCpi.isEmpty()) {
            return 1.0;
        }
        return targetCpi.get().indexValue() / candidateCpi.get().indexValue();
    }

    /**
     * {@link #selectFinalSample}이 고정해 놓은 finalSample을 주어진 weight 배열(순서는
     * {@code fs.finalSample()}과 동일)로 집계한다. baseline은 항상 원본 {@code score.weight()}를
     * 그대로 넣고, series correction 실험은 {@code correctedWeight = weight * seriesFactor}로
     * 바꿔치기한 배열을 넣는다 - 그 외 공식(기간보정/winsorize/후보선정/legacy confidence 구조)은
     * 전혀 건드리지 않는다.
     */
    MultiYearBacktestPrediction aggregate(MultiYearFestivalRecord target, FinalSample fs, double[] weights) {
        List<MultiYearScoredCandidate> finalSample = fs.finalSample();
        MultiYearBacktestQuery query = fs.query();
        MultiYearCandidateSelectionResult selection = fs.selection();

        int sampleCount = finalSample.size();
        double[] values = finalSample.stream().mapToDouble(MultiYearScoredCandidate::winsorizedBudgetKrw).toArray();
        double[] similarities = finalSample.stream().mapToDouble(c -> c.score().similarity()).toArray();
        double[] hasDurationFlags = finalSample.stream().mapToDouble(c -> c.record().getDurationDays() != null ? 1.0 : 0.0).toArray();

        double weightedAverage = MultiYearBacktestMath.weightedMean(values, weights);
        double estimated = MultiYearBacktestMath.weightedGeometricMean(values, weights);
        double p25 = MultiYearBacktestMath.weightedQuantile(values, weights, 0.25);
        double p50 = MultiYearBacktestMath.weightedQuantile(values, weights, 0.50);
        double p60 = MultiYearBacktestMath.weightedQuantile(values, weights, config.getRecommendedBasePercentile());
        double p75 = MultiYearBacktestMath.weightedQuantile(values, weights, 0.75);

        double similarityScoreAvg = MultiYearBacktestMath.weightedMean(similarities, weights);
        double completenessScore = MultiYearBacktestMath.weightedMean(hasDurationFlags, weights);

        double legacyConfidenceScore = legacyConfidenceScore(sampleCount, similarityScoreAvg, p25, p50, p75, completenessScore);
        double contingencyRate = config.getContingencyBaseRate()
                + (1 - legacyConfidenceScore / 100.0) * config.getContingencyMaxExtraRate();
        double recommendedBase = Math.max(estimated, p60);
        double recommendedBudget = recommendedBase * (1 + contingencyRate);

        double effectiveSampleSize = MultiYearBacktestMath.effectiveSampleSize(weights);
        List<FallbackLevel> originLevels = finalSample.stream().map(MultiYearScoredCandidate::originLevel).toList();
        MultiYearDataQualityV3 v3 = v3Calculator.compute(effectiveSampleSize, similarityScoreAvg, p25, p75,
                completenessScore, originLevels, weights, query.district() != null);

        long distinctSeriesCount = MultiYearBacktestSeriesDiagnostics.distinctSeriesCount(
                finalSample.stream().map(MultiYearScoredCandidate::record).toList());

        long actualBudget = MultiYearFeatureResolver.budgetKrw(target);
        long estimatedRounded = Math.round(estimated);
        // p25/p75는 winsorize의 log-exp 왕복 계산 때문에 이론상 정수인 값도 부동소수점 잡음(예:
        // 99999999.99999997)이 낄 수 있다 - 리포트/CSV에 노출하는 값과 typicalRangeCoverage 판정
        // 기준을 반드시 같은(반올림된) 값으로 맞춰야 "표에 보이는 P25~P75 범위인데 coverage=false"처럼
        // 모순되게 보이는 걸 막을 수 있다.
        long p25Rounded = Math.round(p25);
        long p75Rounded = Math.round(p75);
        double absoluteError = Math.abs((double) estimatedRounded - actualBudget);
        double absolutePercentageError = actualBudget != 0 ? absoluteError / actualBudget : Double.NaN;
        double absoluteLogError = (estimated > 0 && actualBudget > 0)
                ? Math.abs(Math.log(estimated) - Math.log(actualBudget)) : Double.NaN;
        boolean typicalRangeCoverage = actualBudget >= p25Rounded && actualBudget <= p75Rounded;

        return new MultiYearBacktestPrediction(
                target.getDatasetYear(), target.getId(), target.getFestivalName(),
                query.regionCode() != null ? query.regionCode().name() : null, query.district(),
                target.getFestivalType(), target.getVenueType() != null ? target.getVenueType().name() : null,
                target.getDurationDays(), actualBudget, estimatedRounded, Math.round(weightedAverage),
                Math.round(recommendedBudget), p25Rounded, p75Rounded, sampleCount, distinctSeriesCount,
                selection.level().name(), v3.score(), typicalRangeCoverage,
                absoluteError, absolutePercentageError, absoluteLogError
        );
    }

    /**
     * production {@code DurationAdjuster.adjust}와 동일한 공식 - 클래스가 package-private이라 포팅.
     * sourceBudgetKrw를 {@code long}이 아니라 {@code double}로 받는다 - inflation 보정이 켜지면
     * CPI 비율(정수 아님) 곱셈을 거친 값이 들어올 수 있기 때문이다.
     */
    private double durationAdjust(double sourceBudgetKrw, Integer sourceDurationDays, int targetDurationDays) {
        if (sourceDurationDays == null || sourceDurationDays <= 0) {
            return sourceBudgetKrw;
        }
        double rawRatio = targetDurationDays / (double) sourceDurationDays;
        double clampedRatio = MultiYearBacktestMath.clip(rawRatio, config.getDurationRatioClampMin(), config.getDurationRatioClampMax());
        return sourceBudgetKrw * Math.pow(clampedRatio, config.getDurationElasticity());
    }

    /**
     * production {@code ConfidenceCalculator.calculate}와 동일한 공식(legacy) - recommendedBudget의
     * 예비비율 계산에만 쓰인다(지시사항 11절: production confidence는 legacy 유지, 이 backtest는
     * legacy와 같은 공식을 재사용할 뿐 production 클래스를 수정하지 않는다). 등급(HIGH/MEDIUM/LOW)은
     * 이 backtest에서 전혀 쓰지 않으므로 점수만 반환한다.
     */
    private double legacyConfidenceScore(int sampleCount, double weightedSimilarityAvg, double p25, double p50, double p75,
                                          double completenessScore) {
        double sampleScore = Math.min(sampleCount / config.getConfidenceSampleScoreDivisor(), 1.0);
        double cap = config.getConfidenceDispersionCap();
        double dispersionRatio = Math.min((p75 - p25) / Math.max(p50, 1), cap) / cap;
        double stabilityScore = 1 - dispersionRatio;

        double confidenceRatio = sampleScore * config.getConfidenceSampleWeight()
                + weightedSimilarityAvg * config.getConfidenceSimilarityWeight()
                + stabilityScore * config.getConfidenceStabilityWeight()
                + completenessScore * config.getConfidenceCompletenessWeight();
        double score = confidenceRatio * 100;
        if (sampleCount < config.getConfidenceLowSampleCap()) {
            score = Math.min(score, config.getConfidenceLowSampleCapScore());
        }
        return score;
    }

    /** {@link #selectFinalSample}의 결과 - 후보선정+유사도+winsorize까지 끝난, 재집계 준비가 된 표본. */
    record FinalSample(MultiYearBacktestQuery query, MultiYearCandidateSelectionResult selection,
                        List<MultiYearScoredCandidate> finalSample) {
    }
}