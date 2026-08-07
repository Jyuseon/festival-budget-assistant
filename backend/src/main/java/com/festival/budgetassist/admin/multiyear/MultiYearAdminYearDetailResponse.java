package com.festival.budgetassist.admin.multiyear;

/** GET /api/v1/admin/multiyear-datasets/years/{year} */
public record MultiYearAdminYearDetailResponse(
        int year,
        boolean available,
        MultiYearQualityCard qualityCard,
        MultiYearBudgetStatistics budgetStatistics,
        boolean covidAffectedYear
) {
    static MultiYearAdminYearDetailResponse unavailable(int year) {
        return new MultiYearAdminYearDetailResponse(year, false, MultiYearQualityCard.empty(), MultiYearBudgetStatistics.empty(), false);
    }
}