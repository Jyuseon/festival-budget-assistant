package com.festival.budgetassist.multiyear.backtest;

/**
 * S0/S1/S2 비교용 확장 지표 - 기존 baseline 지표({@link MultiYearBacktestMetricsSummary})에
 * 지시사항 8/11절이 요구하는 추가 지표를 더한다.
 *
 * @param medianSignedLogError median(log(predicted/actual)) - 양수=전체적으로 과대예측, 음수=과소예측
 * @param medianPredictedActualRatio median(predicted/actual) - 1.0에 가까울수록 좋음
 * @param medianRangeWidthRatio median((P75-P25)/estimatedBudget) - typicalRange가 얼마나 넓은지(참고용)
 */
record MultiYearSeriesCorrectionMetrics(
        MultiYearBacktestMetricsSummary base,
        double medianSignedLogError,
        double medianPredictedActualRatio,
        double medianRangeWidthRatio
) {
}