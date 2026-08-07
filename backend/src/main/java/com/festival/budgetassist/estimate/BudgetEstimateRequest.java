package com.festival.budgetassist.estimate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * POST /api/v1/budget-estimates 요청 본문.
 * regionCode/festivalType/venueType는 GET /api/v1/metadata가 내려주는 code 값 그대로 받는다
 * (예: "SEOUL", "CULTURE_ART", "GREEN"). district는 선택값이며 metadata의
 * districtsByRegion에 있는 문자열과 정확히 일치해야 매칭된다.
 */
public record BudgetEstimateRequest(
        @NotBlank String regionCode,
        String district,
        @NotBlank String festivalType,
        @NotBlank String venueType,
        @NotNull @Min(2) Integer durationDays
) {
}