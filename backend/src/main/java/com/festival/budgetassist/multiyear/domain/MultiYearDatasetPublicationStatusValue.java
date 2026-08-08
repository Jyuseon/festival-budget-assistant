package com.festival.budgetassist.multiyear.domain;

/**
 * 특정 연도(datasetYear)의 다년도 개최계획 데이터가 "공개 완료" 상태인지 나타낸다. {@link
 * com.festival.budgetassist.multiyear.backtest.ReferenceDataPolicy#INCLUDE_PUBLISHED_SAME_YEAR}가
 * 같은 연도 데이터를 reference로 쓸 수 있는지 판단하는 유일한 기준이다.
 */
public enum MultiYearDatasetPublicationStatusValue {

    /** 문체부 등에서 아직 해당 연도 개최계획 전체가 공개되지 않았거나(연중), 일부만 반영된 상태 - 기본값. */
    PARTIAL,

    /** 해당 연도 개최계획 데이터 전체가 공개 완료됐다고 운영자가 확인한 상태 - same-year reference 허용. */
    PUBLISHED_COMPLETE
}