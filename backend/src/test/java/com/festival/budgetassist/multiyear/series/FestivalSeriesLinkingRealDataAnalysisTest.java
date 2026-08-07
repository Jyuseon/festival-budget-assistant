package com.festival.budgetassist.multiyear.series;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.festival.budgetassist.multiyear.csv.MultiYearCsvImportService;
import com.festival.budgetassist.multiyear.domain.MatchMethod;

/**
 * 실제 sanitized CSV(2017~2026, 10,198행)를 Import한 뒤 festivalSeries 연결까지 전부 실행해
 * 진짜 통계를 산출하는 로컬 전용 분석 테스트.
 *
 * <p>{@code FESTIVAL_MULTIYEAR_CSV_PATH} 환경변수가 없으면 건너뛴다(CI/일반 빌드는 영향 없음) -
 * {@code MultiYearCsvYearProfileAcceptanceTest}와 동일한 패턴이다. 리포트는
 * {@link FestivalSeriesLinkingReportFormatter}로 콘솔에 출력하고,
 * {@code multiyear-series-linking-report.txt}로도 저장해 사람이 눈으로 검토할 수 있게 한다.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class FestivalSeriesLinkingRealDataAnalysisTest {

    private static final Logger log = LoggerFactory.getLogger(FestivalSeriesLinkingRealDataAnalysisTest.class);

    @Autowired
    private MultiYearCsvImportService importService;
    @Autowired
    private FestivalSeriesLinkingService linkingService;

    @Test
    void realCsv_linkSeriesAndPrintFullReport() throws IOException {
        String csvPathValue = System.getenv("FESTIVAL_MULTIYEAR_CSV_PATH");
        Assumptions.assumeTrue(csvPathValue != null && !csvPathValue.isBlank(),
                "FESTIVAL_MULTIYEAR_CSV_PATH 환경변수가 없어 로컬 분석 테스트를 건너뜁니다.");

        Path csvPath = Path.of(csvPathValue);
        Assumptions.assumeTrue(Files.isRegularFile(csvPath),
                "FESTIVAL_MULTIYEAR_CSV_PATH 파일을 찾을 수 없어 건너뜁니다: " + csvPath.toAbsolutePath());

        byte[] bytes = Files.readAllBytes(csvPath);
        importService.importFromBytes(bytes, csvPath.getFileName().toString());

        FestivalSeriesLinkingReport report = linkingService.linkAll();
        List<String> lines = FestivalSeriesLinkingReportFormatter.format(report);
        lines.forEach(log::info);

        try {
            Path out = Path.of("multiyear-series-linking-report.txt");
            Files.write(out, lines, StandardCharsets.UTF_8);
            log.info("리포트 파일 저장 완료: {}", out.toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        assertEquals(10198, report.totalRecords());
        assertTrue(report.distinctSeriesCount() > 0 && report.distinctSeriesCount() <= report.totalRecords());
        long sumByMethod = report.matchMethodCounts().values().stream().mapToLong(Long::longValue).sum();
        assertEquals(report.totalRecords(), sumByMethod, "matchMethod별 합계는 전체 행 수와 같아야 함");
        long fuzzyApplied = report.matchMethodCounts().getOrDefault(MatchMethod.FUZZY, 0L);
        assertTrue(fuzzyApplied >= 0);
    }
}