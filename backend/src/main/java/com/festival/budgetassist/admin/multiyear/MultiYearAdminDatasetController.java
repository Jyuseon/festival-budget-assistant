package com.festival.budgetassist.admin.multiyear;

import jakarta.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 다년도(2017~2026) 데이터 검증 관리자 API. 기존 {@code /api/v1/admin/datasets}(2026 전용
 * production 데이터)와는 완전히 분리된 경로이며, 같은 {@code festival.admin-ui.enabled=true}
 * 플래그로 켜고 끈다(기본값 false - 운영에서는 이 컨트롤러 자체가 존재하지 않아 항상 404).
 *
 * <p><b>대부분 읽기 전용이다</b> - CSV Import는 여전히 {@code MultiYearCsvImportRunner} CLI로만
 * 수행하고, {@code summary}/{@code years/*} 계열은 그 결과를 사람이 눈으로 검증하기 위한 순수
 * 조회다. {@code /budget-assistant}가 쓰는 production {@code BudgetEstimatorService}/
 * {@code FestivalRecord}는 참조하지 않는다.</p>
 *
 * <p><b>유일한 예외</b>: {@code publication-status}는 연도별 "다년도 개최계획 데이터셋 공개
 * 완성" 여부를 운영자가 직접 표시하는 의도적인 쓰기 기능이다({@link
 * MultiYearAdminPublicationStatusService} 참고, "그 해 축제가 끝났다"는 뜻이 아니다). 이 값이
 * {@code multiyear.experimental}의 {@code ReferenceDataPolicy.INCLUDE_PUBLISHED_SAME_YEAR}
 * 활성화 여부를 결정하므로 관리자 화면에서만 바꿀 수 있게 의도적으로 좁혀 뒀다 - 이 컨트롤러
 * 전체가 이미 {@code festival.admin-ui.enabled}로 잠겨 있어 자동으로 관리자 전용이다.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/multiyear-datasets")
@ConditionalOnProperty(prefix = "festival.admin-ui", name = "enabled", havingValue = "true")
class MultiYearAdminDatasetController {

    private final MultiYearAdminDatasetQueryService queryService;
    private final MultiYearAdminPublicationStatusService publicationStatusService;

    MultiYearAdminDatasetController(MultiYearAdminDatasetQueryService queryService,
                                     MultiYearAdminPublicationStatusService publicationStatusService) {
        this.queryService = queryService;
        this.publicationStatusService = publicationStatusService;
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

    @GetMapping("/publication-status")
    public MultiYearAdminPublicationStatusResponse publicationStatus() {
        return publicationStatusService.list();
    }

    /** 유일한 쓰기 endpoint - 클래스 Javadoc 참고. */
    @PutMapping("/publication-status/{year}")
    public MultiYearAdminPublicationStatusEntry setPublicationStatus(@PathVariable int year,
                                                                       @Valid @RequestBody MultiYearAdminPublicationStatusUpdateRequest request) {
        return publicationStatusService.setStatus(year, request.status());
    }
}