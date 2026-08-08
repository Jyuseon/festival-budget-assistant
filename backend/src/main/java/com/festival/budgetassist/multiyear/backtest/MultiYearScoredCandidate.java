package com.festival.budgetassist.multiyear.backtest;

import com.festival.budgetassist.estimate.FallbackLevel;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;

record MultiYearScoredCandidate(
        MultiYearFestivalRecord record,
        MultiYearSimilarityScore score,
        double adjustedBudgetKrw,
        double winsorizedBudgetKrw,
        FallbackLevel originLevel
) {
}