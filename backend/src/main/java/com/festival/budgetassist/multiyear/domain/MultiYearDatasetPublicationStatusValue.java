package com.festival.budgetassist.multiyear.domain;

/**
 * 특정 연도(datasetYear)의 "지역축제 개최계획 데이터셋"이 공개 기준으로 완성됐는지 나타낸다.
 * {@link com.festival.budgetassist.multiyear.backtest.ReferenceDataPolicy#INCLUDE_PUBLISHED_SAME_YEAR}가
 * 같은 연도 데이터를 reference로 쓸 수 있는지 판단하는 유일한 기준이다.
 *
 * <p><b>{@link #PUBLISHED_PLAN_COMPLETE}의 의미를 정확히 하는 것이 중요하다</b>: "그 해 축제가
 * 모두 개최·집행 완료됐다"는 뜻이 아니다 - 그런 의미라면 그 해가 다 지나야만 true가 될 수 있어
 * 애초에 "같은 연도를 참고"한다는 개념 자체가 성립하지 않는다. 실제 의미는 "해당 연도의 지역축제
 * 개최계획(예산 포함) 데이터셋 원본이 문체부 등을 통해 공개 기준으로 완성되어, 아직 그 해 축제가
 * 실제로 열리기 전이라도 계획예산 참고자료로 안전하게 쓸 수 있다"는 뜻이다 - 이름이 "PUBLISHED"인
 * 이유이기도 하다(집행이 아니라 "계획이 공개됨"). 예: 2026년 3월에 2026년 전체 개최계획이
 * 공개됐다면, 2026년 하반기 축제를 그 시점에 기획하면서 "이미 공개된 2026년 다른 축제들의
 * 계획예산"을 참고자료로 함께 쓸 수 있다.</p>
 */
public enum MultiYearDatasetPublicationStatusValue {

    /** 문체부 등에서 아직 해당 연도 개최계획 데이터셋 전체가 공개되지 않았거나(연중), 일부만 반영된 상태 - 기본값. */
    PARTIAL,

    /**
     * 해당 연도 개최계획 데이터셋 전체가 공개 기준으로 완성됐다고 운영자가 확인한 상태 - same-year
     * reference 허용. "그 해 축제가 전부 끝났다"는 뜻이 아니다(클래스 Javadoc 참고).
     */
    PUBLISHED_PLAN_COMPLETE
}