package com.festival.budgetassist.dataset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.festival.budgetassist.festival.domain.FestivalRecord;
import com.festival.budgetassist.festival.domain.VisitorCountStatus;
import com.festival.budgetassist.festival.domain.VisitorMeasurementMethod;
import com.festival.budgetassist.festival.repository.FestivalRecordRepository;

/**
 * 실제 원본 2026년 지역축제 개최 계획 엑셀로만 검증하는 로컬 인수 테스트.
 *
 * <p>원본 파일은 개인정보가 포함되어 있어 저장소에 없다. {@code FESTIVAL_EXCEL_PATH}
 * 환경변수가 설정되어 있고 파일이 실제로 존재할 때만 실행되며, 그렇지 않으면
 * {@link Assumptions}로 조용히 건너뛴다 — CI나 일반 빌드가 이 파일의 부재로 실패하지 않는다.</p>
 *
 * <p>기대값은 openpyxl로 원본 파일을 직접 열어 산출한 실측치이며(Phase 0/2 분석 결과),
 * 가이드 문서의 4장 통계와 전부 일치함을 별도로 확인했다.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class FestivalExcelImporterLocalAcceptanceTest {

    @Autowired
    private FestivalExcelImporter importer;
    @Autowired
    private FestivalRecordRepository festivalRecordRepository;

    @Test
    void realFile_matchesKnown2026Statistics() throws IOException {
        String pathValue = System.getenv("FESTIVAL_EXCEL_PATH");
        Assumptions.assumeTrue(pathValue != null && !pathValue.isBlank(),
                "FESTIVAL_EXCEL_PATH 환경변수가 없어 로컬 인수 테스트를 건너뜁니다.");

        Path path = Path.of(pathValue);
        Assumptions.assumeTrue(Files.isRegularFile(path),
                "FESTIVAL_EXCEL_PATH 파일을 찾을 수 없어 건너뜁니다: " + path.toAbsolutePath());

        byte[] bytes = Files.readAllBytes(path);
        int datasetYear = Known2026DatasetProfile.DATASET_YEAR;

        ImportResult result = importer.importFromBytes(bytes, path.getFileName().toString(), datasetYear);

        assertTrue(!result.duplicate(), "테스트 트랜잭션은 매번 롤백되므로 첫 실행이어야 함");
        ImportSummary summary = result.summary();

        assertEquals(Known2026DatasetProfile.TOTAL_ROWS, summary.totalRows());
        assertEquals(Known2026DatasetProfile.VALID_BUDGET_ROWS, summary.validBudgetRows());
        assertEquals(22, summary.unconfirmedBudgetRows());
        assertEquals(5, summary.noResponseBudgetRows());
        assertEquals(1, summary.zeroBudgetRows());
        // R열(총 일수)이 비어 있는 행은 131건이지만, 그중 1건은 시작~종료 날짜 성분이 모두 온전해
        // DataNormalizationService가 날짜 계산으로 기간을 채운다(Phase 0 사전 조사로 확인한 사실).
        // 따라서 최종적으로 durationDays가 null로 남는 행은 130건이다.
        assertEquals(130, summary.missingDurationRows());
        assertEquals(Known2026DatasetProfile.REGION_COUNT, summary.regionCount());
        assertEquals(Known2026DatasetProfile.FESTIVAL_TYPE_COUNT, summary.festivalTypeCount());
        assertEquals(Known2026DatasetProfile.VENUE_TYPE_COUNT, summary.venueTypeCount());

        assertTrue(result.referenceProfileCheck().applicable());
        assertTrue(result.referenceProfileCheck().matches(), () -> "불일치: " + result.referenceProfileCheck().mismatches());

        assertEquals(Known2026DatasetProfile.TOTAL_ROWS, festivalRecordRepository.countByDatasetYear(datasetYear));

        List<FestivalRecord> records = festivalRecordRepository.findAll().stream()
                .filter(r -> r.getDatasetYear() == datasetYear)
                .toList();

        // Phase 0에서 openpyxl로 직접 산출한 실측치와 대조 (AA/AB/AC 방문객 특수값 교차 검증)
        assertEquals(43, count(records, r -> r.getPreviousVisitorsStatus() == VisitorCountStatus.NOT_TALLIED));
        assertEquals(36, count(records, r -> r.getPreviousVisitorsStatus() == VisitorCountStatus.FIRST_TIME_HELD));
        assertEquals(4, count(records, r -> r.getPreviousVisitorsStatus() == VisitorCountStatus.RECENTLY_NOT_HELD));
        assertEquals(22, count(records, r -> r.getDomesticVisitorsStatus() == VisitorCountStatus.UNKNOWN));
        assertEquals(826, count(records, r -> r.getForeignVisitorsStatus() == VisitorCountStatus.UNKNOWN));

        assertEquals(453, count(records, r -> r.getVisitorMeasurementMethod() == VisitorMeasurementMethod.MEASURED));
        assertEquals(232, count(records, r -> r.getVisitorMeasurementMethod() == VisitorMeasurementMethod.ESTIMATED));
        assertEquals(43, count(records, r -> r.getVisitorMeasurementMethod() == VisitorMeasurementMethod.NOT_TALLIED));
        assertEquals(40, count(records, r -> r.getVisitorMeasurementMethod() == VisitorMeasurementMethod.OTHER));
        assertEquals(498, count(records, r -> r.getVisitorMeasurementMethod() == VisitorMeasurementMethod.NO_RESPONSE));

        assertEquals(126, count(records, r -> r.getAdministrativeDistrict() == null));
        assertEquals(3, count(records, r -> "미상".equals(r.getFirstHeldYearNote())));

        // AI~AN 원본 컬럼이 애초에 엔티티/DTO에 없으므로 개인정보가 저장될 물리적 경로 자체가 없다.
        // (FestivalExcelImporterTest#festivalRecordEntity_hasNoPersonalInformationFields 참고)
    }

    private static long count(List<FestivalRecord> records, java.util.function.Predicate<FestivalRecord> predicate) {
        return records.stream().filter(predicate).count();
    }
}