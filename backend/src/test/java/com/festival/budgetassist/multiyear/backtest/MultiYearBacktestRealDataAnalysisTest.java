package com.festival.budgetassist.multiyear.backtest;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

/**
 * 실제 sanitized CSV(2017~2026, 10,198행)를 Import한 뒤 leakage-safe baseline backtest를 전부
 * 실행해 진짜 통계를 산출하는 로컬 전용 분석 테스트. {@code FestivalSeriesLinkingRealDataAnalysisTest}와
 * 완전히 동일한 패턴이다 - {@code FESTIVAL_MULTIYEAR_CSV_PATH} 환경변수가 없으면 건너뛴다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class MultiYearBacktestRealDataAnalysisTest {

    private static final Logger log = LoggerFactory.getLogger(MultiYearBacktestRealDataAnalysisTest.class);

    @Autowired
    private MultiYearCsvImportService importService;
    @Autowired
    private MultiYearBacktestService backtestService;
    @Autowired
    private MultiYearBacktestMetricsCalculator metricsCalculator;
    @Autowired
    private MultiYearDataQualityV3Calculator v3Calculator;

    @Test
    void realCsv_runBaselineBacktestAndPrintFullReport() throws IOException {
        String csvPathValue = System.getenv("FESTIVAL_MULTIYEAR_CSV_PATH");
        Assumptions.assumeTrue(csvPathValue != null && !csvPathValue.isBlank(),
                "FESTIVAL_MULTIYEAR_CSV_PATH 환경변수가 없어 로컬 분석 테스트를 건너뜁니다.");

        Path csvPath = Path.of(csvPathValue);
        Assumptions.assumeTrue(Files.isRegularFile(csvPath),
                "FESTIVAL_MULTIYEAR_CSV_PATH 파일을 찾을 수 없어 건너뜁니다: " + csvPath.toAbsolutePath());

        byte[] bytes = Files.readAllBytes(csvPath);
        importService.importFromBytes(bytes, csvPath.getFileName().toString());

        List<MultiYearFoldResult> foldResults = backtestService.runAllFolds();
        List<String> reportLines = MultiYearBacktestReportFormatter.format(foldResults, metricsCalculator, v3Calculator);
        reportLines.forEach(log::info);

        try {
            Path out = Path.of("multiyear-backtest-baseline-report.txt");
            Files.write(out, reportLines, StandardCharsets.UTF_8);
            log.info("리포트 파일 저장 완료: {}", out.toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        writePredictionsCsv(foldResults);

        assertTrue(foldResults.size() == 3, "fold는 Primary 2025/2026 + Secondary 2024 총 3개여야 함");
        assertTrue(foldResults.stream().anyMatch(f -> !f.predictions().isEmpty()), "적어도 하나의 fold는 예측 결과가 있어야 함");
    }

    private void writePredictionsCsv(List<MultiYearFoldResult> foldResults) {
        List<String> csvLines = new ArrayList<>();
        csvLines.add(String.join(",", "targetYear", "festivalName", "region", "district", "festivalType", "venueType",
                "durationDays", "actualBudget", "estimatedBudget", "weightedAverage", "recommendedBudget", "p25", "p75",
                "sampleCount", "distinctSeriesCount", "fallbackLevel", "dataQualityV3", "absoluteError",
                "absolutePercentageError", "absoluteLogError"));

        for (MultiYearFoldResult fold : foldResults) {
            for (MultiYearBacktestPrediction p : fold.predictions()) {
                csvLines.add(String.join(",",
                        String.valueOf(p.targetYear()), csvEscape(p.festivalName()), csvEscape(p.region()),
                        csvEscape(p.district()), csvEscape(p.festivalType()), csvEscape(p.venueType()),
                        p.durationDays() == null ? "" : String.valueOf(p.durationDays()),
                        String.valueOf(p.actualBudget()), String.valueOf(p.estimatedBudget()),
                        String.valueOf(p.weightedAverageBudget()), String.valueOf(p.recommendedBudget()),
                        String.valueOf(p.p25()), String.valueOf(p.p75()), String.valueOf(p.sampleCount()),
                        String.valueOf(p.distinctSeriesCount()), p.fallbackLevel(), String.valueOf(p.dataQualityV3()),
                        String.valueOf(p.absoluteError()),
                        Double.isFinite(p.absolutePercentageError()) ? String.valueOf(p.absolutePercentageError()) : "",
                        Double.isFinite(p.absoluteLogError()) ? String.valueOf(p.absoluteLogError()) : ""
                ));
            }
        }

        try {
            Path path = Path.of("multiyear-backtest-baseline-predictions.csv");
            Files.write(path, csvLines, StandardCharsets.UTF_8);
            log.info("예측 단위 CSV 저장 완료: {} ({}행)", path.toAbsolutePath(), csvLines.size() - 1);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}