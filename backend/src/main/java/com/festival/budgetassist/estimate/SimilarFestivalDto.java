package com.festival.budgetassist.estimate;

/** 유사 축제 Top N 응답 항목. 디버깅/설명 가능성을 위해 하위 점수를 전부 노출한다. */
public record SimilarFestivalDto(
        String festivalName,
        String regionName,
        String districtName,
        String festivalTypeName,
        String venueTypeName,
        Integer actualDurationDays,
        Long actualBudgetKrw,
        long durationAdjustedBudgetKrw,
        double festivalTypeScore,
        double regionScore,
        double venueTypeScore,
        double durationScore,
        double similarity,
        double weight
) {
}