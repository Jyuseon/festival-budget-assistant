package com.festival.budgetassist.multiyear.backtest;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
 * CandidateSelector 연도 concentration 실측 분석 (사용자 요청 5~7절, 10절 composition 비교).
 * 실제 sanitized CSV(2017~2026)를 Import한 뒤, 2026 실데이터에 존재하는 (region, festivalType,
 * venueType, durationDays) 조합으로 벤치마크 쿼리를 만들고, V0(현재)~V4(hybrid) selector 후보를
 * 전부 leakage-safe training pool(2017~2025)에 대해 돌려 리포트 파일로 남긴다.
 *
 * <p>선정만 전략별로 다르고, 이후 유사도/기간보정/winsorize/threshold+상위 N건 컷은 항상
 * {@link MultiYearBacktestService#selectFinalSample(MultiYearCandidateSelectionStrategy,
 * MultiYearBacktestQuery, int, List, boolean)}를 통해 baseline과 완전히 동일한 공식으로
 * 계산된다({@link MultiYearSelectorLabConcentrationAnalyzer} 참고) - selector를 바꿔도 채점
 * 공식은 절대 바뀌지 않는다는 것을 코드 구조 자체가 보장한다.</p>
 *
 * <p>{@code FESTIVAL_MULTIYEAR_CSV_PATH} 환경변수가 없으면 건너뛴다 - {@code
 * MultiYearBacktestRealDataAnalysisTest}와 동일한 패턴.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class MultiYearSelectorLabConcentrationRealDataAnalysisTest {

    private static final Logger log = LoggerFactory.getLogger(MultiYearSelectorLabConcentrationRealDataAnalysisTest.class);
    private static final int TARGET_YEAR = 2026;

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
    private AlgorithmConfig config;

    @Test
    void realCsv_analyzeCandidateSelectorYearConcentration() throws IOException {
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
        List<MultiYearFestivalRecord> trainingPool = dataset.trainingPool(); // 2017~2025, leakage-safe, quality-filtered

        List<MultiYearBacktestQuery> noDistrictBenchmark = MultiYearSelectorLabBenchmarkBuilder.buildNoDistrictBenchmark(dataset.evalTargets());
        List<MultiYearBacktestQuery> districtBenchmark = MultiYearSelectorLabBenchmarkBuilder.buildDistrictBenchmark(dataset.evalTargets());

        log.info("2026 평가대상 {}건 -> district=null 벤치마크 {}건, district 벤치마크 {}건 (training pool {}건)",
                dataset.evalTargets().size(), noDistrictBenchmark.size(), districtBenchmark.size(), trainingPool.size());

        List<VariantEntry> variants = buildVariants();
        MultiYearSelectorLabConcentrationAnalyzer analyzer = new MultiYearSelectorLabConcentrationAnalyzer(backtestService);

        // variant -> (query -> stats or null)
        Map<String, List<MultiYearSelectorLabConcentrationStats>> byVariantNoDistrict = runAll(variants, noDistrictBenchmark, trainingPool, analyzer);
        Map<String, List<MultiYearSelectorLabConcentrationStats>> byVariantDistrict = runAll(variants, districtBenchmark, trainingPool, analyzer);

        List<String> lines = new ArrayList<>();
        lines.add("================ CandidateSelector 연도 Concentration 분석 (V0~V4) ================");
        lines.add("targetYear=%d, training=2017~%d, trainingPool=%d건".formatted(TARGET_YEAR, TARGET_YEAR - 1, trainingPool.size()));
        lines.add("벤치마크: district=null %d건 (2026 실데이터 (region,type,venue,duration) 유니크 조합), district 지정 %d건"
                .formatted(noDistrictBenchmark.size(), districtBenchmark.size()));
        lines.add("");

        lines.add("---------------- [1] district=null 벤치마크 ----------------");
        appendSection(lines, "V0_CURRENT", byVariantNoDistrict.get("V0_CURRENT"));
        appendCompositionComparison(lines, byVariantNoDistrict);
        lines.add("");
        lines.add("---------------- [2] district 지정 벤치마크(참고, 표본 더 작음) ----------------");
        appendSection(lines, "V0_CURRENT", byVariantDistrict.get("V0_CURRENT"));
        appendCompositionComparison(lines, byVariantDistrict);

        lines.forEach(log::info);
        Path out = Path.of("multiyear-selectorlab-concentration-report.txt");
        try {
            Files.write(out, lines, StandardCharsets.UTF_8);
            log.info("리포트 파일 저장 완료: {}", out.toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        writeRawCsv(byVariantNoDistrict.get("V0_CURRENT"), "multiyear-selectorlab-v0-nodistrict-raw.csv");

        assertTrue(byVariantNoDistrict.get("V0_CURRENT").size() > 20, "district=null 벤치마크가 너무 적으면 분포 분석 의미가 없음");
    }

    // ------------------------------------------------------------------

    private record VariantEntry(String label, MultiYearCandidateSelectionStrategy strategy) {
    }

    private List<VariantEntry> buildVariants() {
        List<VariantEntry> variants = new ArrayList<>();
        variants.add(new VariantEntry("V0_CURRENT", v0Selector));
        for (double cap : List.of(0.4, 0.5, 0.6)) {
            MultiYearSelectorLabV1YearCap v1 = new MultiYearSelectorLabV1YearCap(config, cap);
            variants.add(new VariantEntry(v1.label(), v1));
        }
        for (double band : List.of(0.05, 0.10)) {
            MultiYearSelectorLabV2DiversifiedTopK v2 = new MultiYearSelectorLabV2DiversifiedTopK(config, similarityCalculator, band);
            variants.add(new VariantEntry(v2.label(), v2));
        }
        for (int n : List.of(3, 4, 5)) {
            MultiYearSelectorLabV3MinDistinctYears v3 = new MultiYearSelectorLabV3MinDistinctYears(config, n);
            variants.add(new VariantEntry(v3.label(), v3));
        }
        MultiYearSelectorLabV4Hybrid v4 = new MultiYearSelectorLabV4Hybrid(config, similarityCalculator, 0.5, 0.10);
        variants.add(new VariantEntry(v4.label(), v4));
        return variants;
    }

    private Map<String, List<MultiYearSelectorLabConcentrationStats>> runAll(List<VariantEntry> variants,
            List<MultiYearBacktestQuery> benchmark, List<MultiYearFestivalRecord> trainingPool,
            MultiYearSelectorLabConcentrationAnalyzer analyzer) {
        Map<String, List<MultiYearSelectorLabConcentrationStats>> result = new LinkedHashMap<>();
        for (VariantEntry v : variants) {
            List<MultiYearSelectorLabConcentrationStats> statsList = new ArrayList<>();
            for (MultiYearBacktestQuery query : benchmark) {
                MultiYearSelectorLabConcentrationStats stats = analyzer.analyze(v.strategy(), query, TARGET_YEAR, trainingPool);
                if (stats != null) {
                    statsList.add(stats);
                }
            }
            result.put(v.label(), statsList);
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 리포트 구성
    // ------------------------------------------------------------------

    private void appendSection(List<String> lines, String label, List<MultiYearSelectorLabConcentrationStats> statsList) {
        lines.add("[%s] 예측 %d건".formatted(label, statsList.size()));
        if (statsList.isEmpty()) {
            lines.add("  (표본 없음)");
            return;
        }

        // 6절: distinctYearsUsed 분포
        Map<String, Long> distinctYearsBuckets = new LinkedHashMap<>();
        distinctYearsBuckets.put("1", 0L);
        distinctYearsBuckets.put("2", 0L);
        distinctYearsBuckets.put("3", 0L);
        distinctYearsBuckets.put("4", 0L);
        distinctYearsBuckets.put("5+", 0L);
        distinctYearsBuckets.put("8+", 0L);
        long only2025 = 0;
        long topShareGe90 = 0;
        long topShareGe75 = 0;
        long topShareGe50 = 0;
        for (MultiYearSelectorLabConcentrationStats s : statsList) {
            int d = s.distinctYearsUsed();
            if (d == 1) distinctYearsBuckets.merge("1", 1L, Long::sum);
            else if (d == 2) distinctYearsBuckets.merge("2", 1L, Long::sum);
            else if (d == 3) distinctYearsBuckets.merge("3", 1L, Long::sum);
            else if (d == 4) distinctYearsBuckets.merge("4", 1L, Long::sum);
            else distinctYearsBuckets.merge("5+", 1L, Long::sum);
            if (d >= 8) distinctYearsBuckets.merge("8+", 1L, Long::sum);

            if (d == 1 && s.latestSourceYear() == 2025) only2025++;
            double topShare = s.topYearWeightShare();
            if (topShare >= 0.90) topShareGe90++;
            if (topShare >= 0.75) topShareGe75++;
            if (topShare >= 0.50) topShareGe50++;
        }
        int n = statsList.size();
        lines.add("  distinctYearsUsed 분포:");
        distinctYearsBuckets.forEach((k, v) -> lines.add("    %s: %d건 (%.1f%%)".formatted(k, v, 100.0 * v / n)));
        lines.add("  sourceYear=2025 단일연도만 사용: %d건 (%.1f%%)".formatted(only2025, 100.0 * only2025 / n));
        lines.add("  top-year weightShare >= 90%%: %d건 (%.1f%%)".formatted(topShareGe90, 100.0 * topShareGe90 / n));
        lines.add("  top-year weightShare >= 75%%: %d건 (%.1f%%)".formatted(topShareGe75, 100.0 * topShareGe75 / n));
        lines.add("  top-year weightShare >= 50%%: %d건 (%.1f%%)".formatted(topShareGe50, 100.0 * topShareGe50 / n));
        lines.add("  effectiveYearCount: median=%.2f, p25=%.2f, p75=%.2f".formatted(
                median(statsList, MultiYearSelectorLabConcentrationStats::effectiveYearCount),
                percentile(statsList, MultiYearSelectorLabConcentrationStats::effectiveYearCount, 0.25),
                percentile(statsList, MultiYearSelectorLabConcentrationStats::effectiveYearCount, 0.75)));
        lines.add("  sampleCount: median=%.0f, averageSimilarity: median=%.3f, minimumSimilarity: median=%.3f".formatted(
                median(statsList, s -> (double) s.sampleCount()),
                median(statsList, MultiYearSelectorLabConcentrationStats::averageSimilarity),
                median(statsList, MultiYearSelectorLabConcentrationStats::minimumSimilarity)));

        // 7절: fallback tier별 breakdown
        Map<String, List<MultiYearSelectorLabConcentrationStats>> byLevel = new LinkedHashMap<>();
        for (MultiYearSelectorLabConcentrationStats s : statsList) {
            byLevel.computeIfAbsent(s.fallbackLevel(), k -> new ArrayList<>()).add(s);
        }
        lines.add("  fallback tier별 breakdown:");
        byLevel.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> tierOrder(e.getKey())))
                .forEach(e -> {
                    List<MultiYearSelectorLabConcentrationStats> group = e.getValue();
                    lines.add("    %-24s case=%4d  median distinctYears=%.1f  median effectiveYearCount=%.2f  median latestYearWeightShare=%.3f"
                            .formatted(e.getKey(), group.size(),
                                    median(group, s -> (double) s.distinctYearsUsed()),
                                    median(group, MultiYearSelectorLabConcentrationStats::effectiveYearCount),
                                    median(group, MultiYearSelectorLabConcentrationStats::latestYearWeightShare)));
                });

        // 5절: 예시 3건(연도별 breakdown 포함) - 가장 concentration이 심한 사례 위주로 보여준다.
        lines.add("  예시(연도별 candidateCount/weightShare) - concentration 상위 3건:");
        statsList.stream()
                .sorted(Comparator.comparingDouble(MultiYearSelectorLabConcentrationStats::topYearWeightShare).reversed())
                .limit(3)
                .forEach(s -> {
                    lines.add("    sampleCount=%d distinctYearsUsed=%d fallbackLevel=%s avgSimilarity=%.3f".formatted(
                            s.sampleCount(), s.distinctYearsUsed(), s.fallbackLevel(), s.averageSimilarity()));
                    s.yearBreakdown().stream()
                            .sorted(Comparator.comparingDouble(MultiYearSelectorLabYearShare::weightShare).reversed())
                            .forEach(y -> lines.add("      %d -> %d건 / %.1f%%".formatted(y.year(), y.candidateCount(), y.weightShare() * 100)));
                });
    }

    private void appendCompositionComparison(List<String> lines, Map<String, List<MultiYearSelectorLabConcentrationStats>> byVariant) {
        lines.add("");
        lines.add("  ---- V0~V4 candidate composition 비교 (10절) ----");
        lines.add("  %-28s %6s %6s %9s %9s %9s %9s %8s".formatted(
                "variant", "n", "medN", "medDistY", "medEffY", "medLatShr", "medAvgSim", "medMinSim"));
        byVariant.forEach((label, statsList) -> {
            if (statsList.isEmpty()) {
                lines.add("  %-28s (표본 없음)".formatted(label));
                return;
            }
            lines.add("  %-28s %6d %6.0f %9.2f %9.2f %9.3f %9.3f %8.3f".formatted(
                    label, statsList.size(),
                    median(statsList, s -> (double) s.sampleCount()),
                    median(statsList, s -> (double) s.distinctYearsUsed()),
                    median(statsList, MultiYearSelectorLabConcentrationStats::effectiveYearCount),
                    median(statsList, MultiYearSelectorLabConcentrationStats::latestYearWeightShare),
                    median(statsList, MultiYearSelectorLabConcentrationStats::averageSimilarity),
                    median(statsList, MultiYearSelectorLabConcentrationStats::minimumSimilarity)));
        });
    }

    private int tierOrder(String levelName) {
        try {
            return com.festival.budgetassist.estimate.FallbackLevel.valueOf(levelName).ordinal();
        } catch (IllegalArgumentException e) {
            return 99;
        }
    }

    private double median(List<MultiYearSelectorLabConcentrationStats> statsList, java.util.function.ToDoubleFunction<MultiYearSelectorLabConcentrationStats> f) {
        double[] values = statsList.stream().mapToDouble(f).toArray();
        return MultiYearBacktestMath.median(values);
    }

    private double percentile(List<MultiYearSelectorLabConcentrationStats> statsList, java.util.function.ToDoubleFunction<MultiYearSelectorLabConcentrationStats> f, double q) {
        double[] values = statsList.stream().mapToDouble(f).toArray();
        return MultiYearBacktestMath.quantile(values, q);
    }

    private void writeRawCsv(List<MultiYearSelectorLabConcentrationStats> statsList, String fileName) {
        List<String> csvLines = new ArrayList<>();
        csvLines.add(String.join(",", "sampleCount", "distinctYearsUsed", "effectiveYearCount", "earliestSourceYear",
                "latestSourceYear", "fallbackLevel", "averageSimilarity", "minimumSimilarity", "topYearWeightShare", "latestYearWeightShare"));
        for (MultiYearSelectorLabConcentrationStats s : statsList) {
            csvLines.add(String.join(",", String.valueOf(s.sampleCount()), String.valueOf(s.distinctYearsUsed()),
                    String.format(Locale.ROOT, "%.4f", s.effectiveYearCount()), String.valueOf(s.earliestSourceYear()),
                    String.valueOf(s.latestSourceYear()), s.fallbackLevel(),
                    String.format(Locale.ROOT, "%.4f", s.averageSimilarity()), String.format(Locale.ROOT, "%.4f", s.minimumSimilarity()),
                    String.format(Locale.ROOT, "%.4f", s.topYearWeightShare()), String.format(Locale.ROOT, "%.4f", s.latestYearWeightShare())));
        }
        try {
            Files.write(Path.of(fileName), csvLines, StandardCharsets.UTF_8);
            log.info("raw CSV 저장 완료: {}", Path.of(fileName).toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}