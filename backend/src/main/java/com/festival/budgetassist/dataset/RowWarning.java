package com.festival.budgetassist.dataset;

/**
 * Import를 막지는 않지만 사람이 확인해야 하는 행 단위 데이터 품질 이슈.
 * {@code sourceRowNumber}는 엑셀 B열(연번)이다.
 */
public record RowWarning(Integer sourceRowNumber, String message) {
}