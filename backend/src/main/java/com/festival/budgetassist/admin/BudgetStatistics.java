package com.festival.budgetassist.admin;

/**
 * 예산이 확정(CONFIRMED, 0보다 큼)인 표본에 대한 통계. 단위는 원(KRW)이며,
 * 화면 렌더링 시에만 만원/억원 문자열로 변환한다(가이드 12.3 원칙).
 */
public record BudgetStatistics(
        long sampleCount,
        double meanKrw,
        double medianKrw,
        double p25Krw,
        double p75Krw,
        double p90Krw,
        long maxKrw
) {
    static BudgetStatistics empty() {
        return new BudgetStatistics(0, 0, 0, 0, 0, 0, 0);
    }
}