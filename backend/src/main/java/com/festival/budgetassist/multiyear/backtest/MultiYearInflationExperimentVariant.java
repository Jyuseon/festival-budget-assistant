package com.festival.budgetassist.multiyear.backtest;

/** inflation x series-correction 2x2 실험의 4가지 조합 (S2는 이번 라운드 제외). */
enum MultiYearInflationExperimentVariant {

    A_S0_INFLATION_OFF(MultiYearSeriesCorrectionMode.S0_BASELINE, false, "A. S0 + inflation OFF (현재 baseline)"),
    B_S1_INFLATION_OFF(MultiYearSeriesCorrectionMode.S1_SOFT_SQRT, false, "B. S1 + inflation OFF (soft series correction)"),
    C_S0_INFLATION_ON(MultiYearSeriesCorrectionMode.S0_BASELINE, true, "C. S0 + inflation ON"),
    D_S1_INFLATION_ON(MultiYearSeriesCorrectionMode.S1_SOFT_SQRT, true, "D. S1 + inflation ON");

    private final MultiYearSeriesCorrectionMode seriesMode;
    private final boolean inflationOn;
    private final String label;

    MultiYearInflationExperimentVariant(MultiYearSeriesCorrectionMode seriesMode, boolean inflationOn, String label) {
        this.seriesMode = seriesMode;
        this.inflationOn = inflationOn;
        this.label = label;
    }

    MultiYearSeriesCorrectionMode seriesMode() {
        return seriesMode;
    }

    boolean inflationOn() {
        return inflationOn;
    }

    String label() {
        return label;
    }
}