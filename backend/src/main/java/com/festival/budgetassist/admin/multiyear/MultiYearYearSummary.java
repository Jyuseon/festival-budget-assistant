package com.festival.budgetassist.admin.multiyear;

/**
 * 연도 1개에 대한 요약 행. {@code GET .../summary}의 {@code years} 배열과 화면의 "전체 연도
 * 비교" 표가 이 레코드 하나로 충분하도록, 확보율/중앙값까지 미리 계산해 담는다(연도별 상세
 * API를 10번 호출하지 않아도 되게 하기 위함).
 *
 * @param positiveBudgetCount     budgetQualityFlag와 무관하게 budget_total_million &gt; 0인 행 수
 *                                (독립적인 sanity check - UNIT_SCALE_SUSPECT 행도 숫자는 양수라 포함됨)
 * @param validBudgetCount        budgetQualityFlag = VALID인 행 수(정상 예산 통계에 쓰이는 모집단)
 * @param medianValidBudgetMillion VALID 표본만의 예산 중앙값(백만원). 표본이 없으면 0.
 */
public record MultiYearYearSummary(
        int datasetYear,
        int totalCount,
        int positiveBudgetCount,
        int validBudgetCount,
        int budgetUnitSuspectCount,
        int missingOrNonPositiveBudgetCount,
        int durationAvailableCount,
        double durationAvailableRatePercent,
        int venueTypeAvailableCount,
        double venueTypeAvailableRatePercent,
        int covidAffectedCount,
        double medianValidBudgetMillion
) {
    static MultiYearYearSummary empty(int year) {
        return new MultiYearYearSummary(year, 0, 0, 0, 0, 0, 0, 0.0, 0, 0.0, 0, 0.0);
    }
}