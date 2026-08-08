package com.festival.budgetassist.multiyear.backtest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;
import com.festival.budgetassist.multiyear.repository.MultiYearFestivalRecordRepository;
import com.festival.budgetassist.multiyear.series.FestivalSeriesLinkingService;
import com.festival.budgetassist.multiyear.series.FestivalNameNormalizer;

/**
 * festivalSeries 중복 보정(S0/S1/S2) 단독 비교 실험.
 *
 * <p><b>festivalSeries는 오직 training sample duplicate correction에만 쓴다</b> - target
 * festival의 과거 예산을 직접 예측에 사용하지 않는다(지시사항 1절). 이 서비스가 series를 쓰는
 * 곳은 딱 한 곳, {@link #buildCorrectedWeights}에서 후보(candidate)의 series 반복 횟수(n)로
 * weight를 깎는 것뿐이다 - candidate selection(어떤 후보를 뽑을지)은 {@link
 * MultiYearBacktestService#selectFinalSample}를 그대로 재사용해 S0/S1/S2 사이에 완전히 동일하게
 * 유지한다(지시사항 5절).</p>
 *
 * <p><b>leakage-safe series 구성</b>(지시사항 2절, 가장 중요): 각 fold마다 {@link
 * FestivalSeriesLinkingService#computeSeriesGroupsInMemory}를 그 fold의 training pool에만
 * 적용해 series membership을 처음부터 다시 계산한다 - 이미 만들어진(2017~2026 전체 기준)
 * {@code FestivalSeriesMembership}을 잘라 쓰지 않는다. 규칙(결정적 클러스터링 + fuzzy HIGH +
 * strict chain linking) 자체는 festivalSeries v1과 완전히 동일한 코드를 그대로 재사용한다 -
 * 바뀐 게 없다.</p>
 */
@Service
class MultiYearSeriesCorrectionBacktestService {

    private final MultiYearFestivalRecordRepository recordRepository;
    private final MultiYearBacktestDatasetBuilder datasetBuilder;
    private final MultiYearBacktestService backtestService;
    private final FestivalSeriesLinkingService seriesLinkingService;

    MultiYearSeriesCorrectionBacktestService(MultiYearFestivalRecordRepository recordRepository,
                                              MultiYearBacktestDatasetBuilder datasetBuilder,
                                              MultiYearBacktestService backtestService,
                                              FestivalSeriesLinkingService seriesLinkingService) {
        this.recordRepository = recordRepository;
        this.datasetBuilder = datasetBuilder;
        this.backtestService = backtestService;
        this.seriesLinkingService = seriesLinkingService;
    }

    /** S0/S1/S2 x (Primary 2025/2026 + Secondary 2024) 전체 9개 조합을 전부 실행한다. */
    Map<MultiYearSeriesCorrectionMode, List<MultiYearFoldCorrectionResult>> runAllModesAllFolds() {
        List<MultiYearFestivalRecord> allRecords = recordRepository.findAll();
        Map<MultiYearSeriesCorrectionMode, List<MultiYearFoldCorrectionResult>> byMode = new LinkedHashMap<>();
        for (MultiYearSeriesCorrectionMode mode : MultiYearSeriesCorrectionMode.values()) {
            List<MultiYearFoldCorrectionResult> foldResults = new ArrayList<>();
            for (MultiYearBacktestFold fold : MultiYearBacktestFold.all()) {
                foldResults.add(runFold(allRecords, fold, mode));
            }
            byMode.put(mode, foldResults);
        }
        return byMode;
    }

    MultiYearFoldCorrectionResult runFold(List<MultiYearFestivalRecord> allRecords, MultiYearBacktestFold fold,
                                           MultiYearSeriesCorrectionMode mode) {
        MultiYearBacktestDataset dataset = datasetBuilder.build(allRecords, fold);

        // leakage-safe: 이 fold의 training pool만으로 series membership을 처음부터 다시 계산한다
        // (지시사항 2절) - 2017~2026 전체로 만든 기존 series를 잘라 쓰지 않는다.
        Map<Long, Long> groupIdByRecordId = seriesLinkingService.computeSeriesGroupsInMemory(dataset.trainingPool());
        Map<Long, Long> groupSizeByGroupId = new LinkedHashMap<>();
        for (Long groupId : groupIdByRecordId.values()) {
            groupSizeByGroupId.merge(groupId, 1L, Long::sum);
        }
        Map<Long, String> labelByGroupId = buildGroupLabels(dataset.trainingPool(), groupIdByRecordId);
        // 지시사항 9절: target 자체의 "과거 series 관측 수" - 평가 분석 전용, 아래 predictOne()의
        // 어떤 예측 계산에도 들어가지 않는다(단순 사후 라벨링). 결정적(정규화 이름+지역+district
        // 완전일치) 근사치를 쓴다 - FestivalNameNormalizer가 이미 회차/연도 표기를 벗겨내므로
        // "제27회 무주반딧불축제"/"무주반딧불축제(2018)" 같은 흔한 변형은 그대로 잡힌다.
        Map<MultiYearBacktestSeriesDiagnostics.GroupKey, Long> pastCountByDeterministicKey = new LinkedHashMap<>();
        for (MultiYearFestivalRecord r : dataset.trainingPool()) {
            pastCountByDeterministicKey.merge(MultiYearBacktestSeriesDiagnostics.keyOf(r), 1L, Long::sum);
        }

        List<MultiYearSeriesCorrectionPrediction> predictions = new ArrayList<>();
        int noFinalSample = 0;
        for (MultiYearFestivalRecord target : dataset.evalTargets()) {
            long pastCount = pastCountByDeterministicKey.getOrDefault(MultiYearBacktestSeriesDiagnostics.keyOf(target), 0L);
            MultiYearSeriesCorrectionPrediction p = predictOne(target, dataset.trainingPool(), mode,
                    groupIdByRecordId, groupSizeByGroupId, labelByGroupId, pastSeriesLengthBucket(pastCount));
            if (p == null) {
                noFinalSample++;
            } else {
                predictions.add(p);
            }
        }

        return new MultiYearFoldCorrectionResult(fold, mode, predictions, dataset.trainingPool().size(),
                dataset.evalTargets().size(), noFinalSample);
    }

    private MultiYearSeriesCorrectionPrediction predictOne(MultiYearFestivalRecord target, List<MultiYearFestivalRecord> trainingPool,
                                                             MultiYearSeriesCorrectionMode mode,
                                                             Map<Long, Long> groupIdByRecordId, Map<Long, Long> groupSizeByGroupId,
                                                             Map<Long, String> labelByGroupId, String pastSeriesLengthBucket) {
        MultiYearBacktestService.FinalSample fs = backtestService.selectFinalSample(target, trainingPool);
        if (fs == null) {
            return null;
        }

        int n = fs.finalSample().size();
        double[] weights = new double[n];
        long maxSeriesRecordCount = 1;
        long maxSeriesGroupId = -1;
        for (int i = 0; i < n; i++) {
            MultiYearScoredCandidate candidate = fs.finalSample().get(i);
            Long groupId = groupIdByRecordId.get(candidate.record().getId());
            long seriesRecordCount = groupId != null ? groupSizeByGroupId.getOrDefault(groupId, 1L) : 1L;
            weights[i] = candidate.score().weight() * mode.seriesFactor(seriesRecordCount);
            if (seriesRecordCount > maxSeriesRecordCount) {
                maxSeriesRecordCount = seriesRecordCount;
                maxSeriesGroupId = groupId;
            }
        }

        MultiYearBacktestPrediction base = backtestService.aggregate(target, fs, weights);

        long distinctSeriesCountInSample = fs.finalSample().stream()
                .map(c -> {
                    Long groupId = groupIdByRecordId.get(c.record().getId());
                    return groupId != null ? groupId : -c.record().getId(); // 그룹 없음 = 자기 자신만의 고유 id
                })
                .distinct()
                .count();

        String mostRepeatedLabel = maxSeriesGroupId >= 0 ? labelByGroupId.getOrDefault(maxSeriesGroupId, "-") : "-";

        double signedLogError = (base.estimatedBudget() > 0 && base.actualBudget() > 0)
                ? Math.log((double) base.estimatedBudget() / base.actualBudget())
                : Double.NaN;

        return new MultiYearSeriesCorrectionPrediction(mode, base.targetYear(), base.recordId(), base.festivalName(),
                base.region(), base.district(), base.festivalType(), base.actualBudget(), base.estimatedBudget(),
                base.weightedAverageBudget(), base.recommendedBudget(), base.p25(), base.p75(), base.sampleCount(),
                distinctSeriesCountInSample, maxSeriesRecordCount, mostRepeatedLabel, base.dataQualityV3(),
                base.typicalRangeCoverage(), base.absoluteError(), base.absolutePercentageError(),
                base.absoluteLogError(), signedLogError, pastSeriesLengthBucket);
    }

    /** 지시사항 9절 구간: 과거 series 없음 / 1~2년 / 3~5년 / 6년 이상. */
    static String pastSeriesLengthBucket(long pastCount) {
        if (pastCount <= 0) {
            return "과거 series 없음";
        }
        if (pastCount <= 2) {
            return "1~2년";
        }
        if (pastCount <= 5) {
            return "3~5년";
        }
        return "6년 이상";
    }

    /** 각 series 그룹의 대표 라벨(가장 이른 연도 record의 정규화 축제명) - 사람이 읽는 리포트용. */
    private Map<Long, String> buildGroupLabels(List<MultiYearFestivalRecord> trainingPool, Map<Long, Long> groupIdByRecordId) {
        Map<Long, MultiYearFestivalRecord> anchorByGroupId = new LinkedHashMap<>();
        for (MultiYearFestivalRecord r : trainingPool) {
            Long groupId = groupIdByRecordId.get(r.getId());
            if (groupId == null) {
                continue;
            }
            MultiYearFestivalRecord current = anchorByGroupId.get(groupId);
            if (current == null || isEarlier(r, current)) {
                anchorByGroupId.put(groupId, r);
            }
        }
        Map<Long, String> labels = new LinkedHashMap<>();
        anchorByGroupId.forEach((groupId, anchor) -> labels.put(groupId, FestivalNameNormalizer.normalize(anchor.getFestivalName())));
        return labels;
    }

    private boolean isEarlier(MultiYearFestivalRecord a, MultiYearFestivalRecord b) {
        int yearCompare = Comparator.comparing(MultiYearFestivalRecord::getDatasetYear).compare(a, b);
        if (yearCompare != 0) {
            return yearCompare < 0;
        }
        return a.getId() < b.getId();
    }

    /** {@link MultiYearBacktestMetricsCalculator}(기존 baseline 지표 계산기)를 그대로 재사용하기 위한 변환. */
    static MultiYearBacktestPrediction toBacktestPrediction(MultiYearSeriesCorrectionPrediction p) {
        return new MultiYearBacktestPrediction(p.targetYear(), p.recordId(), p.festivalName(), p.region(), p.district(),
                p.festivalType(), null, null, p.actualBudget(), p.estimatedBudget(), p.weightedAverageBudget(),
                p.recommendedBudget(), p.p25(), p.p75(), p.sampleCount(), p.distinctSeriesCountInSample(), "N/A",
                p.dataQualityV3(), p.typicalRangeCoverage(), p.absoluteError(), p.absolutePercentageError(), p.absoluteLogError());
    }
}