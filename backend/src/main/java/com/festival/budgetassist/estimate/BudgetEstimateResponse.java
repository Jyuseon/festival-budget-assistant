package com.festival.budgetassist.estimate;

import java.util.List;

/**
 * POST /api/v1/budget-estimates 응답.
 *
 * <p>{@code calculationTrace}는 {@code festival.calculation-trace.enabled=true}(로컬 개발
 * 환경)일 때만 채워지고, 그 외에는 null이다 - 운영 응답에는 계산 과정 서술을 포함하지 않는다.</p>
 */
public record BudgetEstimateResponse(
        int datasetYear,
        String algorithmVersion,

        long weightedAverageBudgetKrw,
        long estimatedBudgetKrw,
        long recommendedBudgetKrw,
        BudgetRange typicalRange,
        long p50Krw,
        long p60Krw,

        int sampleCount,
        ConfidenceInfo confidence,

        String fallbackLevel,
        String fallbackLabel,

        List<String> basis,
        List<String> warnings,
        List<SimilarFestivalDto> similarFestivals,

        List<String> calculationTrace,
        ConfidenceBreakdown confidenceBreakdown,

        /** fallback 단계별 최종 weight 점유율. confidence 점수에는 반영되지 않는 설명 전용 정보. */
        List<ScopeWeightShare> scopeWeightBreakdown
) {
}