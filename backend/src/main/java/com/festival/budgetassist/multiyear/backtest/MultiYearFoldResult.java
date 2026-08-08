package com.festival.budgetassist.multiyear.backtest;

import java.util.List;

record MultiYearFoldResult(
        MultiYearBacktestFold fold,
        List<MultiYearBacktestPrediction> predictions,
        int trainingPoolSize,
        int evalTargetCount,
        int evalExcludedNoFinalSample,
        int trainingExcludedLowQuality,
        int trainingExcludedMissingFeature,
        int evalExcludedLowQuality,
        int evalExcludedMissingFeature
) {
}