package com.festival.budgetassist.multiyear.csv;

/**
 * Import를 막지는 않지만 사람이 확인해야 하는 행 단위 데이터 품질 이슈.
 *
 * @param datasetYear    해당 행의 연도
 * @param sourceRowNumber CSV의 source_row (원본 엑셀 연번). 없으면 null일 수 있다.
 * @param message        경고 메시지
 */
public record MultiYearRowWarning(Integer datasetYear, Integer sourceRowNumber, String message) {
}