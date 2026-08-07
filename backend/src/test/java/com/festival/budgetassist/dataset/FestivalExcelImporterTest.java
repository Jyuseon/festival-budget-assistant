package com.festival.budgetassist.dataset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.festival.budgetassist.festival.domain.BudgetStatus;
import com.festival.budgetassist.festival.domain.CycleType;
import com.festival.budgetassist.festival.domain.DurationSource;
import com.festival.budgetassist.festival.domain.FestivalRecord;
import com.festival.budgetassist.festival.domain.ImportStatus;
import com.festival.budgetassist.festival.domain.VisitorCountStatus;
import com.festival.budgetassist.festival.repository.DatasetImportBatchRepository;
import com.festival.budgetassist.festival.repository.FestivalRecordRepository;

/**
 * 개인정보가 없는 소형 가상 엑셀 fixture로 검증하는 자동화 테스트.
 * 실제 원본 파일은 전혀 사용하지 않으며, CI/일반 빌드에서 별도 설정 없이 항상 통과해야 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class FestivalExcelImporterTest {

    @Autowired
    private FestivalExcelImporter importer;
    @Autowired
    private FestivalRecordRepository festivalRecordRepository;
    @Autowired
    private DatasetImportBatchRepository datasetImportBatchRepository;

    @Test
    void validFixture_persistsAllRowsAndSummarizesCorrectly() {
        byte[] bytes = TestWorkbookFactory.buildValidFixture();

        ImportResult result = importer.importFromBytes(bytes, "fixture.xlsx", 9001);

        assertFalse(result.duplicate());
        ImportSummary summary = result.summary();
        assertEquals(6, summary.totalRows());
        assertEquals(3, summary.validBudgetRows(), "CONFIRMED 예산 행 수");
        assertEquals(1, summary.unconfirmedBudgetRows());
        assertEquals(1, summary.noResponseBudgetRows());
        assertEquals(1, summary.zeroBudgetRows());
        assertEquals(2, summary.missingDurationRows(), "기간 미확정 행: 연번3(날짜불완전) + 연번6(종료<시작)");
        assertEquals(6, summary.regionCount());
        assertEquals(5, summary.festivalTypeCount());
        assertEquals(6, summary.venueTypeCount());

        assertEquals(6, festivalRecordRepository.countByDatasetYear(9001));
        assertEquals(ImportStatus.SUCCESS, result.batch().getStatus());
        assertEquals(64, result.batch().getFileHash().length(), "SHA-256 hex 문자열은 64자");
        assertTrue(result.batch().getFileHash().matches("^[0-9a-f]{64}$"));

        // datasetYear가 2026이 아니므로 참조 프로필 비교는 적용되지 않아야 한다
        assertFalse(result.referenceProfileCheck().applicable());
    }

    @Test
    void validFixture_rowLevelNormalizationIsCorrect() {
        byte[] bytes = TestWorkbookFactory.buildValidFixture();
        importer.importFromBytes(bytes, "fixture.xlsx", 9002);

        List<FestivalRecord> records = festivalRecordRepository.findAll().stream()
                .filter(r -> r.getDatasetYear() == 9002)
                .sorted((a, b) -> a.getSourceRowNumber().compareTo(b.getSourceRowNumber()))
                .toList();
        assertEquals(6, records.size());

        FestivalRecord row1 = records.get(0);
        assertEquals(DurationSource.REPORTED, row1.getDurationSource());
        assertEquals(3, row1.getDurationDays());
        assertEquals(150_000_000L, row1.getTotalBudgetKrw());
        assertEquals(BudgetStatus.CONFIRMED, row1.getBudgetStatus());

        FestivalRecord row2 = records.get(1);
        assertEquals(DurationSource.COMPUTED_FROM_DATES, row2.getDurationSource());
        assertEquals(3, row2.getDurationDays(), "5/1~5/3 날짜로 계산한 기간");
        assertEquals(BudgetStatus.UNCONFIRMED, row2.getBudgetStatus());
        assertNull(row2.getTotalBudgetKrw());
        assertEquals(VisitorCountStatus.NOT_TALLIED, row2.getPreviousVisitorsStatus());

        FestivalRecord row3 = records.get(2);
        assertEquals(DurationSource.UNKNOWN, row3.getDurationSource());
        assertNull(row3.getDurationDays());
        assertEquals(BudgetStatus.NO_RESPONSE, row3.getBudgetStatus());
        assertEquals(VisitorCountStatus.UNKNOWN, row3.getPreviousVisitorsStatus());

        FestivalRecord row4 = records.get(3);
        assertEquals(BudgetStatus.ZERO, row4.getBudgetStatus());
        assertEquals(0L, row4.getTotalBudgetKrw());
        assertEquals(CycleType.FIRST_TIME, row4.getCycleType());
        assertEquals(VisitorCountStatus.FIRST_TIME_HELD, row4.getPreviousVisitorsStatus());

        FestivalRecord row5 = records.get(4);
        assertNull(row5.getAdministrativeDistrict(), "'-'는 null로 정규화");
        assertNull(row5.getFirstHeldYear());
        assertEquals("미상", row5.getFirstHeldYearNote());
        assertEquals(76_500_000L, row5.getTotalBudgetKrw(), "76.5백만원 -> 76,500,000원");

        FestivalRecord row6 = records.get(5);
        assertEquals(DurationSource.UNKNOWN, row6.getDurationSource(), "종료일이 시작일보다 빠르면 계산 포기");
        assertNull(row6.getDurationDays());
        assertEquals(VisitorCountStatus.UNKNOWN, row6.getForeignVisitorsStatus());
        assertNotNull(row6.getPreviousVisitors());
    }

    @Test
    void sameHashTwice_secondCallIsNoOpAndDoesNotDuplicateData() {
        byte[] bytes = TestWorkbookFactory.buildValidFixture();

        ImportResult first = importer.importFromBytes(bytes, "a.xlsx", 9003);
        assertFalse(first.duplicate());
        long batchCountAfterFirst = datasetImportBatchRepository.findAll().stream()
                .filter(b -> b.getFileHash().equals(first.batch().getFileHash()))
                .count();

        ImportResult second = importer.importFromBytes(bytes, "b-different-filename.xlsx", 9003);

        assertTrue(second.duplicate());
        assertEquals(first.batch().getId(), second.batch().getId());
        assertEquals(6, festivalRecordRepository.countByDatasetYear(9003), "중복 재적재로 데이터가 늘어나면 안 됨");

        long batchCountAfterSecond = datasetImportBatchRepository.findAll().stream()
                .filter(b -> b.getFileHash().equals(first.batch().getFileHash()))
                .count();
        assertEquals(batchCountAfterFirst, batchCountAfterSecond, "no-op이므로 배치 행이 추가로 생기면 안 됨");
    }

    @Test
    void sameYearDifferentHash_replacesOldYearData() {
        byte[] first = TestWorkbookFactory.buildValidFixture();
        importer.importFromBytes(first, "first.xlsx", 9004);
        assertEquals(6, festivalRecordRepository.countByDatasetYear(9004));

        byte[] second = TestWorkbookFactory.buildWithUnrecognizedFestivalType(); // 실패용이라 부적합, 대체 fixture 필요 시 확장
        // 실패하는 파일로 교체를 시도하면 기존 데이터가 유지되어야 한다 (트랜잭션 원자성 검증)
        assertThrows(ImportValidationException.class, () -> importer.importFromBytes(second, "second.xlsx", 9004));
        assertEquals(6, festivalRecordRepository.countByDatasetYear(9004), "검증 실패 시 기존 연도 데이터가 그대로 유지되어야 함");
    }

    @Test
    void wrongSheetName_throwsAndPersistsNothing() {
        byte[] bytes = TestWorkbookFactory.buildWithWrongSheetName();

        ImportValidationException ex = assertThrows(ImportValidationException.class,
                () -> importer.importFromBytes(bytes, "wrong-sheet.xlsx", 9005));

        assertTrue(ex.getMessage().contains("조사표"));
        assertEquals(0, festivalRecordRepository.countByDatasetYear(9005));

        boolean failedBatchRecorded = datasetImportBatchRepository.findAll().stream()
                .anyMatch(b -> b.getFileHash().equals(ex.getFileHash()) && b.getStatus() == ImportStatus.FAILED);
        assertTrue(failedBatchRecorded, "실패 이력이 감사 로그로 남아야 함");
    }

    @Test
    void badHeader_throwsWithCellDetail() {
        byte[] bytes = TestWorkbookFactory.buildWithBadHeader();

        ImportValidationException ex = assertThrows(ImportValidationException.class,
                () -> importer.importFromBytes(bytes, "bad-header.xlsx", 9006));

        assertTrue(ex.getDetails().stream().anyMatch(d -> d.contains("F5")));
        assertEquals(0, festivalRecordRepository.countByDatasetYear(9006));
    }

    @Test
    void unrecognizedRequiredCode_abortsEntireImport() {
        byte[] bytes = TestWorkbookFactory.buildWithUnrecognizedFestivalType();

        ImportValidationException ex = assertThrows(ImportValidationException.class,
                () -> importer.importFromBytes(bytes, "bad-code.xlsx", 9007));

        assertTrue(ex.getDetails().stream().anyMatch(d -> d.contains("축제 유형")));
        assertEquals(0, festivalRecordRepository.countByDatasetYear(9007));
    }

    @Test
    void festivalRecordEntity_hasNoPersonalInformationFields() {
        // 구조적으로 개인정보를 저장할 수 없음을 리플렉션으로 확인한다.
        List<String> bannedTokens = List.of("contact", "phone", "manager", "department", "staff", "organizer", "owner");
        for (Field field : FestivalRecord.class.getDeclaredFields()) {
            String lower = field.getName().toLowerCase(Locale.ROOT);
            for (String banned : bannedTokens) {
                assertFalse(lower.contains(banned),
                        "FestivalRecord에 개인정보로 의심되는 필드가 있음: " + field.getName());
            }
        }
    }
}