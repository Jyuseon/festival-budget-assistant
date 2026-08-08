package com.festival.budgetassist.multiyear.backtest;

/**
 * baseline backtest 평가대상 1건의 예측 결과 + 오차 지표. CSV/리포트 양쪽에서 재사용한다.
 *
 * @param estimatedBudget 가중 기하평균(production {@code estimatedBudgetKrw}와 동일한 정의) - 정확도 평가의 주 지표(6절)
 * @param weightedAverageBudget 가중 산술평균 - 참고용, 주 지표 아님
 * @param recommendedBudget 예비비 포함 기획 정책값 - 참고용, 주 지표 아님(6절)
 * @param sampleCount 최종 표본 수(가중치 무관 실제 건수)
 * @param distinctSeriesCount {@link MultiYearBacktestSeriesDiagnostics}의 간소화 근사치(참고용)
 * @param typicalRangeCoverage actual이 [p25,p75] 범위 안에 들어오는지
 */
record MultiYearBacktestPrediction(
        int targetYear,
        long recordId,
        String festivalName,
        String region,
        String district,
        String festivalType,
        String venueType,
        Integer durationDays,
        long actualBudget,
        long estimatedBudget,
        long weightedAverageBudget,
        long recommendedBudget,
        long p25,
        long p75,
        int sampleCount,
        long distinctSeriesCount,
        String fallbackLevel,
        double dataQualityV3,
        boolean typicalRangeCoverage,
        double absoluteError,
        double absolutePercentageError,
        double absoluteLogError
) {
}