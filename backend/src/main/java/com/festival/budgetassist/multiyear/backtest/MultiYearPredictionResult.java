package com.festival.budgetassist.multiyear.backtest;

import java.util.List;

/**
 * {@link MultiYearBacktestService#predictForQuery}의 결과 - festivalSeries v1 baseline S0
 * 계산식을 실제 사용자 입력 1건에 즉석 적용한 순수 예측값. backtest 내부 타입(FinalSample 등)을
 * 전혀 노출하지 않는 안정적인 공개 계약이다.
 *
 * <p>target 자체의 "실제 예산"이 없으므로(아직 열리지 않은 가상의 축제) 오차 지표(APE/ALE 등)는
 * 포함하지 않는다 - {@link MultiYearBacktestPrediction}(backtest 평가용, 정답과 비교)과는
 * 다른 목적의 DTO다.</p>
 *
 * @param sampleCount 최종 후보 수
 * @param distinctYearsUsed 최종 후보들이 걸쳐 있는 서로 다른 datasetYear 수
 * @param earliestSourceYear 최종 후보 중 가장 이른 datasetYear(후보가 없으면 null)
 * @param latestSourceYear 최종 후보 중 가장 늦은 datasetYear(후보가 없으면 null)
 * @param fallbackLevel 도달한 candidate selection 단계(예: SAME_REGION_TYPE)
 * @param averageSimilarity 최종 후보의 가중평균 similarity(0~1)
 * @param dataQualityV3 confidence v3 후보 점수(순수 분석용 - threshold 없음, production confidence 아님)
 */
public record MultiYearPredictionResult(
        int targetYear,
        int trainingYearFrom,
        int trainingYearTo,
        long estimatedBudgetKrw,
        long weightedAverageBudgetKrw,
        long recommendedBudgetKrw,
        long p25Krw,
        long p50Krw,
        long p75Krw,
        int sampleCount,
        int distinctYearsUsed,
        Integer earliestSourceYear,
        Integer latestSourceYear,
        String fallbackLevel,
        double averageSimilarity,
        double dataQualityV3,
        List<MultiYearPredictionCandidate> topCandidates
) {

    /** candidate가 하나도 없을 때(표본 0건) 반환하는 빈 결과. */
    static MultiYearPredictionResult empty(int targetYear, int trainingYearFrom, int trainingYearTo) {
        return new MultiYearPredictionResult(targetYear, trainingYearFrom, trainingYearTo,
                0, 0, 0, 0, 0, 0, 0, 0, null, null, "NONE", 0, 0, List.of());
    }
}