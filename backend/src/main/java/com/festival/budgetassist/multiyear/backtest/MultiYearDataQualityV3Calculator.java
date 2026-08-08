package com.festival.budgetassist.multiyear.backtest;

import java.util.List;

import org.springframework.stereotype.Component;

import com.festival.budgetassist.estimate.FallbackLevel;

/**
 * production {@code ConfidenceAnalysisRunner}의 "confidence v3 후보" 공식을 그대로 옮겨 적은
 * 것이다(순수 분석 전용, production confidence에는 전혀 반영되지 않음 - 지시사항 11절: "production
 * confidence는 계속 legacy로 유지"). 공식/상수(divisor 15·2, 가중치 0.20/0.35/0.20/0.10/0.15)는
 * {@code ConfidenceAnalysisRunner}의 v3 후보와 완전히 동일하다 - 그 클래스가 production
 * {@code FestivalRecord}(2026 단일 연도) 전용이라 다년도 {@code MultiYearFestivalRecord}에는
 * 재사용할 수 없어 별도로 포팅했다.
 *
 * <p>HIGH/MEDIUM/LOW threshold는 아직 만들지 않는다(지시사항 11절) - score만 계산한다.</p>
 */
@Component
class MultiYearDataQualityV3Calculator {

    private static final double SAMPLE_QUALITY_DIVISOR = 15.0;
    private static final double STABILITY_QUALITY_DIVISOR = 2.0;

    private static final double SAMPLE_WEIGHT = 0.20;
    private static final double SIMILARITY_WEIGHT = 0.35;
    private static final double STABILITY_WEIGHT = 0.20;
    private static final double COMPLETENESS_WEIGHT = 0.10;
    private static final double LOCAL_EVIDENCE_WEIGHT = 0.15;

    /**
     * @param effectiveSampleSize Kish's ESS (가중치 기반)
     * @param weightedSimilarityAvg 최종 표본의 가중평균 similarity, 0~1
     * @param p25 가중 25백분위 예산(winsorize 이후)
     * @param p75 가중 75백분위 예산(winsorize 이후)
     * @param completenessScore 최종 표본 중 duration 값을 가진 비율(가중), 0~1
     * @param finalSampleOriginLevels 최종 표본 각 record의 출처 fallback 단계(scope weight 계산용)
     * @param finalSampleWeights finalSampleOriginLevels와 같은 순서의 weight 배열
     * @param districtProvided 이번 조회에 district가 주어졌는지(로컬 evidence 공식 분기)
     */
    MultiYearDataQualityV3 compute(double effectiveSampleSize, double weightedSimilarityAvg,
                                    double p25, double p75, double completenessScore,
                                    List<FallbackLevel> finalSampleOriginLevels, double[] finalSampleWeights,
                                    boolean districtProvided) {
        double sampleQuality = 1 - Math.exp(-effectiveSampleSize / SAMPLE_QUALITY_DIVISOR);
        double similarityQuality = weightedSimilarityAvg;
        double stabilityQuality = stabilityQuality(p25, p75);
        double completenessQuality = completenessScore;
        double localEvidenceQuality = localEvidenceQuality(finalSampleOriginLevels, finalSampleWeights, districtProvided);

        double score = (sampleQuality * SAMPLE_WEIGHT
                + similarityQuality * SIMILARITY_WEIGHT
                + stabilityQuality * STABILITY_WEIGHT
                + completenessQuality * COMPLETENESS_WEIGHT
                + localEvidenceQuality * LOCAL_EVIDENCE_WEIGHT) * 100;

        return new MultiYearDataQualityV3(sampleQuality, similarityQuality, stabilityQuality,
                completenessQuality, localEvidenceQuality, score);
    }

    private double stabilityQuality(double p25, double p75) {
        if (p25 <= 0 || p75 <= 0) {
            return 0.0;
        }
        double ratio = p75 / p25;
        if (!Double.isFinite(ratio) || ratio <= 0) {
            return 0.0;
        }
        double spread = Math.max(Math.log(ratio), 0.0);
        return Math.exp(-spread / STABILITY_QUALITY_DIVISOR);
    }

    private double localEvidenceQuality(List<FallbackLevel> originLevels, double[] weights, boolean districtProvided) {
        if (originLevels.size() != weights.length || weights.length == 0) {
            return 0.0;
        }
        double totalWeight = 0;
        double districtWeight = 0;
        double regionWeight = 0;
        for (int i = 0; i < weights.length; i++) {
            totalWeight += weights[i];
            FallbackLevel level = originLevels.get(i);
            if (level == FallbackLevel.SAME_DISTRICT_TYPE_VENUE) {
                districtWeight += weights[i];
            } else if (level == FallbackLevel.SAME_REGION_TYPE_VENUE || level == FallbackLevel.SAME_REGION_TYPE) {
                regionWeight += weights[i];
            }
            // NATIONWIDE_TYPE_VENUE / NATIONWIDE_TYPE / GLOBAL_SIMILARITY: local evidence에서 제외.
        }
        if (totalWeight <= 0) {
            return 0.0;
        }
        double districtShare = districtWeight / totalWeight;
        double regionShare = regionWeight / totalWeight;
        return districtProvided ? districtShare + 0.5 * regionShare : regionShare;
    }

    /** Spearman(순위) 상관계수 - v3 점수 vs 절대 log 예측오차 비교용(순위 동점은 평균순위 처리). */
    double spearmanCorrelation(double[] a, double[] b) {
        double[] rankA = descendingRanks(a);
        double[] rankB = descendingRanks(b);
        return pearson(rankA, rankB);
    }

    private double[] descendingRanks(double[] values) {
        int n = values.length;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (x, y) -> Double.compare(values[y], values[x]));
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
        double meanA = java.util.Arrays.stream(a).average().orElse(0);
        double meanB = java.util.Arrays.stream(b).average().orElse(0);
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
}