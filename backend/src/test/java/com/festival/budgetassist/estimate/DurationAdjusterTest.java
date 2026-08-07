package com.festival.budgetassist.estimate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DurationAdjusterTest {

    private static final double DELTA = 1.0;

    private final AlgorithmConfig config = new AlgorithmConfig();
    private final DurationAdjuster adjuster = new DurationAdjuster(config);

    @Test
    void sameDuration_noAdjustment() {
        double result = adjuster.adjust(100_000_000L, 3, 3);
        assertEquals(100_000_000.0, result, DELTA);
    }

    @Test
    void missingSourceDuration_returnsRawBudget() {
        double result = adjuster.adjust(100_000_000L, null, 5);
        assertEquals(100_000_000.0, result, DELTA);
    }

    @Test
    void longerTarget_increasesBudget() {
        double result = adjuster.adjust(100_000_000L, 2, 4);
        double expected = 100_000_000L * Math.pow(2.0, config.getDurationElasticity());
        assertEquals(expected, result, DELTA);
        assertEquals(true, result > 100_000_000L, "기간이 늘면 보정 예산도 늘어야 함");
    }

    @Test
    void extremeRatio_isClampedBeforeExponentiation() {
        // targetDays/sourceDays = 10 (1일 -> 10일) 이지만 clamp 상한 2.0이 적용되어야 한다.
        double resultExtreme = adjuster.adjust(100_000_000L, 1, 10);
        double resultAtClampBoundary = adjuster.adjust(100_000_000L, 1, 2); // ratio = 2.0, clamp 없이도 동일

        assertEquals(resultAtClampBoundary, resultExtreme, DELTA,
                "비율이 clamp 상한(2.0)을 넘으면 clamp된 값과 같은 결과가 나와야 함");
    }

    @Test
    void shorterTarget_decreasesBudget() {
        double result = adjuster.adjust(100_000_000L, 10, 5);
        assertEquals(true, result < 100_000_000L, "기간이 줄면 보정 예산도 줄어야 함");
    }
}