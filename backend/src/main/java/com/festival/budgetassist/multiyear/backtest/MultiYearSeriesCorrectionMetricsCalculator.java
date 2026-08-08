package com.festival.budgetassist.multiyear.backtest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/** S0/S1/S2 비교 전용 지표 계산 (지시사항 7~11절). {@link MultiYearBacktestMetricsCalculator}를 기본 지표 계산에 재사용한다. */
@Component
class MultiYearSeriesCorrectionMetricsCalculator {

    private static final long[] BUDGET_SIZE_BUCKET_UPPER_BOUNDS_KRW = {
            100_000_000L, 300_000_000L, 1_000_000_000L, 3_000_000_000L
    };
    private static final String[] BUDGET_SIZE_BUCKET_LABELS = {
            "<= 100M", "100M~300M", "300M~1B", "1B~3B", "> 3B"
    };

    private final MultiYearBacktestMetricsCalculator baseCalculator;
    private final MultiYearDataQualityV3Calculator v3Calculator;

    MultiYearSeriesCorrectionMetricsCalculator(MultiYearBacktestMetricsCalculator baseCalculator,
                                                MultiYearDataQualityV3Calculator v3Calculator) {
        this.baseCalculator = baseCalculator;
        this.v3Calculator = v3Calculator;
    }

    MultiYearSeriesCorrectionMetrics summarize(List<MultiYearSeriesCorrectionPrediction> predictions) {
        List<MultiYearBacktestPrediction> asBase = predictions.stream()
                .map(MultiYearSeriesCorrectionBacktestService::toBacktestPrediction).toList();
        MultiYearBacktestMetricsSummary base = baseCalculator.summarize(asBase);

        double[] signedLogErrors = validDoubles(predictions, MultiYearSeriesCorrectionPrediction::signedLogError);
        double medianSignedLogError = signedLogErrors.length > 0 ? MultiYearBacktestMath.median(signedLogErrors) : Double.NaN;
        double medianRatio = Double.isFinite(medianSignedLogError) ? Math.exp(medianSignedLogError) : Double.NaN;

        double[] rangeWidthRatios = predictions.stream()
                .filter(p -> p.estimatedBudget() > 0)
                .mapToDouble(p -> (double) (p.p75() - p.p25()) / p.estimatedBudget())
                .toArray();
        double medianRangeWidthRatio = rangeWidthRatios.length > 0 ? MultiYearBacktestMath.median(rangeWidthRatios) : Double.NaN;

        return new MultiYearSeriesCorrectionMetrics(base, medianSignedLogError, medianRatio, medianRangeWidthRatio);
    }

    List<MultiYearSeriesCorrectionBudgetBucket> budgetSizeBreakdown(List<MultiYearSeriesCorrectionPrediction> predictions) {
        Map<String, List<MultiYearSeriesCorrectionPrediction>> byBucket = new LinkedHashMap<>();
        for (String label : BUDGET_SIZE_BUCKET_LABELS) {
            byBucket.put(label, new ArrayList<>());
        }
        for (MultiYearSeriesCorrectionPrediction p : predictions) {
            byBucket.get(bucketLabel(p.actualBudget())).add(p);
        }

        List<MultiYearSeriesCorrectionBudgetBucket> result = new ArrayList<>();
        for (String label : BUDGET_SIZE_BUCKET_LABELS) {
            List<MultiYearSeriesCorrectionPrediction> group = byBucket.get(label);
            double[] ape = validDoubles(group, MultiYearSeriesCorrectionPrediction::absolutePercentageError);
            double[] ale = validDoubles(group, MultiYearSeriesCorrectionPrediction::absoluteLogError);
            double[] sle = validDoubles(group, MultiYearSeriesCorrectionPrediction::signedLogError);
            result.add(new MultiYearSeriesCorrectionBudgetBucket(label, group.size(),
                    ape.length > 0 ? MultiYearBacktestMath.median(ape) : Double.NaN,
                    ale.length > 0 ? MultiYearBacktestMath.median(ale) : Double.NaN,
                    sle.length > 0 ? MultiYearBacktestMath.median(sle) : Double.NaN,
                    sle.length > 0 ? Math.exp(MultiYearBacktestMath.median(sle)) : Double.NaN));
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

    List<MultiYearSeriesLengthBucket> seriesLengthBreakdown(List<MultiYearSeriesCorrectionPrediction> predictions) {
        String[] order = {"과거 series 없음", "1~2년", "3~5년", "6년 이상"};
        Map<String, List<MultiYearSeriesCorrectionPrediction>> byBucket = new LinkedHashMap<>();
        for (String label : order) {
            byBucket.put(label, new ArrayList<>());
        }
        for (MultiYearSeriesCorrectionPrediction p : predictions) {
            byBucket.computeIfAbsent(p.pastSeriesLengthBucket(), k -> new ArrayList<>()).add(p);
        }
        List<MultiYearSeriesLengthBucket> result = new ArrayList<>();
        for (String label : order) {
            List<MultiYearSeriesCorrectionPrediction> group = byBucket.get(label);
            double[] ape = validDoubles(group, MultiYearSeriesCorrectionPrediction::absolutePercentageError);
            double[] ale = validDoubles(group, MultiYearSeriesCorrectionPrediction::absoluteLogError);
            result.add(new MultiYearSeriesLengthBucket(label, group.size(),
                    ape.length > 0 ? MultiYearBacktestMath.median(ape) : Double.NaN,
                    ale.length > 0 ? MultiYearBacktestMath.median(ale) : Double.NaN));
        }
        return result;
    }

    List<MultiYearRangeCoverageBucket> rangeCoverageBreakdown(List<MultiYearSeriesCorrectionPrediction> predictions) {
        Map<String, List<MultiYearSeriesCorrectionPrediction>> byBucket = new LinkedHashMap<>();
        for (String label : BUDGET_SIZE_BUCKET_LABELS) {
            byBucket.put(label, new ArrayList<>());
        }
        for (MultiYearSeriesCorrectionPrediction p : predictions) {
            byBucket.get(bucketLabel(p.actualBudget())).add(p);
        }
        List<MultiYearRangeCoverageBucket> result = new ArrayList<>();
        for (String label : BUDGET_SIZE_BUCKET_LABELS) {
            List<MultiYearSeriesCorrectionPrediction> group = byBucket.get(label);
            long inRange = group.stream().filter(MultiYearSeriesCorrectionPrediction::typicalRangeCoverage).count();
            double coverage = group.isEmpty() ? Double.NaN : (double) inRange / group.size();
            double[] widthRatios = group.stream().filter(p -> p.estimatedBudget() > 0)
                    .mapToDouble(p -> (double) (p.p75() - p.p25()) / p.estimatedBudget()).toArray();
            double medianWidth = widthRatios.length > 0 ? MultiYearBacktestMath.median(widthRatios) : Double.NaN;
            result.add(new MultiYearRangeCoverageBucket(label, group.size(), coverage, medianWidth));
        }
        return result;
    }

    double v3ErrorSpearmanCorrelation(List<MultiYearSeriesCorrectionPrediction> predictions) {
        List<MultiYearSeriesCorrectionPrediction> valid = predictions.stream()
                .filter(p -> Double.isFinite(p.absoluteLogError())).toList();
        if (valid.size() < 3) {
            return Double.NaN;
        }
        double[] v3Scores = valid.stream().mapToDouble(MultiYearSeriesCorrectionPrediction::dataQualityV3).toArray();
        double[] logErrors = valid.stream().mapToDouble(MultiYearSeriesCorrectionPrediction::absoluteLogError).toArray();
        return v3Calculator.spearmanCorrelation(v3Scores, logErrors);
    }

    /**
     * S0 대비 S1/S2에서 estimatedBudget이 가장 크게 움직인 target Top N (지시사항 10절).
     * recordId로 세 mode의 예측을 조인한다 - 셋 다 candidate selection이 동일하므로(5절) 같은
     * recordId는 반드시 세 mode 모두에 존재하거나 모두 없다.
     */
    List<MultiYearSeriesCorrectionMover> topMovers(List<MultiYearSeriesCorrectionPrediction> s0,
                                                     List<MultiYearSeriesCorrectionPrediction> s1,
                                                     List<MultiYearSeriesCorrectionPrediction> s2, int topN) {
        Map<Long, MultiYearSeriesCorrectionPrediction> s0ById = indexByRecordId(s0);
        Map<Long, MultiYearSeriesCorrectionPrediction> s1ById = indexByRecordId(s1);
        Map<Long, MultiYearSeriesCorrectionPrediction> s2ById = indexByRecordId(s2);

        List<MultiYearSeriesCorrectionMover> movers = new ArrayList<>();
        for (Map.Entry<Long, MultiYearSeriesCorrectionPrediction> entry : s0ById.entrySet()) {
            MultiYearSeriesCorrectionPrediction p0 = entry.getValue();
            MultiYearSeriesCorrectionPrediction p1 = s1ById.get(entry.getKey());
            MultiYearSeriesCorrectionPrediction p2 = s2ById.get(entry.getKey());
            if (p1 == null || p2 == null) {
                continue;
            }
            String verdict = verdict(p0, p1, p2);
            movers.add(new MultiYearSeriesCorrectionMover(p0.targetYear(), p0.festivalName(), p0.region(), p0.district(),
                    p0.actualBudget(), p0.estimatedBudget(), p1.estimatedBudget(), p2.estimatedBudget(),
                    p0.sampleCount(), p0.distinctSeriesCountInSample(), p0.mostRepeatedSeriesLabel(),
                    p0.mostRepeatedSeriesRecordCount(), p0.absolutePercentageError(), p1.absolutePercentageError(),
                    p2.absolutePercentageError(), verdict));
        }

        return movers.stream()
                .sorted(Comparator.comparingDouble(this::movementMagnitude).reversed())
                .limit(topN)
                .toList();
    }

    /** S0 대비 S1/S2 중 더 크게 움직인 쪽의 log-scale 이동폭 - Top N 정렬 기준(mover 레코드 필드에서 직접 계산, 외부 상태 없음). */
    private double movementMagnitude(MultiYearSeriesCorrectionMover m) {
        double d1 = Math.abs(Math.log(Math.max(m.s1Estimated(), 1)) - Math.log(Math.max(m.s0Estimated(), 1)));
        double d2 = Math.abs(Math.log(Math.max(m.s2Estimated(), 1)) - Math.log(Math.max(m.s0Estimated(), 1)));
        return Math.max(d1, d2);
    }

    private String verdict(MultiYearSeriesCorrectionPrediction p0, MultiYearSeriesCorrectionPrediction p1,
                            MultiYearSeriesCorrectionPrediction p2) {
        boolean s1Better = p1.absolutePercentageError() < p0.absolutePercentageError();
        boolean s2Better = p2.absolutePercentageError() < p0.absolutePercentageError();
        if (s1Better && s2Better) {
            return "S1/S2 모두 개선";
        }
        if (!s1Better && !s2Better) {
            return "S1/S2 모두 악화";
        }
        return "혼재(S1/S2 방향 다름)";
    }

    private Map<Long, MultiYearSeriesCorrectionPrediction> indexByRecordId(List<MultiYearSeriesCorrectionPrediction> predictions) {
        Map<Long, MultiYearSeriesCorrectionPrediction> map = new LinkedHashMap<>();
        for (MultiYearSeriesCorrectionPrediction p : predictions) {
            map.put(p.recordId(), p);
        }
        return map;
    }

    private double[] validDoubles(List<MultiYearSeriesCorrectionPrediction> predictions,
                                   java.util.function.ToDoubleFunction<MultiYearSeriesCorrectionPrediction> extractor) {
        return predictions.stream().mapToDouble(extractor).filter(Double::isFinite).toArray();
    }
}