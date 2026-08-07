package com.festival.budgetassist.estimate;

/**
 * confidenceScore를 구성하는 하위 점수(각 0~1). 개발 모드에서만
 * {@link BudgetEstimateResponse#confidenceBreakdown()}로 노출된다 - 운영 응답에는 없다.
 *
 * <p>앞 4개(sampleScore~completenessScore)는 현재 기본(legacy) 공식의 구성요소다.
 * 뒤쪽 v1.1* 필드들은 비교용 후보 공식({@link ConfidenceCalculator#calculateV11})의
 * 산출물이며, 이번 응답의 대표 {@code confidence} 필드는 여전히 legacy 값을 쓴다 -
 * v1.1은 아직 기본 공식으로 채택되지 않았다.</p>
 */
public record ConfidenceBreakdown(
        double sampleScore,
        double similarityScore,
        double stabilityScore,
        double completenessScore,

        int rawSampleCount,
        double effectiveSampleSize,
        double effectiveSampleScore,
        double stabilityScoreV11,
        double scopeScore,
        double confidenceScoreV11,
        String confidenceLevelV11,

        /**
         * v1.2 = v1.1과 같은 effectiveSampleScore/stabilityScoreV11/completenessScore를
         * 재사용하되 scopeScore를 완전히 빼고 재가중한 값. 새 하위점수 필드가 따로 없는 건
         * v1.1 필드들과 정확히 같은 값을 공유하기 때문이다(가중치 조합만 다르다).
         */
        double confidenceScoreV12,
        String confidenceLevelV12
) {
}