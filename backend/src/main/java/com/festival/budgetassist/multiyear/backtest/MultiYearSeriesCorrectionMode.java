package com.festival.budgetassist.multiyear.backtest;

/**
 * festivalSeries 중복 보정 실험(S0/S1/S2)의 세 가지 방식. n은 해당 backtest fold의 training
 * 기간 안에서 그 candidate가 속한 series가 관측된 record 수다(전체 2017~2026 recordCount 아님).
 */
enum MultiYearSeriesCorrectionMode {

    /** baseline - 보정 없음. seriesFactor = 1.0 (항상). */
    S0_BASELINE,
    /** soft correction. seriesFactor = 1 / sqrt(n). */
    S1_SOFT_SQRT,
    /** full inverse correction. seriesFactor = 1 / n. */
    S2_FULL_INVERSE;

    double seriesFactor(long n) {
        if (n <= 1) {
            return 1.0;
        }
        return switch (this) {
            case S0_BASELINE -> 1.0;
            case S1_SOFT_SQRT -> 1.0 / Math.sqrt(n);
            case S2_FULL_INVERSE -> 1.0 / n;
        };
    }
}