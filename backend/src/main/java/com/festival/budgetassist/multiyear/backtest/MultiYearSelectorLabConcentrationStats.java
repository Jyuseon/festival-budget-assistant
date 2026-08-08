package com.festival.budgetassist.multiyear.backtest;

import java.util.Comparator;
import java.util.List;

/**
 * selector 전략 1개 x 쿼리 1건에 대한 연도 concentration 진단 결과 (5~7절).
 *
 * @param effectiveYearCount Simpson effective number: {@code 1 / Σ(weightShare_y²)}. 한 연도에
 *                            쏠릴수록 1에 가깝고, 여러 연도에 균등할수록 커진다. 아직 selection
 *                            공식에는 쓰지 않는 진단 전용 값이다(6절).
 */
record MultiYearSelectorLabConcentrationStats(
        int sampleCount,
        int distinctYearsUsed,
        double effectiveYearCount,
        int earliestSourceYear,
        int latestSourceYear,
        String fallbackLevel,
        double averageSimilarity,
        double minimumSimilarity,
        List<MultiYearSelectorLabYearShare> yearBreakdown
) {
    double topYearWeightShare() {
        return yearBreakdown.stream().mapToDouble(MultiYearSelectorLabYearShare::weightShare).max().orElse(0.0);
    }

    double latestYearWeightShare() {
        return yearBreakdown.stream()
                .max(Comparator.comparingInt(MultiYearSelectorLabYearShare::year))
                .map(MultiYearSelectorLabYearShare::weightShare)
                .orElse(0.0);
    }
}