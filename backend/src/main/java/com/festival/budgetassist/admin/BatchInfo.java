package com.festival.budgetassist.admin;

import java.time.Instant;

import com.festival.budgetassist.festival.domain.DatasetImportBatch;

/**
 * 관리자 화면에 노출할 Import 배치 정보. {@link DatasetImportBatch}에서 개인정보와 무관한
 * 필드만 옮겨 담는다(애초에 그 엔티티에 개인정보 필드가 없다).
 */
public record BatchInfo(
        Long batchId,
        Integer datasetYear,
        String originalFileName,
        String fileHash,
        String status,
        Instant importedAt,
        int totalRows,
        int validBudgetRows,
        int invalidRows,
        /** FAILED일 때만 값이 있음. 구조적 오류 메시지만 담기며(시트/헤더/코드 인식 실패), 행 데이터 원문은 포함하지 않는다. */
        String failureMessage
) {
    static BatchInfo from(DatasetImportBatch batch) {
        boolean failed = batch.getStatus() == com.festival.budgetassist.festival.domain.ImportStatus.FAILED;
        return new BatchInfo(
                batch.getId(),
                batch.getDatasetYear(),
                batch.getOriginalFileName(),
                batch.getFileHash(),
                batch.getStatus().name(),
                batch.getImportedAt(),
                batch.getTotalRows(),
                batch.getValidBudgetRows(),
                batch.getInvalidRows(),
                failed ? batch.getErrorSummary() : null
        );
    }
}