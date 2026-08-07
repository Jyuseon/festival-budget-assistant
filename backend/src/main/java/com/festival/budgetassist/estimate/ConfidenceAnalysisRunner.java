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
 * v1.1/v1.2/v2/v3 후보 confidence를 나란히 비교한다: 각각의 분포, 구성요소별 분포, 등급 비율, 최고/최저
 * 사례, (legacy vs v1.1) Spearman 순위상관계수와 순위가 가장 크게 바뀐 사례를 분석한다. v1.2는
 * v1.1과 effectiveSampleScore/similarityScore/stabilityScoreV11/completenessScore를 그대로
 * 공유하고 scopeScore만 제외한 재가중 버전이므로 별도의 구성요소 분포는 다시 찍지 않는다.
 * v2는 이 4개 raw component가 서로 다른 척도(scale)를 가진다는 문제 자체를 겨냥한 후보로,
 * 이번 1,580건 benchmark에서 직접 산출한 P10/P90으로 각 component를 0~1 robust scaling한
 * 뒤(calibration) 재가중한다 - v2는 순수 분석 전용이며 AlgorithmConfig/ConfidenceCalculator에는
 * 전혀 반영하지 않는다(운영 코드에 영향 없이 이 CLI 안에서만 계산·출력한다).
 * v3는 v2에서 드러난 "전체 benchmark 분위수로 모든 component를 강제 calibration하면 component별
 * 분포 모양 차이 때문에 0/1 saturation·대량 동점·직관에 반하는 지역간 역전이 생긴다"는 문제를 겨냥한
 * 후보로, benchmark 분위수 기반 calibration을 전혀 쓰지 않고 각 component 고유의 의미에 맞는 변환식을
 * 개별적으로 적용한다: sampleQuality=1-exp(-ESS/15), similarityQuality=similarityScore(raw),
 * stabilityQuality=exp(-ln(P75/P25)/2), completenessQuality=completenessScore(raw),
 * localEvidenceQuality는 scopeWeightBreakdown의 실제 weight 비율(district 입력 시 동일 시군구
 * + 0.5×동일 광역지역 추가, 없으면 동일 광역지역 비율 그대로, 전국 비율은 항상 제외)로 계산한다.
 * v3 역시 v2와 마찬가지로 순수 분석 전용이며 AlgorithmConfig/ConfidenceCalculator/production
 * confidence에는 전혀 반영하지 않는다. threshold(HIGH/MEDIUM/LOW)는 아직 만들지 않는다 - 이번
 * 단계는 점수 라벨보다 순위와 설명 가능성을 우선 검증하기 위한 것이다.
 * 그리고 SAME_DISTRICT_TYPE_VENUE 단계가 실제로 발동하는 시군구 3곳을 자동 선정해 다섯 공식과
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
        line("Confidence legacy vs v1.1 vs v1.2 vs v2 vs v3 비교 리포트 - 생성 시각(UTC): " + DateTimeFormatter.ISO_INSTANT.format(Instant.now()));

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

        CalibrationBounds v2Bounds = computeBenchmarkAndBounds(results);
        List<V2Result> v2Results = results.stream().map(r -> calibrateCase(r, v2Bounds)).toList();
        printV2Distribution(v2Results);
        printV2Extremes(v2Results);

        // districtProvided=false: 이번 1,580건 benchmark는 전부 district 미지정 조회이므로
        // (runDistributionScan 참고) localEvidenceQuality는 항상 "동일 광역지역 비율 그대로" 공식을 쓴다.
        List<V3Result> v3Results = results.stream().map(r -> computeV3(r, false)).toList();
        printV3Distribution(v3Results);
        printV3TieAnalysis(v3Results);
        printV3Extremes(v3Results);

        runDistrictAnalysis(confirmedPool, v2Bounds);

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
    // 6b. Confidence v2 후보: raw component 척도(scale) 불일치 자체를 겨냥한 calibration.
    //     P10/P90은 코드에 하드코딩하지 않고 이번 1,580건 benchmark에서 직접 산출한다.
    //     scope/fallback은 전혀 포함하지 않고, AlgorithmConfig/ConfidenceCalculator도
    //     건드리지 않는다 - 이 CLI 안에서만 계산되는 순수 분석용 후보다.
    // ------------------------------------------------------------------

    private static final double V2_SAMPLE_WEIGHT = 0.20;
    private static final double V2_SIMILARITY_WEIGHT = 0.40;
    private static final double V2_STABILITY_WEIGHT = 0.30;
    private static final double V2_COMPLETENESS_WEIGHT = 0.10;

    private record CalibrationBounds(
            double sampleP10, double sampleP90,
            double similarityP10, double similarityP90,
            double stabilityP10, double stabilityP90,
            double completenessP10, double completenessP90) {
    }

    private record V2Result(CaseResult base, double calibratedSample, double calibratedSimilarity,
                             double calibratedStability, double calibratedCompleteness, double score) {
    }

    /** raw component 4종의 benchmark 분포(P5~P95)를 출력하고, calibration에 쓸 P10/P90을 산출해 반환한다. */
    private CalibrationBounds computeBenchmarkAndBounds(List<CaseResult> results) {
        double[] sample = results.stream().mapToDouble(r -> r.response().confidenceBreakdown().effectiveSampleScore()).toArray();
        double[] similarity = results.stream().mapToDouble(r -> r.response().confidenceBreakdown().similarityScore()).toArray();
        double[] stability = results.stream().mapToDouble(r -> r.response().confidenceBreakdown().stabilityScoreV11()).toArray();
        double[] completeness = results.stream().mapToDouble(r -> r.response().confidenceBreakdown().completenessScore()).toArray();

        line("================ Confidence v2 후보: raw component benchmark 분포 (n=%d) ================".formatted(results.size()));
        printPercentiles("effectiveSampleScore", sample);
        printPercentiles("similarityScore", similarity);
        printPercentiles("stabilityScore(v1.1, log)", stability);
        printPercentiles("completenessScore", completeness);

        CalibrationBounds bounds = new CalibrationBounds(
                WeightedStatistics.quantile(sample, 0.10), WeightedStatistics.quantile(sample, 0.90),
                WeightedStatistics.quantile(similarity, 0.10), WeightedStatistics.quantile(similarity, 0.90),
                WeightedStatistics.quantile(stability, 0.10), WeightedStatistics.quantile(stability, 0.90),
                WeightedStatistics.quantile(completeness, 0.10), WeightedStatistics.quantile(completeness, 0.90));

        line("--- calibration에 사용할 P10/P90 (이번 benchmark에서 산출한 값 - 코드에 하드코딩하지 않음) ---");
        line("  effectiveSampleScore: P10=%s, P90=%s".formatted(fmt(bounds.sampleP10()), fmt(bounds.sampleP90())));
        line("  similarityScore     : P10=%s, P90=%s".formatted(fmt(bounds.similarityP10()), fmt(bounds.similarityP90())));
        line("  stabilityScore      : P10=%s, P90=%s".formatted(fmt(bounds.stabilityP10()), fmt(bounds.stabilityP90())));
        line("  completenessScore   : P10=%s, P90=%s".formatted(fmt(bounds.completenessP10()), fmt(bounds.completenessP90())));

        return bounds;
    }

    private void printPercentiles(String label, double[] values) {
        line("  %s: P5=%s, P10=%s, P25=%s, P50=%s, P75=%s, P90=%s, P95=%s".formatted(
                label,
                fmt(WeightedStatistics.quantile(values, 0.05)),
                fmt(WeightedStatistics.quantile(values, 0.10)),
                fmt(WeightedStatistics.quantile(values, 0.25)),
                fmt(WeightedStatistics.quantile(values, 0.50)),
                fmt(WeightedStatistics.quantile(values, 0.75)),
                fmt(WeightedStatistics.quantile(values, 0.90)),
                fmt(WeightedStatistics.quantile(values, 0.95))));
    }

    /** P10/P90 robust scaling: (raw-P10)/(P90-P10), [0,1] 범위로 clamp. */
    private double calibrate(double raw, double p10, double p90) {
        if (p90 <= p10) {
            // 실제 benchmark에서는 발생하지 않지만(4개 component 모두 P90>P10), 0 나눗셈 방지용 방어 코드.
            return raw >= p90 ? 1.0 : 0.0;
        }
        double scaled = (raw - p10) / (p90 - p10);
        return Math.max(0.0, Math.min(1.0, scaled));
    }

    private V2Result calibrateCase(CaseResult r, CalibrationBounds bounds) {
        ConfidenceBreakdown b = r.response().confidenceBreakdown();
        double calSample = calibrate(b.effectiveSampleScore(), bounds.sampleP10(), bounds.sampleP90());
        double calSimilarity = calibrate(b.similarityScore(), bounds.similarityP10(), bounds.similarityP90());
        double calStability = calibrate(b.stabilityScoreV11(), bounds.stabilityP10(), bounds.stabilityP90());
        double calCompleteness = calibrate(b.completenessScore(), bounds.completenessP10(), bounds.completenessP90());
        double score = (calSample * V2_SAMPLE_WEIGHT + calSimilarity * V2_SIMILARITY_WEIGHT
                + calStability * V2_STABILITY_WEIGHT + calCompleteness * V2_COMPLETENESS_WEIGHT) * 100;
        return new V2Result(r, calSample, calSimilarity, calStability, calCompleteness, score);
    }

    private void printV2Distribution(List<V2Result> v2Results) {
        double[] scores = v2Results.stream().mapToDouble(V2Result::score).toArray();
        double[] sorted = scores.clone();
        Arrays.sort(sorted);
        double iqr = WeightedStatistics.quantile(scores, 0.75) - WeightedStatistics.quantile(scores, 0.25);
        double range = sorted[sorted.length - 1] - sorted[0];

        line("--- [v2] confidenceScore 분포 (n=%d, threshold 미적용) ---".formatted(v2Results.size()));
        printStats("confidenceScore(v2)", scores);
        line("  IQR(P75-P25)=%s, range(max-min)=%s".formatted(fmt(iqr), fmt(range)));
    }

    private void printV2Extremes(List<V2Result> v2Results) {
        List<V2Result> byScoreDesc = v2Results.stream()
                .sorted(Comparator.comparingDouble(V2Result::score).reversed())
                .toList();

        line("--- [v2] confidence 최고 20건 (raw -> calibrated 나란히 표시) ---");
        byScoreDesc.stream().limit(20).forEach(this::printCaseV2);

        line("--- [v2] confidence 최저 20건 (raw -> calibrated 나란히 표시) ---");
        List<V2Result> ascending = new ArrayList<>(byScoreDesc);
        Collections.reverse(ascending);
        ascending.stream().limit(20).forEach(this::printCaseV2);
    }

    private void printCaseV2(V2Result v2) {
        CaseResult r = v2.base();
        ConfidenceBreakdown b = r.response().confidenceBreakdown();
        line(("  [%s / %s / %s / %d일] v2=%.2f | sample %s->%s | sim %s->%s | stab %s->%s | comp %s->%s "
                + "| legacy=%s v1.1=%s v1.2=%s | sampleCount=%d fallback=%s").formatted(
                r.region(), r.type(), r.venue(), r.duration(), v2.score(),
                fmt(b.effectiveSampleScore()), fmt(v2.calibratedSample()),
                fmt(b.similarityScore()), fmt(v2.calibratedSimilarity()),
                fmt(b.stabilityScoreV11()), fmt(v2.calibratedStability()),
                fmt(b.completenessScore()), fmt(v2.calibratedCompleteness()),
                fmt(r.legacyScore()), fmt(r.v11Score()), fmt(r.v12Score()),
                r.response().sampleCount(), r.response().fallbackLevel()));
    }

    // ------------------------------------------------------------------
    // 6b. Confidence v3 후보: benchmark 분위수 기반 강제 calibration(v2) 대신,
    //     각 component 고유의 의미에 맞는 변환식을 개별 적용한다. 4개는 raw component를
    //     그대로 쓰거나(similarity/completeness) 고정된 형태의 단조 변환(sample/stability)만
    //     적용하고, localEvidenceQuality만 기존 categorical scopeScore 대신
    //     scopeWeightBreakdown의 실제 weight 비율을 직접 사용한다. AlgorithmConfig/
    //     ConfidenceCalculator/production confidence는 전혀 건드리지 않는 순수 분석 후보다.
    // ------------------------------------------------------------------

    private static final double V3_SAMPLE_QUALITY_DIVISOR = 15.0;
    private static final double V3_STABILITY_QUALITY_DIVISOR = 2.0;

    private static final double V3_SAMPLE_WEIGHT = 0.20;
    private static final double V3_SIMILARITY_WEIGHT = 0.35;
    private static final double V3_STABILITY_WEIGHT = 0.20;
    private static final double V3_COMPLETENESS_WEIGHT = 0.10;
    private static final double V3_LOCAL_EVIDENCE_WEIGHT = 0.15;

    private record V3Result(CaseResult base, double sampleQuality, double similarityQuality,
                             double p75OverP25, double stabilityQuality, double completenessQuality,
                             double localEvidenceQuality, double score) {
    }

    /** sampleQuality = 1 - exp(-ESS / 15). ESS 10≈0.49, 20≈0.74, 30≈0.86, 50≈0.96. */
    private double v3SampleQuality(double effectiveSampleSize) {
        return 1 - Math.exp(-effectiveSampleSize / V3_SAMPLE_QUALITY_DIVISOR);
    }

    /**
     * P75/P25 비율. typicalRange(=BudgetEstimatorService가 confidence 계산에도 쓰는 바로 그
     * p25/p75를 반올림한 값)로부터 계산하므로 별도로 raw p25/p75를 다시 구할 필요가 없다.
     * winsorize된 예산은 항상 양수라 방어 분기는 실제로는 거의 타지 않는다.
     */
    private double p75OverP25(BudgetEstimateResponse response) {
        long low = response.typicalRange().lowKrw();
        long high = response.typicalRange().highKrw();
        if (low <= 0 || high <= 0) {
            return Double.NaN;
        }
        return (double) high / (double) low;
    }

    /** stabilityQuality = exp(-spread/2), spread=ln(P75/P25). P75/P25=2→0.71, 4→0.50, 8→0.35. */
    private double v3StabilityQuality(double p75OverP25) {
        if (!Double.isFinite(p75OverP25) || p75OverP25 <= 0) {
            return 0.0;
        }
        double spread = Math.max(Math.log(p75OverP25), 0.0);
        return Math.exp(-spread / V3_STABILITY_QUALITY_DIVISOR);
    }

    /**
     * scopeWeightBreakdown의 실제 weight 비율로 계산하는 local evidence quality.
     * district가 주어진 경우: 동일 시군구 비율 + 0.5 * 동일 광역지역(추가) 비율 - 전국 비율은 제외.
     * district가 없는 경우(이번 1,580건 benchmark 전부): 동일 광역지역 비율을 그대로 사용한다 -
     * district가 없을 때는 광역지역이 가장 로컬한 근거이므로 절반으로 깎지 않는다. 전국 비율은
     * 두 경우 모두 local evidence에 포함하지 않는다.
     */
    private double v3LocalEvidenceQuality(List<ScopeWeightShare> scopeWeightBreakdown, boolean districtProvided) {
        double districtShare = 0.0;
        double regionShare = 0.0;
        for (ScopeWeightShare share : scopeWeightBreakdown) {
            double fraction = share.weightSharePercent() / 100.0;
            FallbackLevel level = FallbackLevel.valueOf(share.level());
            switch (level) {
                case SAME_DISTRICT_TYPE_VENUE -> districtShare += fraction;
                case SAME_REGION_TYPE_VENUE, SAME_REGION_TYPE -> regionShare += fraction;
                default -> {
                    // NATIONWIDE_TYPE_VENUE / NATIONWIDE_TYPE / GLOBAL_SIMILARITY: 전국 데이터는
                    // local evidence에 포함하지 않는다.
                }
            }
        }
        return districtProvided ? districtShare + 0.5 * regionShare : regionShare;
    }

    private V3Result computeV3(CaseResult r, boolean districtProvided) {
        ConfidenceBreakdown b = r.response().confidenceBreakdown();
        double sampleQuality = v3SampleQuality(b.effectiveSampleSize());
        double similarityQuality = b.similarityScore();
        double p75OverP25 = p75OverP25(r.response());
        double stabilityQuality = v3StabilityQuality(p75OverP25);
        double completenessQuality = b.completenessScore();
        double localEvidenceQuality = v3LocalEvidenceQuality(r.response().scopeWeightBreakdown(), districtProvided);

        double score = (sampleQuality * V3_SAMPLE_WEIGHT
                + similarityQuality * V3_SIMILARITY_WEIGHT
                + stabilityQuality * V3_STABILITY_WEIGHT
                + completenessQuality * V3_COMPLETENESS_WEIGHT
                + localEvidenceQuality * V3_LOCAL_EVIDENCE_WEIGHT) * 100;

        return new V3Result(r, sampleQuality, similarityQuality, p75OverP25, stabilityQuality,
                completenessQuality, localEvidenceQuality, score);
    }

    private void printV3Distribution(List<V3Result> v3Results) {
        double[] scores = v3Results.stream().mapToDouble(V3Result::score).toArray();
        double[] sorted = scores.clone();
        Arrays.sort(sorted);
        double iqr = WeightedStatistics.quantile(scores, 0.75) - WeightedStatistics.quantile(scores, 0.25);
        double range = sorted[sorted.length - 1] - sorted[0];

        line("--- [v3] confidenceScore 분포 (n=%d, threshold 미적용, component별 개별 변환식 - benchmark 분위수 calibration 없음) ---"
                .formatted(v3Results.size()));
        printStats("confidenceScore(v3)", scores);
        line("  IQR(P75-P25)=%s, range(max-min)=%s".formatted(fmt(iqr), fmt(range)));
        printStats("sampleQuality(1-exp(-ESS/15))", v3Results.stream().mapToDouble(V3Result::sampleQuality).toArray());
        printStats("similarityQuality(raw)", v3Results.stream().mapToDouble(V3Result::similarityQuality).toArray());
        printStats("stabilityQuality(exp(-spread/2))", v3Results.stream().mapToDouble(V3Result::stabilityQuality).toArray());
        printStats("completenessQuality(raw)", v3Results.stream().mapToDouble(V3Result::completenessQuality).toArray());
        printStats("localEvidenceQuality", v3Results.stream().mapToDouble(V3Result::localEvidenceQuality).toArray());
    }

    /** 정확히 같은 v3 점수(소수 둘째자리 기준)를 받는 동점 사례가 얼마나 되는지, 특히 100점/0점 saturation을 확인한다. */
    private void printV3TieAnalysis(List<V3Result> v3Results) {
        Map<String, Long> countsByFormattedScore = new LinkedHashMap<>();
        for (V3Result v : v3Results) {
            countsByFormattedScore.merge(fmt(v.score()), 1L, Long::sum);
        }

        List<Map.Entry<String, Long>> tiedGroups = countsByFormattedScore.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .toList();
        long tiedCaseCount = tiedGroups.stream().mapToLong(Map.Entry::getValue).sum();
        long saturatedAt100 = countsByFormattedScore.getOrDefault(fmt(100.0), 0L);
        long saturatedAt0 = countsByFormattedScore.getOrDefault(fmt(0.0), 0L);

        line("--- [v3] 동점 분석 (소수점 2자리 기준 정확히 같은 점수, n=%d) ---".formatted(v3Results.size()));
        line("  서로 다른 점수 개수: %d개".formatted(countsByFormattedScore.size()));
        line("  동점 그룹 수(2건 이상 동일 점수): %d개, 동점에 포함된 총 건수: %d건 (%.1f%%)".formatted(
                tiedGroups.size(), tiedCaseCount, 100.0 * tiedCaseCount / v3Results.size()));
        line("  100.00점 saturation: %d건, 0.00점 saturation: %d건 (v2와 비교할 기준)".formatted(saturatedAt100, saturatedAt0));
        if (!tiedGroups.isEmpty()) {
            line("  동점 그룹 상위 10개(점수: 건수):");
            tiedGroups.stream().limit(10).forEach(e -> line("    %s점: %d건".formatted(e.getKey(), e.getValue())));
        }
    }

    private void printV3Extremes(List<V3Result> v3Results) {
        List<V3Result> byScoreDesc = v3Results.stream()
                .sorted(Comparator.comparingDouble(V3Result::score).reversed())
                .toList();

        line("--- [v3] confidence 최고 20건 ---");
        byScoreDesc.stream().limit(20).forEach(this::printCaseV3);

        line("--- [v3] confidence 최저 20건 ---");
        List<V3Result> ascending = new ArrayList<>(byScoreDesc);
        Collections.reverse(ascending);
        ascending.stream().limit(20).forEach(this::printCaseV3);
    }

    private void printCaseV3(V3Result v3) {
        CaseResult r = v3.base();
        ConfidenceBreakdown b = r.response().confidenceBreakdown();
        line(("  [%s / %s / %s / %d일] v3=%.2f | ESS=%.1f sampleQuality=%s | similarityQuality=%s "
                + "| P75/P25=%s stabilityQuality=%s | completenessQuality=%s | localEvidenceQuality=%s "
                + "| legacy=%s v1.1=%s v1.2=%s | sampleCount=%d fallback=%s").formatted(
                r.region(), r.type(), r.venue(), r.duration(), v3.score(),
                b.effectiveSampleSize(), fmt(v3.sampleQuality()),
                fmt(v3.similarityQuality()),
                fmt(v3.p75OverP25()), fmt(v3.stabilityQuality()),
                fmt(v3.completenessQuality()),
                fmt(v3.localEvidenceQuality()),
                fmt(r.legacyScore()), fmt(r.v11Score()), fmt(r.v12Score()),
                r.response().sampleCount(), r.response().fallbackLevel()));
    }

    // ------------------------------------------------------------------
    // 6. 시군구(SAME_DISTRICT_TYPE_VENUE) 실제 검증 - legacy/v1.1/v1.2/v2/v3 동시 표시
    // ------------------------------------------------------------------

    private void runDistrictAnalysis(List<FestivalRecord> confirmedPool, CalibrationBounds v2Bounds) {
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

        runDistrictCase("표본 충분", richest.getKey(), richest.getValue(), v2Bounds);
        runDistrictCase("표본 중간", middle.getKey(), middle.getValue(), v2Bounds);
        runDistrictCase("표본 부족", poorest.getKey(), poorest.getValue(), v2Bounds);
    }

    private void runDistrictCase(String label, DistrictCombo combo, long districtCandidateCount, CalibrationBounds v2Bounds) {
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

        CaseResult caseResult = new CaseResult(combo.region(), combo.type(), combo.venue(), DISTRICT_TEST_DURATION, response);
        V2Result v2 = calibrateCase(caseResult, v2Bounds);
        line("  confidence(v2)    =%.2f (threshold 미적용) [raw->calibrated] sample %s->%s, sim %s->%s, stab %s->%s, comp %s->%s".formatted(
                v2.score(),
                fmt(b.effectiveSampleScore()), fmt(v2.calibratedSample()),
                fmt(b.similarityScore()), fmt(v2.calibratedSimilarity()),
                fmt(b.stabilityScoreV11()), fmt(v2.calibratedStability()),
                fmt(b.completenessScore()), fmt(v2.calibratedCompleteness())));

        // district가 실제로 주어진 조회이므로 districtProvided=true(동일 시군구 + 0.5*동일 광역지역).
        V3Result v3 = computeV3(caseResult, true);
        line(("  confidence(v3)    =%.2f (threshold 미적용) [sampleQuality=%s, similarityQuality=%s, "
                + "P75/P25=%s stabilityQuality=%s, completenessQuality=%s, localEvidenceQuality=%s]").formatted(
                v3.score(),
                fmt(v3.sampleQuality()), fmt(v3.similarityQuality()),
                fmt(v3.p75OverP25()), fmt(v3.stabilityQuality()),
                fmt(v3.completenessQuality()), fmt(v3.localEvidenceQuality())));

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