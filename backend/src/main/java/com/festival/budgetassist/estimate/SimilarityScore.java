package com.festival.budgetassist.estimate;

/** 후보 하나에 대한 유사도 하위 점수와 최종 similarity/weight. */
record SimilarityScore(
        double festivalTypeScore,
        double regionScore,
        double venueTypeScore,
        double durationScore,
        double similarity,
        double weight
) {
}