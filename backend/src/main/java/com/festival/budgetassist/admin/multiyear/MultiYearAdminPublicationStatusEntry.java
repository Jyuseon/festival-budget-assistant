package com.festival.budgetassist.admin.multiyear;

import java.time.Instant;

import com.festival.budgetassist.multiyear.domain.MultiYearDatasetPublicationStatusValue;

/** 연도 1개의 publication status - 명시적으로 설정된 적 없으면 PARTIAL(기본값)로 표시된다. */
public record MultiYearAdminPublicationStatusEntry(
        int datasetYear,
        MultiYearDatasetPublicationStatusValue status,
        Instant publishedAt,
        int recordCount
) {
}