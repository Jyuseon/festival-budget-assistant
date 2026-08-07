package com.festival.budgetassist.estimate;

/**
 * 최종 표본의 가중치(weight) 중 각 fallback 단계에서 들어온 후보가 차지하는 비율.
 * confidence 점수에는 더 이상 반영되지 않지만(v1.2부터), "이 추정이 실제로 어느 범위의
 * 데이터에 얼마나 의존했는지"를 사용자에게 설명하기 위한 정보로 유지한다.
 * 예: 동일 시군구 62%, 동일 광역지역 추가 38%, 전국 추가 0%.
 */
public record ScopeWeightShare(String level, String label, double weightSharePercent) {
}