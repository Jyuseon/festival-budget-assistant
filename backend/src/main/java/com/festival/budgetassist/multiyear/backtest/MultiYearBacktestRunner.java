package com.festival.budgetassist.multiyear.backtest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 로컬 전용 leakage-safe 다년도 baseline backtest CLI. DB에 이미 적재된
 * {@code multi_year_festival_record} 전체를 대상으로 {@link MultiYearBacktestService}를 fold별로
 * 실행하고 리포트 텍스트 + 예측 단위 CSV를 남긴다.
 *
 * <p>실행: {@code --analysis.multiyear-backtest.run=true} (다른 분석 CLI와 동일한 패턴).
 * {@code --analysis.multiyear-backtest.report-path}/{@code .predictions-csv-path}로 출력 경로를
 * 바꿀 수 있다(기본값 backend 루트의 multiyear-backtest-baseline-report.txt /
 * multiyear-backtest-baseline-predictions.csv, 둘 다 .gitignore 처리됨).</p>
 */
@Component
@ConditionalOnProperty(prefix = "analysis.multiyear-backtest", name = "run", havingValue = "true")
class MultiYearBacktestRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MultiYearBacktestRunner.class);

    private final MultiYearBacktestService backtestService;
    private final MultiYearBacktestMetricsCalculator metricsCalculator;
    private final MultiYearDataQualityV3Calculator v3Calculator;

    @Value("${analysis.multiyear-backtest.report-path:multiyear-backtest-baseline-report.txt}")
    private String reportPath;

    @Value("${analysis.multiyear-backtest.predictions-csv-path:multiyear-backtest-baseline-predictions.csv}")
    private String predictionsCsvPath;

    MultiYearBacktestRunner(MultiYearBacktestService backtestService, MultiYearBacktestMetricsCalculator metricsCalculator,
                             MultiYearDataQualityV3Calculator v3Calculator) {
        this.backtestService = backtestService;
        this.metricsCalculator = metricsCalculator;
        this.v3Calculator = v3Calculator;
    }

    @Override
    public void run(String... args) {
        log.info("================ leakage-safe 다년도 baseline backtest 시작 ================");
        List<MultiYearFoldResult> foldResults = backtestService.runAllFolds();

        List<String> reportLines = MultiYearBacktestReportFormatter.format(foldResults, metricsCalculator, v3Calculator);
        reportLines.forEach(log::info);
        writeReport(reportLines);
        writePredictionsCsv(foldResults);

        log.info("================ leakage-safe 다년도 baseline backtest 종료 ================");
    }

    private void writeReport(List<String> lines) {
        try {
            Path path = Path.of(reportPath);
            Files.write(path, lines, StandardCharsets.UTF_8);
            log.info("리포트 파일 저장 완료: {}", path.toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
                        String.valueOf(p.targetYear()),
                        csvEscape(p.festivalName()),
                        csvEscape(p.region()),
                        csvEscape(p.district()),
                        csvEscape(p.festivalType()),
                        csvEscape(p.venueType()),
                        p.durationDays() == null ? "" : String.valueOf(p.durationDays()),
                        String.valueOf(p.actualBudget()),
                        String.valueOf(p.estimatedBudget()),
                        String.valueOf(p.weightedAverageBudget()),
                        String.valueOf(p.recommendedBudget()),
                        String.valueOf(p.p25()),
                        String.valueOf(p.p75()),
                        String.valueOf(p.sampleCount()),
                        String.valueOf(p.distinctSeriesCount()),
                        p.fallbackLevel(),
                        String.valueOf(p.dataQualityV3()),
                        String.valueOf(p.absoluteError()),
                        Double.isFinite(p.absolutePercentageError()) ? String.valueOf(p.absolutePercentageError()) : "",
                        Double.isFinite(p.absoluteLogError()) ? String.valueOf(p.absoluteLogError()) : ""
                ));
            }
        }

        try {
            Path path = Path.of(predictionsCsvPath);
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