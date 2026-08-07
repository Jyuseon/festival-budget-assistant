package com.festival.budgetassist.festival.domain;

/**
 * {@link FestivalRecord#getDurationDays()} 값이 어떻게 산출되었는지 표시한다.
 * 안내서 9.3/9.6절이 요구하는 "판단 근거 투명성" 원칙에 따라, 원본 보고값과
 * 파생(계산) 값을 구분해서 이후 통계/신뢰도 계산에서 다르게 취급할 수 있게 한다.
 */
public enum DurationSource {
    /** R열(총 일수)에 숫자가 그대로 보고된 경우. */
    REPORTED,
    /** R열이 비어 있어 시작일~종료일 날짜 성분으로 계산한 경우. */
    COMPUTED_FROM_DATES,
    /** R열도 없고 날짜 성분도 불완전해 계산할 수 없는 경우 (durationDays = null). */
    UNKNOWN
}