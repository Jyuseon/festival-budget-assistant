package com.festival.budgetassist.multiyear.experimental;

/**
 * 다년도 실험 예측에 기여한 Top 후보 1건(지시사항 10절) - 어떤 과거 축제가 왜 높은 weight를
 * 받았는지 디버깅할 수 있게 한다. venueType/durationDays는 원본에 없으면 null 그대로 노출한다
 * (강제로 OTHER/UNKNOWN 채우지 않음).
 */
public record MultiYearSimilarFestivalDto(
        int sourceYear,
        String festivalName,
        String region,
        String district,
        String festivalType,
        String venueType,
        Integer durationDays,
        long originalBudgetKrw,
        long durationAdjustedBudgetKrw,
        double similarity,
        double finalWeight,
        String fallbackStage
) {
}