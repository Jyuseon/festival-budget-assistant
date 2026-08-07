package com.festival.budgetassist.estimate;

import org.springframework.stereotype.Component;

/**
 * 안내서 10장 신뢰도 계산. 통계적 신뢰구간이 아니라 "이 추정치를 얼마나 참고할 만한가"를
 * 나타내는 자체 지표다.
 */
@Component
class ConfidenceCalculator {

    private final AlgorithmConfig config;

    ConfidenceCalculator(AlgorithmConfig config) {
        this.config = config;
    }

    /**
     * @param sampleCount 최종 표본 수
     * @param weightedSimilarityAvg Σ(similarity*weight)/Σweight, 0~1
     * @param p25 가중 25백분위 예산
     * @param p50 가중 중앙값 예산
     * @param p75 가중 75백분위 예산
     * @param completenessScore 개최기간 값을 가진 표본의 가중 비율, 0~1
     */
    ConfidenceResult calculate(int sampleCount, double weightedSimilarityAvg, double p25, double p50, double p75, double completenessScore) {
        double sampleScore = Math.min(sampleCount / config.getConfidenceSampleScoreDivisor(), 1.0);

        double cap = config.getConfidenceDispersionCap();
        double dispersionRatio = Math.min((p75 - p25) / Math.max(p50, 1), cap) / cap;
        double stabilityScore = 1 - dispersionRatio;

        double confidenceRatio = sampleScore * config.getConfidenceSampleWeight()
                + weightedSimilarityAvg * config.getConfidenceSimilarityWeight()
                + stabilityScore * config.getConfidenceStabilityWeight()
                + completenessScore * config.getConfidenceCompletenessWeight();

        double score = confidenceRatio * 100;

        if (sampleCount < config.getConfidenceLowSampleCap()) {
            score = Math.min(score, config.getConfidenceLowSampleCapScore());
        }

        if (sampleCount < config.getConfidenceInsufficientSampleThreshold()) {
            return new ConfidenceResult(score, "LOW", "데이터 부족", sampleScore, weightedSimilarityAvg, stabilityScore, completenessScore);
        }
        if (score >= config.getConfidenceHighThreshold()) {
            return new ConfidenceResult(score, "HIGH", "높음", sampleScore, weightedSimilarityAvg, stabilityScore, completenessScore);
        }
        if (score >= config.getConfidenceMediumThreshold()) {
            return new ConfidenceResult(score, "MEDIUM", "보통", sampleScore, weightedSimilarityAvg, stabilityScore, completenessScore);
        }
        return new ConfidenceResult(score, "LOW", "낮음", sampleScore, weightedSimilarityAvg, stabilityScore, completenessScore);
    }

    /**
     * confidence v1.1 후보 공식 (비교 전용, 아직 기본값 아님). legacy와 같은 입력 재료를
     * 최대한 재사용하되, sampleScore는 Effective Sample Size 기반으로, stabilityScore는
     * cap 없는 로그 기반으로, scopeScore는 fallback 단계를 반영해서 새로 계산한다.
     * HIGH/MEDIUM/LOW 등급 threshold(80/60)와 저표본 캡 규칙은 legacy와 동일하게 적용한다
     * (등급 기준 자체는 이번 비교 대상이 아님).
     *
     * @param rawSampleCount 최종 표본 수(가중치 무관 실제 건수) - 저표본 캡/데이터부족 판정에 사용
     * @param weights 최종 표본의 weight 배열 - effectiveSampleSize 계산에 사용
     * @param weightedSimilarityAvg legacy와 동일한 값 재사용
     * @param p25 가중 25백분위 예산
     * @param p75 가중 75백분위 예산
     * @param completenessScore legacy와 동일한 값 재사용
     * @param fallbackLevel 이번 추정에 실제로 사용된 fallback 단계
     */
    ConfidenceV11Result calculateV11(int rawSampleCount, double[] weights, double weightedSimilarityAvg,
                                      double p25, double p75, double completenessScore, FallbackLevel fallbackLevel) {
        double effectiveSampleSize = WeightedStatistics.effectiveSampleSize(weights);
        double effectiveSampleScore = Math.min(effectiveSampleSize / config.getConfidenceV11EffectiveSampleDivisor(), 1.0);

        double stabilityScore = stabilityScoreV11(p25, p75);
        double scopeScore = scopeScoreFor(fallbackLevel);

        double confidenceRatio = effectiveSampleScore * config.getConfidenceV11SampleWeight()
                + weightedSimilarityAvg * config.getConfidenceV11SimilarityWeight()
                + stabilityScore * config.getConfidenceV11StabilityWeight()
                + completenessScore * config.getConfidenceV11CompletenessWeight()
                + scopeScore * config.getConfidenceV11ScopeWeight();

        double score = confidenceRatio * 100;

        if (rawSampleCount < config.getConfidenceLowSampleCap()) {
            score = Math.min(score, config.getConfidenceLowSampleCapScore());
        }

        if (rawSampleCount < config.getConfidenceInsufficientSampleThreshold()) {
            return new ConfidenceV11Result(score, "LOW", "데이터 부족", effectiveSampleSize, effectiveSampleScore, stabilityScore, scopeScore);
        }
        if (score >= config.getConfidenceHighThreshold()) {
            return new ConfidenceV11Result(score, "HIGH", "높음", effectiveSampleSize, effectiveSampleScore, stabilityScore, scopeScore);
        }
        if (score >= config.getConfidenceMediumThreshold()) {
            return new ConfidenceV11Result(score, "MEDIUM", "보통", effectiveSampleSize, effectiveSampleScore, stabilityScore, scopeScore);
        }
        return new ConfidenceV11Result(score, "LOW", "낮음", effectiveSampleSize, effectiveSampleScore, stabilityScore, scopeScore);
    }

    /**
     * spread = ln(P75/P25), stabilityScore = 1/(1+spread). cap 없이 부드럽게 0에 수렴한다.
     * P25/P75가 0 이하이거나 비율을 계산할 수 없으면(방어적 케이스) 0을 반환한다 -
     * winsorize된 예산은 항상 양수라 실제로는 거의 발생하지 않는다.
     */
    private double stabilityScoreV11(double p25, double p75) {
        if (p25 <= 0 || p75 <= 0) {
            return 0.0;
        }
        double ratio = p75 / p25;
        if (!Double.isFinite(ratio) || ratio <= 0) {
            return 0.0;
        }
        double spread = Math.log(ratio);
        if (spread < 0) {
            // p75 < p25는 정상적으로는 나오지 않지만(가중 분위수 정의상 p75>=p25),
            // 방어적으로 음수 spread를 0으로 잘라 stabilityScore가 1을 넘지 않게 한다.
            spread = 0;
        }
        return 1.0 / (1.0 + spread);
    }

    private double scopeScoreFor(FallbackLevel level) {
        return switch (level) {
            case SAME_DISTRICT_TYPE_VENUE -> config.getScopeScoreSameDistrictTypeVenue();
            case SAME_REGION_TYPE_VENUE -> config.getScopeScoreSameRegionTypeVenue();
            case NATIONWIDE_TYPE_VENUE -> config.getScopeScoreNationwideTypeVenue();
            case SAME_REGION_TYPE -> config.getScopeScoreSameRegionType();
            case NATIONWIDE_TYPE -> config.getScopeScoreNationwideType();
            case GLOBAL_SIMILARITY -> config.getScopeScoreGlobalSimilarity();
        };
    }

    /**
     * confidence v1.2 후보 (비교 전용, 아직 기본값 아님). v1.1과 정확히 같은
     * effectiveSampleScore/stabilityScore(로그기반)/completenessScore를 재사용하되,
     * scopeScore 항을 완전히 제외하고 남은 4개 요소만으로 재가중한다(가중치 합 1.00).
     * "fallback 범위가 넓다는 이유만으로 이미 안정적인 데이터가 이중으로 감점되는" 문제를
     * 확인한 뒤, scope를 confidence 점수에서 빼고 설명용 정보로만 남기기 위한 후보다.
     *
     * @param rawSampleCount 저표본 캡/데이터부족 판정용 (legacy·v1.1과 동일 기준)
     * @param effectiveSampleScore v1.1에서 이미 계산한 값을 그대로 재사용(중복 계산 방지)
     * @param weightedSimilarityAvg legacy·v1.1과 동일한 값
     * @param stabilityScoreV11 v1.1에서 이미 계산한 로그 기반 stabilityScore를 그대로 재사용
     * @param completenessScore legacy·v1.1과 동일한 값
     */
    ConfidenceV12Result calculateV12(int rawSampleCount, double effectiveSampleScore, double weightedSimilarityAvg,
                                      double stabilityScoreV11, double completenessScore) {
        double confidenceRatio = effectiveSampleScore * config.getConfidenceV12SampleWeight()
                + weightedSimilarityAvg * config.getConfidenceV12SimilarityWeight()
                + stabilityScoreV11 * config.getConfidenceV12StabilityWeight()
                + completenessScore * config.getConfidenceV12CompletenessWeight();

        double score = confidenceRatio * 100;

        if (rawSampleCount < config.getConfidenceLowSampleCap()) {
            score = Math.min(score, config.getConfidenceLowSampleCapScore());
        }

        if (rawSampleCount < config.getConfidenceInsufficientSampleThreshold()) {
            return new ConfidenceV12Result(score, "LOW", "데이터 부족");
        }
        if (score >= config.getConfidenceHighThreshold()) {
            return new ConfidenceV12Result(score, "HIGH", "높음");
        }
        if (score >= config.getConfidenceMediumThreshold()) {
            return new ConfidenceV12Result(score, "MEDIUM", "보통");
        }
        return new ConfidenceV12Result(score, "LOW", "낮음");
    }
}