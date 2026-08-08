package com.festival.budgetassist.multiyear.experimental;

import java.util.List;

/**
 * POST /api/v1/experimental/multiyear-budget-estimates 응답(지시사항 8/9/10절).
 *
 * <p>{@code experimentalRecommendedBudgetKrw}는 production의 "추천 예산"과 같은 확정값이
 * 아니다 - 아직 검증 중인 실험 모델의 참고값일 뿐이다(지시사항 12절, UI에서 별도 표시 문구
 * 필요). 핵심 prediction target은 {@code estimatedBudgetKrw}다(현재 backtest가 직접
 * 검증하는 값과 동일).</p>
 */
public record MultiYearExperimentalEstimateResponse(
        String model,
        int targetYear,
        int trainingYearFrom,
        int trainingYearTo,

        long estimatedBudgetKrw,
        long weightedAverageBudgetKrw,
        long experimentalRecommendedBudgetKrw,
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

        MultiYearExperimentSettingsDto experimentSettings,
        List<MultiYearSimilarFestivalDto> topSimilarFestivals
) {
}