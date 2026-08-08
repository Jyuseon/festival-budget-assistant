package com.festival.budgetassist.multiyear.backtest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.festival.budgetassist.estimate.AlgorithmConfig;
import com.festival.budgetassist.estimate.FallbackLevel;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;

/**
 * Selector 후보 V3 - MIN DISTINCT YEARS.
 *
 * <p>V0/V1과 같은 계층형 fallback 사다리를 훑되, "표본 수 충분"과 별개로 누적 후보가 최소
 * {@code minDistinctYears}개 서로 다른 datasetYear를 포함할 때까지 tier를 계속 넓힌다. V1(연도
 * 쏠림 상한)과 달리 "얼마나 균등한가"가 아니라 "몇 개 연도가 대표되는가"만 본다 - 예를 들어 49건이
 * 2025년, 1건만 2019년이어도 distinctYearsUsed=2로 조건을 만족해버릴 수 있다는 약점이 있다(8절
 * 요청대로 결과 리포트에서 함께 확인한다).</p>
 */
final class MultiYearSelectorLabV3MinDistinctYears implements MultiYearCandidateSelectionStrategy {

    private final AlgorithmConfig config;
    private final int minDistinctYears;

    MultiYearSelectorLabV3MinDistinctYears(AlgorithmConfig config, int minDistinctYears) {
        this.config = config;
        this.minDistinctYears = minDistinctYears;
    }

    String label() {
        return "V3_MIN_DISTINCT_YEARS_%d".formatted(minDistinctYears);
    }

    @Override
    public MultiYearCandidateSelectionResult select(List<MultiYearFestivalRecord> trainingPool, MultiYearBacktestQuery target) {
        boolean hasDistrict = target.district() != null;
        boolean hasVenue = target.venueType() != null;

        Map<Long, MultiYearFestivalRecord> accumulated = new LinkedHashMap<>();
        Map<Long, FallbackLevel> originLevel = new LinkedHashMap<>();
        List<MultiYearLevelContribution> levelBreakdown = new ArrayList<>();
        FallbackLevel reachedLevel = FallbackLevel.SAME_DISTRICT_TYPE_VENUE;

        for (FallbackLevel level : FallbackLevel.values()) {
            if ((MultiYearFallbackTierMatcher.requiresDistrict(level) && !hasDistrict)
                    || (MultiYearFallbackTierMatcher.requiresVenue(level) && !hasVenue)) {
                continue;
            }

            int sizeBefore = accumulated.size();
            Predicate<MultiYearFestivalRecord> matcher = MultiYearFallbackTierMatcher.matcherFor(level, target);
            for (MultiYearFestivalRecord record : trainingPool) {
                if (matcher.test(record)) {
                    MultiYearFestivalRecord previous = accumulated.putIfAbsent(record.getId(), record);
                    if (previous == null) {
                        originLevel.put(record.getId(), level);
                    }
                }
            }
            levelBreakdown.add(new MultiYearLevelContribution(level, accumulated.size() - sizeBefore, accumulated.size()));
            reachedLevel = level;

            boolean sizeOk = accumulated.size() >= config.getRecommendedSampleCount();
            boolean diversityOk = distinctYears(accumulated.values()) >= minDistinctYears;
            if (sizeOk && diversityOk) {
                break;
            }
        }

        return new MultiYearCandidateSelectionResult(reachedLevel, new ArrayList<>(accumulated.values()), levelBreakdown, originLevel);
    }

    private int distinctYears(Iterable<MultiYearFestivalRecord> records) {
        Set<Integer> years = new HashSet<>();
        for (MultiYearFestivalRecord r : records) {
            years.add(r.getDatasetYear());
        }
        return years.size();
    }
}