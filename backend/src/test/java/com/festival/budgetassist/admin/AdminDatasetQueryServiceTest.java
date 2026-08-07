package com.festival.budgetassist.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.festival.budgetassist.dataset.FestivalExcelImporter;
import com.festival.budgetassist.dataset.TestWorkbookFactory;

/**
 * TestWorkbookFactory의 6행 fixture를 실제로 Import한 뒤, 관리자 조회 API가 계산하는
 * 통계가 손으로 미리 계산해둔 값과 정확히 일치하는지 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class AdminDatasetQueryServiceTest {

    @Autowired
    private FestivalExcelImporter importer;
    @Autowired
    private AdminDatasetQueryService queryService;

    @Test
    void noDataYet_allEndpointsReportUnavailable() {
        assertFalse(queryService.getOverview().hasAnyAttempt());
        assertFalse(queryService.getSummary().available());
        assertFalse(queryService.getDistributions().available());
        assertFalse(queryService.getIssues().available());
        assertFalse(queryService.getSample().available());
    }

    @Test
    void afterImport_overviewReflectsLatestSuccess() {
        importer.importFromBytes(TestWorkbookFactory.buildValidFixture(), "fixture.xlsx", 9101);

        AdminDatasetOverviewResponse overview = queryService.getOverview();
        assertTrue(overview.hasAnyAttempt());
        assertTrue(overview.hasLiveData());
        assertEquals("SUCCESS", overview.latestAttempt().status());
        assertEquals("SUCCESS", overview.latestSuccess().status());
        assertEquals(6, overview.latestSuccess().totalRows());
    }

    @Test
    void afterFailedImport_overviewShowsFailureButKeepsNoLiveData() {
        try {
            importer.importFromBytes(TestWorkbookFactory.buildWithWrongSheetName(), "bad.xlsx", 9102);
        } catch (RuntimeException expected) {
            // 구조 검증 실패는 의도된 것 - 여기서는 overview 반영만 확인한다.
        }

        AdminDatasetOverviewResponse overview = queryService.getOverview();
        assertTrue(overview.hasAnyAttempt());
        assertEquals("FAILED", overview.latestAttempt().status());
        assertFalse(overview.hasLiveData(), "성공 이력이 없으므로 live data도 없어야 함");
    }

    @Test
    void summary_matchesHandCalculatedFixtureStats() {
        importer.importFromBytes(TestWorkbookFactory.buildValidFixture(), "fixture.xlsx", 9103);

        AdminDatasetSummaryResponse summary = queryService.getSummary();
        assertTrue(summary.available());
        assertEquals(6, summary.totalRows());
        assertEquals(3, summary.validBudgetRows());
        assertEquals(1, summary.unconfirmedBudgetRows());
        assertEquals(1, summary.noResponseBudgetRows());
        assertEquals(1, summary.zeroBudgetRows());
        assertEquals(2, summary.missingDurationRows());
        assertEquals(6, summary.regionCount());
        assertEquals(5, summary.festivalTypeCount());
        assertEquals(6, summary.venueTypeCount());
        assertFalse(summary.referenceProfileCheck().applicable(), "9103년은 알려진 프로필이 없어야 함");
    }

    @Test
    void distributions_matchHandCalculatedFixtureStats() {
        importer.importFromBytes(TestWorkbookFactory.buildValidFixture(), "fixture.xlsx", 9104);

        AdminDatasetDistributionsResponse dist = queryService.getDistributions();
        assertTrue(dist.available());
        assertEquals(6, dist.regionCounts().size());
        dist.regionCounts().forEach(c -> assertEquals(1, c.count()));

        Map<String, Long> typeCounts = dist.festivalTypeCounts().stream()
                .collect(Collectors.toMap(CategoryCount::code, CategoryCount::count));
        assertEquals(2L, typeCounts.get("CULTURE_ART"), "연번1, 연번6이 문화예술");
        assertEquals(1L, typeCounts.get("NATURE_ECOLOGY"));

        BudgetStatistics stats = dist.budgetStatistics();
        assertEquals(3, stats.sampleCount());
        assertEquals(175_500_000d, stats.meanKrw(), 1);
        assertEquals(150_000_000d, stats.medianKrw(), 1);
        assertEquals(113_250_000d, stats.p25Krw(), 1);
        assertEquals(225_000_000d, stats.p75Krw(), 1);
        assertEquals(270_000_000d, stats.p90Krw(), 1);
        assertEquals(300_000_000L, stats.maxKrw());

        Map<String, Long> buckets = dist.durationBuckets().stream()
                .collect(Collectors.toMap(DurationBucket::label, DurationBucket::count));
        assertEquals(1L, buckets.get("2일"));
        assertEquals(2L, buckets.get("3일"));
        assertEquals(1L, buckets.get("4~7일"));
        assertEquals(2L, buckets.get("미기재"));
    }

    @Test
    void issues_containsOnlyTheReversedDateRow() {
        importer.importFromBytes(TestWorkbookFactory.buildValidFixture(), "fixture.xlsx", 9105);

        AdminDatasetIssuesResponse issues = queryService.getIssues();
        assertTrue(issues.available());
        assertEquals(1, issues.totalWarnings(), "연번6(종료<시작)만 경고가 발생해야 함");
        assertEquals(6, issues.issues().get(0).sourceRowNumber());
        assertTrue(issues.issues().get(0).message().contains("종료일이 시작일보다 빠름"));
    }

    @Test
    void sample_hasNoPersonalInformationAndListsColumnCatalog() {
        importer.importFromBytes(TestWorkbookFactory.buildValidFixture(), "fixture.xlsx", 9106);

        AdminDatasetSampleResponse sample = queryService.getSample();
        assertTrue(sample.available());
        assertEquals(6, sample.sampleRows().size());
        assertEquals("개인정보성 컬럼 저장 결과: 저장되지 않음", sample.personalInfoStatusLabel());
        assertTrue(sample.excludedColumns().stream().anyMatch(c -> c.contains("담당자 성명")));
        assertTrue(sample.excludedColumns().stream().anyMatch(c -> c.contains("연락처")));

        List<String> fieldNames = List.of(SampleRow.class.getRecordComponents()).stream()
                .map(c -> c.getName().toLowerCase())
                .toList();
        assertFalse(fieldNames.stream().anyMatch(n -> n.contains("contact") || n.contains("phone")
                || n.contains("manager") || n.contains("department") || n.contains("staff")));
    }
}