package com.festival.budgetassist.multiyear.backtest;

/** {@link MultiYearDataQualityV3Calculator}의 결과 - confidence v3 후보 점수와 그 구성요소. */
record MultiYearDataQualityV3(
        double sampleQuality,
        double similarityQuality,
        double stabilityQuality,
        double completenessQuality,
        double localEvidenceQuality,
        double score
) {
}