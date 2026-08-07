package com.festival.budgetassist.admin.multiyear;

import java.util.List;

/**
 * GET /api/v1/admin/multiyear-datasets/summary
 *
 * <p>{@code years}는 항상 2017~2026 10개년을 전부 포함한다(데이터가 없는 연도는 0으로 채운
 * {@link MultiYearYearSummary#empty}) - 화면이 "전체 연도 한눈에 보기"를 위해 매번 존재 여부를
 * 따로 확인하지 않아도 되게 하기 위함이다.</p>
 */
public record MultiYearAdminSummaryResponse(
        boolean available,
        int totalRecords,
        List<MultiYearYearSummary> years,
        MultiYearSeriesStatus seriesStatus
) {
}