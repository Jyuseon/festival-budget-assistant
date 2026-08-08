package com.festival.budgetassist.multiyear.backtest;

/** 한 그룹(fold 하나, primary 합산, 또는 breakdown 한 구간)의 정확도 지표 요약 (지시사항 7절). */
record MultiYearBacktestMetricsSummary(
        int evaluationCount,
        double mae,
        double medianAbsoluteError,
        double medianAbsolutePercentageError,
        double p75AbsolutePercentageError,
        double p90AbsolutePercentageError,
        double medianAbsoluteLogError,
        double within25PercentRatio,
        double within50PercentRatio,
        double within2xRatio,
        double typicalRangeCoverageRatio
) {
}