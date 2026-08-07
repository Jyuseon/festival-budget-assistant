package com.festival.budgetassist.admin.multiyear;

/**
 * budgetQualityFlag = VALID 표본만의 예산 통계. 단위는 백만원(원본 DB 값 그대로) - 화면에서만
 * "원" 단위로 보기 좋게 포맷팅한다. UNIT_SCALE_SUSPECT/MISSING_OR_NONPOSITIVE는 이 통계에서
 * 제외된다.
 */
public record MultiYearBudgetStatistics(
        int sampleCount,
        double meanMillion,
        double p25Million,
        double medianMillion,
        double p75Million,
        double p90Million,
        double p95Million,
        double maxMillion
) {
    static MultiYearBudgetStatistics empty() {
        return new MultiYearBudgetStatistics(0, 0, 0, 0, 0, 0, 0, 0);
    }
}