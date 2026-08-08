package com.festival.budgetassist.multiyear.backtest;

/** S0 대비 S1/S2에서 prediction이 가장 크게 변한 target 1건 (지시사항 10절). */
record MultiYearSeriesCorrectionMover(
        int targetYear,
        String festivalName,
        String region,
        String district,
        long actualBudget,
        long s0Estimated,
        long s1Estimated,
        long s2Estimated,
        int candidateCount,
        long distinctSeriesCountInSample,
        String mostRepeatedSeriesLabel,
        long mostRepeatedSeriesRecordCount,
        double s0AbsolutePercentageError,
        double s1AbsolutePercentageError,
        double s2AbsolutePercentageError,
        String verdict
) {
}