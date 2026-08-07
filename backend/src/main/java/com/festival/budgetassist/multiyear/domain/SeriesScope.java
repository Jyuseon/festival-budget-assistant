package com.festival.budgetassist.multiyear.domain;

/**
 * {@link FestivalSeries}가 시군구 단위까지 특정됐는지, 광역지역 단위로만 특정됐는지.
 *
 * <p>district(시군구)가 없는 행(구 연도 일부 시트 등)은 안내서 지시에 따라 district가 있는
 * 행과 절대 자동으로 같은 series로 묶지 않는다 - 결정적 매칭도 fuzzy 매칭도 이 스코프 경계를
 * 넘지 않는다.</p>
 */
public enum SeriesScope {
    DISTRICT_LEVEL,
    REGION_LEVEL
}