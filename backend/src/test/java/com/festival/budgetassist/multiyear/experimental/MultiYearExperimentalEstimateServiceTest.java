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
import com.festival.budgetassist.multiyear.domain.MultiYearImportBatch;
import com.festival.budgetassist.multiyear.repository.MultiYearFestivalRecordRepository;
import com.festival.budgetassist.multiyear.repository.MultiYearImportBatchRepository;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class MultiYearExperimentalEstimateServiceTest {

    @Autowired
    private MultiYearExperimentalEstimateService service;
    @Autowired
    private MultiYearFestivalRecordRepository recordRepository;
    @Autowired
    private MultiYearImportBatchRepository batchRepository;

    private MultiYearImportBatch batch;

    @BeforeEach
    void setUp() {
        batch = batchRepository.save(MultiYearImportBatch.builder()
                .originalFileName("experimental-fixture.csv")
                .fileHash(UUID.randomUUID().toString().replace("-", "") + "0000000000000000000000")
                .totalRows(0).unitScaleSuspectRows(0).missingOrNonpositiveBudgetRows(0)
                .missingDurationRows(0).covidAffectedRows(0)
                .importedAt(Instant.now()).status(ImportStatus.SUCCESS)
                .build());
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
    void estimate_returnsBaselineS0WithAllExperimentSettingsOff() {
        for (int y = 2018; y <= 2025; y++) {
            row(y, y, "실험API테스트축제" + y, Region.GYEONGGI, "이천시", "CULTURE_ART", 100 + y, BudgetQualityFlag.VALID);
        }

        MultiYearExperimentalEstimateResponse response = service.estimate(
                new MultiYearExperimentalEstimateRequest("GYEONGGI", "이천시", "CULTURE_ART", "GREEN", 5));

        assertEquals("MULTIYEAR_BASELINE_S0", response.model());
        assertEquals(2026, response.targetYear());
        assertEquals(2017, response.trainingYearFrom());
        assertEquals(2025, response.trainingYearTo());
        assertTrue(response.sampleCount() > 0);
        assertTrue(response.estimatedBudgetKrw() > 0);

        assertFalse(response.experimentSettings().inflationAdjusted());
        assertEquals("NONE", response.experimentSettings().seriesCorrection());
        assertNull(response.experimentSettings().recencyHalfLife());
        assertFalse(response.experimentSettings().covidAdjustment());
    }

    @Test
    void estimate_unitScaleSuspectCandidate_excludedFromSampleCount() {
        row(2020, 1, "정상후보", Region.GYEONGGI, "이천시", "CULTURE_ART", 100, BudgetQualityFlag.VALID);
        row(2021, 2, "이상치후보", Region.GYEONGGI, "이천시", "CULTURE_ART", 999_999, BudgetQualityFlag.UNIT_SCALE_SUSPECT);

        MultiYearExperimentalEstimateResponse response = service.estimate(
                new MultiYearExperimentalEstimateRequest("GYEONGGI", "이천시", "CULTURE_ART", "GREEN", 5));

        assertEquals(1, response.sampleCount());
        assertEquals(100_000_000L, response.estimatedBudgetKrw());
    }

    @Test
    void estimate_topSimilarFestivals_neverNullVenueOrDurationForcedValue() {
        // venue/duration 없는 옛 데이터 스타일 후보 - null 그대로 노출돼야 한다(OTHER/UNKNOWN 강제 금지).
        for (int i = 1; i <= 25; i++) {
            row(2020, i, "상세정보테스트" + i, Region.GYEONGGI, "이천시", "CULTURE_ART", 100 + i, BudgetQualityFlag.VALID);
        }

        MultiYearExperimentalEstimateResponse response = service.estimate(
                new MultiYearExperimentalEstimateRequest("GYEONGGI", "이천시", "CULTURE_ART", "GREEN", 5));

        assertTrue(response.topSimilarFestivals().size() > 0);
        assertTrue(response.topSimilarFestivals().stream().allMatch(f -> f.venueType() == null),
                "원본에 venueType이 없는 후보는 null 그대로 노출돼야 함(강제 대체 금지)");
        assertTrue(response.topSimilarFestivals().stream().allMatch(f -> f.durationDays() == null));
    }

    @Test
    void estimate_invalidRegionCode_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> service.estimate(
                new MultiYearExperimentalEstimateRequest("NOT_A_REGION", null, "CULTURE_ART", "GREEN", 5)));
    }
}