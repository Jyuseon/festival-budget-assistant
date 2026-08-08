package com.festival.budgetassist.multiyear.backtest;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

import com.festival.budgetassist.estimate.AlgorithmConfig;
import com.festival.budgetassist.multiyear.csv.MultiYearCsvImportService;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;
import com.festival.budgetassist.multiyear.repository.MultiYearFestivalRecordRepository;

/**
 * concentration 분석(section 5~10)에서 가장 합리적이라고 판단한 selector 후보 2~3개를 기존
 * leakage-safe backtest fold(Primary 2017~2024→2025, 2017~2025→2026 / Secondary 2017~2023→2024)
 * 에서 V0(현재)와 정확도 비교한다 (11~12절). CPI/series correction/recency/COVID는 전부 OFF로
 * 고정한다(13절 - selector 자체의 영향만 분리해서 본다).
 *
 * <p>{@code FESTIVAL_MULTIYEAR_CSV_PATH} 환경변수가 없으면 건너뛴다.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class MultiYearSelectorLabBacktestRealDataAnalysisTest {

    private static final Logger log = LoggerFactory.getLogger(MultiYearSelectorLabBacktestRealDataAnalysisTest.class);

    @Autowired
    private MultiYearCsvImportService importService;
    @Autowired
    private MultiYearFestivalRecordRepository recordRepository;
    @Autowired
    private MultiYearSelectorLabBacktestService selectorLabBacktestService;
    @Autowired
    private MultiYearBacktestMetricsCalculator metricsCalculator;
    @Autowired
    private MultiYearCandidateSelector v0Selector;
    @Autowired
    private MultiYearSimilarityCalculator similarityCalculator;
    @Autowired
    private AlgorithmConfig config;

    @Test
    void realCsv_compareSelectorCandidatesOnLeakageSafeBacktest() throws IOException {
        String csvPathValue = System.getenv("FESTIVAL_MULTIYEAR_CSV_PATH");
        Assumptions.assumeTrue(csvPathValue != null && !csvPathValue.isBlank(),
                "FESTIVAL_MULTIYEAR_CSV_PATH 환경변수가 없어 로컬 분석 테스트를 건너뜁니다.");
        Path csvPath = Path.of(csvPathValue);
        Assumptions.assumeTrue(Files.isRegularFile(csvPath),
                "FESTIVAL_MULTIYEAR_CSV_PATH 파일을 찾을 수 없어 건너뜁니다: " + csvPath.toAbsolutePath());

        byte[] bytes = Files.readAllBytes(csvPath);
        importService.importFromBytes(bytes, csvPath.getFileName().toString());
        List<MultiYearFestivalRecord> allRecords = recordRepository.findAll();

        Map<String, MultiYearCandidateSelectionStrategy> candidates = new LinkedHashMap<>();
        candidates.put("V0_CURRENT", v0Selector);
        candidates.put("V1_YEAR_CAP_50pct", new MultiYearSelectorLabV1YearCap(config, 0.5));
        candidates.put("V4_HYBRID_cap50pct_loss0.10", new MultiYearSelectorLabV4Hybrid(config, similarityCalculator, 0.5, 0.10));

        List<String> lines = new ArrayList<>();
        lines.add("================ Selector 후보 leakage-safe backtest 비교 (V0 vs V1 vs V4) ================");
        lines.add("CPI/series correction/recency/COVID 전부 OFF (13절) - selector 자체의 영향만 분리");
        lines.add("");

        for (MultiYearBacktestFold fold : MultiYearBacktestFold.all()) {
            lines.add("========== fold: %s ==========".formatted(fold.label()));
            for (var entry : candidates.entrySet()) {
                String label = entry.getKey();
                MultiYearFoldResult result = selectorLabBacktestService.runFold(allRecords, fold, entry.getValue());
                appendFoldReport(lines, label, result);
            }
            lines.add("");
        }

        lines.forEach(log::info);
        Path out = Path.of("multiyear-selectorlab-backtest-report.txt");
        try {
            Files.write(out, lines, StandardCharsets.UTF_8);
            log.info("리포트 파일 저장 완료: {}", out.toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        assertTrue(lines.size() > 10, "리포트가 비정상적으로 짧음");
    }

    private void appendFoldReport(List<String> lines, String label, MultiYearFoldResult result) {
        List<MultiYearBacktestPrediction> predictions = result.predictions();
        MultiYearBacktestMetricsSummary summary = metricsCalculator.summarize(predictions);

        lines.add("  [%s] evalTargets=%d, noFinalSample=%d, 평가 %d건".formatted(
                label, result.evalTargetCount(), result.evalExcludedNoFinalSample(), predictions.size()));
        lines.add("    MAE=%,.0f  MedianAE=%,.0f  MdAPE=%.1f%%  P75APE=%.1f%%  P90APE=%.1f%%  MedianALE=%.3f".formatted(
                summary.mae(), summary.medianAbsoluteError(), summary.medianAbsolutePercentageError() * 100,
                summary.p75AbsolutePercentageError() * 100, summary.p90AbsolutePercentageError() * 100,
                summary.medianAbsoluteLogError()));
        lines.add("    within25%%=%.1f%%  within50%%=%.1f%%  0.5x~2x=%.1f%%  typicalRangeCoverage=%.1f%%".formatted(
                summary.within25PercentRatio() * 100, summary.within50PercentRatio() * 100,
                summary.within2xRatio() * 100, summary.typicalRangeCoverageRatio() * 100));

        double medianSignedLogError = medianSignedLogError(predictions);
        double medianRatio = medianPredictedActualRatio(predictions);
        double medianRangeWidthRatio = medianRangeWidthRatio(predictions);
        lines.add("    medianSignedLogError=%.3f (양수=과대추정 경향)  medianPredicted/ActualRatio=%.3f  medianRangeWidthRatio(P75/P25)=%.2f".formatted(
                medianSignedLogError, medianRatio, medianRangeWidthRatio));

        lines.add("    예산 규모별 breakdown (n, medianAPE, medianSignedLogError):");
        for (MultiYearBudgetSizeBucketMetrics bucket : metricsCalculator.budgetSizeBreakdown(predictions)) {
            List<MultiYearBacktestPrediction> group = predictions.stream()
                    .filter(p -> bucketOf(p.actualBudget()).equals(bucket.bucketLabel()))
                    .toList();
            double bucketSignedLogError = medianSignedLogError(group);
            lines.add("      %-10s n=%4d  medianAPE=%s  medianSignedLogError=%s".formatted(
                    bucket.bucketLabel(), bucket.count(),
                    Double.isNaN(bucket.medianAbsolutePercentageError()) ? "N/A" : "%.1f%%".formatted(bucket.medianAbsolutePercentageError() * 100),
                    group.isEmpty() ? "N/A" : "%.3f".formatted(bucketSignedLogError)));
        }
    }

    private String bucketOf(long actualBudgetKrw) {
        if (actualBudgetKrw <= 100_000_000L) return "<= 100M";
        if (actualBudgetKrw <= 300_000_000L) return "100M~300M";
        if (actualBudgetKrw <= 1_000_000_000L) return "300M~1B";
        if (actualBudgetKrw <= 3_000_000_000L) return "1B~3B";
        return "> 3B";
    }

    private double medianSignedLogError(List<MultiYearBacktestPrediction> predictions) {
        double[] values = predictions.stream()
                .filter(p -> p.estimatedBudget() > 0 && p.actualBudget() > 0)
                .mapToDouble(p -> Math.log((double) p.estimatedBudget() / p.actualBudget()))
                .toArray();
        return values.length > 0 ? MultiYearBacktestMath.median(values) : Double.NaN;
    }

    private double medianPredictedActualRatio(List<MultiYearBacktestPrediction> predictions) {
        double[] values = predictions.stream()
                .filter(p -> p.actualBudget() > 0)
                .mapToDouble(p -> (double) p.estimatedBudget() / p.actualBudget())
                .toArray();
        return values.length > 0 ? MultiYearBacktestMath.median(values) : Double.NaN;
    }

    private double medianRangeWidthRatio(List<MultiYearBacktestPrediction> predictions) {
        double[] values = predictions.stream()
                .filter(p -> p.p25() > 0)
                .mapToDouble(p -> (double) p.p75() / p.p25())
                .toArray();
        return values.length > 0 ? MultiYearBacktestMath.median(values) : Double.NaN;
    }
}