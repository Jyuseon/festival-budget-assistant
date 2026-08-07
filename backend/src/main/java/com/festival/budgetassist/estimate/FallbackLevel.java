package com.festival.budgetassist.estimate;

/**
 * 후보 축제를 모을 때 얼마나 조건을 완화했는지를 나타낸다. 안내서 9.7절의 6단계 fallback을
 * 그대로 코드화한 것 - 숫자가 클수록 더 넓은 범위에서 후보를 끌어왔다는 뜻이다.
 */
public enum FallbackLevel {

    /** 동일 시군구 + 동일 축제유형 + 동일 장소유형. district 입력이 없으면 이 단계는 건너뛴다. */
    SAME_DISTRICT_TYPE_VENUE(1, "동일 시군구·축제유형·장소유형 데이터로 추정했습니다."),

    /** 동일 광역지역 + 동일 축제유형 + 동일 장소유형. */
    SAME_REGION_TYPE_VENUE(2, "동일 시군구 내 표본이 부족하여 동일 광역지역의 동일 유형·장소 데이터를 함께 사용했습니다."),

    /** 전국 + 동일 축제유형 + 동일 장소유형. */
    NATIONWIDE_TYPE_VENUE(3, "동일 지역 내 표본이 부족하여 전국 동일 유형·장소 데이터를 함께 사용했습니다."),

    /** 동일 광역지역 + 동일 축제유형 (장소유형 무관). */
    SAME_REGION_TYPE(4, "동일 유형·장소 조합 표본이 부족하여 동일 광역지역의 동일 유형 데이터(장소 유형 무관)를 함께 사용했습니다."),

    /** 전국 + 동일 축제유형 (장소유형 무관). */
    NATIONWIDE_TYPE(5, "표본이 더 부족하여 전국의 동일 축제 유형 데이터를 함께 사용했습니다."),

    /** 전국 전체 표본에서 유사도 기준으로만 선정 (축제유형 무관). */
    GLOBAL_SIMILARITY(6, "표본이 매우 부족하여 전체 데이터에서 유사도 기준으로만 후보를 선정했습니다.");

    private final int order;
    private final String label;

    FallbackLevel(int order, String label) {
        this.order = order;
        this.label = label;
    }

    public int getOrder() {
        return order;
    }

    public String getLabel() {
        return label;
    }
}