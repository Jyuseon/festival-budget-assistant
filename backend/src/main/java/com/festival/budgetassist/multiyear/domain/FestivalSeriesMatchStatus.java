package com.festival.budgetassist.multiyear.domain;

/**
 * {@link FestivalSeries} 전체의 형성 방식 요약.
 */
public enum FestivalSeriesMatchStatus {
    /** 관측 행이 1건뿐 - 아직 어떤 방법으로도 다른 행과 연결되지 않음. */
    SINGLETON,
    /** EXACT/NORMALIZED_EXACT(정규화된 이름+지역+시군구 완전 일치)만으로 2건 이상 묶임. */
    DETERMINISTIC,
    /** 최소 1건은 fuzzy(HIGH confidence)로 합류함 - 결정적 매칭보다 신뢰도가 낮으니 검토 우선순위 높음. */
    FUZZY_MERGED
}