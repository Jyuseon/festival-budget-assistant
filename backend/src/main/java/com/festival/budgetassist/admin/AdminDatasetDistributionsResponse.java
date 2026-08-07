package com.festival.budgetassist.admin;

import java.util.List;

/** GET /api/v1/admin/datasets/latest/distributions */
public record AdminDatasetDistributionsResponse(
        boolean available,
        List<CategoryCount> regionCounts,
        List<CategoryCount> festivalTypeCounts,
        List<CategoryCount> venueTypeCounts,
        BudgetStatistics budgetStatistics,
        List<DurationBucket> durationBuckets
) {
    static AdminDatasetDistributionsResponse unavailable() {
        return new AdminDatasetDistributionsResponse(false, List.of(), List.of(), List.of(), BudgetStatistics.empty(), List.of());
    }
}