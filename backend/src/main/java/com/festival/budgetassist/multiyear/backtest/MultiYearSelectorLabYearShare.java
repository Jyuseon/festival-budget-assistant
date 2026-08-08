package com.festival.budgetassist.multiyear.backtest;

/** 최종 표본 안에서 특정 연도가 차지하는 candidate 수/weight 비중 (5절 "연도별 candidateCount/totalFinalWeight/weightShare"). */
record MultiYearSelectorLabYearShare(int year, int candidateCount, double totalWeight, double weightShare) {
}