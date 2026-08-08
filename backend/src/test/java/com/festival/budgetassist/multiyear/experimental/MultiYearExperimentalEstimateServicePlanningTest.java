package com.festival.budgetassist.multiyear.experimental;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
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
 * {@code MultiYearExperimentalEstimateRequest.planningYear}/{@code referenceDataPolicy} 확장
 * 경로 검증(사용자 요청 12/13/16절) - 하위호환(planningYear 없는 기존 요청)과 새 경로(있는 요청)
 * 둘 다 이 서비스 레벨에서 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class MultiYearExperimentalEstimateServicePlanningTest {

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
                .originalFileName("experimental-planning-fixture.csv")
                .fileHash(UUID.randomUUID().toString().replace("-", "") + "0000000000000000000000")
                .totalRows(0).unitScaleSuspectRows(0).missingOrNonpositiveBudgetRows(0)
                .missingDurationRows(0).covidAffectedRows(0)
                .importedAt(Instant.now()).status(ImportStatus.SUCCESS)
                .build());
        for (int y = 2017; y <= 2026; y++) {
            row(y, y, "실험API플래닝테스트" + y, Region.GYEONGGI, "이천시", "CULTURE_ART", 100 + y, BudgetQualityFlag.VALID);
        }
    }

    private MultiYearFestivalRecord row(int year, int sourceRow, String name, Region region, String district,
                                         String type, long budgetMillion, BudgetQualityFlag quality) {
        return recordRepository.save(MultiYearFestivalRecord.builder()
                .datasetYear(year).sourceRowNumber(sourceRow).sourceSheet("test")
                .festivalName(name)
                .regionRaw(region.getDisplayName()).regionText(region.getDisplayName()).regionCode(region)
                .districtRaw(district).districtText(district)
                .festivalType(type)
                .budgetTotalMillion(BigDecimal.valueOf(budgetMillion))
                .budgetQualityFlag(quality)
                .covidAffected(false)
                .importBatch(batch)
                .build());
    }

    @Test
    void estimate_withoutPlanningYear_staysOnLegacyBaselineS0Path() {
        MultiYearExperimentalEstimateResponse response = service.estimate(
                new MultiYearExperimentalEstimateRequest("GYEONGGI", "이천시", "CULTURE_ART", "GREEN", 5, null, null));

        assertEquals("MULTIYEAR_BASELINE_S0", response.model());
        assertEquals(2026, response.targetYear());
        assertEquals(2025, response.trainingYearTo(), "레거시 경로는 2026을 제외한 2017~2025만 써야 함");
        assertNull(response.requestedReferenceDataPolicy(), "레거시 요청이면 신규 필드는 전부 null이어야 함");
        assertNull(response.appliedReferenceDataPolicy());
        assertNull(response.effectiveYearCount());
        assertNull(response.yearWeightBreakdown());
    }

    @Test
    void estimate_planningYear2027_historicalOnly_usesCandidateSelectorV1AndFullTenYearRange() {
        MultiYearExperimentalEstimateResponse response = service.estimate(
                new MultiYearExperimentalEstimateRequest("GYEONGGI", "이천시", "CULTURE_ART", "GREEN", 5,
                        2027, "HISTORICAL_ONLY"));

        assertEquals("MULTIYEAR_PLANNING_V1", response.model());
        assertEquals(2027, response.targetYear());
        assertEquals(2017, response.trainingYearFrom());
        assertEquals(2026, response.trainingYearTo(), "2027 기획은 2017~2026 전체(10개년)를 참고해야 함");
        assertEquals("HISTORICAL_ONLY", response.requestedReferenceDataPolicy());
        assertEquals("HISTORICAL_ONLY", response.appliedReferenceDataPolicy());
        assertFalse(response.yearWeightBreakdown().isEmpty());
    }

    @Test
    void estimate_planningYear2026_includePublishedSameYear_whenPublished_includes2026() {
        publicationStatusRepository.save(MultiYearDatasetPublicationStatus.builder()
                .datasetYear(2026).status(MultiYearDatasetPublicationStatusValue.PUBLISHED_PLAN_COMPLETE).publishedAt(Instant.now())
                .build());

        MultiYearExperimentalEstimateResponse response = service.estimate(
                new MultiYearExperimentalEstimateRequest("GYEONGGI", "이천시", "CULTURE_ART", "GREEN", 5,
                        2026, "INCLUDE_PUBLISHED_SAME_YEAR"));

        assertEquals("INCLUDE_PUBLISHED_SAME_YEAR", response.requestedReferenceDataPolicy());
        assertEquals("INCLUDE_PUBLISHED_SAME_YEAR", response.appliedReferenceDataPolicy());
        assertEquals(2026, response.trainingYearTo());
        assertEquals(2026, response.latestSourceYear());
    }

    @Test
    void estimate_planningYear2026_includePublishedSameYear_whenNotPublished_fallsBackToHistoricalOnly() {
        MultiYearExperimentalEstimateResponse response = service.estimate(
                new MultiYearExperimentalEstimateRequest("GYEONGGI", "이천시", "CULTURE_ART", "GREEN", 5,
                        2026, "INCLUDE_PUBLISHED_SAME_YEAR"));

        assertEquals("INCLUDE_PUBLISHED_SAME_YEAR", response.requestedReferenceDataPolicy(), "요청은 그대로 기록");
        assertEquals("HISTORICAL_ONLY", response.appliedReferenceDataPolicy(), "미공개면 자동으로 낮춰 적용");
        assertEquals(2025, response.trainingYearTo());
        assertTrue(response.latestSourceYear() <= 2025);
    }

    @Test
    void estimate_invalidReferenceDataPolicy_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> service.estimate(
                new MultiYearExperimentalEstimateRequest("GYEONGGI", "이천시", "CULTURE_ART", "GREEN", 5,
                        2027, "NOT_A_POLICY")));
    }
}