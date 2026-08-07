package com.festival.budgetassist.estimate;

import java.util.Arrays;

/**
 * 순수 함수로만 구성된 통계 유틸리티 (안내서 9.6/9.8/13장). 외부 통계 라이브러리에
 * 의존하지 않는다. 전부 정적 메서드라 단위 테스트가 쉽다.
 */
final class WeightedStatistics {

    private WeightedStatistics() {
    }

    static double weightedMean(double[] values, double[] weights) {
        requireSameLength(values, weights);
        double sumValueWeight = 0;
        double sumWeight = 0;
        for (int i = 0; i < values.length; i++) {
            sumValueWeight += values[i] * weights[i];
            sumWeight += weights[i];
        }
        if (sumWeight == 0) {
            return 0;
        }
        return sumValueWeight / sumWeight;
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
        if (sumWeight == 0) {
            return 0;
        }
        return Math.exp(sumLogWeight / sumWeight);
    }

    /**
     * 가중 백분위수 (Hazen 방식 - 각 표본에 자기 가중치의 절반씩을 좌우로 걸치는 중점법).
     * values/weights는 같은 순서로 대응해야 하며, 내부에서 값 기준 정렬한다.
     */
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

    /** 가중치 없는(모두 동일 가중) 선형 보간 백분위수. Winsorization 기준값(모집단 P5/P95) 계산에 쓴다. */
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

    /** value를 [lowerBound, upperBound] 범위로 잘라낸다. 원본 배열은 바꾸지 않는다. */
    static double clip(double value, double lowerBound, double upperBound) {
        return Math.max(lowerBound, Math.min(upperBound, value));
    }

    /**
     * Effective Sample Size (Kish's ESS): (Σw)² / Σ(w²).
     * 가중치가 전부 같으면 실제 표본 수와 같아지고, 소수의 가중치가 크게 쏠려 있으면
     * 실제 표본 수보다 작게 나와 "사실상 몇 건에 의존하는 추정인가"를 나타낸다.
     */
    static double effectiveSampleSize(double[] weights) {
        double sumWeight = 0;
        double sumWeightSquared = 0;
        for (double w : weights) {
            sumWeight += w;
            sumWeightSquared += w * w;
        }
        if (sumWeightSquared == 0) {
            return 0;
        }
        return (sumWeight * sumWeight) / sumWeightSquared;
    }

    private static void requireSameLength(double[] values, double[] weights) {
        if (values.length != weights.length) {
            throw new IllegalArgumentException("values와 weights의 길이가 다릅니다: %d vs %d".formatted(values.length, weights.length));
        }
    }
}