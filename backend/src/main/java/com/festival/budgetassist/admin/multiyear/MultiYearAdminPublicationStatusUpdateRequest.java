package com.festival.budgetassist.admin.multiyear;

import jakarta.validation.constraints.NotNull;

import com.festival.budgetassist.multiyear.domain.MultiYearDatasetPublicationStatusValue;

/** PUT /api/v1/admin/multiyear-datasets/publication-status/{year} 요청 본문. */
public record MultiYearAdminPublicationStatusUpdateRequest(@NotNull MultiYearDatasetPublicationStatusValue status) {
}