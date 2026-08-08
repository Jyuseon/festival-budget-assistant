package com.festival.budgetassist.multiyear.backtest;

import com.festival.budgetassist.estimate.FallbackLevel;

/** {@link MultiYearCandidateSelector}가 각 fallback 단계에서 새로 추가한 후보 수(관찰/설명용). */
record MultiYearLevelContribution(FallbackLevel level, int added, int cumulativeTotal) {
}