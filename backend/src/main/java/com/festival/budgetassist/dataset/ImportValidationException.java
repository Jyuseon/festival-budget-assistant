package com.festival.budgetassist.dataset;

import java.util.List;

/**
 * 구조적 검증(시트 존재, 헤더 일치) 또는 필수 필드 정규화 실패로 Import를 진행할 수 없을 때 던진다.
 *
 * <p>이 예외는 DB 트랜잭션이 시작되기 전에만 던져지도록 설계되어 있으므로, 이 예외가 발생하면
 * 기존에 적재된 {@code FestivalRecord} 데이터는 절대 변경되지 않는다.</p>
 */
public class ImportValidationException extends RuntimeException {

    private final String fileHash;
    private final String originalFileName;
    private final List<String> details;

    public ImportValidationException(String message, String fileHash, String originalFileName, List<String> details) {
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