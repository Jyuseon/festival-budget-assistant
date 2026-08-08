package com.festival.budgetassist.multiyear.backtest;

/**
 * {@link MultiYearSimilarityCalculator}의 결과. production {@code SimilarityScore}와 달리
 * venue/duration은 "이번 비교에 실제로 쓰였는지"(available)를 함께 담는다 - 둘 다 없으면
 * 그 feature는 similarity 합산에서 통째로 빠지고(가중치 재정규화), score 필드는 참고용으로
 * NaN이 아니라 0.0으로 채워 둔다(합산에는 안 쓰이므로 무해하다).
 */
record MultiYearSimilarityScore(
        double typeScore,
        double regionScore,
        boolean venueAvailable,
        double venueScore,
        boolean durationAvailable,
        double durationScore,
        double similarity,
        double weight
) {
}