package com.festival.budgetassist.admin.multiyear;

import java.util.List;

/**
 * GET /api/v1/admin/multiyear-datasets/years/{year}/sample?limit=&amp;offset=
 *
 * <p>{@code limit}은 서버에서 {@code MAX_LIMIT}로 강제 상한을 두어, 클라이언트가 큰 값을
 * 보내도 해당 연도 전체(최대 1,266건)를 한 번에 내려주지 않는다.</p>
 */
public record MultiYearAdminSampleResponse(
        int year,
        boolean available,
        int totalCountForYear,
        int limit,
        int offset,
        List<MultiYearSampleRow> rows
) {
    static MultiYearAdminSampleResponse unavailable(int year, int limit, int offset) {
        return new MultiYearAdminSampleResponse(year, false, 0, limit, offset, List.of());
    }
}