package com.festival.budgetassist.estimate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * confidence v1.1 후보 공식 검증. legacy {@link ConfidenceCalculatorTest}와는 별도로,
 * 새 stabilityScore(로그 기반)/effectiveSampleScore/scopeScore 공식만 확인한다.
 */
class ConfidenceCalculatorV11Test {

    private final AlgorithmConfig config = new AlgorithmConfig();
    private final ConfidenceCalculator calculator = new ConfidenceCalculator(config);

    @Test
    void stability_noSpread_isMaxScore() {
        // P25 == P75 -> spread = ln(1) = 0 -> stabilityScore = 1/(1+0) = 1.0
        ConfidenceV11Result result = calculator.calculateV11(
                20, new double[]{1, 1, 1, 1, 1}, 0.8, 100, 100, 1.0, FallbackLevel.NATIONWIDE_TYPE_VENUE);
        assertEquals(1.0, result.stabilityScore(), 1e-6);
    }

    @Test
    void stability_eTimesSpread_isHalf() {
        // P75/P25 = e -> spread = 1 -> stabilityScore = 1/(1+1) = 0.5
        double p25 = 100;
        double p75 = p25 * Math.E;
        ConfidenceV11Result result = calculator.calculateV11(
                20, new double[]{1, 1, 1, 1, 1}, 0.8, p25, p75, 1.0, FallbackLevel.NATIONWIDE_TYPE_VENUE);
        assertEquals(0.5, result.stabilityScore(), 1e-6);
    }

    @Test
    void stability_widerSpread_isLowerButNeverCappedAtZero() {
        double narrow = calculator.calculateV11(20, new double[]{1, 1}, 0.8, 100, 200, 1.0, FallbackLevel.NATIONWIDE_TYPE_VENUE).stabilityScore();
        double wide = calculator.calculateV11(20, new double[]{1, 1}, 0.8, 10, 10_000, 1.0, FallbackLevel.NATIONWIDE_TYPE_VENUE).stabilityScore();
        assertTrue(wide < narrow);
        assertTrue(wide > 0, "cap 없이 부드럽게 0에 수렴해야 하며, 정확히 0이 되면 안 됨(극단적인 분산에서도)");
    }

    @Test
    void stability_invalidPercentiles_fallBackToZero() {
        assertEquals(0.0, calculator.calculateV11(20, new double[]{1}, 0.8, 0, 100, 1.0, FallbackLevel.NATIONWIDE_TYPE_VENUE).stabilityScore());
        assertEquals(0.0, calculator.calculateV11(20, new double[]{1}, 0.8, -5, 100, 1.0, FallbackLevel.NATIONWIDE_TYPE_VENUE).stabilityScore());
    }

    @Test
    void effectiveSampleScore_usesEffectiveSampleSizeNotRawCount() {
        // rawSampleCount=4, 하지만 가중치가 한쪽으로 쏠려 effectiveSampleSize는 훨씬 작다.
        ConfidenceV11Result result = calculator.calculateV11(
                4, new double[]{10, 1, 1, 1}, 0.8, 100, 200, 1.0, FallbackLevel.NATIONWIDE_TYPE_VENUE);
        double expectedEss = 169.0 / 103.0;
        assertEquals(expectedEss, result.effectiveSampleSize(), 1e-6);
        assertEquals(Math.min(expectedEss / config.getConfidenceV11EffectiveSampleDivisor(), 1.0), result.effectiveSampleScore(), 1e-6);
    }

    @Test
    void scopeScore_mapsEachFallbackLevelToConfiguredValue() {
        double[] weights = {1, 1, 1, 1, 1};
        assertEquals(config.getScopeScoreSameDistrictTypeVenue(),
                calculator.calculateV11(20, weights, 0.8, 100, 200, 1.0, FallbackLevel.SAME_DISTRICT_TYPE_VENUE).scopeScore());
        assertEquals(config.getScopeScoreSameRegionTypeVenue(),
                calculator.calculateV11(20, weights, 0.8, 100, 200, 1.0, FallbackLevel.SAME_REGION_TYPE_VENUE).scopeScore());
        assertEquals(config.getScopeScoreNationwideTypeVenue(),
                calculator.calculateV11(20, weights, 0.8, 100, 200, 1.0, FallbackLevel.NATIONWIDE_TYPE_VENUE).scopeScore());
        assertEquals(config.getScopeScoreSameRegionType(),
                calculator.calculateV11(20, weights, 0.8, 100, 200, 1.0, FallbackLevel.SAME_REGION_TYPE).scopeScore());
        assertEquals(config.getScopeScoreNationwideType(),
                calculator.calculateV11(20, weights, 0.8, 100, 200, 1.0, FallbackLevel.NATIONWIDE_TYPE).scopeScore());
        assertEquals(config.getScopeScoreGlobalSimilarity(),
                calculator.calculateV11(20, weights, 0.8, 100, 200, 1.0, FallbackLevel.GLOBAL_SIMILARITY).scopeScore());
    }

    @Test
    void lowSampleCount_capsScoreJustLikeLegacy() {
        ConfidenceV11Result result = calculator.calculateV11(
                2, new double[]{1, 1}, 1.0, 100, 100, 1.0, FallbackLevel.SAME_DISTRICT_TYPE_VENUE);
        assertEquals("LOW", result.level());
        assertEquals("데이터 부족", result.label());
    }
}