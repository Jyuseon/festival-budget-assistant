package com.festival.budgetassist.multiyear.backtest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/** {@link MultiYearBacktestPrediction} 목록에서 정확도 지표/구간별 breakdown을 계산한다 (지시사항 7~11절). */
@Component
class MultiYearBacktestMetricsCalculator {

    /** 이 미만이면 breakdown 그룹을 "표본 부족 - 참고용"으로 별도 표시한다 (10절). */
    static final int SMALL_SAMPLE_THRESHOLD = 10;

    private static final long[] BUDGET_SIZE_BUCKET_UPPER_BOUNDS_KRW = {
            100_000_000L, 300_000_000L, 1_000_000_000L, 3_000_000_000L
    };
    private static final String[] BUDGET_SIZE_BUCKET_LABELS = {
            "<= 100M", "100M~300M", "300M~1B", "1B~3B", "> 3B"
    };

    MultiYearBacktestMetricsSummary summarize(List<MultiYearBacktestPrediction> predictions) {
        int n = predictions.size();
        if (n == 0) {
            return new MultiYearBacktestMetricsSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        double[] absoluteErrors = predictions.stream().mapToDouble(MultiYearBacktestPrediction::absoluteError).toArray();
        double[] ape = validDoubles(predictions, MultiYearBacktestPrediction::absolutePercentageError);
        double[] ale = validDoubles(predictions, MultiYearBacktestPrediction::absoluteLogError);

        double mae = Arrays.stream(absoluteErrors).average().orElse(0);
        double medianAE = MultiYearBacktestMath.median(absoluteErrors);
        double medianAPE = ape.length > 0 ? MultiYearBacktestMath.median(ape) : Double.NaN;
        double p75Ape = ape.length > 0 ? MultiYearBacktestMath.quantile(ape, 0.75) : Double.NaN;
        double p90Ape = ape.length > 0 ? MultiYearBacktestMath.quantile(ape, 0.90) : Double.NaN;
        double medianAle = ale.length > 0 ? MultiYearBacktestMath.median(ale) : Double.NaN;

        long within25 = predictions.stream().filter(p -> isValid(p.absolutePercentageError()) && p.absolutePercentageError() <= 0.25).count();
        long within50 = predictions.stream().filter(p -> isValid(p.absolutePercentageError()) && p.absolutePercentageError() <= 0.50).count();
        long within2x = predictions.stream().filter(this::isWithin0_5xTo2x).count();
        long inTypicalRange = predictions.stream().filter(MultiYearBacktestPrediction::typicalRangeCoverage).count();

        return new MultiYearBacktestMetricsSummary(n, mae, medianAE, medianAPE, p75Ape, p90Ape, medianAle,
                (double) within25 / n, (double) within50 / n, (double) within2x / n, (double) inTypicalRange / n);
    }

    private boolean isWithin0_5xTo2x(MultiYearBacktestPrediction p) {
        if (p.actualBudget() <= 0) {
            return false;
        }
        double ratio = (double) p.estimatedBudget() / p.actualBudget();
        return ratio >= 0.5 && ratio <= 2.0;
    }

    private double[] validDoubles(List<MultiYearBacktestPrediction> predictions,
                                   java.util.function.ToDoubleFunction<MultiYearBacktestPrediction> extractor) {
        return predictions.stream().mapToDouble(extractor)
                .filter(this::isValid)
                .toArray();
    }

    private boolean isValid(double v) {
        return Double.isFinite(v);
    }

    // ------------------------------------------------------------------
    // 9절: 예산 규모별 breakdown
    // ------------------------------------------------------------------

    List<MultiYearBudgetSizeBucketMetrics> budgetSizeBreakdown(List<MultiYearBacktestPrediction> predictions) {
        Map<String, List<MultiYearBacktestPrediction>> byBucket = new LinkedHashMap<>();
        for (String label : BUDGET_SIZE_BUCKET_LABELS) {
            byBucket.put(label, new ArrayList<>());
        }
        for (MultiYearBacktestPrediction p : predictions) {
            byBucket.get(bucketLabel(p.actualBudget())).add(p);
        }

        List<MultiYearBudgetSizeBucketMetrics> result = new ArrayList<>();
        for (String label : BUDGET_SIZE_BUCKET_LABELS) {
            List<MultiYearBacktestPrediction> group = byBucket.get(label);
            double[] ape = validDoubles(group, MultiYearBacktestPrediction::absolutePercentageError);
            double medianApe = ape.length > 0 ? MultiYearBacktestMath.median(ape) : Double.NaN;
            result.add(new MultiYearBudgetSizeBucketMetrics(label, group.size(), medianApe));
        }
        return result;
    }

    private String bucketLabel(long actualBudgetKrw) {
        for (int i = 0; i < BUDGET_SIZE_BUCKET_UPPER_BOUNDS_KRW.length; i++) {
            if (actualBudgetKrw <= BUDGET_SIZE_BUCKET_UPPER_BOUNDS_KRW[i]) {
                return BUDGET_SIZE_BUCKET_LABELS[i];
            }
        }
        return BUDGET_SIZE_BUCKET_LABELS[BUDGET_SIZE_BUCKET_LABELS.length - 1];
    }

    // ------------------------------------------------------------------
    // 10절: festivalType / region별 breakdown
    // ------------------------------------------------------------------

    List<MultiYearTypeMetrics> typeBreakdown(List<MultiYearBacktestPrediction> predictions) {
        Map<String, List<MultiYearBacktestPrediction>> byType = new LinkedHashMap<>();
        for (MultiYearBacktestPrediction p : predictions) {
            byType.computeIfAbsent(primaryType(p.festivalType()), k -> new ArrayList<>()).add(p);
        }
        List<MultiYearTypeMetrics> result = new ArrayList<>();
        byType.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    List<MultiYearBacktestPrediction> group = e.getValue();
                    double[] ape = validDoubles(group, MultiYearBacktestPrediction::absolutePercentageError);
                    double[] ale = validDoubles(group, MultiYearBacktestPrediction::absoluteLogError);
                    result.add(new MultiYearTypeMetrics(e.getKey(), group.size(),
                            ape.length > 0 ? MultiYearBacktestMath.median(ape) : Double.NaN,
                            ale.length > 0 ? MultiYearBacktestMath.median(ale) : Double.NaN,
                            group.size() < SMALL_SAMPLE_THRESHOLD));
                });
        return result;
    }

    List<MultiYearRegionMetrics> regionBreakdown(List<MultiYearBacktestPrediction> predictions) {
        Map<String, List<MultiYearBacktestPrediction>> byRegion = new LinkedHashMap<>();
        for (MultiYearBacktestPrediction p : predictions) {
            byRegion.computeIfAbsent(p.region() == null ? "UNKNOWN" : p.region(), k -> new ArrayList<>()).add(p);
        }
        List<MultiYearRegionMetrics> result = new ArrayList<>();
        byRegion.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    List<MultiYearBacktestPrediction> group = e.getValue();
                    double[] ape = validDoubles(group, MultiYearBacktestPrediction::absolutePercentageError);
                    double[] ale = validDoubles(group, MultiYearBacktestPrediction::absoluteLogError);
                    result.add(new MultiYearRegionMetrics(e.getKey(), group.size(),
                            ape.length > 0 ? MultiYearBacktestMath.median(ape) : Double.NaN,
                            ale.length > 0 ? MultiYearBacktestMath.median(ale) : Double.NaN,
                            group.size() < SMALL_SAMPLE_THRESHOLD));
                });
        return result;
    }

    /** festivalType 컬럼("A|B" 복합 가능)에서 breakdown용 대표 유형 하나만 뽑는다(첫 인식 가능한 토큰). */
    private String primaryType(String festivalTypeRaw) {
        if (festivalTypeRaw == null || festivalTypeRaw.isBlank()) {
            return "UNKNOWN";
        }
        for (String token : festivalTypeRaw.split("\\|")) {
            String trimmed = token.trim();
            try {
                return com.festival.budgetassist.festival.domain.FestivalType.valueOf(trimmed).name();
            } catch (IllegalArgumentException ignored) {
                // 다음 토큰 시도
            }
        }
        return "UNKNOWN";
    }

    // ------------------------------------------------------------------
    // 11절: v3 data-quality score 구간별 MdAPE + Spearman(v3, |log error|)
    // ------------------------------------------------------------------

    List<MultiYearV3BucketMetrics> v3Breakdown(List<MultiYearBacktestPrediction> predictions) {
        Map<Integer, List<MultiYearBacktestPrediction>> byDecile = new LinkedHashMap<>();
        for (int i = 0; i < 10; i++) {
            byDecile.put(i, new ArrayList<>());
        }
        for (MultiYearBacktestPrediction p : predictions) {
            int decile = Math.min(9, Math.max(0, (int) (p.dataQualityV3() / 10)));
            byDecile.get(decile).add(p);
        }
        List<MultiYearV3BucketMetrics> result = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            List<MultiYearBacktestPrediction> group = byDecile.get(i);
            double[] ape = validDoubles(group, MultiYearBacktestPrediction::absolutePercentageError);
            String label = "%d~%d".formatted(i * 10, (i + 1) * 10);
            result.add(new MultiYearV3BucketMetrics(label, group.size(), ape.length > 0 ? MultiYearBacktestMath.median(ape) : Double.NaN));
        }
        return result;
    }

    /** Spearman(v3 score, 절대 log 예측오차) - v3가 높을수록 error가 낮은 음의 상관이면 바람직한 방향. */
    double v3ErrorSpearmanCorrelation(List<MultiYearBacktestPrediction> predictions, MultiYearDataQualityV3Calculator v3Calc) {
        List<MultiYearBacktestPrediction> valid = predictions.stream()
                .filter(p -> Double.isFinite(p.absoluteLogError()))
                .toList();
        if (valid.size() < 3) {
            return Double.NaN;
        }
        double[] v3Scores = valid.stream().mapToDouble(MultiYearBacktestPrediction::dataQualityV3).toArray();
        double[] logErrors = valid.stream().mapToDouble(MultiYearBacktestPrediction::absoluteLogError).toArray();
        return v3Calc.spearmanCorrelation(v3Scores, logErrors);
    }

    // ------------------------------------------------------------------
    // 최대 실패 사례
    // ------------------------------------------------------------------

    List<MultiYearBacktestPrediction> topFailures(List<MultiYearBacktestPrediction> predictions, int topN) {
        return predictions.stream()
                .filter(p -> Double.isFinite(p.absoluteLogError()))
                .sorted(Comparator.comparingDouble(MultiYearBacktestPrediction::absoluteLogError).reversed())
                .limit(topN)
                .toList();
    }
}