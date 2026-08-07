package com.festival.budgetassist.multiyear.series;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 로컬 전용 festivalSeries 연결 분석 CLI. {@code MultiYearCsvImportRunner}로 이미 DB에 적재된
 * {@code multi_year_festival_record}를 대상으로 {@link FestivalSeriesLinkingService#linkAll()}를
 * 실행하고 리포트를 콘솔 + 텍스트 파일로 남긴다.
 *
 * <p>실행: {@code --analysis.series-linking.run=true} ({@code ConfidenceAnalysisRunner}와
 * 동일한 패턴). {@code --analysis.series-linking.report-path}로 출력 파일 경로를 바꿀 수
 * 있다(기본값 backend 루트의 multiyear-series-linking-report.txt).</p>
 */
@Component
@ConditionalOnProperty(prefix = "analysis.series-linking", name = "run", havingValue = "true")
class FestivalSeriesLinkingRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(FestivalSeriesLinkingRunner.class);

    private final FestivalSeriesLinkingService linkingService;

    @Value("${analysis.series-linking.report-path:multiyear-series-linking-report.txt}")
    private String reportPath;

    FestivalSeriesLinkingRunner(FestivalSeriesLinkingService linkingService) {
        this.linkingService = linkingService;
    }

    @Override
    public void run(String... args) {
        log.info("================ festivalSeries 연결 분석 시작 ================");
        FestivalSeriesLinkingReport report = linkingService.linkAll();
        List<String> lines = FestivalSeriesLinkingReportFormatter.format(report);
        lines.forEach(log::info);
        writeReport(lines);
        log.info("================ festivalSeries 연결 분석 종료 ================");
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
}