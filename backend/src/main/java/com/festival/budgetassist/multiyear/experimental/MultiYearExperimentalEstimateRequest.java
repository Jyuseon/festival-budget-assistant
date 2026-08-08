package com.festival.budgetassist.multiyear.experimental;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * POST /api/v1/experimental/multiyear-budget-estimates 요청 본문 - production
 * {@code BudgetEstimateRequest}와 필드가 완전히 동일하다(같은 화면 입력값을 그대로 양쪽에
 * 전달하기 위함, 지시사항 5절). festivalName은 의도적으로 없다 - production UI에 없는 정보를
 * 다년도 실험에서만 쓰면 안 된다(과거 동일 series budget을 직접 lookup하는 leakage 방지).
 */
public record MultiYearExperimentalEstimateRequest(
        @NotBlank String regionCode,
        String district,
        @NotBlank String festivalType,
        @NotBlank String venueType,
        @NotNull @Min(2) Integer durationDays
) {
}