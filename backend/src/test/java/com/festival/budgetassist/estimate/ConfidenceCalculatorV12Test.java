package com.festival.budgetassist.estimate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ConfidenceCalculatorV12Test {

    private final AlgorithmConfig config = new AlgorithmConfig();
    private final ConfidenceCalculator calculator = new ConfidenceCalculator(config);

    @Test
    void combinesFourComponentsWithConfiguredWeights_noScopeTerm() {
        double effectiveSampleScore = 0.9;
        double similarity = 0.8;
        double stability = 0.4;
        double completeness = 0.95;

        double expectedRatio = effectiveSampleScore * config.getConfidenceV12SampleWeight()
                + similarity * config.getConfidenceV12SimilarityWeight()
                + stability * config.getConfidenceV12StabilityWeight()
                + completeness * config.getConfidenceV12CompletenessWeight();

        ConfidenceV12Result result = calculator.calculateV12(20, effectiveSampleScore, similarity, stability, completeness);

        assertEquals(expectedRatio * 100, result.score(), 1e-6);
    }

    @Test
    void weightsSumToOne() {
        double sum = config.getConfidenceV12SampleWeight() + config.getConfidenceV12SimilarityWeight()
                + config.getConfidenceV12StabilityWeight() + config.getConfidenceV12CompletenessWeight();
        assertEquals(1.0, sum, 1e-9);
    }

    @Test
    void lowSampleCount_stillCapsScoreLikeOtherFormulas() {
        ConfidenceV12Result result = calculator.calculateV12(2, 1.0, 1.0, 1.0, 1.0);
        assertEquals("LOW", result.level());
        assertEquals("데이터 부족", result.label());
    }

    @Test
    void sameInputsRegardlessOfFallbackLevel_scoreIsIdentical() {
        // v1.2는 scope를 완전히 빼므로, 같은 4개 구성요소값이면 fallbackLevel과 무관하게
        // (호출 시 애초에 fallbackLevel 인자를 받지 않으므로) 항상 같은 점수가 나와야 한다.
        ConfidenceV12Result a = calculator.calculateV12(30, 0.8, 0.8, 0.4, 0.9);
        ConfidenceV12Result b = calculator.calculateV12(30, 0.8, 0.8, 0.4, 0.9);
        assertEquals(a.score(), b.score(), 1e-9);
    }
}