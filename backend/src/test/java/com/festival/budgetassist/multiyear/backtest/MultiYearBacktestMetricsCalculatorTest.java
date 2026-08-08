package com.festival.budgetassist.multiyear.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/** {@link MultiYearBacktestMetricsCalculator}의 순수 지표 계산 - Spring 컨텍스트 없이 빠르게 검증. */
class MultiYearBacktestMetricsCalculatorTest {

    private final MultiYearBacktestMetricsCalculator calculator = new MultiYearBacktestMetricsCalculator();

    private MultiYearBacktestPrediction prediction(long actual, long estimated) {
        double ale = (estimated > 0 && actual > 0) ? Math.abs(Math.log(estimated) - Math.log(actual)) : Double.NaN;
        return new MultiYearBacktestPrediction(2025, 1, "축제", "GYEONGGI", null, "CULTURE_ART", null, null,
                actual, estimated, estimated, estimated, estimated, estimated, 10, 5, "SAME_REGION_TYPE", 70.0,
                true, Math.abs((double) estimated - actual), Math.abs((double) estimated - actual) / actual, ale);
    }

    @Test
    void summarize_computesWithin25And50AndTwoXRatiosCorrectly() {
        // 정확히 맞음(오차 0%), +20%(25%이내), +40%(50%이내지만25%밖), +150%(2x밖)
        List<MultiYearBacktestPrediction> predictions = List.of(
                prediction(100, 100),
                prediction(100, 120),
                prediction(100, 140),
                prediction(100, 250)
        );

        MultiYearBacktestMetricsSummary summary = calculator.summarize(predictions);

        assertEquals(4, summary.evaluationCount());
        assertEquals(0.5, summary.within25PercentRatio(), 1e-9, "0%, 20% 두 건만 25% 이내");
        assertEquals(0.75, summary.within50PercentRatio(), 1e-9, "0%,20%,40% 세 건이 50% 이내");
        assertEquals(0.75, summary.within2xRatio(), 1e-9, "250/100=2.5x인 마지막 건만 0.5x~2.0x 범위 밖");
    }

    @Test
    void budgetSizeBreakdown_assignsBucketsByActualBudget() {
        List<MultiYearBacktestPrediction> predictions = List.of(
                prediction(50_000_000L, 50_000_000L),      // <=100M
                prediction(200_000_000L, 200_000_000L),    // 100M~300M
                prediction(500_000_000L, 500_000_000L),    // 300M~1B
                prediction(2_000_000_000L, 2_000_000_000L),// 1B~3B
                prediction(5_000_000_000L, 5_000_000_000L) // >3B
        );

        List<MultiYearBudgetSizeBucketMetrics> buckets = calculator.budgetSizeBreakdown(predictions);

        assertEquals(5, buckets.size());
        for (MultiYearBudgetSizeBucketMetrics b : buckets) {
            assertEquals(1, b.count(), "각 구간에 정확히 1건씩 들어가야 함: " + b.bucketLabel());
        }
    }

    @Test
    void typeBreakdown_flagsSmallSampleGroups() {
        List<MultiYearBacktestPrediction> predictions = List.of(prediction(100, 100), prediction(100, 110));
        List<MultiYearTypeMetrics> types = calculator.typeBreakdown(predictions);
        assertEquals(1, types.size());
        assertEquals(true, types.get(0).smallSample(), "표본 2건은 SMALL_SAMPLE_THRESHOLD(10) 미만이라 표시돼야 함");
    }
}