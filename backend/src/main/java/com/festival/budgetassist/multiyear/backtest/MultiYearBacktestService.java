package com.festival.budgetassist.multiyear.backtest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.festival.budgetassist.estimate.AlgorithmConfig;
import com.festival.budgetassist.estimate.FallbackLevel;
import com.festival.budgetassist.festival.domain.FestivalType;
import com.festival.budgetassist.festival.domain.Region;
import com.festival.budgetassist.festival.domain.VenueType;
import com.festival.budgetassist.multiyear.domain.MultiYearDatasetPublicationStatus;
import com.festival.budgetassist.multiyear.domain.MultiYearDatasetPublicationStatusValue;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;
import com.festival.budgetassist.multiyear.repository.MultiYearDatasetPublicationStatusRepository;
import com.festival.budgetassist.multiyear.repository.MultiYearFestivalRecordRepository;

/**
 * leakage-safe 다년도 baseline backtest 오케스트레이터 - 그리고 {@link #predictForQuery}를 통해
 * 같은 순수 계산 경로를 "실제 사용자 입력 1건에 대한 즉석 예측"에도 재사용할 수 있게 공개한다
 * ({@code multiyear.experimental} 패키지의 {@code MultiYearExperimentalEstimateService}가
 * 이 메서드를 호출한다 - 자세한 설명은 그 클래스 Javadoc 참고).
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
 * <p>후보 선정({@link #selectFinalSample})과 통계 집계({@link #computeCoreStats})를 분리해 둔
 * 것은 {@link MultiYearSeriesCorrectionBacktestService}(festivalSeries 중복 보정 S0/S1/S2
 * 비교 실험)가 "정확히 같은 candidate selection 결과"를 재사용하면서 최종 weight만 다르게 넣어
 * 재집계할 수 있게 하기 위해서다 - 두 메서드는 package-private이라 같은 패키지 안에서만
 * 재사용된다. {@link #predictForQuery}만 공개(public) API로, 이 계산 core를 외부(experimental
 * API 등)에 노출하되 backtest 내부 타입({@code FinalSample}/{@code MultiYearScoredCandidate}
 * 등)은 전혀 새어나가지 않고 새 공개 DTO({@link MultiYearPredictionResult})로만 반환한다.</p>
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
    private final MultiYearReferenceYearFilter referenceYearFilter;
    private final MultiYearCandidateSelectorV1 candidateSelectorV1;
    private final MultiYearDatasetPublicationStatusRepository publicationStatusRepository;

    MultiYearBacktestService(MultiYearFestivalRecordRepository recordRepository,
                              MultiYearBacktestDatasetBuilder datasetBuilder,
                              MultiYearCandidateSelector candidateSelector,
                              MultiYearSimilarityCalculator similarityCalculator,
                              MultiYearDataQualityV3Calculator v3Calculator,
                              AnnualPriceIndexProvider priceIndexProvider,
                              AlgorithmConfig config,
                              MultiYearReferenceYearFilter referenceYearFilter,
                              MultiYearCandidateSelectorV1 candidateSelectorV1,
                              MultiYearDatasetPublicationStatusRepository publicationStatusRepository) {
        this.recordRepository = recordRepository;
        this.datasetBuilder = datasetBuilder;
        this.candidateSelector = candidateSelector;
        this.similarityCalculator = similarityCalculator;
        this.v3Calculator = v3Calculator;
        this.priceIndexProvider = priceIndexProvider;
        this.config = config;
        this.referenceYearFilter = referenceYearFilter;
        this.candidateSelectorV1 = candidateSelectorV1;
        this.publicationStatusRepository = publicationStatusRepository;
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

    // ------------------------------------------------------------------
    // 실제 사용자 입력 1건에 대한 즉석 예측 - /budget-assistant "다년도 실험 분석" 영역이 쓴다.
    // ------------------------------------------------------------------

    /**
     * {@link #predictForQuery}가 사용하는 targetYear(현재 고정값 2026). 호출자(예: 실험 API
     * 서비스)가 이 값보다 이른 record만 미리 repository 쿼리 단계에서 걸러 오도록(성능) 노출한다 -
     * "2026"을 여러 곳에 따로 하드코딩해 값이 어긋날 위험을 없앤다.
     */
    public int predictionTargetYear() {
        return MultiYearBacktestFold.PRIMARY_2026.targetYear();
    }

    /**
     * 실제 backtest(evaluation against a known historical target)가 아니라, "이 조건으로
     * targetYear에 축제를 연다면"이라는 가상의 요청 1건에 대한 예측이다 - 그래서 target 자체의
     * "실제 예산"이 없다(아직 열리지 않은 축제이므로) - {@link #aggregate}가 계산하는
     * absoluteError/APE/ALE/typicalRangeCoverage 같은 "정답과 비교하는" 지표는 여기 전혀
     * 등장하지 않는다. candidate selection/유사도/기간보정/winsorize/가중통계 공식은
     * {@link #selectFinalSample}/{@link #computeCoreStats}를 그대로 재사용한다 - 이 메서드
     * 자체는 그 두 메서드를 호출/조립하는 얇은 wrapper일 뿐, 새 계산식을 추가하지 않는다.
     *
     * <p>festivalSeries는 이 경로 어디에서도 쓰이지 않는다(과거 series budget을 직접 참조하는
     * 건 production에 없는 정보를 쓰는 leakage이므로 애초에 config 대상이 아니다) - inflation도
     * 항상 OFF로 고정 호출된다(seriesCorrection/inflation/recency/COVID는 전부 이번 기능의
     * 범위 밖).</p>
     *
     * @param recordsBeforeTargetYear 이미 leakage-safe하게 {@code datasetYear < targetYear}로
     *                                걸러진 record 목록(호출자가 repository 쿼리 단계에서 걸러
     *                                오는 것을 권장 - {@link #runAllFolds}처럼 전체를 넘겨도
     *                                동작은 같지만 불필요하게 느리다. {@link
     *                                MultiYearBacktestDatasetBuilder}가 어차피 다시 한 번
     *                                datasetYear/데이터품질/feature 결측 기준으로 걸러내므로
     *                                안전하다 - 이 메서드 자체가 leakage를 만들지 않는다).
     */
    public MultiYearPredictionResult predictForQuery(Region regionCode, String district, Set<FestivalType> festivalTypeTokens,
                                                       VenueType venueType, Integer durationDays,
                                                       List<MultiYearFestivalRecord> recordsBeforeTargetYear) {
        MultiYearBacktestFold fold = MultiYearBacktestFold.PRIMARY_2026; // targetYear=2026, training 2017~2025(고정)
        int targetYear = fold.targetYear();
        int trainingYearFrom = 2017;
        int trainingYearTo = fold.trainCutoffYearInclusive();

        MultiYearBacktestDataset dataset = datasetBuilder.build(recordsBeforeTargetYear, fold);
        List<MultiYearFestivalRecord> trainingPool = dataset.trainingPool();

        MultiYearBacktestQuery query = new MultiYearBacktestQuery(regionCode, district, festivalTypeTokens, venueType, durationDays);
        FinalSample fs = selectFinalSample(query, targetYear, trainingPool, false);
        if (fs == null) {
            return MultiYearPredictionResult.empty(targetYear, trainingYearFrom, trainingYearTo);
        }

        double[] weights = fs.finalSample().stream().mapToDouble(c -> c.score().weight()).toArray();
        CoreStats stats = computeCoreStats(fs, weights);

        List<MultiYearFestivalRecord> sampleRecords = fs.finalSample().stream().map(MultiYearScoredCandidate::record).toList();
        int distinctYearsUsed = (int) sampleRecords.stream().map(MultiYearFestivalRecord::getDatasetYear).distinct().count();
        int earliestSourceYear = sampleRecords.stream().mapToInt(MultiYearFestivalRecord::getDatasetYear).min().orElseThrow();
        int latestSourceYear = sampleRecords.stream().mapToInt(MultiYearFestivalRecord::getDatasetYear).max().orElseThrow();

        List<MultiYearPredictionCandidate> topCandidates = fs.finalSample().stream()
                .limit(10)
                .map(this::toPredictionCandidate)
                .toList();

        return new MultiYearPredictionResult(
                targetYear, trainingYearFrom, trainingYearTo,
                Math.round(stats.estimated()), Math.round(stats.weightedAverage()), Math.round(stats.recommendedBudget()),
                Math.round(stats.p25()), Math.round(stats.p50()), Math.round(stats.p75()),
                stats.sampleCount(), distinctYearsUsed, earliestSourceYear, latestSourceYear,
                fs.selection().level().name(), stats.similarityScoreAvg(), stats.v3().score(),
                topCandidates
        );
    }

    // ------------------------------------------------------------------
    // planningYear 일반화 - Budget Planning Assistant 전용 진입점(2절/7~13절). predictForQuery
    // (V0, targetYear=2026 고정)와는 완전히 독립적이다 - 서로 다른 selector/다른 연도 범위를 쓴다.
    // ------------------------------------------------------------------

    /**
     * planningYear를 직접 받는 일반화된 예측 진입점. {@link #predictForQuery}와의 차이:
     * <ul>
     *   <li>{@link MultiYearBacktestFold} 상수(연도마다 새로 추가해야 하는 backtest 전용 값)에
     *       전혀 의존하지 않는다 - planningYear가 몇 년이든(2026, 2027, ...) 코드 변경 없이
     *       동작한다.</li>
     *   <li>참고 데이터 범위는 {@link ReferenceDataPolicy}로 결정하고, {@link
     *       MultiYearReferenceYearFilter}가 leakage-safe backtest 코드({@link
     *       MultiYearBacktestDatasetBuilder})와 완전히 분리된 자체 필터로 연도를 자른다.</li>
     *   <li>candidate selection은 V0가 아니라 selector lab에서 확정한 {@link
     *       MultiYearCandidateSelectorV1}을 쓴다. 그 이후(유사도/기간보정/winsorize/threshold+
     *       상위 N건 컷)는 {@link #scoreAndFinalize}로 baseline과 동일한 공식을 그대로 쓴다.</li>
     * </ul>
     *
     * @param requestedPolicy 호출자가 요청한 정책 - {@code INCLUDE_PUBLISHED_SAME_YEAR}인데
     *                        planningYear 데이터셋이 아직 공개 완료로 표시돼 있지 않으면 자동으로
     *                        {@code HISTORICAL_ONLY}로 낮춰 적용한다(같은 연도 데이터를 무조건
     *                        포함하지 않는다 - 결과의 {@code appliedReferenceDataPolicy}로 확인 가능).
     * @param allRecords 필터링 전 전체 record - planningYear보다 미래인 record가 섞여 있어도 이
     *                   메서드가 절대 포함시키지 않는다(leakage-safe, {@link
     *                   MultiYearReferenceYearFilter} 참고).
     */
    public MultiYearPlanningEstimateResult estimateForPlanning(Region regionCode, String district, Set<FestivalType> festivalTypeTokens,
                                                                 VenueType venueType, Integer durationDays,
                                                                 int planningYear, ReferenceDataPolicy requestedPolicy,
                                                                 List<MultiYearFestivalRecord> allRecords) {
        ReferenceDataPolicy appliedPolicy = resolveEffectivePolicy(planningYear, requestedPolicy);
        boolean includeSameYear = appliedPolicy == ReferenceDataPolicy.INCLUDE_PUBLISHED_SAME_YEAR;
        List<MultiYearFestivalRecord> referencePool = referenceYearFilter.filter(allRecords, planningYear, includeSameYear);

        int referenceYearFrom = referencePool.stream().mapToInt(MultiYearFestivalRecord::getDatasetYear).min().orElse(planningYear);
        int referenceYearTo = includeSameYear ? planningYear : planningYear - 1;

        MultiYearBacktestQuery query = new MultiYearBacktestQuery(regionCode, district, festivalTypeTokens, venueType, durationDays);
        FinalSample fs = selectFinalSample(candidateSelectorV1, query, planningYear, referencePool, false);
        if (fs == null) {
            return MultiYearPlanningEstimateResult.empty(planningYear, requestedPolicy, appliedPolicy, referenceYearFrom, referenceYearTo);
        }

        double[] weights = fs.finalSample().stream().mapToDouble(c -> c.score().weight()).toArray();
        CoreStats stats = computeCoreStats(fs, weights);

        List<MultiYearFestivalRecord> sampleRecords = fs.finalSample().stream().map(MultiYearScoredCandidate::record).toList();
        int distinctYearsUsed = (int) sampleRecords.stream().map(MultiYearFestivalRecord::getDatasetYear).distinct().count();
        Integer earliestSourceYear = sampleRecords.isEmpty() ? null
                : sampleRecords.stream().mapToInt(MultiYearFestivalRecord::getDatasetYear).min().getAsInt();
        Integer latestSourceYear = sampleRecords.isEmpty() ? null
                : sampleRecords.stream().mapToInt(MultiYearFestivalRecord::getDatasetYear).max().getAsInt();

        double effectiveYearCount = effectiveYearCount(fs.finalSample(), weights);
        List<MultiYearPlanningYearWeightShare> yearWeightBreakdown = yearWeightBreakdown(fs.finalSample(), weights);

        List<MultiYearPredictionCandidate> topCandidates = fs.finalSample().stream()
                .limit(10)
                .map(this::toPredictionCandidate)
                .toList();

        return new MultiYearPlanningEstimateResult(
                planningYear, requestedPolicy, appliedPolicy, referenceYearFrom, referenceYearTo,
                Math.round(stats.estimated()), Math.round(stats.weightedAverage()), Math.round(stats.recommendedBudget()),
                Math.round(stats.p25()), Math.round(stats.p50()), Math.round(stats.p75()),
                stats.sampleCount(), distinctYearsUsed, effectiveYearCount, earliestSourceYear, latestSourceYear,
                fs.selection().level().name(), stats.similarityScoreAvg(), stats.v3().score(),
                yearWeightBreakdown, topCandidates
        );
    }

    /**
     * {@code INCLUDE_PUBLISHED_SAME_YEAR}를 요청해도 해당 planningYear의 {@link
     * MultiYearDatasetPublicationStatus}가 {@code PUBLISHED_COMPLETE}로 표시돼 있지 않으면
     * {@code HISTORICAL_ONLY}로 낮춰 적용한다(9절: "같은 연도 데이터를 무조건 포함하면 안 된다").
     * 행이 아예 없는 연도는 PARTIAL과 동일하게 취급한다(안전한 기본값).
     */
    private ReferenceDataPolicy resolveEffectivePolicy(int planningYear, ReferenceDataPolicy requested) {
        if (requested != ReferenceDataPolicy.INCLUDE_PUBLISHED_SAME_YEAR) {
            return ReferenceDataPolicy.HISTORICAL_ONLY;
        }
        Optional<MultiYearDatasetPublicationStatus> status = publicationStatusRepository.findByDatasetYear(planningYear);
        boolean publishedComplete = status.isPresent()
                && status.get().getStatus() == MultiYearDatasetPublicationStatusValue.PUBLISHED_COMPLETE;
        return publishedComplete ? ReferenceDataPolicy.INCLUDE_PUBLISHED_SAME_YEAR : ReferenceDataPolicy.HISTORICAL_ONLY;
    }

    /** Simpson effective number - 진단값이 아니라 응답 필드로 노출한다(13절: "사용자가 실제로 어느 연도 데이터를 얼마나 참고했는지"). */
    private double effectiveYearCount(List<MultiYearScoredCandidate> finalSample, double[] weights) {
        Map<Integer, Double> weightByYear = new LinkedHashMap<>();
        double total = 0;
        for (int i = 0; i < finalSample.size(); i++) {
            int year = finalSample.get(i).record().getDatasetYear();
            weightByYear.merge(year, weights[i], Double::sum);
            total += weights[i];
        }
        if (total <= 0) {
            return 0.0;
        }
        double sumSquaredShare = 0;
        for (double w : weightByYear.values()) {
            double share = w / total;
            sumSquaredShare += share * share;
        }
        return sumSquaredShare > 0 ? 1.0 / sumSquaredShare : 0.0;
    }

    private List<MultiYearPlanningYearWeightShare> yearWeightBreakdown(List<MultiYearScoredCandidate> finalSample, double[] weights) {
        Map<Integer, Long> countByYear = new TreeMap<>();
        Map<Integer, Double> weightByYear = new TreeMap<>();
        double total = 0;
        for (int i = 0; i < finalSample.size(); i++) {
            int year = finalSample.get(i).record().getDatasetYear();
            countByYear.merge(year, 1L, Long::sum);
            weightByYear.merge(year, weights[i], Double::sum);
            total += weights[i];
        }
        double finalTotal = total;
        List<MultiYearPlanningYearWeightShare> result = new ArrayList<>();
        countByYear.forEach((year, count) -> {
            double share = finalTotal > 0 ? weightByYear.getOrDefault(year, 0.0) / finalTotal : 0.0;
            result.add(new MultiYearPlanningYearWeightShare(year, count.intValue(), share));
        });
        return result;
    }

    private MultiYearPredictionCandidate toPredictionCandidate(MultiYearScoredCandidate c) {
        MultiYearFestivalRecord r = c.record();
        return new MultiYearPredictionCandidate(
                r.getDatasetYear(), r.getFestivalName(),
                r.getRegionCode() != null ? r.getRegionCode().name() : null,
                MultiYearFeatureResolver.resolveDistrict(r),
                r.getFestivalType(),
                r.getVenueType() != null ? r.getVenueType().name() : null,
                r.getDurationDays(),
                MultiYearFeatureResolver.budgetKrw(r),
                Math.round(c.adjustedBudgetKrw()),
                c.score().similarity(), c.score().weight(),
                c.originLevel() != null ? c.originLevel().name() : null
        );
    }

    /** inflation 미적용 - 기존 baseline/series-correction 실험이 쓰는 기본 경로(하위호환, 동작 동일). */
    FinalSample selectFinalSample(MultiYearFestivalRecord target, List<MultiYearFestivalRecord> trainingPool) {
        return selectFinalSample(target, trainingPool, false);
    }

    /** inflation 파라미터 포함 오버로드 - {@link MultiYearBacktestQuery#from}으로 target을 query로 변환한 뒤 core에 위임한다. */
    FinalSample selectFinalSample(MultiYearFestivalRecord target, List<MultiYearFestivalRecord> trainingPool, boolean applyInflation) {
        return selectFinalSample(MultiYearBacktestQuery.from(target), target.getDatasetYear(), trainingPool, applyInflation);
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
     * <p>query/targetYear를 직접 받는 이 형태가 core다 - {@link #predictForQuery}(실제 target
     * record가 없는 즉석 예측)와 target-record 기반 오버로드(backtest 평가) 둘 다 여기로
     * 수렴한다.</p>
     *
     * @param applyInflation true면 candidate의 실제 budget을 targetYear 가격 수준으로
     *                        환산한 뒤(inflationAdjustedBudget = raw * CPI(targetYear)/CPI(candidateYear))
     *                        이후 단계를 진행한다. CPI(candidateYear)는 항상 training 기간(따라서
     *                        targetYear보다 이른 연도)의 값만 참조한다 - target year 이후 CPI는
     *                        구조적으로 조회 자체가 불가능하다(트레이닝 풀 자체가 이미 그렇게 필터링됨).
     */
    FinalSample selectFinalSample(MultiYearBacktestQuery query, int targetYear, List<MultiYearFestivalRecord> trainingPool,
                                   boolean applyInflation) {
        MultiYearCandidateSelectionResult selection = candidateSelector.select(trainingPool, query);
        if (selection.candidates().isEmpty()) {
            return null;
        }
        return scoreAndFinalize(query, targetYear, trainingPool, applyInflation, selection);
    }

    /**
     * selector lab 전용 추가 진입점 - {@code candidateSelector}(V0, production/실험 API가 실제로
     * 쓰는 유일한 경로) 대신 임의의 {@link MultiYearCandidateSelectionStrategy}(V1~V4 등)로 후보를
     * 선정한 뒤, 이후 채점 단계는 {@link #scoreAndFinalize}로 baseline과 완전히 동일하게 위임한다.
     * production/{@code multiyear.experimental} 어디에서도 이 오버로드를 호출하지 않는다 -
     * CandidateSelector concentration 분석/backtest 비교({@code MultiYearSelectorLab*})만 쓴다.
     */
    FinalSample selectFinalSample(MultiYearCandidateSelectionStrategy strategy, MultiYearBacktestQuery query, int targetYear,
                                   List<MultiYearFestivalRecord> trainingPool, boolean applyInflation) {
        MultiYearCandidateSelectionResult selection = strategy.select(trainingPool, query);
        if (selection.candidates().isEmpty()) {
            return null;
        }
        return scoreAndFinalize(query, targetYear, trainingPool, applyInflation, selection);
    }

    /** {@link #selectFinalSample(MultiYearCandidateSelectionStrategy, MultiYearBacktestQuery, int, List, boolean)}의 target-record 기반 편의 오버로드(selector lab backtest 전용, inflation 항상 OFF). */
    FinalSample selectFinalSample(MultiYearCandidateSelectionStrategy strategy, MultiYearFestivalRecord target,
                                   List<MultiYearFestivalRecord> trainingPool) {
        return selectFinalSample(strategy, MultiYearBacktestQuery.from(target), target.getDatasetYear(), trainingPool, false);
    }

    /**
     * 이미 선정이 끝난 {@code selection}(어떤 전략으로 만들어졌든 상관없이)을 받아 유사도 -&gt;
     * [물가보정] -&gt; 기간보정 -&gt; winsorize -&gt; threshold+상위 N건 컷까지 baseline과 완전히
     * 동일한 공식으로 채점한다. {@link #selectFinalSample(MultiYearBacktestQuery, int, List,
     * boolean)}(V0 경로)와 selector lab 경로 둘 다 이 메서드로 수렴한다 - "선정 전략이 달라도
     * 채점 공식은 절대 바뀌지 않는다"를 코드 구조로 보장한다.
     */
    private FinalSample scoreAndFinalize(MultiYearBacktestQuery query, int targetYear, List<MultiYearFestivalRecord> trainingPool,
                                          boolean applyInflation, MultiYearCandidateSelectionResult selection) {
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
        CoreStats stats = computeCoreStats(fs, weights);
        MultiYearBacktestQuery query = fs.query();
        MultiYearCandidateSelectionResult selection = fs.selection();

        long distinctSeriesCount = MultiYearBacktestSeriesDiagnostics.distinctSeriesCount(
                fs.finalSample().stream().map(MultiYearScoredCandidate::record).toList());

        long actualBudget = MultiYearFeatureResolver.budgetKrw(target);
        long estimatedRounded = Math.round(stats.estimated());
        // p25/p75는 winsorize의 log-exp 왕복 계산 때문에 이론상 정수인 값도 부동소수점 잡음(예:
        // 99999999.99999997)이 낄 수 있다 - 리포트/CSV에 노출하는 값과 typicalRangeCoverage 판정
        // 기준을 반드시 같은(반올림된) 값으로 맞춰야 "표에 보이는 P25~P75 범위인데 coverage=false"처럼
        // 모순되게 보이는 걸 막을 수 있다.
        long p25Rounded = Math.round(stats.p25());
        long p75Rounded = Math.round(stats.p75());
        double absoluteError = Math.abs((double) estimatedRounded - actualBudget);
        double absolutePercentageError = actualBudget != 0 ? absoluteError / actualBudget : Double.NaN;
        double absoluteLogError = (stats.estimated() > 0 && actualBudget > 0)
                ? Math.abs(Math.log(stats.estimated()) - Math.log(actualBudget)) : Double.NaN;
        boolean typicalRangeCoverage = actualBudget >= p25Rounded && actualBudget <= p75Rounded;

        return new MultiYearBacktestPrediction(
                target.getDatasetYear(), target.getId(), target.getFestivalName(),
                query.regionCode() != null ? query.regionCode().name() : null, query.district(),
                target.getFestivalType(), target.getVenueType() != null ? target.getVenueType().name() : null,
                target.getDurationDays(), actualBudget, estimatedRounded, Math.round(stats.weightedAverage()),
                Math.round(stats.recommendedBudget()), p25Rounded, p75Rounded, stats.sampleCount(), distinctSeriesCount,
                selection.level().name(), stats.v3().score(), typicalRangeCoverage,
                absoluteError, absolutePercentageError, absoluteLogError
        );
    }

    /**
     * {@link #selectFinalSample}이 고정한 finalSample + 주어진 weight로 "정답과 비교하지 않는"
     * 순수 통계(가중평균/추정예산/P25~P75/유사도평균/완전성/v3/추천예산)만 계산한다. backtest
     * 평가({@link #aggregate})와 즉석 예측({@link #predictForQuery}) 둘 다 이 메서드를 공유한다 -
     * "target의 실제 예산이 있는지 없는지"와 무관하게 이 단계까지는 완전히 동일한 계산이다.
     */
    private CoreStats computeCoreStats(FinalSample fs, double[] weights) {
        List<MultiYearScoredCandidate> finalSample = fs.finalSample();
        MultiYearBacktestQuery query = fs.query();

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

        return new CoreStats(sampleCount, weightedAverage, estimated, p25, p50, p60, p75,
                similarityScoreAvg, completenessScore, recommendedBudget, v3);
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

    /** {@link #computeCoreStats}의 결과 - "정답과 비교하지 않는" 순수 통계. */
    private record CoreStats(int sampleCount, double weightedAverage, double estimated, double p25, double p50, double p60,
                              double p75, double similarityScoreAvg, double completenessScore, double recommendedBudget,
                              MultiYearDataQualityV3 v3) {
    }
}