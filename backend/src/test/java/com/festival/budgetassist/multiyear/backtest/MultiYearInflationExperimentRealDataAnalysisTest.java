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
 * 실제 sanitized CSV(2017~2026, 10,198행)로 inflation x series-correction 2x2(A/B/C/D) 비교를
 * 전부 실행하는 로컬 전용 분석 테스트. 다른 real-data 분석 테스트와 동일한 패턴이다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class MultiYearInflationExperimentRealDataAnalysisTest {

    private static final Logger log = LoggerFactory.getLogger(MultiYearInflationExperimentRealDataAnalysisTest.class);

    @Autowired
    private MultiYearCsvImportService importService;
    @Autowired
    private MultiYearInflationExperimentService experimentService;
    @Autowired
    private MultiYearSeriesCorrectionMetricsCalculator metricsCalculator;
    @Autowired
    private AnnualPriceIndexProvider priceIndexProvider;

    @Test
    void realCsv_runInflationExperimentAndPrintFullReport() throws IOException {
        String csvPathValue = System.getenv("FESTIVAL_MULTIYEAR_CSV_PATH");
        Assumptions.assumeTrue(csvPathValue != null && !csvPathValue.isBlank(),
                "FESTIVAL_MULTIYEAR_CSV_PATH 환경변수가 없어 로컬 분석 테스트를 건너뜁니다.");

        Path csvPath = Path.of(csvPathValue);
        Assumptions.assumeTrue(Files.isRegularFile(csvPath),
                "FESTIVAL_MULTIYEAR_CSV_PATH 파일을 찾을 수 없어 건너뜁니다: " + csvPath.toAbsolutePath());

        byte[] bytes = Files.readAllBytes(csvPath);
        importService.importFromBytes(bytes, csvPath.getFileName().toString());

        Map<MultiYearInflationExperimentVariant, List<MultiYearFoldCorrectionResult>> byVariant = experimentService.runAll();
        List<String> reportLines = MultiYearInflationExperimentReportFormatter.format(byVariant, metricsCalculator, priceIndexProvider);
        reportLines.forEach(log::info);

        try {
            Path out = Path.of("multiyear-inflation-report.txt");
            Files.write(out, reportLines, StandardCharsets.UTF_8);
            log.info("리포트 파일 저장 완료: {}", out.toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        assertEquals(4, byVariant.size(), "A/B/C/D 네 가지 변형이 전부 실행돼야 함");
        for (MultiYearInflationExperimentVariant variant : MultiYearInflationExperimentVariant.values()) {
            assertEquals(3, byVariant.get(variant).size(), "각 변형은 Primary 2025/2026 + Secondary 2024 3개 fold");
        }

        // A와 C의 sampleCount(후보 수)는 동일해야 한다(4절: 동일 candidate pool).
        List<MultiYearFoldCorrectionResult> aFolds = byVariant.get(MultiYearInflationExperimentVariant.A_S0_INFLATION_OFF);
        List<MultiYearFoldCorrectionResult> cFolds = byVariant.get(MultiYearInflationExperimentVariant.C_S0_INFLATION_ON);
        for (int i = 0; i < aFolds.size(); i++) {
            assertEquals(aFolds.get(i).predictions().size(), cFolds.get(i).predictions().size(),
                    "A/C의 평가 성공 건수(=candidate pool이 비어있지 않은 target 수)가 같아야 함");
        }
    }
}