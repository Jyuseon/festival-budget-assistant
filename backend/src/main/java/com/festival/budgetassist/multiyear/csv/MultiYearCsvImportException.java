package com.festival.budgetassist.multiyear.csv;

import java.util.List;

/**
 * CSV 구조 검증(필수 컬럼 누락, 행 파싱 실패 등) 또는 필수 필드 정규화 실패로 Import를 진행할 수
 * 없을 때 던진다. 이 예외가 발생하면 DB는 전혀 변경되지 않는다(트랜잭션 시작 전에만 던져짐).
 */
public class MultiYearCsvImportException extends RuntimeException {

    private final String fileHash;
    private final String originalFileName;
    private final List<String> details;

    public MultiYearCsvImportException(String message, String fileHash, String originalFileName, List<String> details) {
        super(message);
        this.fileHash = fileHash;
        this.originalFileName = originalFileName;
        this.details = details;
    }

    public String getFileHash() {
        return fileHash;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public List<String> getDetails() {
        return details;
    }
}