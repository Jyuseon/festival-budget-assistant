package com.festival.budgetassist.multiyear.backtest;

import java.util.List;
import java.util.Map;

import com.festival.budgetassist.estimate.FallbackLevel;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;

record MultiYearCandidateSelectionResult(
        FallbackLevel level,
        List<MultiYearFestivalRecord> candidates,
        List<MultiYearLevelContribution> levelBreakdown,
        Map<Long, FallbackLevel> originLevelByRecordId
) {
}