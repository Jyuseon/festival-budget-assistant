package com.festival.budgetassist.multiyear.backtest;

import java.util.List;

/**
 * {@link MultiYearBacktestService#estimateForPlanning}의 결과 - "실제 축제가 이 금액을 쓸
 * 것이다"가 아니라 "지금까지 공개된 유사 지역축제 개최계획 데이터를 기준으로 봤을 때 이 정도
 * 계획예산이 현실적이다"라는 의미다(사용자 요청 7절/16절).
 *
 * @param requestedReferenceDataPolicy 호출자가 요청한 정책
 * @param appliedReferenceDataPolicy 실제로 적용된 정책 - {@code INCLUDE_PUBLISHED_SAME_YEAR}를
 *                                    요청했지만 해당 planningYear 데이터셋이 아직 공개 완료가
 *                                    아니면 자동으로 {@code HISTORICAL_ONLY}로 낮춰 적용되고, 이
 *                                    필드가 그 사실을 그대로 드러낸다(조용히 바꾸지 않는다).
 * @param yearWeightBreakdown 실제로 어느 연도 데이터를 얼마나 참고했는지(연도별 weight 비중)
 */
public record MultiYearPlanningEstimateResult(
        int planningYear,
        ReferenceDataPolicy requestedReferenceDataPolicy,
        ReferenceDataPolicy appliedReferenceDataPolicy,
        int referenceYearFrom,
        int referenceYearTo,
        long estimatedBudgetKrw,
        long weightedAverageBudgetKrw,
        long recommendedBudgetKrw,
        long p25Krw,
        long p50Krw,
        long p75Krw,
        int sampleCount,
        int distinctYearsUsed,
        double effectiveYearCount,
        Integer earliestSourceYear,
        Integer latestSourceYear,
        String fallbackLevel,
        double averageSimilarity,
        double dataQualityV3,
        List<MultiYearPlanningYearWeightShare> yearWeightBreakdown,
        List<MultiYearPredictionCandidate> topCandidates
) {
    static MultiYearPlanningEstimateResult empty(int planningYear, ReferenceDataPolicy requested, ReferenceDataPolicy applied,
                                                   int referenceYearFrom, int referenceYearTo) {
        return new MultiYearPlanningEstimateResult(planningYear, requested, applied, referenceYearFrom, referenceYearTo,
                0, 0, 0, 0, 0, 0, 0, 0, 0.0, null, null, "NONE", 0.0, 0.0, List.of(), List.of());
    }
}