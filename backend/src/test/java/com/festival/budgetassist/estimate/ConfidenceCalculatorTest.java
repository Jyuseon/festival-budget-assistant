package com.festival.budgetassist.estimate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConfidenceCalculatorTest {

    private final AlgorithmConfig config = new AlgorithmConfig();
    private final ConfidenceCalculator calculator = new ConfidenceCalculator(config);

    @Test
    void mediumConfidence_matchesHandCalculation() {
        // sampleScore = 20/25 = 0.8
        // dispersionRatio = min((300-100)/200, 1.5)/1.5 = 1.0/1.5 = 0.6667 -> stability=0.3333
        // ratio = 0.8*0.30 + 0.9*0.40 + 0.3333*0.20 + 1.0*0.10 = 0.24+0.36+0.06667+0.10 = 0.76667
        ConfidenceResult result = calculator.calculate(20, 0.9, 100, 200, 300, 1.0);

        assertEquals(76.67, result.score(), 0.1);
        assertEquals("MEDIUM", result.level());
        assertEquals("보통", result.label());
    }

    @Test
    void highSampleHighSimilarityLowDispersion_isHighConfidence() {
        ConfidenceResult result = calculator.calculate(30, 0.95, 190, 200, 210, 1.0);
        assertTrue(result.score() >= config.getConfidenceHighThreshold());
        assertEquals("HIGH", result.level());
    }

    @Test
    void sampleBelowLowSampleCap_scoreIsCapped() {
        // sampleCount=4 < confidenceLowSampleCap(5) -> 아무리 다른 지표가 좋아도 55점을 넘을 수 없다.
        ConfidenceResult result = calculator.calculate(4, 1.0, 100, 100, 100, 1.0);
        assertTrue(result.score() <= config.getConfidenceLowSampleCapScore());
    }

    @Test
    void sampleBelowInsufficientThreshold_isInsufficientData() {
        ConfidenceResult result = calculator.calculate(2, 1.0, 100, 100, 100, 1.0);
        assertEquals("LOW", result.level());
        assertEquals("데이터 부족", result.label());
    }
}