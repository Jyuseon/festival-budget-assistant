package com.festival.budgetassist.multiyear.domain;

/**
 * {@link FestivalSeriesMembership}이 어떤 방식으로 {@link FestivalSeries}에 연결됐는지.
 */
public enum MatchMethod {
    /** 원본 축제명 문자열이 시리즈 내 다른 행과 완전히 동일(연도/지역/시군구도 일치). */
    EXACT,
    /** normalizedFestivalName은 같지만 원본 축제명은 다름(예: "제27회 OO축제" vs "제28회 OO축제"). */
    NORMALIZED_EXACT,
    /** 결정적 규칙(EXACT/NORMALIZED_EXACT)으로 묶이지 않아 문자열 유사도+보조 신호로 자동 연결(HIGH confidence만 실제 연결). */
    FUZZY,
    /** 사람이 수동으로 확정한 연결(이번 단계에서는 생성하지 않음 - 향후 검토 UI용으로 예약). */
    MANUAL,
    /** 어떤 방법으로도 다른 행과 연결되지 않음 - 이 행 혼자만의 series(recordCount=1)를 이룬다. */
    UNMATCHED
}