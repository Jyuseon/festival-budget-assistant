package com.festival.budgetassist.admin;

import java.util.List;

/** GET /api/v1/admin/datasets/latest/issues - Import를 막지 않은 행 단위 데이터 품질 경고 목록. */
public record AdminDatasetIssuesResponse(
        boolean available,
        int totalWarnings,
        boolean truncated,
        List<IssueItem> issues
) {
    static AdminDatasetIssuesResponse unavailable() {
        return new AdminDatasetIssuesResponse(false, 0, false, List.of());
    }
}