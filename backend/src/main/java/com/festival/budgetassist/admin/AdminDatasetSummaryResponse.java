package com.festival.budgetassist.admin;

import com.festival.budgetassist.dataset.ReferenceProfileCheck;

/**
 * GET /api/v1/admin/datasets/latest/summary
 * 상단 지표 카드 9종 + 배치 정보 + 알려진 기준값(2026년) 대비 일치 여부.
 * 모든 수치는 최신 성공 배치에 연결된 festival_record를 그때그때 다시 집계한 값이다.
 */
public record AdminDatasetSummaryResponse(
        boolean available,
        BatchInfo batch,
        int totalRows,
        int validBudgetRows,
        int unconfirmedBudgetRows,
        int noResponseBudgetRows,
        int zeroBudgetRows,
        int missingDurationRows,
        int regionCount,
        int festivalTypeCount,
        int venueTypeCount,
        ReferenceProfileCheck referenceProfileCheck
) {
    static AdminDatasetSummaryResponse unavailable() {
        return new AdminDatasetSummaryResponse(false, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, null);
    }
}