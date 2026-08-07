package com.festival.budgetassist.dataset;

/**
 * '조사표' 시트의 열 위치 상수 (0-based, POI 기준).
 *
 * <p>AI~AN(담당자·연락처·비고, 개인정보/자유서술)열은 의도적으로 상수를 정의하지 않는다 —
 * 코드 어디에서도 물리적으로 그 열 인덱스를 참조할 수 없게 해서, 개인정보를 읽어들일
 * 경로 자체를 없앤다.</p>
 */
final class ExcelColumns {

    static final int SOURCE_ROW_NUMBER = 1;       // B: 연번
    static final int REGION = 2;                  // C: 광역자치단체명
    static final int ADMINISTRATIVE_DISTRICT = 3; // D: 기초자치단체명
    static final int FESTIVAL_NAME = 4;            // E: 축제명
    static final int FESTIVAL_TYPE = 5;            // F: 축제 유형
    static final int VENUE_NAME = 6;               // G: 개최 장소(장소명)
    static final int VENUE_TYPE = 7;               // H: 개최 장소 유형
    static final int VENUE_REGION = 8;             // I: 개최지 시도
    static final int VENUE_DISTRICT = 9;           // J: 개최지 시군구
    // K(개최지 읍면동)은 가이드가 MVP 미사용으로 명시 — 상수 없음

    static final int START_YEAR = 11;   // L
    static final int START_MONTH = 12;  // M
    static final int START_DAY = 13;    // N
    static final int END_YEAR = 14;     // O
    static final int END_MONTH = 15;    // P
    static final int END_DAY = 16;      // Q
    static final int DURATION_DAYS = 17;    // R: 총 일수
    static final int DURATION_NOTE = 18;    // S: 개최기간 비고

    static final int CYCLE = 19;            // T: 개최 주기
    static final int FIRST_HELD_YEAR = 20;  // U: 최초 개최연도

    static final int TOTAL_BUDGET = 21;     // V: 예산 합계(백만원)
    static final int NATIONAL_BUDGET = 22;  // W: 국비
    static final int LOCAL_BUDGET = 23;     // X: 지방비
    static final int OTHER_BUDGET = 24;     // Y: 기타
    // Z(국비지원 부처명)은 가이드 7.1 DB 모델에 없음 — 상수 없음

    static final int PREVIOUS_VISITORS = 26;  // AA: 방문객수(전년) 전체
    static final int DOMESTIC_VISITORS = 27;  // AB: 내국인
    static final int FOREIGN_VISITORS = 28;   // AC: 외국인
    static final int MEASUREMENT_METHOD = 29; // AD: 계측 방법
    // AE(방문객 비고)는 자유서술 — 상수 없음
    // AF~AH(주최 전담조직)는 가이드 7.1 DB 모델에 없음 — 상수 없음
    // AI~AN(담당자/연락처/비고)는 개인정보 — 상수 없음, 절대 읽지 않음

    private ExcelColumns() {
    }
}