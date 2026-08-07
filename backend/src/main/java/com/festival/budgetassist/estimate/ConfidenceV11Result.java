package com.festival.budgetassist.estimate;

/**
 * confidence v1.1 후보 공식의 결과. legacy {@link ConfidenceResult}와 나란히 비교하기 위한
 * 것으로, HIGH/MEDIUM/LOW 등급 threshold(80/60)는 legacy와 동일한 값을 그대로 적용한다
 * (등급 기준 자체는 이번 비교에서 바꾸지 않는다).
 */
record ConfidenceV11Result(
        double score,
        String level,
        String label,
        double effectiveSampleSize,
        double effectiveSampleScore,
        double stabilityScore,
        double scopeScore
) {
}