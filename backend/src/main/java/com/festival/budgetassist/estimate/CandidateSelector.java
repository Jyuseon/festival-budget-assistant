package com.festival.budgetassist.estimate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

import com.festival.budgetassist.festival.domain.FestivalRecord;
import com.festival.budgetassist.festival.domain.FestivalType;
import com.festival.budgetassist.festival.domain.Region;
import com.festival.budgetassist.festival.domain.VenueType;

/**
 * 안내서 9.7절 계층형 fallback. 좁은 조건부터 시작해서, 목표 표본 수({@link AlgorithmConfig#getRecommendedSampleCount()})에
 * 도달할 때까지 조건을 넓혀가며 중복 없이 후보를 누적한다(단순 교체가 아니라 합집합).
 *
 * <p>district(시군구) 입력이 없으면 1단계(SAME_DISTRICT_TYPE_VENUE)는 건너뛴다.</p>
 */
@Component
class CandidateSelector {

    private final AlgorithmConfig config;

    CandidateSelector(AlgorithmConfig config) {
        this.config = config;
    }

    CandidateSelectionResult select(List<FestivalRecord> eligiblePool, Region region, String district,
                                     FestivalType festivalType, VenueType venueType) {
        boolean hasDistrict = district != null && !district.isBlank();

        Map<Long, FestivalRecord> accumulated = new LinkedHashMap<>();
        Map<Long, FallbackLevel> originLevel = new LinkedHashMap<>();
        List<LevelContribution> levelBreakdown = new ArrayList<>();
        FallbackLevel reachedLevel = FallbackLevel.SAME_DISTRICT_TYPE_VENUE;

        for (FallbackLevel level : FallbackLevel.values()) {
            if (level == FallbackLevel.SAME_DISTRICT_TYPE_VENUE && !hasDistrict) {
                continue;
            }
            int sizeBefore = accumulated.size();
            Predicate<FestivalRecord> matcher = matcherFor(level, region, district, festivalType, venueType);
            for (FestivalRecord record : eligiblePool) {
                if (matcher.test(record)) {
                    FestivalRecord previous = accumulated.putIfAbsent(record.getId(), record);
                    if (previous == null) {
                        // 이 단계에서 처음 들어온 후보다 - 관찰용 기록일 뿐 선택 여부에는 영향 없음.
                        originLevel.put(record.getId(), level);
                    }
                }
            }
            levelBreakdown.add(new LevelContribution(level, accumulated.size() - sizeBefore, accumulated.size()));
            reachedLevel = level;
            if (accumulated.size() >= config.getRecommendedSampleCount()) {
                break;
            }
        }

        return new CandidateSelectionResult(reachedLevel, new ArrayList<>(accumulated.values()), levelBreakdown, originLevel);
    }

    private Predicate<FestivalRecord> matcherFor(FallbackLevel level, Region region, String district,
                                                   FestivalType festivalType, VenueType venueType) {
        return switch (level) {
            case SAME_DISTRICT_TYPE_VENUE -> r -> district.equals(r.getAdministrativeDistrict())
                    && r.getFestivalType() == festivalType && r.getVenueType() == venueType;
            case SAME_REGION_TYPE_VENUE -> r -> r.getRegion() == region
                    && r.getFestivalType() == festivalType && r.getVenueType() == venueType;
            case NATIONWIDE_TYPE_VENUE -> r -> r.getFestivalType() == festivalType && r.getVenueType() == venueType;
            case SAME_REGION_TYPE -> r -> r.getRegion() == region && r.getFestivalType() == festivalType;
            case NATIONWIDE_TYPE -> r -> r.getFestivalType() == festivalType;
            case GLOBAL_SIMILARITY -> r -> true;
        };
    }
}