package com.festival.budgetassist.estimate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.festival.budgetassist.festival.domain.BudgetStatus;
import com.festival.budgetassist.festival.domain.FestivalRecord;
import com.festival.budgetassist.festival.domain.FestivalType;
import com.festival.budgetassist.festival.domain.Region;
import com.festival.budgetassist.festival.domain.VenueType;
import com.festival.budgetassist.festival.repository.FestivalRecordRepository;

/**
 * 로컬 전용 진단 CLI. 실제 DB 데이터를 이용해 (지역×유형×장소유형×대표기간) 조합을 최대한
 * 실존 조합 기준으로 생성해 {@link BudgetEstimatorService}를 반복 실행하고, legacy confidence와
 * v1.1/v1.2 후보 confidence를 나란히 비교한다: 각각의 분포, 구성요소별 분포, 등급 비율, 최고/최저
 * 사례, (legacy vs v1.1) Spearman 순위상관계수와 순위가 가장 크게 바뀐 사례를 분석한다. v1.2는
 * v1.1과 effectiveSampleScore/similarityScore/stabilityScoreV11/completenessScore를 그대로
 * 공유하고 scopeScore만 제외한 재가중 버전이므로 별도의 구성요소 분포는 다시 찍지 않는다.
 * 그리고 SAME_DISTRICT_TYPE_VENUE 단계가 실제로 발동하는 시군구 3곳을 자동 선정해 세 공식과
 * fallback 단계별 weight 점유율을 함께 검증한다.
 *
 * <p>AlgorithmConfig의 유사도/기간보정/winsorize/추천예산/80·60 threshold/CandidateSelector
 * 동작은 전혀 건드리지 않는다 - confidence 공식만 순수 비교 대상이다.</p>
 *
 * <p>결과는 콘솔 로그뿐 아니라 UTF-8 텍스트 파일로도 저장한다({@code --analysis.confidence.report-path}로
 * 경로를 바꿀 수 있고, 기본값은 backend 루트의 confidence-analysis-report.txt).</p>
 *
 * <p>실행: {@code --analysis.confidence.run=true} (import.run과 동일한 패턴, 웹서버 비활성화)</p>
 */
@Component
@ConditionalOnProperty(prefix = "analysis.confidence", name = "run", havingValue = "true")
class ConfidenceAnalysisRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ConfidenceAnalysisRunner.class);
    private static final int[] REPRESENTATIVE_DURATIONS = {2, 3, 5, 7, 14};
    private static final int DISTRICT_TEST_DURATION = 3;
    private static final int TOP_N = 10;

    private final FestivalRecordRepository festivalRecordRepository;
    private final BudgetEstimatorService budgetEstimatorService;
    private final List<String> reportLines = new ArrayList<>();

    @Value("${analysis.confidence.report-path:confidence-analysis-report.txt}")
    private String reportPath;

    ConfidenceAnalysisRunner(FestivalRecordRepository festivalRecordRepository, BudgetEstimatorService budgetEstimatorService) {
        this.festivalRecordRepository = festivalRecordRepository;
        this.budgetEstimatorService = budgetEstimatorService;
    }

    @Override
    public void run(String... args) {
        line("Confidence legacy vs v1.1 vs v1.2 비교 리포트 - 생성 시각(UTC): " + DateTimeFormatter.ISO_INSTANT.format(Instant.now()));

        int datasetYear = festivalRecordRepository.findMaxDatasetYear()
                .orElseThrow(() -> new IllegalStateException("적재된 데이터가 없습니다. 먼저 CLI로 Import를 실행하세요."));
        List<FestivalRecord> confirmedPool = festivalRecordRepository.findByDatasetYearAndBudgetStatus(datasetYear, BudgetStatus.CONFIRMED);

        line("================ 분석 시작 (datasetYear=%d, 모집단 %d건) ================".formatted(datasetYear, confirmedPool.size()));

        List<CaseResult> results = runDistributionScan(confirmedPool);
        if (results.isEmpty()) {
            line("[오류] 유효한 결과가 하나도 없습니다. festival.calculation-trace.enabled=true 설정을 확인하세요.");
            writeReport();
            return;
        }

        printLegacyDistribution(results);
        printV11Distribution(results);
        printV11Extremes(results);
        printRankComparison(results);
        printV12Distribution(results);
        printV12Extremes(results);

        runDistrictAnalysis(confirmedPool);

        line("================ 분석 종료 ================");
        writeReport();
    }

    private void line(String message) {
        log.info(message);
        reportLines.add(message);
    }

    private void writeReport() {
        try {
            Path path = Path.of(reportPath);
            Files.write(path, reportLines, StandardCharsets.UTF_8);
            log.info("리포트 파일 저장 완료: {}", path.toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------------
    // 1. 전체 분포 스캔 (legacy/v1.1 동시 계산 - 같은 estimate() 호출 하나로 둘 다 얻는다)
    // ------------------------------------------------------------------

    private List<CaseResult> runDistributionScan(List<FestivalRecord> confirmedPool) {
        List<Combo> combos = confirmedPool.stream()
                .map(r -> new Combo(r.getRegion(), r.getFestivalType(), r.getVenueType()))
                .distinct()
                .sorted(Comparator.comparing((Combo c) -> c.region().name())
                        .thenComparing(c -> c.type().name())
                        .thenComparing(c -> c.venue().name()))
                .toList();

        line("실제 데이터에 존재하는 (지역,축제유형,장소유형) 조합: %d건 x 대표기간 %d개 = 최대 %d건 실행".formatted(
                combos.size(), REPRESENTATIVE_DURATIONS.length, combos.size() * REPRESENTATIVE_DURATIONS.length));

        List<CaseResult> results = new ArrayList<>();
        int emptyCount = 0;
        int noBreakdownCount = 0;

        for (Combo combo : combos) {
            for (int duration : REPRESENTATIVE_DURATIONS) {
                BudgetEstimateRequest request = new BudgetEstimateRequest(
                        combo.region().name(), null, combo.type().name(), combo.venue().name(), duration);
                BudgetEstimateResponse response = budgetEstimatorService.estimate(request);
                if (response.sampleCount() == 0) {
                    emptyCount++;
                    continue;
                }
                if (response.confidenceBreakdown() == null) {
                    noBreakdownCount++;
                    continue;
                }
                results.add(new CaseResult(combo.region(), combo.type(), combo.venue(), duration, response));
            }
        }

        line("실행 완료: 유효 케이스 %d건, 표본 0건이라 제외 %d건, confidenceBreakdown 없음(설정 확인 필요) %d건".formatted(
                results.size(), emptyCount, noBreakdownCount));
        return results;
    }

    // ------------------------------------------------------------------
    // 2. legacy 분포 (기존과 동일한 내용, 비교 기준선)
    // ------------------------------------------------------------------

    private void printLegacyDistribution(List<CaseResult> results) {
        line("--- [legacy] confidenceScore 분포 (n=%d) ---".formatted(results.size()));
        printStats("confidenceScore(legacy)", results.stream().mapToDouble(CaseResult::legacyScore).toArray());
        printStats("sampleScore", results.stream().mapToDouble(r -> r.response().confidenceBreakdown().sampleScore()).toArray());
        printStats("similarityScore", results.stream().mapToDouble(r -> r.response().confidenceBreakdown().similarityScore()).toArray());
        printStats("stabilityScore(legacy, cap=1.5)", results.stream().mapToDouble(r -> r.response().confidenceBreakdown().stabilityScore()).toArray());
        printStats("completenessScore", results.stream().mapToDouble(r -> r.response().confidenceBreakdown().completenessScore()).toArray());
        printLevelCounts("legacy", results, CaseResult::legacyLevel);
    }

    // ------------------------------------------------------------------
    // 3. v1.1 분포
    // ------------------------------------------------------------------

    private void printV11Distribution(List<CaseResult> results) {
        line("--- [v1.1] confidenceScore 분포 (n=%d) ---".formatted(results.size()));
        printStats("confidenceScore(v1.1)", results.stream().mapToDouble(CaseResult::v11Score).toArray());
        printStats("effectiveSampleScore", results.stream().mapToDouble(r -> r.response().confidenceBreakdown().effectiveSampleScore()).toArray());
        printStats("similarityScore(공유)", results.stream().mapToDouble(r -> r.response().confidenceBreakdown().similarityScore()).toArray());
        printStats("stabilityScore(v1.1, log)", results.stream().mapToDouble(r -> r.response().confidenceBreakdown().stabilityScoreV11()).toArray());
        printStats("completenessScore(공유)", results.stream().mapToDouble(r -> r.response().confidenceBreakdown().completenessScore()).toArray());
        printStats("scopeScore", results.stream().mapToDouble(r -> r.response().confidenceBreakdown().scopeScore()).toArray());
        printLevelCounts("v1.1", results, CaseResult::v11Level);
    }

    private void printLevelCounts(String tag, List<CaseResult> results, java.util.function.Function<CaseResult, String> levelExtractor) {
        Map<String, Long> levelCounts = new LinkedHashMap<>();
        levelCounts.put("HIGH", 0L);
        levelCounts.put("MEDIUM", 0L);
        levelCounts.put("LOW", 0L);
        for (CaseResult r : results) {
            levelCounts.merge(levelExtractor.apply(r), 1L, Long::sum);
        }
        line("--- [%s] 등급별 건수/비율 (n=%d, threshold 80/60은 legacy와 동일하게 적용) ---".formatted(tag, results.size()));
        levelCounts.forEach((level, count) ->
                line("  %s: %d건 (%.1f%%)".formatted(level, count, 100.0 * count / results.size())));
    }

    private void printStats(String label, double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        double min = sorted[0];
        double max = sorted[sorted.length - 1];
        double mean = Arrays.stream(sorted).average().orElse(0);
        line("  %s: min=%s, P10=%s, P25=%s, median=%s, P75=%s, P90=%s, max=%s, mean=%s".formatted(
                label,
                fmt(min),
                fmt(WeightedStatistics.quantile(values, 0.10)),
                fmt(WeightedStatistics.quantile(values, 0.25)),
                fmt(WeightedStatistics.quantile(values, 0.50)),
                fmt(WeightedStatistics.quantile(values, 0.75)),
                fmt(WeightedStatistics.quantile(values, 0.90)),
                fmt(max),
                fmt(mean)));
    }

    private String fmt(double v) {
        return "%.2f".formatted(v);
    }

    // ------------------------------------------------------------------
    // 4. v1.1 최고/최저 N건
    // ------------------------------------------------------------------

    private void printV11Extremes(List<CaseResult> results) {
        List<CaseResult> byScoreDesc = results.stream()
                .sorted(Comparator.comparingDouble(CaseResult::v11Score).reversed())
                .toList();

        line("--- [v1.1] confidence 최고 %d건 ---".formatted(TOP_N));
        byScoreDesc.stream().limit(TOP_N).forEach(this::printCaseV11);

        line("--- [v1.1] confidence 최저 %d건 ---".formatted(TOP_N));
        List<CaseResult> ascending = new ArrayList<>(byScoreDesc);
        Collections.reverse(ascending);
        ascending.stream().limit(TOP_N).forEach(this::printCaseV11);
    }

    private void printCaseV11(CaseResult r) {
        BudgetEstimateResponse resp = r.response();
        ConfidenceBreakdown b = resp.confidenceBreakdown();
        line(("  [%s / %s / %s / %d일] v1.1=%s(%s) legacy=%s(%s) | effN=%.1f effSampleScore=%s sim=%s stab=%s comp=%s scope=%s "
                + "| sampleCount=%d fallback=%s 추천예산=%d원 P25~P75=%d~%d원").formatted(
                r.region(), r.type(), r.venue(), r.duration(),
                fmt(resp.confidenceBreakdown().confidenceScoreV11()), resp.confidenceBreakdown().confidenceLevelV11(),
                fmt(resp.confidence().score()), resp.confidence().level(),
                b.effectiveSampleSize(), fmt(b.effectiveSampleScore()), fmt(b.similarityScore()), fmt(b.stabilityScoreV11()),
                fmt(b.completenessScore()), fmt(b.scopeScore()),
                resp.sampleCount(), resp.fallbackLevel(), resp.recommendedBudgetKrw(),
                resp.typicalRange().lowKrw(), resp.typicalRange().highKrw()));
    }

    // ------------------------------------------------------------------
    // 5. legacy vs v1.1 순위 비교 (Spearman) + 순위 변동 최대 사례
    // ------------------------------------------------------------------

    private void printRankComparison(List<CaseResult> results) {
        double[] legacy = results.stream().mapToDouble(CaseResult::legacyScore).toArray();
        double[] v11 = results.stream().mapToDouble(CaseResult::v11Score).toArray();

        double[] legacyRank = descendingRanks(legacy); // 1 = 가장 높은 점수
        double[] v11Rank = descendingRanks(v11);

        double spearman = pearson(legacyRank, v11Rank);
        line("--- legacy vs v1.1 순위 비교 (n=%d) ---".formatted(results.size()));
        line("Spearman 순위상관계수: %.4f".formatted(spearman));

        // delta > 0 : v1.1에서 순위번호가 작아짐(더 높은 순위로 이동, 상대적으로 더 신뢰됨)
        int n = results.size();
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        double[] delta = new double[n];
        for (int i = 0; i < n; i++) {
            delta[i] = legacyRank[i] - v11Rank[i];
        }

        List<Integer> movedUp = Arrays.stream(order).sorted(Comparator.comparingDouble((Integer i) -> delta[i]).reversed()).toList();
        List<Integer> movedDown = Arrays.stream(order).sorted(Comparator.comparingDouble((Integer i) -> delta[i])).toList();

        line("--- 순위가 가장 크게 상승한 5건 (v1.1에서 상대적으로 더 신뢰됨) ---");
        for (int i = 0; i < Math.min(5, n); i++) {
            printRankMover(results.get(movedUp.get(i)), legacyRank[movedUp.get(i)], v11Rank[movedUp.get(i)]);
        }

        line("--- 순위가 가장 크게 하락한 5건 (v1.1에서 상대적으로 덜 신뢰됨) ---");
        for (int i = 0; i < Math.min(5, n); i++) {
            printRankMover(results.get(movedDown.get(i)), legacyRank[movedDown.get(i)], v11Rank[movedDown.get(i)]);
        }
    }

    private void printRankMover(CaseResult r, double legacyRank, double v11Rank) {
        ConfidenceBreakdown b = r.response().confidenceBreakdown();
        line(("  [%s / %s / %s / %d일] legacy순위=%.0f(%.1f점) -> v1.1순위=%.0f(%.1f점) | "
                + "legacy[sample=%s,stab=%s] v1.1[effSample=%s,stab=%s,scope=%s]").formatted(
                r.region(), r.type(), r.venue(), r.duration(),
                legacyRank, r.legacyScore(), v11Rank, r.v11Score(),
                fmt(b.sampleScore()), fmt(b.stabilityScore()),
                fmt(b.effectiveSampleScore()), fmt(b.stabilityScoreV11()), fmt(b.scopeScore())));
    }

    /** 내림차순(1=최고점) 순위, 동점은 평균 순위로 처리. */
    private double[] descendingRanks(double[] values) {
        int n = values.length;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Double.compare(values[b], values[a]));

        double[] ranks = new double[n];
        int i = 0;
        while (i < n) {
            int j = i;
            while (j + 1 < n && values[order[j + 1]] == values[order[i]]) {
                j++;
            }
            double avgRank = ((i + 1) + (j + 1)) / 2.0;
            for (int k = i; k <= j; k++) {
                ranks[order[k]] = avgRank;
            }
            i = j + 1;
        }
        return ranks;
    }

    private double pearson(double[] a, double[] b) {
        int n = a.length;
        double meanA = Arrays.stream(a).average().orElse(0);
        double meanB = Arrays.stream(b).average().orElse(0);
        double num = 0;
        double denA = 0;
        double denB = 0;
        for (int i = 0; i < n; i++) {
            double da = a[i] - meanA;
            double db = b[i] - meanB;
            num += da * db;
            denA += da * da;
            denB += db * db;
        }
        if (denA == 0 || denB == 0) {
            return 0;
        }
        return num / Math.sqrt(denA * denB);
    }

    // ------------------------------------------------------------------
    // 5b. v1.2 분포 (scopeScore 제외: effectiveSampleScore/similarityScore/stabilityScoreV11/completenessScore는
    //     v1.1과 완전히 동일한 값을 재사용하므로 - printV11Distribution에서 이미 분포를 보였다 - 여기서는
    //     confidenceScore(v1.2) 자체와 등급 비율만 별도로 본다.)
    // ------------------------------------------------------------------

    private void printV12Distribution(List<CaseResult> results) {
        line("--- [v1.2] confidenceScore 분포 (n=%d, scopeScore 제외 / 나머지 4개 구성요소는 v1.1과 동일값 재사용) ---".formatted(results.size()));
        printStats("confidenceScore(v1.2)", results.stream().mapToDouble(CaseResult::v12Score).toArray());
        printLevelCounts("v1.2", results, CaseResult::v12Level);
    }

    // ------------------------------------------------------------------
    // 5c. v1.2 최고/최저 N건
    // ------------------------------------------------------------------

    private void printV12Extremes(List<CaseResult> results) {
        List<CaseResult> byScoreDesc = results.stream()
                .sorted(Comparator.comparingDouble(CaseResult::v12Score).reversed())
                .toList();

        line("--- [v1.2] confidence 최고 %d건 ---".formatted(TOP_N));
        byScoreDesc.stream().limit(TOP_N).forEach(this::printCaseV12);

        line("--- [v1.2] confidence 최저 %d건 ---".formatted(TOP_N));
        List<CaseResult> ascending = new ArrayList<>(byScoreDesc);
        Collections.reverse(ascending);
        ascending.stream().limit(TOP_N).forEach(this::printCaseV12);
    }

    private void printCaseV12(CaseResult r) {
        BudgetEstimateResponse resp = r.response();
        ConfidenceBreakdown b = resp.confidenceBreakdown();
        line(("  [%s / %s / %s / %d일] v1.2=%s(%s) v1.1=%s(%s) legacy=%s(%s) | effN=%.1f effSampleScore=%s sim=%s stab=%s comp=%s "
                + "| sampleCount=%d fallback=%s 추천예산=%d원 P25~P75=%d~%d원").formatted(
                r.region(), r.type(), r.venue(), r.duration(),
                fmt(b.confidenceScoreV12()), b.confidenceLevelV12(),
                fmt(b.confidenceScoreV11()), b.confidenceLevelV11(),
                fmt(resp.confidence().score()), resp.confidence().level(),
                b.effectiveSampleSize(), fmt(b.effectiveSampleScore()), fmt(b.similarityScore()), fmt(b.stabilityScoreV11()),
                fmt(b.completenessScore()),
                resp.sampleCount(), resp.fallbackLevel(), resp.recommendedBudgetKrw(),
                resp.typicalRange().lowKrw(), resp.typicalRange().highKrw()));
    }

    // ------------------------------------------------------------------
    // 6. 시군구(SAME_DISTRICT_TYPE_VENUE) 실제 검증 - legacy/v1.1/v1.2 동시 표시
    // ------------------------------------------------------------------

    private void runDistrictAnalysis(List<FestivalRecord> confirmedPool) {
        Map<DistrictCombo, Long> countsByCombo = new LinkedHashMap<>();
        for (FestivalRecord r : confirmedPool) {
            if (r.getAdministrativeDistrict() == null) {
                continue;
            }
            DistrictCombo key = new DistrictCombo(r.getRegion(), r.getAdministrativeDistrict(), r.getFestivalType(), r.getVenueType());
            countsByCombo.merge(key, 1L, Long::sum);
        }

        List<Map.Entry<DistrictCombo, Long>> sorted = countsByCombo.entrySet().stream()
                .sorted(Map.Entry.<DistrictCombo, Long>comparingByValue().reversed())
                .toList();

        if (sorted.isEmpty()) {
            line("[경고] 시군구 단위 (지역,시군구,유형,장소유형) 조합이 하나도 없어 시군구 분석을 건너뜁니다.");
            return;
        }

        Map.Entry<DistrictCombo, Long> richest = sorted.get(0);
        Map.Entry<DistrictCombo, Long> poorest = sorted.get(sorted.size() - 1);
        Map.Entry<DistrictCombo, Long> middle = sorted.get(sorted.size() / 2);

        line("================ 시군구(SAME_DISTRICT_TYPE_VENUE) 실제 검증 ================");
        line("전체 (지역,시군구,유형,장소유형) 조합 수: %d건, district 후보 수 범위: %d~%d건".formatted(
                sorted.size(), poorest.getValue(), richest.getValue()));

        runDistrictCase("표본 충분", richest.getKey(), richest.getValue());
        runDistrictCase("표본 중간", middle.getKey(), middle.getValue());
        runDistrictCase("표본 부족", poorest.getKey(), poorest.getValue());
    }

    private void runDistrictCase(String label, DistrictCombo combo, long districtCandidateCount) {
        BudgetEstimateRequest request = new BudgetEstimateRequest(
                combo.region().name(), combo.district(), combo.type().name(), combo.venue().name(), DISTRICT_TEST_DURATION);
        BudgetEstimateResponse response = budgetEstimatorService.estimate(request);

        line("[%s] %s %s / %s / %s / %d일".formatted(label, combo.region(), combo.district(), combo.type(), combo.venue(), DISTRICT_TEST_DURATION));
        line("  SAME_DISTRICT_TYPE_VENUE 후보 수(district+유형+장소유형 정확히 일치): %d건".formatted(districtCandidateCount));

        // BudgetEstimatorService가 trace에 "   LEVEL: +N건 (누적 M건)" 형식으로 남긴 줄만 뽑아 단계별 후보 구성을 보여준다.
        if (response.calculationTrace() != null) {
            List<String> levelLines = response.calculationTrace().stream()
                    .filter(t -> t.contains("누적"))
                    .toList();
            if (!levelLines.isEmpty()) {
                line("  fallback 단계별 후보 구성:");
                levelLines.forEach(l -> line("   " + l.trim()));
            }
        }

        line("  최종 sampleCount=%d, fallbackLevel=%s".formatted(response.sampleCount(), response.fallbackLevel()));
        line("  추천예산=%d원, 통계추정=%d원, 가중평균=%d원, P25~P75=%d~%d원".formatted(
                response.recommendedBudgetKrw(), response.estimatedBudgetKrw(), response.weightedAverageBudgetKrw(),
                response.typicalRange().lowKrw(), response.typicalRange().highKrw()));

        ConfidenceBreakdown b = response.confidenceBreakdown();
        line("  confidence(legacy)=%s(%s) [sample=%s,sim=%s,stab=%s,comp=%s]".formatted(
                fmt(response.confidence().score()), response.confidence().label(),
                fmt(b.sampleScore()), fmt(b.similarityScore()), fmt(b.stabilityScore()), fmt(b.completenessScore())));
        line("  confidence(v1.1)  =%s(%s) [effN=%.1f,effSample=%s,sim=%s,stab=%s,comp=%s,scope=%s]".formatted(
                fmt(b.confidenceScoreV11()), b.confidenceLevelV11(),
                b.effectiveSampleSize(), fmt(b.effectiveSampleScore()), fmt(b.similarityScore()), fmt(b.stabilityScoreV11()),
                fmt(b.completenessScore()), fmt(b.scopeScore())));
        line("  confidence(v1.2)  =%s(%s) [effN=%.1f,effSample=%s,sim=%s,stab=%s,comp=%s] (scope 제외)".formatted(
                fmt(b.confidenceScoreV12()), b.confidenceLevelV12(),
                b.effectiveSampleSize(), fmt(b.effectiveSampleScore()), fmt(b.similarityScore()), fmt(b.stabilityScoreV11()),
                fmt(b.completenessScore())));

        if (!response.scopeWeightBreakdown().isEmpty()) {
            line("  fallback 단계별 최종 weight 점유율(설명용, confidence에는 미반영):");
            for (ScopeWeightShare share : response.scopeWeightBreakdown()) {
                line("   %s: %.1f%%".formatted(share.label(), share.weightSharePercent()));
            }
        }

        List<SimilarFestivalDto> top = response.similarFestivals();
        double shownWeightSum = top.stream().mapToDouble(SimilarFestivalDto::weight).sum();
        if (!top.isEmpty() && shownWeightSum > 0) {
            double top1Share = top.get(0).weight() / shownWeightSum;
            String note = response.sampleCount() <= top.size()
                    ? "정확값(표본 전체가 상위 목록에 포함됨)"
                    : "상위 %d건 기준 상한 근사값(전체 %d건 중 일부만 노출되어 실제 점유율은 이보다 낮거나 같음)".formatted(top.size(), response.sampleCount());
            line("  1위 후보 가중치 점유율: %s%% [%s]".formatted(fmt(top1Share * 100), note));
        }

        line("  Top %d 유사 축제:".formatted(Math.min(5, top.size())));
        for (int i = 0; i < Math.min(5, top.size()); i++) {
            SimilarFestivalDto f = top.get(i);
            line("    %d. %s | %s%s | %s/%s | 실제기간=%s 실제예산=%s원 보정예산=%d원 | similarity=%s weight=%s".formatted(
                    i + 1, f.festivalName(), f.regionName(), f.districtName() == null ? "" : " " + f.districtName(),
                    f.festivalTypeName(), f.venueTypeName(), f.actualDurationDays(), f.actualBudgetKrw(), f.durationAdjustedBudgetKrw(),
                    fmt(f.similarity()), fmt(f.weight())));
        }
    }

    private record Combo(Region region, FestivalType type, VenueType venue) {
    }

    private record DistrictCombo(Region region, String district, FestivalType type, VenueType venue) {
    }

    private record CaseResult(Region region, FestivalType type, VenueType venue, int duration, BudgetEstimateResponse response) {
        double legacyScore() {
            return response.confidence().score();
        }

        String legacyLevel() {
            return response.confidence().level();
        }

        double v11Score() {
            return response.confidenceBreakdown().confidenceScoreV11();
        }

        String v11Level() {
            return response.confidenceBreakdown().confidenceLevelV11();
        }

        double v12Score() {
            return response.confidenceBreakdown().confidenceScoreV12();
        }

        String v12Level() {
            return response.confidenceBreakdown().confidenceLevelV12();
        }
    }
}