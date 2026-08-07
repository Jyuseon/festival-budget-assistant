package com.festival.budgetassist.multiyear.domain;

/**
 * sanitized CSV의 {@code duration_source} 컬럼 값.
 *
 * <p>기존 2026 전용 {@link com.festival.budgetassist.festival.domain.DurationSource}(REPORTED/
 * COMPUTED_FROM_DATES/UNKNOWN)와는 의미가 다르다 — 다년도 CSV는 엑셀에서 날짜 성분으로
 * 재계산하지 않고, "원본에 총일수가 있었는지" / "원문 텍스트에서 명시적으로 읽었는지" /
 * "복합·반복 일정이라 파싱하지 못했는지"만 구분한다. 그래서 별도 enum으로 둔다.</p>
 */
public enum CsvDurationSource {
    /** 원본 '총일수' 열에 숫자가 그대로 있었던 경우. */
    SOURCE_TOTAL_DAYS,
    /** 개최기간 원문 텍스트에서 명시적으로 일수를 읽어낸 경우(예: "(2일간)"). */
    EXPLICIT_TEXT,
    /** 복합/반복 일정 등으로 원문에서 총일수를 확정적으로 읽어내지 못한 경우. durationDays는 null이다. */
    UNPARSED
}