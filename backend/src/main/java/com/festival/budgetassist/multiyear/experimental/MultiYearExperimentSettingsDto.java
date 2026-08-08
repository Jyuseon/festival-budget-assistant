package com.festival.budgetassist.multiyear.experimental;

/**
 * 현재 활성화된 다년도 실험 설정(지시사항 9절) - UI가 사용자에게 "지금 어떤 모델을 보고
 * 있는지" 혼동 없이 보여주기 위한 것이다. 이번 구현은 전부 OFF/NONE/null로 고정되어 있다 -
 * CPI/series correction/recency/COVID 중 어느 것도 아직 채택되지 않았다.
 */
public record MultiYearExperimentSettingsDto(
        boolean inflationAdjusted,
        String seriesCorrection,
        Double recencyHalfLife,
        boolean covidAdjustment
) {
    static final MultiYearExperimentSettingsDto BASELINE_S0 =
            new MultiYearExperimentSettingsDto(false, "NONE", null, false);
}