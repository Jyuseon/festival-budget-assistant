package com.festival.budgetassist.multiyear.experimental;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.festival.budgetassist.multiyear.domain.MultiYearDatasetPublicationStatus;
import com.festival.budgetassist.multiyear.domain.MultiYearDatasetPublicationStatusValue;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;
import com.festival.budgetassist.multiyear.domain.MultiYearImportBatch;
import com.festival.budgetassist.multiyear.repository.MultiYearDatasetPublicationStatusRepository;
import com.festival.budgetassist.multiyear.repository.MultiYearFestivalRecordRepository;
import com.festival.budgetassist.multiyear.repository.MultiYearImportBatchRepository;

/**
 * {@link MultiYearExperimentalEstimateService#planningMetadata}(planningYear 선택지 하드코딩
 * 방지) 검증.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class MultiYearPlanningMetadataTest {

    @Autowired
    private MultiYearExperimentalEstimateService service;
    @Autowired
    private MultiYearFestivalRecordRepository recordRepository;
    @Autowired
    private MultiYearImportBatchRepository batchRepository;
    @Autowired
    private MultiYearDatasetPublicationStatusRepository publicationStatusRepository;

    private MultiYearImportBatch batch;

    @BeforeEach
    void setUp() {
        batch = batchRepository.save(MultiYearImportBatch.builder()
                .originalFileName("planning-metadata-fixture.csv")
                .fileHash(UUID.randomUUID().toString().replace("-", "") + "0000000000000000000000")
                .totalRows(0).unitScaleSuspectRows(0).missingOrNonpositiveBudgetRows(0)
                .missingDurationRows(0).covidAffectedRows(0)
                .importedAt(Instant.now()).status(ImportStatus.SUCCESS)
                .build());
    }

    private void row(int year) {
        recordRepository.save(MultiYearFestivalRecord.builder()
                .datasetYear(year).sourceRowNumber(year).sourceSheet("test")
                .festivalName("메타데이터테스트" + year).regionRaw("경기").regionText("경기").regionCode(Region.GYEONGGI)
                .festivalType("CULTURE_ART").budgetTotalMillion(BigDecimal.valueOf(100))
                .budgetQualityFlag(BudgetQualityFlag.VALID).covidAffected(false).importBatch(batch)
                .build());
    }

    @Test
    void planningMetadata_noHardcodedYears_derivedFromMaxDatasetYear() {
        row(2024);
        row(2025);
        row(2026); // 최신 연도

        MultiYearPlanningMetadataResponse response = service.planningMetadata();

        assertEquals(List.of(2026, 2027), response.availablePlanningYears(),
                "availablePlanningYears는 [최신 datasetYear, +1]이어야 함 - 상수 하드코딩 없이 계산됨");
        assertEquals(2026, response.defaultPlanningYear());
        assertTrue(response.publishedPlanCompleteYears().isEmpty());
    }

    @Test
    void planningMetadata_reflectsPublishedPlanCompleteYears() {
        row(2025);
        row(2026);
        publicationStatusRepository.save(MultiYearDatasetPublicationStatus.builder()
                .datasetYear(2026).status(MultiYearDatasetPublicationStatusValue.PUBLISHED_PLAN_COMPLETE).publishedAt(Instant.now())
                .build());

        MultiYearPlanningMetadataResponse response = service.planningMetadata();

        assertEquals(List.of(2026), response.publishedPlanCompleteYears());
    }

    @Test
    void planningMetadata_noData_returnsEmpty() {
        MultiYearPlanningMetadataResponse response = service.planningMetadata();

        assertTrue(response.availablePlanningYears().isEmpty());
    }
}