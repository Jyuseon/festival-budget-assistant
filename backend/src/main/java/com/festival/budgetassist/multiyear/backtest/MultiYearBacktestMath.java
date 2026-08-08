package com.festival.budgetassist.multiyear.backtest;

import java.util.Arrays;

/**
 * baseline backtest 전용 순수 통계 유틸리티.
 *
 * <p>{@code com.festival.budgetassist.estimate.WeightedStatistics}/{@code DurationAdjuster}/
 * {@code ConfidenceCalculator}와 완전히 동일한 공식을 그대로 옮겨 적은 것이다 - production
 * 클래스들이 전부 package-private이라 다른 패키지({@code multiyear.backtest})에서 import할 수
 * 없고(그리고 애초에 이 클래스들을 건드리거나 다른 패키지로 노출시키는 것 자체가 "production
 * BudgetEstimator/CandidateSelector/similarity/P60/confidence는 변경 금지"에 저촉될 수 있어),
 * 대신 여기 순수 함수로 다시 옮겨 적었다. 가중치/threshold 값은 절대 여기 하드코딩하지 않고
 * 항상 {@link com.festival.budgetassist.estimate.AlgorithmConfig}(읽기 전용 공개 Bean)에서
 * 가져와 production과 완전히 동일한 값을 쓴다 - 공식 포팅 + 값 재사용으로 "현재 알고리즘 구조를
 * 최대한 그대로 다년도 데이터에 적용"한다.</p>
 *
 * <p>이 클래스는 production 코드가 절대 참조하지 않는다(단방향) - production estimate 패키지의
 * 어떤 클래스도 이 클래스를 import하지 않는다.</p>
 */
final class MultiYearBacktestMath {

    private MultiYearBacktestMath() {
    }

    static double weightedMean(double[] values, double[] weights) {
        requireSameLength(values, weights);
        double sumValueWeight = 0;
        double sumWeight = 0;
        for (int i = 0; i < values.length; i++) {
            sumValueWeight += values[i] * weights[i];
            sumWeight += weights[i];
        }
        return sumWeight == 0 ? 0 : sumValueWeight / sumWeight;
    }

    /** values는 전부 0보다 커야 한다(log 계산). */
    static double weightedGeometricMean(double[] values, double[] weights) {
        requireSameLength(values, weights);
        double sumLogWeight = 0;
        double sumWeight = 0;
        for (int i = 0; i < values.length; i++) {
            sumLogWeight += Math.log(values[i]) * weights[i];
            sumWeight += weights[i];
        }
        return sumWeight == 0 ? 0 : Math.exp(sumLogWeight / sumWeight);
    }

    /** 가중 백분위수 (Hazen 방식) - production WeightedStatistics.weightedQuantile과 동일 알고리즘. */
    static double weightedQuantile(double[] values, double[] weights, double q) {
        requireSameLength(values, weights);
        int n = values.length;
        if (n == 0) {
            return 0;
        }
        if (n == 1) {
            return values[0];
        }

        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Double.compare(values[a], values[b]));

        double totalWeight = 0;
        for (double w : weights) {
            totalWeight += w;
        }
        if (totalWeight == 0) {
            return median(values);
        }

        double[] sortedValues = new double[n];
        double[] cumFraction = new double[n];
        double running = 0;
        for (int i = 0; i < n; i++) {
            int idx = order[i];
            sortedValues[i] = values[idx];
            running += weights[idx];
            cumFraction[i] = (running - 0.5 * weights[idx]) / totalWeight;
        }

        if (q <= cumFraction[0]) {
            return sortedValues[0];
        }
        if (q >= cumFraction[n - 1]) {
            return sortedValues[n - 1];
        }
        for (int i = 0; i < n - 1; i++) {
            if (q >= cumFraction[i] && q <= cumFraction[i + 1]) {
                double span = cumFraction[i + 1] - cumFraction[i];
                double fraction = span == 0 ? 0 : (q - cumFraction[i]) / span;
                return sortedValues[i] + (sortedValues[i + 1] - sortedValues[i]) * fraction;
            }
        }
        return sortedValues[n - 1];
    }

    /** 가중치 없는 선형보간 백분위수 - winsorize 기준값(모집단 P5/P95 등) 계산에 쓴다. */
    static double quantile(double[] values, double q) {
        if (values.length == 0) {
            return 0;
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        if (sorted.length == 1) {
            return sorted[0];
        }
        double index = q * (sorted.length - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) {
            return sorted[lower];
        }
        double fraction = index - lower;
        return sorted[lower] + (sorted[upper] - sorted[lower]) * fraction;
    }

    static double median(double[] values) {
        return quantile(values, 0.5);
    }

    static double clip(double value, double lowerBound, double upperBound) {
        return Math.max(lowerBound, Math.min(upperBound, value));
    }

    /** Effective Sample Size (Kish's ESS): (Sum w)^2 / Sum(w^2). */
    static double effectiveSampleSize(double[] weights) {
        double sumWeight = 0;
        double sumWeightSquared = 0;
        for (double w : weights) {
            sumWeight += w;
            sumWeightSquared += w * w;
        }
        return sumWeightSquared == 0 ? 0 : (sumWeight * sumWeight) / sumWeightSquared;
    }

    private static void requireSameLength(double[] values, double[] weights) {
        if (values.length != weights.length) {
            throw new IllegalArgumentException("values와 weights의 길이가 다릅니다: %d vs %d".formatted(values.length, weights.length));
        }
    }
}