package com.festival.budgetassist.multiyear.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.festival.budgetassist.festival.domain.Region;
import com.festival.budgetassist.festival.domain.VenueType;
import com.festival.budgetassist.multiyear.domain.BudgetQualityFlag;
import com.festival.budgetassist.multiyear.domain.CsvDurationSource;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;
import com.festival.budgetassist.multiyear.repository.MultiYearFestivalRecordRepository;
import com.festival.budgetassist.multiyear.repository.MultiYearImportBatchRepository;

/**
 * 개인정보가 없는 소형 가상 CSV fixture로 검증하는 자동화 테스트. 실제 sanitized CSV(1만여 행)는
 * 전혀 필요하지 않으며 CI/일반 빌드에서 항상 통과해야 한다.
 *
 * <p>실제 전체 CSV로 year_profiles.json과 대조하는 검증은
 * {@link MultiYearCsvYearProfileAcceptanceTest}(로컬 전용, 조건부 skip)에서 한다.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class MultiYearCsvImportServiceTest {

    private static final String HEADER = "dataset_year,source_sheet,source_row,source_sha256,region_raw,region,"
            + "district_raw,district,festival_name,festival_type_raw,festival_type,venue_raw,venue_type_raw,"
            + "venue_type,period_raw,duration_days,duration_source,duration_note_raw,cycle,event_mode,"
            + "event_status,covid_affected,first_held_year,budget_total_raw,budget_total_million,"
            + "budget_national_million,budget_local_million,budget_other_million,budget_quality_flag,"
            + "budget_quality_note,visitor_total_persons";

    // 연번5(2017, EXPLICIT_TEXT 기간, venue_type 공란, 원문에 콤마 포함)
    private static final String ROW_A = "2017,서울,5,hashA,서울특별시,서울,시자체,시자체,서울드럼페스티벌,문화예술,CULTURE_ART,"
            + "\"광장(서울광장, 청계광장)\",,,5.27~5.28,2,EXPLICIT_TEXT,(2일간),매년,,,False,1999,400,400,400,0,400,VALID,,40000";

    // 연번6(2017, 기간/예산 미확정 - UNPARSED/MISSING_OR_NONPOSITIVE, venue_type 공란)
    private static final String ROW_B = "2017,서울,6,hashA,서울특별시,서울,시자체,시자체,서울거리예술축제,지역특산물,LOCAL_SPECIALTY,"
            + "어딘가,,,10.4~10.8,,UNPARSED,,매년,,,False,2003,,,,,,MISSING_OR_NONPOSITIVE,예산 미기재,750000";

    // 2020, covid_affected=True
    private static final String ROW_C = "2020,전북,10,hashC,전북,전북,군산시,군산시,군산불빛축제,문화예술,CULTURE_ART,"
            + "월명공원,,,10.1~10.3,3,SOURCE_TOTAL_DAYS,,비정기,오프라인,취소,True,2010,500,500,500,0,500,VALID,,0";

    // 2024, budget_quality_flag=UNIT_SCALE_SUSPECT (자동 보정 금지 - 원본 그대로 저장돼야 함)
    private static final String ROW_D = "2024,세부현황,20,hashD,경기,경기,수원시,수원시,수원화성문화제,전통역사,TRADITION_HISTORY,"
            + "수원화성,,,10.4~10.6,3,SOURCE_TOTAL_DAYS,,매년,대면,,False,1964,5000000,5000000,5000000,0,5000000,"
            + "UNIT_SCALE_SUSPECT,단위 의심,100000";

    // 2025, venue_type 존재(마을형)
    private static final String ROW_E = "2025,조사표,8,hashE,강원,강원,춘천시,춘천시,춘천 감자페스타,지역특산물,LOCAL_SPECIALTY,"
            + "신북장터,마을형,마을형,2025/6//2025/6/,2,SOURCE_TOTAL_DAYS,미정,매년,대면,,False,2024,100,100,100,0,100,"
            + "VALID,,20000";

    // 2018, region 컬럼이 "세종특별자치시"(접미사 포함) - Region enum 강제매핑이 아니라 fallback으로만 인식돼야 함
    private static final String ROW_F = "2018,08_세종특별자치시,50,hashF,08_세종특별자치시,세종특별자치시,본청,본청,세종축제,"
            + "주민화합,COMMUNITY,세종호수공원,,,9.1~9.3,3,SOURCE_TOTAL_DAYS,,매년,,,False,2012,300,300,300,0,300,VALID,,15000";

    // 2019, festival_type이 복합유형(파이프 연결) - 단일 enum으로 강제하지 않고 문자열 그대로 보존
    private static final String ROW_G = "2019,경북,77,hashG,경북,경북,경주시,경주시,경주문화재야행,문화예술,"
            + "CULTURE_ART|TRADITION_HISTORY,경주읍성,,,9.1~9.2,,UNPARSED,,매년,,,False,2015,200,200,200,0,200,VALID,,5000";

    @Autowired
    private MultiYearCsvImportService importService;
    @Autowired
    private MultiYearFestivalRecordRepository recordRepository;
    @Autowired
    private MultiYearImportBatchRepository batchRepository;

    private static byte[] csv(String... rows) {
        String text = HEADER + "\n" + String.join("\n", rows) + "\n";
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void validFixture_persistsAllRowsGroupedByYear() {
        byte[] bytes = csv(ROW_A, ROW_B, ROW_C, ROW_D, ROW_E, ROW_F, ROW_G);

        MultiYearImportResult result = importService.importFromBytes(bytes, "fixture.csv");

        assertFalse(result.duplicate());
        MultiYearImportSummary summary = result.summary();
        assertEquals(7, summary.totalRows());
        assertEquals(2, summary.rowCountByYear().get(2017));
        assertEquals(1, summary.rowCountByYear().get(2018));
        assertEquals(1, summary.rowCountByYear().get(2019));
        assertEquals(1, summary.rowCountByYear().get(2020));
        assertEquals(1, summary.rowCountByYear().get(2024));
        assertEquals(1, summary.rowCountByYear().get(2025));
        assertEquals(1, summary.unitScaleSuspectRows(), "UNIT_SCALE_SUSPECT는 알고리즘 후보 제외 대상으로 집계돼야 함");
        assertEquals(1, summary.missingOrNonpositiveBudgetRows());
        assertEquals(1, summary.covidAffectedRows());
        assertEquals(2, summary.missingDurationRows(), "ROW_B, ROW_G가 duration_days 미확정");

        assertEquals(7, recordRepository.count());
    }

    @Test
    void neverFabricatesMissingVenueTypeOrDuration() {
        importService.importFromBytes(csv(ROW_A, ROW_B), "fixture.csv");

        List<MultiYearFestivalRecord> rows2017 = recordRepository.findByDatasetYear(2017);
        MultiYearFestivalRecord rowA = rows2017.stream().filter(r -> r.getSourceRowNumber() == 5).findFirst().orElseThrow();
        MultiYearFestivalRecord rowB = rows2017.stream().filter(r -> r.getSourceRowNumber() == 6).findFirst().orElseThrow();

        assertNull(rowA.getVenueType(), "venue_type 원본이 공란이면 OTHER로 강제 매핑하지 않고 null이어야 함");
        assertNull(rowB.getVenueType());

        assertEquals(2, rowA.getDurationDays());
        assertEquals(CsvDurationSource.EXPLICIT_TEXT, rowA.getDurationSource());
        assertNull(rowB.getDurationDays(), "duration_days 공란이면 0이나 임의값이 아니라 null이어야 함");
        assertEquals(CsvDurationSource.UNPARSED, rowB.getDurationSource());

        assertEquals(BigDecimal.valueOf(400), rowA.getBudgetTotalMillion());
        assertNull(rowB.getBudgetTotalMillion(), "예산 미기재 행은 null이어야 함");
        assertEquals(BudgetQualityFlag.MISSING_OR_NONPOSITIVE, rowB.getBudgetQualityFlag());
    }

    @Test
    void unitScaleSuspectBudgetIsStoredVerbatimNotAutoCorrected() {
        importService.importFromBytes(csv(ROW_D), "fixture.csv");

        MultiYearFestivalRecord row = recordRepository.findByDatasetYear(2024).get(0);
        assertEquals(BudgetQualityFlag.UNIT_SCALE_SUSPECT, row.getBudgetQualityFlag());
        assertEquals(0, BigDecimal.valueOf(5_000_000).compareTo(row.getBudgetTotalMillion()),
                "UNIT_SCALE_SUSPECT 값을 자동으로 /1000 하면 안 됨 - 원본 그대로 저장");

        assertEquals(1, recordRepository.findByDatasetYearAndBudgetQualityFlagNot(2024, BudgetQualityFlag.VALID).size());
        assertTrue(recordRepository.findByDatasetYearAndBudgetQualityFlagNot(2024, BudgetQualityFlag.UNIT_SCALE_SUSPECT).isEmpty(),
                "UNIT_SCALE_SUSPECT를 제외하면 알고리즘 후보 표본은 0건이어야 함");
    }

    @Test
    void covidAffectedFlagIsPreserved() {
        importService.importFromBytes(csv(ROW_C, ROW_E), "fixture.csv");

        MultiYearFestivalRecord covidRow = recordRepository.findByDatasetYear(2020).get(0);
        MultiYearFestivalRecord normalRow = recordRepository.findByDatasetYear(2025).get(0);
        assertTrue(covidRow.isCovidAffected());
        assertFalse(normalRow.isCovidAffected());
    }

    @Test
    void venueTypeIsMappedWhenPresent() {
        importService.importFromBytes(csv(ROW_E), "fixture.csv");

        MultiYearFestivalRecord row = recordRepository.findByDatasetYear(2025).get(0);
        assertEquals(VenueType.VILLAGE, row.getVenueType());
    }

    @Test
    void compositeAndLegacyFestivalTypesAreKeptAsRawStringNotForcedIntoEnum() {
        importService.importFromBytes(csv(ROW_G), "fixture.csv");

        MultiYearFestivalRecord row = recordRepository.findByDatasetYear(2019).get(0);
        assertEquals("CULTURE_ART|TRADITION_HISTORY", row.getFestivalType());
    }

    @Test
    void regionFallbackHandlesSuffixedLegacyText() {
        importService.importFromBytes(csv(ROW_F), "fixture.csv");

        MultiYearFestivalRecord row = recordRepository.findByDatasetYear(2018).get(0);
        assertEquals("세종특별자치시", row.getRegionText());
        assertEquals(Region.SEJONG, row.getRegionCode(), "접미사가 붙은 표기도 강제 추론이 아닌 fallback 규칙으로 인식돼야 함");
    }

    @Test
    void sameHashTwice_secondCallIsNoOp() {
        byte[] bytes = csv(ROW_A, ROW_C);

        MultiYearImportResult first = importService.importFromBytes(bytes, "a.csv");
        assertFalse(first.duplicate());

        MultiYearImportResult second = importService.importFromBytes(bytes, "b-different-name.csv");
        assertTrue(second.duplicate());
        assertEquals(first.batch().getId(), second.batch().getId());
        assertEquals(2, recordRepository.count(), "중복 재적재로 데이터가 늘어나면 안 됨");
    }

    @Test
    void reimportReplacesOnlyYearsPresentInNewCsv() {
        importService.importFromBytes(csv(ROW_A, ROW_C), "first.csv");
        assertEquals(1, recordRepository.countByDatasetYear(2017));
        assertEquals(1, recordRepository.countByDatasetYear(2020));

        // 새 CSV(해시가 다름)에는 2017년만 담겨 있음 - 2020년 기존 데이터는 그대로 유지돼야 한다.
        importService.importFromBytes(csv(ROW_B), "second.csv");

        assertEquals(1, recordRepository.countByDatasetYear(2017), "2017년은 새 CSV로 교체됨");
        assertEquals(6, recordRepository.findByDatasetYear(2017).get(0).getSourceRowNumber());
        assertEquals(1, recordRepository.countByDatasetYear(2020), "2020년은 이번 CSV에 없으므로 유지돼야 함");
    }

    @Test
    void missingRequiredColumn_throwsAndPersistsNothing() {
        String brokenHeader = HEADER.replace("budget_quality_flag,", "");
        byte[] bytes = (brokenHeader + "\n" + ROW_A + "\n").getBytes(StandardCharsets.UTF_8);

        MultiYearCsvImportException ex = assertThrows(MultiYearCsvImportException.class,
                () -> importService.importFromBytes(bytes, "broken.csv"));

        assertTrue(ex.getMessage().contains("budget_quality_flag"));
        assertEquals(0, recordRepository.count());
        assertTrue(batchRepository.findAll().stream().anyMatch(b -> b.getFileHash().equals(ex.getFileHash())));
    }

    @Test
    void unrecognizedBudgetQualityFlag_abortsEntireImportAndKeepsExistingYearData() {
        importService.importFromBytes(csv(ROW_A), "first.csv");
        assertEquals(1, recordRepository.countByDatasetYear(2017));

        String badRow = ROW_C.replace("VALID", "TOTALLY_UNKNOWN_FLAG");
        byte[] bytes = csv(badRow);

        assertThrows(MultiYearCsvImportException.class, () -> importService.importFromBytes(bytes, "bad.csv"));

        assertEquals(1, recordRepository.countByDatasetYear(2017), "검증 실패 시 기존 연도 데이터가 그대로 유지돼야 함");
        assertEquals(0, recordRepository.countByDatasetYear(2020));
    }
}