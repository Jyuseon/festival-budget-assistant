package com.festival.budgetassist.estimate;

/**
 * confidence v1.2 후보의 결과. v1.1과 같은 effectiveSampleScore/로그기반 stabilityScore/
 * completenessScore를 그대로 재사용하되 scopeScore 항을 완전히 제외하고 재가중한 것이다.
 * HIGH/MEDIUM/LOW 등급 threshold(80/60)는 legacy·v1.1과 동일하게 적용한다.
 */
record ConfidenceV12Result(double score, String level, String label) {
}