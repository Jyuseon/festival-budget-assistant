package com.festival.budgetassist.multiyear.experimental;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * production {@code /api/v1/budget-estimates}와 완전히 분리된 read-only 실험용 endpoint(지시사항
 * 8절). festivalSeries v1 baseline S0 계산식을 실제 사용자 입력에 적용한 결과만 반환하고, DB에는
 * 아무것도 쓰지 않는다(순수 조회+계산).
 */
@RestController
class MultiYearExperimentalEstimateController {

    private final MultiYearExperimentalEstimateService service;

    MultiYearExperimentalEstimateController(MultiYearExperimentalEstimateService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/experimental/multiyear-budget-estimates")
    public MultiYearExperimentalEstimateResponse estimate(@Valid @RequestBody MultiYearExperimentalEstimateRequest request) {
        return service.estimate(request);
    }
}