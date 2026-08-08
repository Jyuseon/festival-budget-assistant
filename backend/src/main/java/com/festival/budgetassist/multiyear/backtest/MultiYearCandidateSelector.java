package com.festival.budgetassist.multiyear.backtest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

import com.festival.budgetassist.estimate.AlgorithmConfig;
import com.festival.budgetassist.estimate.FallbackLevel;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;

/**
 * baseline backtest 전용 계층형 fallback 후보 선정 - production {@code CandidateSelector}의
 * 6단계 구조(district+유형+장소 -> 광역+유형+장소 -> 전국+유형+장소 -> 광역+유형 -> 전국+유형 ->
 * 전체)를 그대로 옮겨 적되, 두 지점만 다년도 데이터에 맞게 일반화했다:
 * <ol>
 *   <li>district 미입력 시 1단계를 건너뛰는 production 규칙과 같은 원리로, venueType을 모르는
 *       요청(옛 데이터 대부분)은 venueType이 필요한 1~3단계를 전부 건너뛰고 4단계(광역+유형)부터
 *       시작한다 - "정보가 없는 조건은 매칭에 강제로 쓰지 않는다"는 기존 설계를 그대로 확장한
 *       것이다.</li>
 *   <li>festivalType은 단일 enum이 아니라 복합 Set일 수 있어(다년도 데이터), "동일 유형"은
 *       두 Set이 하나라도 겹치는지로 판정한다.</li>
 * </ol>
 */
@Component
class MultiYearCandidateSelector implements MultiYearCandidateSelectionStrategy {

    private final AlgorithmConfig config;

    MultiYearCandidateSelector(AlgorithmConfig config) {
        this.config = config;
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
            boolean requiresDistrict = level == FallbackLevel.SAME_DISTRICT_TYPE_VENUE;
            boolean requiresVenue = level == FallbackLevel.SAME_DISTRICT_TYPE_VENUE
                    || level == FallbackLevel.SAME_REGION_TYPE_VENUE
                    || level == FallbackLevel.NATIONWIDE_TYPE_VENUE;
            if ((requiresDistrict && !hasDistrict) || (requiresVenue && !hasVenue)) {
                continue;
            }

            int sizeBefore = accumulated.size();
            Predicate<MultiYearFestivalRecord> matcher = matcherFor(level, target);
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
            if (accumulated.size() >= config.getRecommendedSampleCount()) {
                break;
            }
        }

        return new MultiYearCandidateSelectionResult(reachedLevel, new ArrayList<>(accumulated.values()), levelBreakdown, originLevel);
    }

    private Predicate<MultiYearFestivalRecord> matcherFor(FallbackLevel level, MultiYearBacktestQuery target) {
        return switch (level) {
            case SAME_DISTRICT_TYPE_VENUE -> r -> target.district().equals(MultiYearFeatureResolver.resolveDistrict(r))
                    && typeMatches(target, r) && r.getVenueType() == target.venueType();
            case SAME_REGION_TYPE_VENUE -> r -> r.getRegionCode() == target.regionCode()
                    && typeMatches(target, r) && r.getVenueType() == target.venueType();
            case NATIONWIDE_TYPE_VENUE -> r -> typeMatches(target, r) && r.getVenueType() == target.venueType();
            case SAME_REGION_TYPE -> r -> r.getRegionCode() == target.regionCode() && typeMatches(target, r);
            case NATIONWIDE_TYPE -> r -> typeMatches(target, r);
            case GLOBAL_SIMILARITY -> r -> true;
        };
    }

    private boolean typeMatches(MultiYearBacktestQuery target, MultiYearFestivalRecord candidate) {
        return MultiYearFeatureResolver.typesOverlap(target.typeTokens(), MultiYearFeatureResolver.resolveTypeTokens(candidate));
    }
}