package com.festival.budgetassist.multiyear.backtest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.festival.budgetassist.estimate.AlgorithmConfig;
import com.festival.budgetassist.estimate.FallbackLevel;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;

/**
 * Selector 후보 V4 - HYBRID (V1 concentration trigger + V2 quality-bounded 확장).
 *
 * <p>1단계는 V0와 완전히 동일하다(strict tier 유지) - 매 tier가 끝날 때마다 표본 수와 함께
 * "최신(가장 비중 높은) 연도가 누적 weight의 {@code maxYearWeightShare}를 넘지 않는가"를 확인한다.
 * 둘 다 만족하면 V0와 똑같이 그 tier에서 멈춘다(대부분의 "문제 없는" 쿼리는 V0와 결과가 완전히
 * 같다).</p>
 *
 * <p>표본은 충분한데 concentration이 기준을 넘는 경우에만 2단계로 들어간다: 그 시점까지의 표본에서
 * 관측한 최고 유사도({@code referenceBestSimilarity})를 기준으로, 이후 더 넓은 tier에서 새로
 * 발견되는 candidate는 {@code similarity >= referenceBestSimilarity - qualityLossBudget}인
 * 경우에만 받아들인다("낮은 유사도 candidate를 단순히 연도 다양성을 위해 채우지 않는다"는 8절
 * 요구를 그대로 반영) - V1처럼 tier가 찾아내는 후보를 전부 받지 않고, V2처럼 품질 하한선 안에서만
 * 확장한다는 뜻에서 "hybrid"다. 이 조건도 GLOBAL_SIMILARITY까지 도달하면 자연히 멈춘다(V0/V1/V3와
 * 동일한 종료 보장).</p>
 */
final class MultiYearSelectorLabV4Hybrid implements MultiYearCandidateSelectionStrategy {

    private final AlgorithmConfig config;
    private final MultiYearSimilarityCalculator similarityCalculator;
    private final double maxYearWeightShare;
    private final double qualityLossBudget;

    MultiYearSelectorLabV4Hybrid(AlgorithmConfig config, MultiYearSimilarityCalculator similarityCalculator,
                                  double maxYearWeightShare, double qualityLossBudget) {
        this.config = config;
        this.similarityCalculator = similarityCalculator;
        this.maxYearWeightShare = maxYearWeightShare;
        this.qualityLossBudget = qualityLossBudget;
    }

    String label() {
        return "V4_HYBRID_cap%.0fpct_loss%.2f".formatted(maxYearWeightShare * 100, qualityLossBudget);
    }

    @Override
    public MultiYearCandidateSelectionResult select(List<MultiYearFestivalRecord> trainingPool, MultiYearBacktestQuery target) {
        boolean hasDistrict = target.district() != null;
        boolean hasVenue = target.venueType() != null;

        Map<Long, MultiYearFestivalRecord> accumulated = new LinkedHashMap<>();
        Map<Long, FallbackLevel> originLevel = new LinkedHashMap<>();
        List<MultiYearLevelContribution> levelBreakdown = new ArrayList<>();
        FallbackLevel reachedLevel = FallbackLevel.SAME_DISTRICT_TYPE_VENUE;

        boolean qualityGateActive = false;
        double qualityFloor = Double.NEGATIVE_INFINITY;

        for (FallbackLevel level : FallbackLevel.values()) {
            if ((MultiYearFallbackTierMatcher.requiresDistrict(level) && !hasDistrict)
                    || (MultiYearFallbackTierMatcher.requiresVenue(level) && !hasVenue)) {
                continue;
            }

            int sizeBefore = accumulated.size();
            Predicate<MultiYearFestivalRecord> matcher = MultiYearFallbackTierMatcher.matcherFor(level, target);
            for (MultiYearFestivalRecord record : trainingPool) {
                if (accumulated.containsKey(record.getId()) || !matcher.test(record)) {
                    continue;
                }
                if (qualityGateActive && similarityCalculator.compute(target, record).similarity() < qualityFloor) {
                    continue; // 2단계(quality-bounded 확장) 진입 후에는 품질 하한 미달 candidate는 아예 받지 않는다.
                }
                accumulated.put(record.getId(), record);
                originLevel.put(record.getId(), level);
            }
            levelBreakdown.add(new MultiYearLevelContribution(level, accumulated.size() - sizeBefore, accumulated.size()));
            reachedLevel = level;

            boolean sizeOk = accumulated.size() >= config.getRecommendedSampleCount();
            double topYearShare = maxYearWeightShare(accumulated.values(), target);
            boolean concentrationOk = topYearShare <= maxYearWeightShare;
            if (sizeOk && concentrationOk) {
                break;
            }
            if (sizeOk && !concentrationOk && !qualityGateActive) {
                // 1단계(strict tier) 종료 시점의 최고 유사도를 기준선으로 고정하고 2단계로 전환한다.
                qualityGateActive = true;
                qualityFloor = bestSimilarity(accumulated.values(), target) - qualityLossBudget;
            }
        }

        return new MultiYearCandidateSelectionResult(reachedLevel, new ArrayList<>(accumulated.values()), levelBreakdown, originLevel);
    }

    private double maxYearWeightShare(Iterable<MultiYearFestivalRecord> records, MultiYearBacktestQuery target) {
        Map<Integer, Double> weightByYear = new LinkedHashMap<>();
        double total = 0;
        for (MultiYearFestivalRecord r : records) {
            double similarity = similarityCalculator.compute(target, r).similarity();
            double weight = similarity * similarity;
            weightByYear.merge(r.getDatasetYear(), weight, Double::sum);
            total += weight;
        }
        if (total <= 0) {
            return 0.0;
        }
        double max = weightByYear.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        return max / total;
    }

    private double bestSimilarity(Iterable<MultiYearFestivalRecord> records, MultiYearBacktestQuery target) {
        double best = 0.0;
        for (MultiYearFestivalRecord r : records) {
            best = Math.max(best, similarityCalculator.compute(target, r).similarity());
        }
        return best;
    }
}