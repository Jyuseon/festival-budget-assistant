package com.festival.budgetassist.admin.multiyear;

import java.util.List;

import com.festival.budgetassist.admin.CategoryCount;

/**
 * GET /api/v1/admin/multiyear-datasets/years/{year}/distributions
 *
 * <p>festivalTypeCounts는 {@code festival_type} 원문 문자열(복합/OTHER/UNKNOWN 포함) 기준이다 -
 * CSV 자체가 아직 최종 확정 분류가 아니라고 명시하고 있어(guide DATA_DICTIONARY), 5종 enum으로
 * 강제 매핑하지 않는다. venueTypeCounts는 2025~2026에만 존재하므로
 * {@code venueTypeDataAvailable=false}면 빈 리스트다(0건이 아니라 "이 연도는 원본에 이 항목이
 * 없음"이라는 뜻).</p>
 */
public record MultiYearAdminDistributionsResponse(
        int year,
        boolean available,
        List<CategoryCount> regionCounts,
        List<CategoryCount> festivalTypeCounts,
        boolean venueTypeDataAvailable,
        List<CategoryCount> venueTypeCounts,
        List<CategoryCount> budgetQualityFlagCounts,
        boolean covidAffectedYear
) {
    static MultiYearAdminDistributionsResponse unavailable(int year) {
        return new MultiYearAdminDistributionsResponse(year, false, List.of(), List.of(), false, List.of(), List.of(), false);
    }
}