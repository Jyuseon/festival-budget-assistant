package com.festival.budgetassist.admin;

/** 개인정보가 제거된 적재 데이터 샘플 1행. FestivalRecord에 없는 필드는 애초에 노출할 수 없다. */
public record SampleRow(
        Integer sourceRowNumber,
        String festivalName,
        String regionName,
        String administrativeDistrict,
        String festivalTypeName,
        String venueName,
        String venueTypeName,
        Integer durationDays,
        String durationSource,
        String cycleTypeName,
        Long totalBudgetKrw,
        String budgetStatus
) {
}