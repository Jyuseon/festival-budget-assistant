package com.festival.budgetassist.multiyear.csv;

import java.util.Map;

/**
 * CSV 한 데이터 행을 컬럼명 -&gt; 원문 문자열로 담은 중간 표현. enum 변환/타입 파싱은 하지 않는다
 * (그건 {@link MultiYearCsvNormalizationService}의 책임).
 *
 * @param lineNumber CSV 파일상의 물리적 줄 번호(1-based, 헤더 포함) - 오류 메시지용
 */
record MultiYearCsvRawRow(int lineNumber, Map<String, String> valuesByColumn) {

    /** 빈 문자열은 null로 취급한다 - CSV에서 빈 필드와 "값 없음"은 같은 의미다. */
    String get(String column) {
        String value = valuesByColumn.get(column);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}