package com.festival.budgetassist.multiyear.domain;

/**
 * sanitized CSV의 {@code budget_quality_flag} 컬럼 값 (다년도 패키지 manifest 기준).
 *
 * <p>{@link #UNIT_SCALE_SUSPECT}는 2024년 일부 행처럼 헤더 단위(백만원)와 맞지 않게
 * 수백~수천 배 크게 입력된 것으로 의심되는 값이다. 가이드(4장)에 따라 이 값은 여기서
 * 자동으로 스케일을 보정하지 않고, 알고리즘 후보 표본에서 제외한 채로 별도 검증 대상으로
 * 남겨둔다 — {@link MultiYearFestivalRecord}에는 원본 그대로 저장한다.</p>
 */
public enum BudgetQualityFlag {
    VALID,
    MISSING_OR_NONPOSITIVE,
    UNIT_SCALE_SUSPECT
}