package com.festival.budgetassist.admin;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 읽기 전용 관리자 API. {@code festival.admin-ui.enabled=true}일 때만 빈이 생성된다
 * (기본값 false, application-local.yml에서만 true) - 운영 환경에서는 이 컨트롤러 자체가
 * 존재하지 않으므로 해당 경로는 항상 404다.
 *
 * <p>쓰기/업로드 API는 없다. 실제 Import는 여전히 CLI({@code FestivalDataImportRunner})로만
 * 수행한다.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/datasets")
@ConditionalOnProperty(prefix = "festival.admin-ui", name = "enabled", havingValue = "true")
class AdminDatasetController {

    private final AdminDatasetQueryService queryService;

    AdminDatasetController(AdminDatasetQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/latest")
    public AdminDatasetOverviewResponse latest() {
        return queryService.getOverview();
    }

    @GetMapping("/latest/summary")
    public AdminDatasetSummaryResponse summary() {
        return queryService.getSummary();
    }

    @GetMapping("/latest/distributions")
    public AdminDatasetDistributionsResponse distributions() {
        return queryService.getDistributions();
    }

    @GetMapping("/latest/issues")
    public AdminDatasetIssuesResponse issues() {
        return queryService.getIssues();
    }

    @GetMapping("/latest/sample")
    public AdminDatasetSampleResponse sample() {
        return queryService.getSample();
    }
}