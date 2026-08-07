package com.festival.budgetassist.admin.multiyear;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 읽기 전용 다년도(2017~2026) 데이터 검증 관리자 API. 기존
 * {@code /api/v1/admin/datasets}(2026 전용 production 데이터)와는 완전히 분리된 경로이며,
 * 같은 {@code festival.admin-ui.enabled=true} 플래그로 켜고 끈다(기본값 false - 운영에서는
 * 이 컨트롤러 자체가 존재하지 않아 항상 404).
 *
 * <p>쓰기/수정/삭제 API는 없다. Import는 여전히 {@code MultiYearCsvImportRunner} CLI로만
 * 수행하고, 이 API는 그 결과를 사람이 눈으로 검증하기 위한 것이다. {@code /budget-assistant}가
 * 쓰는 production {@code BudgetEstimatorService}/{@code FestivalRecord}는 참조하지 않는다.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/multiyear-datasets")
@ConditionalOnProperty(prefix = "festival.admin-ui", name = "enabled", havingValue = "true")
class MultiYearAdminDatasetController {

    private final MultiYearAdminDatasetQueryService queryService;

    MultiYearAdminDatasetController(MultiYearAdminDatasetQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/summary")
    public MultiYearAdminSummaryResponse summary() {
        return queryService.getSummary();
    }

    @GetMapping("/years/{year}")
    public MultiYearAdminYearDetailResponse yearDetail(@PathVariable int year) {
        return queryService.getYearDetail(year);
    }

    @GetMapping("/years/{year}/distributions")
    public MultiYearAdminDistributionsResponse distributions(@PathVariable int year) {
        return queryService.getDistributions(year);
    }

    @GetMapping("/years/{year}/sample")
    public MultiYearAdminSampleResponse sample(@PathVariable int year,
                                                @RequestParam(required = false) Integer limit,
                                                @RequestParam(required = false) Integer offset) {
        return queryService.getSample(year, limit, offset);
    }
}