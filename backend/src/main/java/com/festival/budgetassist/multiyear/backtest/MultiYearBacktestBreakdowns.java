package com.festival.budgetassist.multiyear.backtest;

/** 예산 규모 구간별 breakdown 한 줄 (지시사항 9절). */
record MultiYearBudgetSizeBucketMetrics(String bucketLabel, int count, double medianAbsolutePercentageError) {
}

/** festivalType별 breakdown 한 줄 (지시사항 10절). smallSample=표본이 적어 참고용으로만 봐야 함. */
record MultiYearTypeMetrics(String festivalType, int count, double medianAbsolutePercentageError,
                             double medianAbsoluteLogError, boolean smallSample) {
}

/** region별 breakdown 한 줄 (지시사항 10절). */
record MultiYearRegionMetrics(String region, int count, double medianAbsolutePercentageError,
                               double medianAbsoluteLogError, boolean smallSample) {
}

/** v3 data-quality score 구간별 MdAPE (지시사항 11절). */
record MultiYearV3BucketMetrics(String bucketLabel, int count, double medianAbsolutePercentageError) {
}