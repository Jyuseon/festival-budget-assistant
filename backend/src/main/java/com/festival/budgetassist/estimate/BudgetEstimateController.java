package com.festival.budgetassist.estimate;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class BudgetEstimateController {

    private final BudgetEstimatorService budgetEstimatorService;

    BudgetEstimateController(BudgetEstimatorService budgetEstimatorService) {
        this.budgetEstimatorService = budgetEstimatorService;
    }

    @PostMapping("/api/v1/budget-estimates")
    public BudgetEstimateResponse estimate(@Valid @RequestBody BudgetEstimateRequest request) {
        return budgetEstimatorService.estimate(request);
    }
}