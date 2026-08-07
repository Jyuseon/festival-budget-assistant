package com.festival.budgetassist.dataset;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link ImportSummary}를 {@link Known2026DatasetProfile}과 비교한 결과.
 * datasetYear가 2026이 아니면 비교 자체를 생략한다(다른 연도 데이터는 알려진 기준값이 없음).
 */
public record ReferenceProfileCheck(boolean applicable, boolean matches, List<String> mismatches) {

    public static ReferenceProfileCheck notApplicable() {
        return new ReferenceProfileCheck(false, true, List.of());
    }

    public static ReferenceProfileCheck compare(int datasetYear, ImportSummary summary) {
        if (datasetYear != Known2026DatasetProfile.DATASET_YEAR) {
            return notApplicable();
        }

        List<String> mismatches = new ArrayList<>();
        checkEquals(mismatches, "totalRows", Known2026DatasetProfile.TOTAL_ROWS, summary.totalRows());
        checkEquals(mismatches, "festivalTypeCount", Known2026DatasetProfile.FESTIVAL_TYPE_COUNT, summary.festivalTypeCount());
        checkEquals(mismatches, "venueTypeCount", Known2026DatasetProfile.VENUE_TYPE_COUNT, summary.venueTypeCount());
        checkEquals(mismatches, "regionCount", Known2026DatasetProfile.REGION_COUNT, summary.regionCount());
        checkEquals(mismatches, "validBudgetRows", Known2026DatasetProfile.VALID_BUDGET_ROWS, summary.validBudgetRows());

        return new ReferenceProfileCheck(true, mismatches.isEmpty(), mismatches);
    }

    private static void checkEquals(List<String> mismatches, String label, int expected, int actual) {
        if (expected != actual) {
            mismatches.add("%s: expected=%d actual=%d".formatted(label, expected, actual));
        }
    }
}