package com.festival.budgetassist.dataset;

/**
 * 2026년 지역축제 개최 계획 원본 파일을 실제로 열어서 확인한 참조 통계값.
 *
 * <p>파일명은 사용자가 바꿀 수 있으므로 신뢰할 수 없다 — 대신 이 값들과 실제 파싱 결과를
 * 비교해서 "같은 내용의 파일인지"를 판단하는 참고 자료로 쓴다.</p>
 *
 * <p>이 값과 실제 파싱 결과가 다르더라도 Import 자체를 막지는 않는다(WARN 로그만 남김).
 * 향후 연도가 다른 파일(2027년 등)이 들어오면 행 수·분류 개수가 달라지는 것이 정상이기
 * 때문이다. 즉 이 프로필은 "2026년 데이터셋에 대한 알려진 기준값"이며, 연도별로 확장하려면
 * datasetYear를 키로 하는 맵 형태로 발전시켜야 한다(현재는 2026년 하나만 지원).</p>
 */
public final class Known2026DatasetProfile {

    public static final int DATASET_YEAR = 2026;
    public static final String SHEET_NAME = "조사표";
    public static final int TOTAL_ROWS = 1266;
    public static final int FESTIVAL_TYPE_COUNT = 5;
    public static final int VENUE_TYPE_COUNT = 6;
    public static final int REGION_COUNT = 17;
    public static final int VALID_BUDGET_ROWS = 1238;

    private Known2026DatasetProfile() {
    }
}