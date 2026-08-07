package com.festival.budgetassist.admin.multiyear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.festival.budgetassist.festival.domain.ImportStatus;
import com.festival.budgetassist.festival.domain.Region;
import com.festival.budgetassist.multiyear.domain.BudgetQualityFlag;
import com.festival.budgetassist.multiyear.domain.FestivalSeries;
import com.festival.budgetassist.multiyear.domain.FestivalSeriesMatchStatus;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;
import com.festival.budgetassist.multiyear.domain.MultiYearImportBatch;
import com.festival.budgetassist.multiyear.domain.SeriesScope;
import com.festival.budgetassist.festival.domain.VenueType;
import com.festival.budgetassist.multiyear.repository.FestivalSeriesRepository;
import com.festival.budgetassist.multiyear.repository.MultiYearFestivalRecordRepository;
import com.festival.budgetassist.multiyear.repository.MultiYearImportBatchRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class MultiYearAdminDatasetQueryServiceTest {

    @Autowired
    private MultiYearAdminDatasetQueryService queryService;
    @Autowired
    private MultiYearFestivalRecordRepository recordRepository;
    @Autowired
    private MultiYearImportBatchRepository batchRepository;
    @Autowired
    private FestivalSeriesRepository seriesRepository;

    private MultiYearImportBatch batch;

    private void ensureBatch() {
        if (batch == null) {
            batch = batchRepository.save(MultiYearImportBatch.builder()
                    .originalFileName("fixture.csv")
                    .fileHash(UUID.randomUUID().toString().replace("-", ""))
                    .totalRows(0).unitScaleSuspectRows(0).missingOrNonpositiveBudgetRows(0)
                    .missingDurationRows(0).covidAffectedRows(0)
                    .importedAt(Instant.now()).status(ImportStatus.SUCCESS)
                    .build());
        }
    }

    private MultiYearFestivalRecord row(int year, int sourceRow, String name, Region region, String district,
                                         String type, VenueType venueType, Integer durationDays,
                                         String budgetMillion, BudgetQualityFlag flag, boolean covid) {
        ensureBatch();
        return recordRepository.save(MultiYearFestivalRecord.builder()
                .datasetYear(year)
                .sourceRowNumber(sourceRow)
                .sourceSheet("test")
                .festivalName(name)
                .regionRaw(region.getDisplayName())
                .regionText(region.getDisplayName())
                .regionCode(region)
                .districtRaw(district)
                .districtText(district)
                .festivalType(type)
                .venueType(venueType)
                .durationDays(durationDays)
                .budgetTotalMillion(budgetMillion == null ? null : new BigDecimal(budgetMillion))
                .budgetQualityFlag(flag)
                .covidAffected(covid)
                .importBatch(batch)
                .build());
    }

    @Test
    void noData_summaryReportsUnavailableWithFullYearRangeZeroFilled() {
        MultiYearAdminSummaryResponse response = queryService.getSummary();

        assertFalse(response.available());
        assertEquals(0, response.totalRecords());
        assertEquals(10, response.years().size(), "2017~2026 10개년이 항상 포함돼야 함");
        assertEquals(2017, response.years().get(0).datasetYear());
        assertEquals(2026, response.years().get(9).datasetYear());
        response.years().forEach(y -> assertEquals(0, y.totalCount()));
        assertFalse(response.seriesStatus().analyzed(), "series linking을 아직 안 돌렸으면 분석 전이어야 함");
    }

    @Test
    void summary_aggregatesAcrossYearsCorrectly() {
        row(2017, 1, "축제A", Region.SEOUL, "종로구", "CULTURE_ART", null, 3, "200", BudgetQualityFlag.VALID, false);
        row(2017, 2, "축제B", Region.SEOUL, "종로구", "CULTURE_ART", null, null, null, BudgetQualityFlag.MISSING_OR_NONPOSITIVE, false);
        row(2020, 3, "축제C", Region.BUSAN, "해운대구", "NATURE_ECOLOGY", null, 5, "500", BudgetQualityFlag.VALID, true);

        MultiYearAdminSummaryResponse response = queryService.getSummary();

        assertTrue(response.available());
        assertEquals(3, response.totalRecords());

        MultiYearYearSummary y2017 = response.years().stream().filter(y -> y.datasetYear() == 2017).findFirst().orElseThrow();
        assertEquals(2, y2017.totalCount());
        assertEquals(1, y2017.validBudgetCount());
        assertEquals(1, y2017.missingOrNonPositiveBudgetCount());
        assertEquals(50.0, y2017.durationAvailableRatePercent());

        MultiYearYearSummary y2020 = response.years().stream().filter(y -> y.datasetYear() == 2020).findFirst().orElseThrow();
        assertEquals(1, y2020.totalCount());
        assertEquals(1, y2020.covidAffectedCount());

        MultiYearYearSummary y2019 = response.years().stream().filter(y -> y.datasetYear() == 2019).findFirst().orElseThrow();
        assertEquals(0, y2019.totalCount(), "데이터 없는 연도는 0으로 채워져야 함");
    }

    @Test
    void year2024_unitScaleSuspectIsCountedButExcludedFromBudgetStatistics() {
        row(2024, 1, "정상축제1", Region.GYEONGGI, "수원시", "CULTURE_ART", VenueType.GREEN, 3, "200", BudgetQualityFlag.VALID, false);
        row(2024, 2, "정상축제2", Region.GYEONGGI, "수원시", "CULTURE_ART", VenueType.GREEN, 3, "300", BudgetQualityFlag.VALID, false);
        row(2024, 3, "단위의심축제", Region.GYEONGGI, "수원시", "CULTURE_ART", VenueType.GREEN, 3, "5000000", BudgetQualityFlag.UNIT_SCALE_SUSPECT, false);

        MultiYearAdminYearDetailResponse detail = queryService.getYearDetail(2024);

        assertTrue(detail.available());
        assertEquals(3, detail.qualityCard().totalCount());
        assertEquals(1, detail.qualityCard().budgetUnitSuspectCount(), "2024 UNIT_SCALE_SUSPECT 건수가 눈에 띄게 집계돼야 함");
        assertEquals(2, detail.qualityCard().validBudgetCount());

        // 예산 통계는 VALID 2건(200, 300)만 반영 - 5,000,000짜리 의심값이 통계를 왜곡하면 안 된다.
        assertEquals(2, detail.budgetStatistics().sampleCount());
        assertEquals(250.0, detail.budgetStatistics().meanMillion());
        assertEquals(300.0, detail.budgetStatistics().maxMillion());

        MultiYearAdminSummaryResponse summary = queryService.getSummary();
        MultiYearYearSummary y2024 = summary.years().stream().filter(y -> y.datasetYear() == 2024).findFirst().orElseThrow();
        assertEquals(1, y2024.budgetUnitSuspectCount());
    }

    @Test
    void distributions_venueTypeUnavailableForLegacyYearsButAvailableFor2025Plus() {
        row(2019, 1, "구연도축제", Region.JEONBUK, "전주시", "CULTURE_ART", null, null, "100", BudgetQualityFlag.VALID, false);
        row(2025, 1, "신연도축제", Region.JEONBUK, "전주시", "CULTURE_ART", VenueType.WATERFRONT, 3, "100", BudgetQualityFlag.VALID, false);

        MultiYearAdminDistributionsResponse legacy = queryService.getDistributions(2019);
        assertFalse(legacy.venueTypeDataAvailable(), "2019는 원본에 venueType 항목 자체가 없음");
        assertTrue(legacy.venueTypeCounts().isEmpty());

        MultiYearAdminDistributionsResponse modern = queryService.getDistributions(2025);
        assertTrue(modern.venueTypeDataAvailable());
        assertEquals(1, modern.venueTypeCounts().size());
    }

    @Test
    void distributions_covidAffectedFlagIsExposedFor2020Only() {
        row(2020, 1, "코로나축제", Region.SEOUL, "중구", "CULTURE_ART", null, null, "100", BudgetQualityFlag.VALID, true);
        row(2019, 1, "평시축제", Region.SEOUL, "중구", "CULTURE_ART", null, null, "100", BudgetQualityFlag.VALID, false);

        assertTrue(queryService.getDistributions(2020).covidAffectedYear());
        assertFalse(queryService.getDistributions(2019).covidAffectedYear());
    }

    @Test
    void sample_limitIsClampedAndNeverDumpsFullYear() {
        for (int i = 1; i <= 150; i++) {
            row(2026, i, "축제" + i, Region.GANGWON, "춘천시", "CULTURE_ART", VenueType.GREEN, 3, "100", BudgetQualityFlag.VALID, false);
        }

        MultiYearAdminSampleResponse unbounded = queryService.getSample(2026, 9999, 0);
        assertEquals(100, unbounded.rows().size(), "limit을 아무리 크게 요청해도 서버가 100건으로 강제 상한을 둬야 함");
        assertEquals(150, unbounded.totalCountForYear());

        MultiYearAdminSampleResponse defaultPage = queryService.getSample(2026, null, null);
        assertEquals(20, defaultPage.rows().size(), "limit 미지정 시 기본값 20건");

        MultiYearAdminSampleResponse secondPage = queryService.getSample(2026, 20, 20);
        assertEquals("축제21", secondPage.rows().get(0).festivalName());
    }

    @Test
    void sample_rowsNeverIncludePersonalInformationFields() throws Exception {
        row(2026, 1, "축제A", Region.SEOUL, "종로구", "CULTURE_ART", VenueType.GREEN, 3, "100", BudgetQualityFlag.VALID, false);

        MultiYearAdminSampleResponse sample = queryService.getSample(2026, 1, 0);
        java.util.List<String> bannedTokens = java.util.List.of("contact", "phone", "manager", "department", "staff", "organizer", "owner");
        for (java.lang.reflect.RecordComponent rc : MultiYearSampleRow.class.getRecordComponents()) {
            String lower = rc.getName().toLowerCase(java.util.Locale.ROOT);
            for (String banned : bannedTokens) {
                assertFalse(lower.contains(banned), "개인정보로 의심되는 필드명: " + rc.getName());
            }
        }
        assertEquals(1, sample.rows().size());
    }

    @Test
    void seriesStatus_reflectsPersistedFestivalSeriesData() {
        seriesRepository.save(FestivalSeries.builder()
                .canonicalName("싱글톤축제").canonicalRegion("서울").firstObservedYear(2020).lastObservedYear(2020)
                .recordCount(1).matchStatus(FestivalSeriesMatchStatus.SINGLETON).scope(SeriesScope.DISTRICT_LEVEL)
                .build());
        seriesRepository.save(FestivalSeries.builder()
                .canonicalName("장수축제").canonicalRegion("서울").firstObservedYear(2017).lastObservedYear(2026)
                .recordCount(10).matchStatus(FestivalSeriesMatchStatus.DETERMINISTIC).scope(SeriesScope.DISTRICT_LEVEL)
                .build());

        MultiYearAdminSummaryResponse response = queryService.getSummary();
        // series만 있고 record는 없는 상태 - summary 자체는 데이터 없음(unavailable)이지만 seriesStatus는 독립적으로 채워진다.
        assertTrue(response.seriesStatus().analyzed());
        assertEquals(2, response.seriesStatus().distinctSeriesCount());
        assertEquals(1, response.seriesStatus().singletonSeriesCount());
        assertEquals(1, response.seriesStatus().multiYearSeriesCount());
    }
}