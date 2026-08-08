package com.festival.budgetassist.multiyear.backtest;

import java.util.List;

import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;

/**
 * 한 fold의 leakage-safe 데이터셋.
 *
 * @param trainingPool datasetYear &lt; fold.targetYear 인 record만(데이터 품질/필수 feature 필터 적용 후)
 * @param evalTargets datasetYear == fold.targetYear 인 record만(동일 필터 적용 후)
 * @param trainingExcludedLowQuality training 후보 중 UNIT_SCALE_SUSPECT/MISSING_OR_NONPOSITIVE라 제외된 건수
 * @param trainingExcludedMissingFeature training 후보 중 region/festivalType을 알 수 없어 제외된 건수
 * @param evalExcludedLowQuality 평가 대상 중 UNIT_SCALE_SUSPECT/MISSING_OR_NONPOSITIVE라 제외된 건수
 * @param evalExcludedMissingFeature 평가 대상 중 region/festivalType을 알 수 없어 제외된 건수
 */
record MultiYearBacktestDataset(
        MultiYearBacktestFold fold,
        List<MultiYearFestivalRecord> trainingPool,
        List<MultiYearFestivalRecord> evalTargets,
        int trainingExcludedLowQuality,
        int trainingExcludedMissingFeature,
        int evalExcludedLowQuality,
        int evalExcludedMissingFeature
) {
}