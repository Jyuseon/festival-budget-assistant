package com.festival.budgetassist.multiyear.backtest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.festival.budgetassist.estimate.AlgorithmConfig;
import com.festival.budgetassist.estimate.FallbackLevel;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;

/**
 * Selector 후보 V2 - DIVERSIFIED TOP-K.
 *
 * <p>V0/V1/V3처럼 tier를 하나씩 훑다가 멈추는 대신, "동일 축제유형(overlap)"이라는 최소 전제만
 * 만족하는 training pool 전체를 한 번에 유사도 순으로 정렬한 뒤, 다음 두 단계로 최종 표본을
 * 구성한다:
 * <ol>
 *   <li><b>quality band</b>: {@code bestSimilarity - qualityBand} 이상인 candidate만 "품질
 *       허용권"에 넣는다(권 밖의 낮은 유사도 candidate는 연도 다양성을 위해서도 절대 끌어오지
 *       않는다 - 8절의 명시적 요구사항).</li>
 *   <li><b>연도 균형 인터리빙</b>: 품질 허용권 안에서 연도별로 유사도 내림차순 정렬한 뒤, 연도를
 *       라운드로빈으로 순회하며 뽑는다(각 라운드에서 아직 안 뽑힌 연도 중 그 연도의 최고 유사도
 *       candidate 하나씩) - 그 결과 "같은 유사도대"라면 이미 많이 뽑힌 연도보다 아직 하나도 못
 *       뽑은 연도가 먼저 뽑힌다. {@code maxSampleCount}(기존 50)까지 채운다.</li>
 * </ol>
 * 품질 허용권 안의 candidate만으로 {@code recommendedSampleCount}(20)조차 못 채우면(허용권이 너무
 * 좁은 극단적인 경우), 허용권 밖이라도 순수 유사도 순으로 모자란 만큼 추가한다 - "연도 다양성 때문에
 * V0보다 표본이 줄어드는" 부작용을 막기 위한 방어적 하한이다.
 *
 * <p>후보 universe는 GLOBAL_SIMILARITY(전체)가 아니라 NATIONWIDE_TYPE 수준(축제유형 overlap
 * 필수)으로 제한한다 - 유형이 아예 다른 축제는 similarity 가중치 구조상(유형 가중치 0.40, 불일치
 * 시 점수 0.10) 사실상 항상 threshold 미만이라 넣으나 마나이고, 계산량만 늘린다.</p>
 */
final class MultiYearSelectorLabV2DiversifiedTopK implements MultiYearCandidateSelectionStrategy {

    private final AlgorithmConfig config;
    private final MultiYearSimilarityCalculator similarityCalculator;
    private final double qualityBand;

    MultiYearSelectorLabV2DiversifiedTopK(AlgorithmConfig config, MultiYearSimilarityCalculator similarityCalculator, double qualityBand) {
        this.config = config;
        this.similarityCalculator = similarityCalculator;
        this.qualityBand = qualityBand;
    }

    String label() {
        return "V2_DIVERSIFIED_TOPK_band%.2f".formatted(qualityBand);
    }

    @Override
    public MultiYearCandidateSelectionResult select(List<MultiYearFestivalRecord> trainingPool, MultiYearBacktestQuery target) {
        boolean hasDistrict = target.district() != null;
        boolean hasVenue = target.venueType() != null;

        record Scored(MultiYearFestivalRecord record, double similarity, FallbackLevel origin) {
        }

        List<Scored> universe = new ArrayList<>();
        for (MultiYearFestivalRecord r : trainingPool) {
            if (!MultiYearFallbackTierMatcher.typeMatches(target, r)) {
                continue;
            }
            FallbackLevel origin = MultiYearFallbackTierMatcher.mostSpecificLevel(target, r, hasDistrict, hasVenue);
            double similarity = similarityCalculator.compute(target, r).similarity();
            universe.add(new Scored(r, similarity, origin));
        }

        if (universe.isEmpty()) {
            return new MultiYearCandidateSelectionResult(FallbackLevel.GLOBAL_SIMILARITY, List.of(), List.of(), Map.of());
        }

        universe.sort(Comparator.comparingDouble(Scored::similarity).reversed());
        double bestSimilarity = universe.get(0).similarity();
        double bandFloor = Math.max(bestSimilarity - qualityBand, config.getSimilarityMinThreshold());

        // 연도별 후보를 유사도 내림차순으로 미리 정렬해 둔다(라운드로빈에서 각 연도의 "다음 최선"을 꺼내기 위해).
        Map<Integer, Deque<Scored>> byYear = new TreeMap<>();
        for (Scored s : universe) {
            if (s.similarity() < bandFloor) {
                break; // universe는 이미 유사도 내림차순 정렬됐으므로 여기서부터는 전부 band 밖.
            }
            // universe가 이미 유사도 내림차순이라 ArrayDeque에 쌓이는 순서 자체가 연도별 내림차순이다
            // (peekFirst/pollFirst = 그 연도의 아직 안 뽑힌 candidate 중 최고 유사도) - 별도 정렬 불필요.
            byYear.computeIfAbsent(s.record().getDatasetYear(), k -> new ArrayDeque<>()).add(s);
        }

        List<Scored> picked = new ArrayList<>();
        Map<Long, FallbackLevel> originLevel = new LinkedHashMap<>();
        while (picked.size() < config.getMaxSampleCount() && byYear.values().stream().anyMatch(q -> !q.isEmpty())) {
            boolean anyTaken = false;
            for (Deque<Scored> queue : byYear.values()) {
                if (queue.isEmpty()) {
                    continue;
                }
                Scored s = queue.pollFirst();
                picked.add(s);
                originLevel.put(s.record().getId(), s.origin());
                anyTaken = true;
                if (picked.size() >= config.getMaxSampleCount()) {
                    break;
                }
            }
            if (!anyTaken) {
                break;
            }
        }

        // 방어적 하한: quality band만으로 recommendedSampleCount조차 못 채웠으면 band 밖에서 순수
        // 유사도 순으로 모자란 만큼 보충한다(연도 다양성 로직 때문에 V0보다 표본이 줄어드는 것 방지).
        if (picked.size() < config.getRecommendedSampleCount()) {
            for (Scored s : universe) {
                if (picked.size() >= config.getRecommendedSampleCount() || picked.size() >= config.getMaxSampleCount()) {
                    break;
                }
                if (originLevel.containsKey(s.record().getId())) {
                    continue;
                }
                picked.add(s);
                originLevel.put(s.record().getId(), s.origin());
            }
        }

        FallbackLevel reachedLevel = picked.stream()
                .map(Scored::origin)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.comparingInt(FallbackLevel::ordinal))
                .orElse(FallbackLevel.GLOBAL_SIMILARITY);

        List<MultiYearLevelContribution> levelBreakdown = levelBreakdownOf(universe.stream().map(Scored::record).toList(),
                universe.stream().collect(java.util.stream.Collectors.toMap(sc -> sc.record().getId(), Scored::origin)));

        List<MultiYearFestivalRecord> candidates = picked.stream().map(Scored::record).toList();
        return new MultiYearCandidateSelectionResult(reachedLevel, candidates, levelBreakdown, originLevel);
    }

    /** 참고용 diagnostic breakdown - universe 전체(picked 여부와 무관)를 origin tier별로 센다. */
    private List<MultiYearLevelContribution> levelBreakdownOf(List<MultiYearFestivalRecord> universeRecords,
                                                                Map<Long, FallbackLevel> originByRecordId) {
        Map<FallbackLevel, Integer> counts = new LinkedHashMap<>();
        for (MultiYearFestivalRecord r : universeRecords) {
            FallbackLevel level = originByRecordId.get(r.getId());
            if (level != null) {
                counts.merge(level, 1, Integer::sum);
            }
        }
        List<MultiYearLevelContribution> result = new ArrayList<>();
        int cumulative = 0;
        for (FallbackLevel level : FallbackLevel.values()) {
            int added = counts.getOrDefault(level, 0);
            cumulative += added;
            result.add(new MultiYearLevelContribution(level, added, cumulative));
        }
        return result;
    }
}