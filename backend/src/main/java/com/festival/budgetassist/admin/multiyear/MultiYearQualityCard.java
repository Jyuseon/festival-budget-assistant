package com.festival.budgetassist.admin.multiyear;

/** 선택한 연도의 데이터 품질 카드 6종. */
public record MultiYearQualityCard(
        int totalCount,
        int validBudgetCount,
        int budgetUnitSuspectCount,
        int missingOrNonPositiveBudgetCount,
        double durationAvailableRatePercent,
        double venueTypeAvailableRatePercent
) {
    static MultiYearQualityCard empty() {
        return new MultiYearQualityCard(0, 0, 0, 0, 0.0, 0.0);
    }
}