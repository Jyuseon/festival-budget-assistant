package com.festival.budgetassist.multiyear.experimental;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * POST /api/v1/experimental/multiyear-budget-estimates 요청 본문 - production
 * {@code BudgetEstimateRequest}와 필드가 완전히 동일하다(같은 화면 입력값을 그대로 양쪽에
 * 전달하기 위함, 지시사항 5절). festivalName은 의도적으로 없다 - production UI에 없는 정보를
 * 다년도 실험에서만 쓰면 안 된다(과거 동일 series budget을 직접 lookup하는 leakage 방지).
 *
 * <p>{@code planningYear}/{@code referenceDataPolicy}는 둘 다 nullable이고, 기존
 * 프론트엔드(planningYear를 보내지 않음)와의 하위호환을 위해 다음과 같이 동작한다:
 * <ul>
 *   <li>{@code planningYear == null}: 기존 그대로 - targetYear=2026 고정, V0(baseline S0)
 *       selector, 응답도 기존과 완전히 동일하다.</li>
 *   <li>{@code planningYear != null}: planningYear 일반화 경로({@code
 *       MultiYearBacktestService#estimateForPlanning}) - {@code MultiYearCandidateSelectorV1}
 *       (V4 Hybrid) selector를 쓴다. {@code referenceDataPolicy}가 없으면 {@code
 *       HISTORICAL_ONLY}로 취급한다.</li>
 * </ul>
 */
public record MultiYearExperimentalEstimateRequest(
        @NotBlank String regionCode,
        String district,
        @NotBlank String festivalType,
        @NotBlank String venueType,
        @NotNull @Min(2) Integer durationDays,
        Integer planningYear,
        String referenceDataPolicy
) {
}