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
 * V4 Hybrid의 두 parameter(year concentration cap x qualityLossBudget) 거친 sensitivity 비교
 * (사용자 요청: cap 0.40/0.50/0.60 x qualityLossBudget 0.05/0.10/0.15 = 9개 조합).
 *
 * <p>목적은 최적점 탐색이 아니라 "cap=0.50/qualityLossBudget=0.10이 주변 parameter에서도
 * 안정적인가" 확인이다 - composition(2절)과 2026 fold(train&lt;=2025) backtest(4절)를 한 번에
 * 본다. V4가 실제로 영향을 주는 fold가 2026뿐이라는 것은 이전 라운드에서 이미 확인했으므로 이번
 * sensitivity는 2026 fold만 본다(4절 "V4가 실제로 작동하는 핵심 fold는 2026이므로 우선 비교").</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class MultiYearSelectorLabV4SensitivityRealDataAnalysisTest {

    private static final Logger log = LoggerFactory.getLogger(MultiYearSelectorLabV4SensitivityRealDataAnalysisTest.class);
    private static final int TARGET_YEAR = 2026;
    private static final double[] CAPS = {0.40, 0.50, 0.60};
    private static final double[] LOSS_BUDGETS = {0.05, 0.10, 0.15};

    @Autowired
    private MultiYearCsvImportService importService;
    @Autowired
    private MultiYearFestivalRecordRepository recordRepository;
    @Autowired
    private MultiYearBacktestDatasetBuilder datasetBuilder;
    @Autowired
    private MultiYearBacktestService backtestService;
    @Autowired
    private MultiYearCandidateSelector v0Selector;
    @Autowired
    private MultiYearSimilarityCalculator similarityCalculator;
    @Autowired
    private MultiYearSelectorLabBacktestService selectorLabBacktestService;
    @Autowired
    private MultiYearBacktestMetricsCalculator metricsCalculator;
    @Autowired
    private AlgorithmConfig config;

    @Test
    void realCsv_v4ParameterSensitivity() throws IOException {
        String csvPathValue = System.getenv("FESTIVAL_MULTIYEAR_CSV_PATH");
        Assumptions.assumeTrue(csvPathValue != null && !csvPathValue.isBlank(),
                "FESTIVAL_MULTIYEAR_CSV_PATH 환경변수가 없어 로컬 분석 테스트를 건너뜁니다.");
        Path csvPath = Path.of(csvPathValue);
        Assumptions.assumeTrue(Files.isRegularFile(csvPath),
                "FESTIVAL_MULTIYEAR_CSV_PATH 파일을 찾을 수 없어 건너뜁니다: " + csvPath.toAbsolutePath());

        byte[] bytes = Files.readAllBytes(csvPath);
        importService.importFromBytes(bytes, csvPath.getFileName().toString());
        List<MultiYearFestivalRecord> allRecords = recordRepository.findAll();
        MultiYearBacktestDataset dataset = datasetBuilder.build(allRecords, MultiYearBacktestFold.PRIMARY_2026);
        List<MultiYearBacktestQuery> noDistrictBenchmark = MultiYearSelectorLabBenchmarkBuilder.buildNoDistrictBenchmark(dataset.evalTargets());

        Map<String, MultiYearCandidateSelectionStrategy> combos = new LinkedHashMap<>();
        combos.put("V0_CURRENT (참고)", v0Selector);
        for (double cap : CAPS) {
            for (double loss : LOSS_BUDGETS) {
                MultiYearSelectorLabV4Hybrid v4 = new MultiYearSelectorLabV4Hybrid(config, similarityCalculator, cap, loss);
                combos.put(v4.label(), v4);
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add("================ V4 Hybrid parameter sensitivity (cap x qualityLossBudget, 9 combos) ================");
        lines.add("");
        lines.add("---- [1] candidate composition (district=null 벤치마크, n=%d) ----".formatted(noDistrictBenchmark.size()));
        lines.add("%-32s %6s %6s %9s %9s %9s %9s".formatted("combo", "n", "medN", "medDistY", "medEffY", "medAvgSim", "medMinSim"));
        MultiYearSelectorLabConcentrationAnalyzer analyzer = new MultiYearSelectorLabConcentrationAnalyzer(backtestService);
        for (var entry : combos.entrySet()) {
            List<MultiYearSelectorLabConcentrationStats> statsList = new ArrayList<>();
            for (MultiYearBacktestQuery query : noDistrictBenchmark) {
                MultiYearSelectorLabConcentrationStats stats = analyzer.analyze(entry.getValue(), query, TARGET_YEAR, dataset.trainingPool());
                if (stats != null) {
                    statsList.add(stats);
                }
            }
            lines.add("%-32s %6d %6.0f %9.2f %9.2f %9.3f %9.3f".formatted(
                    entry.getKey(), statsList.size(),
                    median(statsList, s -> (double) s.sampleCount()),
                    median(statsList, s -> (double) s.distinctYearsUsed()),
                    median(statsList, MultiYearSelectorLabConcentrationStats::effectiveYearCount),
                    median(statsList, MultiYearSelectorLabConcentrationStats::averageSimilarity),
                    median(statsList, MultiYearSelectorLabConcentrationStats::minimumSimilarity)));
        }

        lines.add("");
        lines.add("---- [2] leakage-safe backtest, fold=train<=2025->2026 (n=%d evalTargets) ----".formatted(dataset.evalTargets().size()));
        for (var entry : combos.entrySet()) {
            MultiYearFoldResult result = selectorLabBacktestService.runFold(allRecords, MultiYearBacktestFold.PRIMARY_2026, entry.getValue());
            appendBacktestSummary(lines, entry.getKey(), result.predictions());
        }

        lines.forEach(log::info);
        Path out = Path.of("multiyear-selectorlab-v4-sensitivity-report.txt");
        try {
            Files.write(out, lines, StandardCharsets.UTF_8);
            log.info("리포트 파일 저장 완료: {}", out.toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        assertTrue(lines.size() > 10, "리포트가 비정상적으로 짧음");
    }

    private void appendBacktestSummary(List<String> lines, String label, List<MultiYearBacktestPrediction> predictions) {
        MultiYearBacktestMetricsSummary summary = metricsCalculator.summarize(predictions);
        double medianSignedLogError = medianSignedLogError(predictions);
        double medianRatio = medianPredictedActualRatio(predictions);

        lines.add("  [%s]".formatted(label));
        lines.add("    MAE=%,.0f MdAPE=%.1f%% P75APE=%.1f%% P90APE=%.1f%% MedianALE=%.3f medSignedLogErr=%.3f medRatio=%.3f".formatted(
                summary.mae(), summary.medianAbsolutePercentageError() * 100, summary.p75AbsolutePercentageError() * 100,
                summary.p90AbsolutePercentageError() * 100, summary.medianAbsoluteLogError(), medianSignedLogError, medianRatio));
        lines.add("    within25%%=%.1f%% within50%%=%.1f%% 0.5x~2x=%.1f%% typicalRangeCoverage=%.1f%%".formatted(
                summary.within25PercentRatio() * 100, summary.within50PercentRatio() * 100,
                summary.within2xRatio() * 100, summary.typicalRangeCoverageRatio() * 100));
        StringBuilder bySize = new StringBuilder("    규모별 signedLogError: ");
        for (MultiYearBudgetSizeBucketMetrics bucket : metricsCalculator.budgetSizeBreakdown(predictions)) {
            List<MultiYearBacktestPrediction> group = predictions.stream()
                    .filter(p -> bucketOf(p.actualBudget()).equals(bucket.bucketLabel()))
                    .toList();
            double bucketSignedLogError = medianSignedLogError(group);
            bySize.append(bucket.bucketLabel()).append("=").append(group.isEmpty() ? "N/A" : "%.3f".formatted(bucketSignedLogError)).append("  ");
        }
        lines.add(bySize.toString());
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

    private double median(List<MultiYearSelectorLabConcentrationStats> statsList, java.util.function.ToDoubleFunction<MultiYearSelectorLabConcentrationStats> f) {
        double[] values = statsList.stream().mapToDouble(f).toArray();
        return MultiYearBacktestMath.median(values);
    }
}