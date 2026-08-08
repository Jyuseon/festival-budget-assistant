package com.festival.budgetassist.multiyear.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.festival.budgetassist.multiyear.csv.MultiYearCsvImportService;

/**
 * 실제 sanitized CSV(2017~2026, 10,198행)로 festivalSeries 중복 보정(S0/S1/S2) 비교를 전부
 * 실행하는 로컬 전용 분석 테스트. {@code FestivalSeriesLinkingRealDataAnalysisTest}/
 * {@code MultiYearBacktestRealDataAnalysisTest}와 동일한 패턴이다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class MultiYearSeriesCorrectionRealDataAnalysisTest {

    private static final Logger log = LoggerFactory.getLogger(MultiYearSeriesCorrectionRealDataAnalysisTest.class);

    @Autowired
    private MultiYearCsvImportService importService;
    @Autowired
    private MultiYearSeriesCorrectionBacktestService correctionService;
    @Autowired
    private MultiYearSeriesCorrectionMetricsCalculator metricsCalculator;

    @Test
    void realCsv_runSeriesCorrectionComparisonAndPrintFullReport() throws IOException {
        String csvPathValue = System.getenv("FESTIVAL_MULTIYEAR_CSV_PATH");
        Assumptions.assumeTrue(csvPathValue != null && !csvPathValue.isBlank(),
                "FESTIVAL_MULTIYEAR_CSV_PATH 환경변수가 없어 로컬 분석 테스트를 건너뜁니다.");

        Path csvPath = Path.of(csvPathValue);
        Assumptions.assumeTrue(Files.isRegularFile(csvPath),
                "FESTIVAL_MULTIYEAR_CSV_PATH 파일을 찾을 수 없어 건너뜁니다: " + csvPath.toAbsolutePath());

        byte[] bytes = Files.readAllBytes(csvPath);
        importService.importFromBytes(bytes, csvPath.getFileName().toString());

        Map<MultiYearSeriesCorrectionMode, List<MultiYearFoldCorrectionResult>> byMode = correctionService.runAllModesAllFolds();
        List<String> reportLines = MultiYearSeriesCorrectionReportFormatter.format(byMode, metricsCalculator);
        reportLines.forEach(log::info);

        try {
            Path out = Path.of("multiyear-series-correction-report.txt");
            Files.write(out, reportLines, StandardCharsets.UTF_8);
            log.info("리포트 파일 저장 완료: {}", out.toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        assertEquals(3, byMode.size(), "S0/S1/S2 세 가지 mode가 전부 실행돼야 함");
        for (MultiYearSeriesCorrectionMode mode : MultiYearSeriesCorrectionMode.values()) {
            assertEquals(3, byMode.get(mode).size(), "각 mode는 Primary 2025/2026 + Secondary 2024 3개 fold");
        }
    }
}