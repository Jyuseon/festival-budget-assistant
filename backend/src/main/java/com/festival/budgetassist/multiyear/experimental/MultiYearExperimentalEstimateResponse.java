package com.festival.budgetassist.multiyear.experimental;

import java.util.List;

/**
 * POST /api/v1/experimental/multiyear-budget-estimates 응답(지시사항 8/9/10절).
 *
 * <p>{@code experimentalRecommendedBudgetKrw}는 production의 "추천 예산"과 같은 확정값이
 * 아니다 - 아직 검증 중인 실험 모델의 참고값일 뿐이다(지시사항 12절, UI에서 별도 표시 문구
 * 필요). 핵심 prediction target은 {@code estimatedBudgetKrw}다(현재 backtest가 직접
 * 검증하는 값과 동일).</p>
 *
 * <p><b>하위호환 필드</b> {@code targetYear}/{@code trainingYearFrom}/{@code trainingYearTo}는
 * planningYear 일반화 경로에서도 그대로 채워진다({@code targetYear = planningYear},
 * {@code trainingYearFrom/To = referenceYearFrom/To}) - 기존 프론트엔드 필드명을 바꾸지 않기
 * 위함이다. {@code requestedReferenceDataPolicy}부터는 planningYear 요청일 때만 채워지는
 * 새 필드(레거시 요청이면 전부 null)다.</p>
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
        List<MultiYearSimilarFestivalDto> topSimilarFestivals,

        // planningYear 일반화 전용(레거시 요청이면 전부 null) - 사용자 요청 13절.
        String requestedReferenceDataPolicy,
        String appliedReferenceDataPolicy,
        Double effectiveYearCount,
        List<MultiYearPlanningYearWeightShareDto> yearWeightBreakdown
) {
}