package com.festival.budgetassist.estimate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WeightedStatisticsTest {

    private static final double DELTA = 1e-6;

    @Test
    void weightedMean_equalWeights_isPlainAverage() {
        double result = WeightedStatistics.weightedMean(new double[]{10, 20, 30}, new double[]{1, 1, 1});
        assertEquals(20.0, result, DELTA);
    }

    @Test
    void weightedMean_unequalWeights() {
        double result = WeightedStatistics.weightedMean(new double[]{10, 20}, new double[]{1, 3});
        assertEquals(17.5, result, DELTA);
    }

    @Test
    void weightedGeometricMean_matchesKnownExample() {
        // geometric mean of 4 and 9 (equal weight) = sqrt(36) = 6
        double result = WeightedStatistics.weightedGeometricMean(new double[]{4, 9}, new double[]{1, 1});
        assertEquals(6.0, result, DELTA);
    }

    @Test
    void weightedQuantile_equalWeights_matchesPlainLinearInterpolation() {
        double[] values = {10, 20, 30, 40};
        double[] equalWeights = {1, 1, 1, 1};

        double weightedMedian = WeightedStatistics.weightedQuantile(values, equalWeights, 0.5);
        double plainMedian = WeightedStatistics.quantile(values, 0.5);

        assertEquals(25.0, plainMedian, DELTA);
        assertEquals(plainMedian, weightedMedian, DELTA, "동일 가중치일 때는 일반 선형보간 백분위수와 같아야 함");
    }

    @Test
    void weightedQuantile_clampsAtBoundaries() {
        double[] values = {10, 20, 30};
        double[] weights = {1, 1, 1};
        assertEquals(10.0, WeightedStatistics.weightedQuantile(values, weights, 0.0), DELTA);
        assertEquals(30.0, WeightedStatistics.weightedQuantile(values, weights, 1.0), DELTA);
    }

    @Test
    void weightedQuantile_heavierWeightPullsQuantileTowardIt() {
        // 20에 훨씬 큰 가중치를 주면 중앙값이 20에 가까워야 한다
        double[] values = {10, 20, 30};
        double[] weights = {1, 100, 1};
        double median = WeightedStatistics.weightedQuantile(values, weights, 0.5);
        assertEquals(20.0, median, 0.5);
    }

    @Test
    void clip_boundsValueToRange() {
        assertEquals(5.0, WeightedStatistics.clip(5, 0, 10), DELTA);
        assertEquals(0.0, WeightedStatistics.clip(-1, 0, 10), DELTA);
        assertEquals(10.0, WeightedStatistics.clip(15, 0, 10), DELTA);
    }

    @Test
    void emptyInput_doesNotThrow() {
        assertEquals(0.0, WeightedStatistics.weightedMean(new double[0], new double[0]), DELTA);
        assertEquals(0.0, WeightedStatistics.weightedGeometricMean(new double[0], new double[0]), DELTA);
        assertEquals(0.0, WeightedStatistics.weightedQuantile(new double[0], new double[0], 0.5), DELTA);
    }

    @Test
    void effectiveSampleSize_equalWeights_equalsRawCount() {
        double ess = WeightedStatistics.effectiveSampleSize(new double[]{1, 1, 1, 1});
        assertEquals(4.0, ess, DELTA);
    }

    @Test
    void effectiveSampleSize_oneDominantWeight_isMuchSmallerThanRawCount() {
        // 4건이지만 하나가 압도적으로 크면 "사실상 표본 수"는 훨씬 작아야 한다.
        double ess = WeightedStatistics.effectiveSampleSize(new double[]{10, 1, 1, 1});
        assertEquals(169.0 / 103.0, ess, DELTA);
        assertEquals(true, ess < 4.0);
    }

    @Test
    void effectiveSampleSize_allZeroWeights_returnsZero() {
        assertEquals(0.0, WeightedStatistics.effectiveSampleSize(new double[]{0, 0, 0}), DELTA);
    }
}