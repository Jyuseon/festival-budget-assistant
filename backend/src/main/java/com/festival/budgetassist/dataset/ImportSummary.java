package com.festival.budgetassist.dataset;

import java.util.List;

/**
 * 한 번의 Import 실행에 대한 집계 리포트.
 *
 * <p>{@code totalRows}부터 {@code venueTypeCount}까지는 사용자가 명시적으로 요청한 검증
 * 항목이며(파일 해시는 별도로 {@link DatasetIntegrityCheck}에서 다룬다), 실제로 파싱·정규화된
 * 결과에서 계산한 값이다.</p>
 */
public record ImportSummary(
        int totalRows,
        /** 예산이 숫자이고 0보다 큼(BudgetStatus.CONFIRMED). */
        int validBudgetRows,
        int unconfirmedBudgetRows,
        int noResponseBudgetRows,
        int zeroBudgetRows,
        /** durationDays가 끝내 null인 행 수(R열도 없고 날짜로도 계산 불가). */
        int missingDurationRows,
        int regionCount,
        int festivalTypeCount,
        int venueTypeCount,
        List<RowWarning> warnings
) {
}