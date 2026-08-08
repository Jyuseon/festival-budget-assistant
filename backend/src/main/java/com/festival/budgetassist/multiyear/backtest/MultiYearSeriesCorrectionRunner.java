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
 * 로컬 전용 festivalSeries 중복 보정(S0/S1/S2) 비교 CLI.
 *
 * <p>실행: {@code --analysis.multiyear-series-correction.run=true}.
 * {@code --analysis.multiyear-series-correction.report-path}로 출력 경로를 바꿀 수 있다
 * (기본값 backend 루트의 multiyear-series-correction-report.txt, gitignore 처리됨).</p>
 */
@Component
@ConditionalOnProperty(prefix = "analysis.multiyear-series-correction", name = "run", havingValue = "true")
class MultiYearSeriesCorrectionRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MultiYearSeriesCorrectionRunner.class);

    private final MultiYearSeriesCorrectionBacktestService correctionService;
    private final MultiYearSeriesCorrectionMetricsCalculator metricsCalculator;

    @Value("${analysis.multiyear-series-correction.report-path:multiyear-series-correction-report.txt}")
    private String reportPath;

    MultiYearSeriesCorrectionRunner(MultiYearSeriesCorrectionBacktestService correctionService,
                                     MultiYearSeriesCorrectionMetricsCalculator metricsCalculator) {
        this.correctionService = correctionService;
        this.metricsCalculator = metricsCalculator;
    }

    @Override
    public void run(String... args) {
        log.info("================ festivalSeries 중복 보정(S0/S1/S2) 비교 시작 ================");
        Map<MultiYearSeriesCorrectionMode, List<MultiYearFoldCorrectionResult>> byMode = correctionService.runAllModesAllFolds();
        List<String> lines = MultiYearSeriesCorrectionReportFormatter.format(byMode, metricsCalculator);
        lines.forEach(log::info);

        try {
            Path path = Path.of(reportPath);
            Files.write(path, lines, StandardCharsets.UTF_8);
            log.info("리포트 파일 저장 완료: {}", path.toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        log.info("================ festivalSeries 중복 보정(S0/S1/S2) 비교 종료 ================");
    }
}