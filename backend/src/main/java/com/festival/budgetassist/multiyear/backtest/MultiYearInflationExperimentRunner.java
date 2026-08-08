package com.festival.budgetassist.multiyear.backtest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 로컬 전용 inflation x series-correction 2x2(A/B/C/D) 비교 CLI.
 *
 * <p>실행: {@code --analysis.multiyear-inflation.run=true}.
 * {@code --analysis.multiyear-inflation.report-path}로 출력 경로를 바꿀 수 있다(기본값 backend
 * 루트의 multiyear-inflation-report.txt, gitignore 처리됨).</p>
 */
@Component
@ConditionalOnProperty(prefix = "analysis.multiyear-inflation", name = "run", havingValue = "true")
class MultiYearInflationExperimentRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MultiYearInflationExperimentRunner.class);

    private final MultiYearInflationExperimentService experimentService;
    private final MultiYearSeriesCorrectionMetricsCalculator metricsCalculator;
    private final AnnualPriceIndexProvider priceIndexProvider;

    @Value("${analysis.multiyear-inflation.report-path:multiyear-inflation-report.txt}")
    private String reportPath;

    MultiYearInflationExperimentRunner(MultiYearInflationExperimentService experimentService,
                                        MultiYearSeriesCorrectionMetricsCalculator metricsCalculator,
                                        AnnualPriceIndexProvider priceIndexProvider) {
        this.experimentService = experimentService;
        this.metricsCalculator = metricsCalculator;
        this.priceIndexProvider = priceIndexProvider;
    }

    @Override
    public void run(String... args) {
        log.info("================ inflation x series correction(A/B/C/D) 비교 시작 ================");
        Map<MultiYearInflationExperimentVariant, List<MultiYearFoldCorrectionResult>> byVariant = experimentService.runAll();
        List<String> lines = MultiYearInflationExperimentReportFormatter.format(byVariant, metricsCalculator, priceIndexProvider);
        lines.forEach(log::info);

        try {
            Path path = Path.of(reportPath);
            Files.write(path, lines, StandardCharsets.UTF_8);
            log.info("리포트 파일 저장 완료: {}", path.toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        log.info("================ inflation x series correction(A/B/C/D) 비교 종료 ================");
    }
}