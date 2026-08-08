package com.festival.budgetassist.admin.multiyear;

import java.util.List;

/** GET /api/v1/admin/multiyear-datasets/publication-status - 데이터가 존재하는 연도만 나열한다(오름차순). */
public record MultiYearAdminPublicationStatusResponse(List<MultiYearAdminPublicationStatusEntry> years) {
}