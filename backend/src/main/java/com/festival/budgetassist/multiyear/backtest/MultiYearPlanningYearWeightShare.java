package com.festival.budgetassist.multiyear.backtest;

/** 최종 표본 안에서 특정 연도가 차지하는 candidate 수/weight 비중 - 사용자에게 "어느 연도 데이터를 얼마나 참고했는지" 보여주기 위한 공개 DTO. */
public record MultiYearPlanningYearWeightShare(int year, int candidateCount, double weightShare) {
}