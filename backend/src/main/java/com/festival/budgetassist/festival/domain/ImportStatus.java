package com.festival.budgetassist.festival.domain;

/**
 * {@link DatasetImportBatch}의 처리 결과 상태.
 *
 * <p>동일 파일 해시로 이미 SUCCESS 처리된 배치가 있는 경우는 완전한 no-op으로 취급하며
 * (DB에 아무 것도 기록하지 않음) 별도 상태값을 두지 않는다.</p>
 */
public enum ImportStatus {
    SUCCESS,
    FAILED
}