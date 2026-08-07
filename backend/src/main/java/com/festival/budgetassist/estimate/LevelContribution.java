package com.festival.budgetassist.estimate;

/**
 * fallback이 한 단계 진행될 때마다 그 단계에서 새로 추가된 후보 수와, 그 시점까지의
 * 누적 후보 수. CandidateSelector의 선택 로직 자체는 바꾸지 않고, 진행 과정을
 * 관찰(observability)하기 위해서만 기록한다.
 */
record LevelContribution(FallbackLevel level, int added, int cumulativeTotal) {
}