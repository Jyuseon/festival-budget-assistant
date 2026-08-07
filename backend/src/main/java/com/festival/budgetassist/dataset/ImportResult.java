package com.festival.budgetassist.dataset;

import com.festival.budgetassist.festival.domain.DatasetImportBatch;

/**
 * {@link FestivalExcelImporter#importFromBytes} 실행 결과.
 *
 * <p>{@code duplicate=true}인 경우 이번 실행은 완전한 no-op이었고, {@code batch}는 예전에
 * 저장된 배치를(재사용), {@code summary}/{@code referenceProfileCheck}는 null이다.</p>
 */
public record ImportResult(
        boolean duplicate,
        DatasetImportBatch batch,
        ImportSummary summary,
        ReferenceProfileCheck referenceProfileCheck
) {
    public static ImportResult duplicate(DatasetImportBatch existingBatch) {
        return new ImportResult(true, existingBatch, null, null);
    }

    public static ImportResult success(DatasetImportBatch batch, ImportSummary summary, ReferenceProfileCheck check) {
        return new ImportResult(false, batch, summary, check);
    }
}