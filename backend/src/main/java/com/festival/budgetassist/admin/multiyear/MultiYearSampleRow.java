package com.festival.budgetassist.admin.multiyear;

/**
 * 다년도 원본 데이터 샘플 1행. {@link com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord}에
 * 개인정보 필드가 애초에 없으므로(가이드 패키지 CSV 자체가 담당자 연락처 등을 전부 제외하고
 * 만들어짐) 여기서 노출을 막을 필드가 없다 - 다만 그중에서도 화면에 필요한 컬럼만 옮겨 담는다.
 */
public record MultiYearSampleRow(
        int year,
        String region,
        String district,
        String festivalName,
        String festivalTypeRaw,
        String festivalType,
        String venueNameRaw,
        String venueType,
        Integer durationDays,
        Double budgetTotalMillion,
        String budgetQualityFlag
) {
}