package com.festival.budgetassist.estimate;

import org.springframework.stereotype.Component;

/**
 * 안내서 9.5절 - 후보 축제의 실제 예산을, 요청한 개최 일수 기준으로 보정한다.
 * 개최기간 데이터가 없는 후보는 보정하지 않고 원 예산을 그대로 쓴다(대신
 * {@link SimilarityCalculator#durationScore}가 고정 낮은 점수를 줘서 가중치가 줄어든다).
 */
@Component
class DurationAdjuster {

    private final AlgorithmConfig config;

    DurationAdjuster(AlgorithmConfig config) {
        this.config = config;
    }

    /**
     * @param sourceBudgetKrw 후보의 실제 예산(원)
     * @param sourceDurationDays 후보의 실제 개최일수, null이면 보정하지 않음
     * @param targetDurationDays 사용자가 입력한 개최일수
     */
    double adjust(long sourceBudgetKrw, Integer sourceDurationDays, int targetDurationDays) {
        if (sourceDurationDays == null || sourceDurationDays <= 0) {
            return sourceBudgetKrw;
        }
        double rawRatio = targetDurationDays / (double) sourceDurationDays;
        double clampedRatio = WeightedStatistics.clip(rawRatio, config.getDurationRatioClampMin(), config.getDurationRatioClampMax());
        return sourceBudgetKrw * Math.pow(clampedRatio, config.getDurationElasticity());
    }
}