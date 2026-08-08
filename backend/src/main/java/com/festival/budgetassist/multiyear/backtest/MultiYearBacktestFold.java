package com.festival.budgetassist.multiyear.backtest;

import java.util.List;

/**
 * backtest fold 정의 (지시사항 2절).
 * <ul>
 *   <li>Primary: train 2017~2024 -&gt; test 2025, train 2017~2025 -&gt; test 2026</li>
 *   <li>Secondary: train 2017~2023 -&gt; test 2024 (venueType 원본이 대부분 없어 Primary와
 *       섞어 평균내지 않고 항상 별도로 표시한다)</li>
 * </ul>
 */
record MultiYearBacktestFold(int targetYear, boolean primary, String label) {

    static final MultiYearBacktestFold PRIMARY_2025 = new MultiYearBacktestFold(2025, true, "Primary: train<=2024 -> test 2025");
    static final MultiYearBacktestFold PRIMARY_2026 = new MultiYearBacktestFold(2026, true, "Primary: train<=2025 -> test 2026");
    static final MultiYearBacktestFold SECONDARY_2024 = new MultiYearBacktestFold(2024, false, "Secondary: train<=2023 -> test 2024 (venueType 원본 대부분 없음)");

    static List<MultiYearBacktestFold> all() {
        return List.of(PRIMARY_2025, PRIMARY_2026, SECONDARY_2024);
    }

    /** training에 포함되는 마지막 연도(포함) - datasetYear &lt; targetYear와 동치. */
    int trainCutoffYearInclusive() {
        return targetYear - 1;
    }
}