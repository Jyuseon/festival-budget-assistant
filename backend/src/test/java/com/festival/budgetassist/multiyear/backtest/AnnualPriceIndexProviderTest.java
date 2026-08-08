package com.festival.budgetassist.multiyear.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** CPI 리소스({@code multiyear-annual-cpi.tsv})가 올바르게 로드되는지 - Spring 컨텍스트 없이 빠르게 검증. */
class AnnualPriceIndexProviderTest {

    private final AnnualPriceIndexProvider provider = new AnnualPriceIndexProvider();

    @Test
    void loadsAllYearsFrom2017To2026_withNonBlankSource() {
        for (int year = 2017; year <= 2026; year++) {
            var idx = provider.get(year);
            assertTrue(idx.isPresent(), year + "년 CPI가 있어야 함");
            assertTrue(idx.get().indexValue() > 0);
            assertFalse(idx.get().source().isBlank(), "출처가 비어 있으면 안 됨");
        }
    }

    @Test
    void indexIsMonotonicallyIncreasing_acrossTheWholeRange() {
        // 2017~2026 사이 디플레이션 연도가 없었으므로(headline CPI 기준) 단조증가여야 한다 -
        // 데이터 입력 실수(자릿수 오타 등)를 잡는 sanity check.
        double previous = provider.get(2017).orElseThrow().indexValue();
        for (int year = 2018; year <= 2026; year++) {
            double current = provider.get(year).orElseThrow().indexValue();
            assertTrue(current > previous, year + "년 CPI(" + current + ")가 " + (year - 1) + "년(" + previous + ")보다 커야 함");
            previous = current;
        }
    }

    @Test
    void year2020_isTheBaseYear_indexValueIsExactly100() {
        assertEquals(100.0, provider.get(2020).orElseThrow().indexValue(), 1e-9);
    }

    @Test
    void unknownYear_returnsEmpty() {
        assertTrue(provider.get(1999).isEmpty());
        assertTrue(provider.get(2099).isEmpty());
    }
}