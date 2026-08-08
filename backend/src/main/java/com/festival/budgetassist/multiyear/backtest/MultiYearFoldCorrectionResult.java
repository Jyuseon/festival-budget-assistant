package com.festival.budgetassist.multiyear.backtest;

import java.util.List;

record MultiYearFoldCorrectionResult(
        MultiYearBacktestFold fold,
        MultiYearSeriesCorrectionMode mode,
        List<MultiYearSeriesCorrectionPrediction> predictions,
        int trainingPoolSize,
        int evalTargetCount,
        int evalExcludedNoFinalSample
) {
}