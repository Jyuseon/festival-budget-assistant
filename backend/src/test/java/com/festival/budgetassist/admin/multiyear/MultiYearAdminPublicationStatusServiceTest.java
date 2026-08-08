package com.festival.budgetassist.admin.multiyear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.festival.budgetassist.festival.domain.ImportStatus;
import com.festival.budgetassist.festival.domain.Region;
import com.festival.budgetassist.multiyear.domain.BudgetQualityFlag;
import com.festival.budgetassist.multiyear.domain.MultiYearDatasetPublicationStatusValue;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;
import com.festival.budgetassist.multiyear.domain.MultiYearImportBatch;
import com.festival.budgetassist.multiyear.repository.MultiYearFestivalRecordRepository;
import com.festival.budgetassist.multiyear.repository.MultiYearImportBatchRepository;

/**
 * {@link MultiYearAdminPublicationStatusService} 검증 - 연도별 publication status 조회/설정
 * (사용자 요청: "관리자 화면에서 연도별 publication status를 확인/설정할 수 있는 최소 기능").
 * HTTP 계층 테스트는 이 프로젝트 관례상 만들지 않는다({@code MultiYearAdminDatasetControllerEnabledTest}
 * 참고 - 빈 등록 여부만 확인).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class MultiYearAdminPublicationStatusServiceTest {

    @Autowired
    private MultiYearAdminPublicationStatusService service;
    @Autowired
    private MultiYearFestivalRecordRepository recordRepository;
    @Autowired
    private MultiYearImportBatchRepository batchRepository;

    private MultiYearImportBatch batch;

    @BeforeEach
    void setUp() {
        batch = batchRepository.save(MultiYearImportBatch.builder()
                .originalFileName("admin-publication-status-fixture.csv")
                .fileHash(UUID.randomUUID().toString().replace("-", "") + "0000000000000000000000")
                .totalRows(0).unitScaleSuspectRows(0).missingOrNonpositiveBudgetRows(0)
                .missingDurationRows(0).covidAffectedRows(0)
                .importedAt(Instant.now()).status(ImportStatus.SUCCESS)
                .build());
        recordRepository.save(MultiYearFestivalRecord.builder()
                .datasetYear(2026).sourceRowNumber(1).sourceSheet("test")
                .festivalName("공개상태테스트").regionRaw("경기").regionText("경기").regionCode(Region.GYEONGGI)
                .festivalType("CULTURE_ART").budgetTotalMillion(BigDecimal.valueOf(100))
                .budgetQualityFlag(BudgetQualityFlag.VALID).covidAffected(false).importBatch(batch)
                .build());
    }

    @Test
    void list_yearWithoutExplicitStatus_defaultsToPartial() {
        MultiYearAdminPublicationStatusResponse response = service.list();

        MultiYearAdminPublicationStatusEntry entry = response.years().stream()
                .filter(e -> e.datasetYear() == 2026).findFirst().orElseThrow();
        assertEquals(MultiYearDatasetPublicationStatusValue.PARTIAL, entry.status(), "명시적으로 설정한 적 없으면 PARTIAL이 기본값");
        assertNull(entry.publishedAt());
        assertEquals(1, entry.recordCount());
    }

    @Test
    void setStatus_toPublishedPlanComplete_recordsPublishedAtAndReflectsInList() {
        MultiYearAdminPublicationStatusEntry updated = service.setStatus(2026, MultiYearDatasetPublicationStatusValue.PUBLISHED_PLAN_COMPLETE);

        assertEquals(MultiYearDatasetPublicationStatusValue.PUBLISHED_PLAN_COMPLETE, updated.status());
        assertNotNull(updated.publishedAt());

        MultiYearAdminPublicationStatusEntry fromList = service.list().years().stream()
                .filter(e -> e.datasetYear() == 2026).findFirst().orElseThrow();
        assertEquals(MultiYearDatasetPublicationStatusValue.PUBLISHED_PLAN_COMPLETE, fromList.status());
    }

    @Test
    void setStatus_revertingToPartial_clearsPublishedAt() {
        service.setStatus(2026, MultiYearDatasetPublicationStatusValue.PUBLISHED_PLAN_COMPLETE);
        MultiYearAdminPublicationStatusEntry reverted = service.setStatus(2026, MultiYearDatasetPublicationStatusValue.PARTIAL);

        assertEquals(MultiYearDatasetPublicationStatusValue.PARTIAL, reverted.status());
        assertNull(reverted.publishedAt(), "PARTIAL로 되돌리면 publishedAt도 비워야 함");
    }

    @Test
    void list_onlyIncludesYearsThatActuallyHaveData() {
        MultiYearAdminPublicationStatusResponse response = service.list();
        assertTrue(response.years().stream().allMatch(e -> e.recordCount() > 0));
        List<Integer> years = response.years().stream().map(MultiYearAdminPublicationStatusEntry::datasetYear).toList();
        assertEquals(List.of(2026), years);
    }
}