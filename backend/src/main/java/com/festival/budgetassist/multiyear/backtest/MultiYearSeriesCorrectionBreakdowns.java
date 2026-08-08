package com.festival.budgetassist.multiyear.backtest;

/** 예산 규모 구간별 S0/S1/S2 비교 한 줄 (지시사항 8절 - MdAPE/MedianALE/signedLogError/predictedActualRatio). */
record MultiYearSeriesCorrectionBudgetBucket(
        String bucketLabel, int count, double medianAbsolutePercentageError, double medianAbsoluteLogError,
        double medianSignedLogError, double medianPredictedActualRatio
) {
}

/** target 자체의 "과거 series 관측 수" 구간별 성능 (지시사항 9절 - 평가 분석 전용, prediction에는 미사용). */
record MultiYearSeriesLengthBucket(
        String bucketLabel, int count, double medianAbsolutePercentageError, double medianAbsoluteLogError
) {
}

/** 예산 규모 구간별 P25~P75 coverage + range width (지시사항 11절). */
record MultiYearRangeCoverageBucket(
        String bucketLabel, int count, double typicalRangeCoverageRatio, double medianRangeWidthRatio
) {
}