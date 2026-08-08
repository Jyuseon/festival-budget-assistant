package com.festival.budgetassist.multiyear.backtest;

/**
 * S0/S1/S2 각각에 대한 예측 1건. {@link MultiYearBacktestPrediction}과 같은 핵심 지표 필드를
 * 그대로 담되(mode별로 비교하기 위함), series correction 실험 전용 진단 필드를 추가한다:
 * candidate pool은 S0/S1/S2 사이에 항상 같아야 하므로(sampleCount 동일), 실제로 무엇이 달라졌는지
 * 사람이 확인할 수 있게 distinctSeriesCountInSample/mostRepeatedSeries* 를 fold-local
 * festivalSeries v1 grouping(트레이닝 기간만)으로 계산해 남긴다.
 *
 * @param sampleCount 최종 후보 수 - S0/S1/S2 동일해야 함(5절: 같은 candidate pool)
 * @param distinctSeriesCountInSample 최종 표본이 실제로 몇 개의 서로 다른 series로 이뤄져 있는지
 *                                    (fold-local v1 grouping 기준, 진단 전용)
 * @param mostRepeatedSeriesRecordCount 최종 표본 후보들이 속한 series 중 training 기간 내
 *                                      최대 관측 횟수(n) - correction 효과가 가장 클 후보
 * @param mostRepeatedSeriesLabel 위 series의 대표 축제명(정규화명, 사람이 읽기 위한 라벨)
 * @param signedLogError log(estimatedBudget/actualBudget) - 양수=과대예측, 음수=과소예측
 * @param pastSeriesLengthBucket target 자체가 training 기간에 몇 번 등장했는지 구간(평가 분석
 *                               전용 - prediction 계산에는 전혀 쓰이지 않는다, 지시사항 9절)
 */
record MultiYearSeriesCorrectionPrediction(
        MultiYearSeriesCorrectionMode mode,
        int targetYear,
        long recordId,
        String festivalName,
        String region,
        String district,
        String festivalType,
        long actualBudget,
        long estimatedBudget,
        long weightedAverageBudget,
        long recommendedBudget,
        long p25,
        long p75,
        int sampleCount,
        long distinctSeriesCountInSample,
        long mostRepeatedSeriesRecordCount,
        String mostRepeatedSeriesLabel,
        double dataQualityV3,
        boolean typicalRangeCoverage,
        double absoluteError,
        double absolutePercentageError,
        double absoluteLogError,
        double signedLogError,
        String pastSeriesLengthBucket
) {
}