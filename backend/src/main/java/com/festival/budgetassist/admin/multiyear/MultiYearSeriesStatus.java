package com.festival.budgetassist.admin.multiyear;

/**
 * festivalSeries 연결 분석 결과 요약(있으면). {@code FestivalSeriesLinkingService}를 아직
 * 실행하지 않았거나 결과가 비어 있으면 {@code analyzed=false}이고 나머지는 전부 0이다 - 화면은
 * 이 값을 보고 "분석 전"으로 표시한다.
 *
 * <p>이 레코드는 series 데이터를 읽기만 한다 - festivalSeries 연결 알고리즘 자체는 이 UI 작업으로
 * 전혀 수정하지 않았다.</p>
 */
public record MultiYearSeriesStatus(
        boolean analyzed,
        int distinctSeriesCount,
        int singletonSeriesCount,
        int multiYearSeriesCount
) {
    static MultiYearSeriesStatus notAnalyzed() {
        return new MultiYearSeriesStatus(false, 0, 0, 0);
    }
}