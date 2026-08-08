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
 * Selector 후보 V1 - YEAR CAP.
 *
 * <p>{@link MultiYearCandidateSelector}(V0)와 완전히 같은 계층형 fallback 사다리를 훑되, 한 tier를
 * 끝낸 뒤 "표본 수가 충분한가"만으로 멈추지 않고 "특정 연도 하나가 누적 후보의 {@code
 * maxYearShare} 이상을 차지하지는 않는가"도 함께 확인한다. 두 조건이 모두 만족될 때까지(또는
 * GLOBAL_SIMILARITY까지 도달할 때까지) 계속 더 넓은 tier로 내려간다 - "한 연도가 조기에 표본을
 * 채워버려서 다른 연도가 아예 후보가 될 기회조차 얻지 못하는" V0의 구조적 문제(2절 CandidateSelector
 * concentration 분석에서 확인)를 정면으로 겨냥한 설계다.
 *
 * <p>concentration 판정은 (아직 유사도/weight를 계산하기 전 단계라) "레코드 수 비중"으로 근사한다 -
 * 최종 weight share와 정확히 같지는 않지만(scoreAndFinalize의 유사도 기반 top-N 컷이 이후에 한 번
 * 더 적용됨), 후보 지침(8절)이 "candidate 수 또는 weight share 상한" 둘 다 허용하므로 유효한
 * 근사다. 실제 최종 weight share는 concentration 분석 리포트에서 별도로 측정한다.</p>
 */
final class MultiYearSelectorLabV1YearCap implements MultiYearCandidateSelectionStrategy {

    private final AlgorithmConfig config;
    private final double maxYearShare;

    MultiYearSelectorLabV1YearCap(AlgorithmConfig config, double maxYearShare) {
        this.config = config;
        this.maxYearShare = maxYearShare;
    }

    String label() {
        return "V1_YEAR_CAP_%.0fpct".formatted(maxYearShare * 100);
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
            boolean concentrationOk = maxYearShare(accumulated.values()) <= maxYearShare;
            if (sizeOk && concentrationOk) {
                break;
            }
        }

        return new MultiYearCandidateSelectionResult(reachedLevel, new ArrayList<>(accumulated.values()), levelBreakdown, originLevel);
    }

    private double maxYearShare(Iterable<MultiYearFestivalRecord> records) {
        Map<Integer, Integer> byYear = new LinkedHashMap<>();
        int total = 0;
        for (MultiYearFestivalRecord r : records) {
            byYear.merge(r.getDatasetYear(), 1, Integer::sum);
            total++;
        }
        if (total == 0) {
            return 0.0;
        }
        int max = byYear.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return (double) max / total;
    }
}