package com.festival.budgetassist.multiyear.backtest;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;

/**
 * selector 전략 하나를 쿼리 하나에 적용해 연도 concentration 진단 통계를 뽑는다. {@link
 * MultiYearBacktestService#selectFinalSample(MultiYearCandidateSelectionStrategy,
 * MultiYearBacktestQuery, int, List, boolean)}를 그대로 호출하므로 후보 선정만 전략별로 다르고,
 * 유사도/기간보정/winsorize/threshold+상위 N건 컷은 항상 baseline과 동일한 공식이다.
 */
final class MultiYearSelectorLabConcentrationAnalyzer {

    private final MultiYearBacktestService backtestService;

    MultiYearSelectorLabConcentrationAnalyzer(MultiYearBacktestService backtestService) {
        this.backtestService = backtestService;
    }

    /** 최종 표본을 만들 수 없었으면(threshold 미만 등) null. */
    MultiYearSelectorLabConcentrationStats analyze(MultiYearCandidateSelectionStrategy strategy, MultiYearBacktestQuery query,
                                                     int targetYear, List<MultiYearFestivalRecord> trainingPool) {
        MultiYearBacktestService.FinalSample fs = backtestService.selectFinalSample(strategy, query, targetYear, trainingPool, false);
        if (fs == null) {
            return null;
        }

        List<MultiYearScoredCandidate> sample = fs.finalSample();
        int sampleCount = sample.size();
        double[] weights = sample.stream().mapToDouble(c -> c.score().weight()).toArray();
        double[] similarities = sample.stream().mapToDouble(c -> c.score().similarity()).toArray();
        double avgSimilarity = MultiYearBacktestMath.weightedMean(similarities, weights);
        double minSimilarity = java.util.Arrays.stream(similarities).min().orElse(0.0);

        TreeMap<Integer, Long> countByYear = new TreeMap<>();
        TreeMap<Integer, Double> weightByYear = new TreeMap<>();
        double totalWeight = 0;
        for (int i = 0; i < sample.size(); i++) {
            int year = sample.get(i).record().getDatasetYear();
            countByYear.merge(year, 1L, Long::sum);
            weightByYear.merge(year, weights[i], Double::sum);
            totalWeight += weights[i];
        }

        double finalTotalWeight = totalWeight;
        List<MultiYearSelectorLabYearShare> yearBreakdown = new ArrayList<>();
        for (var entry : countByYear.entrySet()) {
            int year = entry.getKey();
            double yearWeight = weightByYear.getOrDefault(year, 0.0);
            double share = finalTotalWeight > 0 ? yearWeight / finalTotalWeight : 0.0;
            yearBreakdown.add(new MultiYearSelectorLabYearShare(year, entry.getValue().intValue(), yearWeight, share));
        }

        double sumSquaredShare = yearBreakdown.stream().mapToDouble(y -> y.weightShare() * y.weightShare()).sum();
        double effectiveYearCount = sumSquaredShare > 0 ? 1.0 / sumSquaredShare : 0.0;

        int earliest = countByYear.firstKey();
        int latest = countByYear.lastKey();

        return new MultiYearSelectorLabConcentrationStats(sampleCount, countByYear.size(), effectiveYearCount,
                earliest, latest, fs.selection().level().name(), avgSimilarity, minSimilarity, yearBreakdown);
    }
}