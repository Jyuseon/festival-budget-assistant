package com.festival.budgetassist.multiyear.csv;

import com.festival.budgetassist.multiyear.domain.MultiYearImportBatch;

/**
 * {@link MultiYearCsvImportService#importFromBytes} 실행 결과.
 *
 * <p>{@code duplicate=true}면 동일 해시의 CSV가 이미 성공 처리되어 있어 완전한 no-op이었고,
 * {@code summary}는 null이다.</p>
 */
public record MultiYearImportResult(boolean duplicate, MultiYearImportBatch batch, MultiYearImportSummary summary) {

    public static MultiYearImportResult duplicate(MultiYearImportBatch existingBatch) {
        return new MultiYearImportResult(true, existingBatch, null);
    }

    public static MultiYearImportResult success(MultiYearImportBatch batch, MultiYearImportSummary summary) {
        return new MultiYearImportResult(false, batch, summary);
    }
}