package com.festival.budgetassist.estimate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.festival.budgetassist.festival.domain.BudgetStatus;
import com.festival.budgetassist.festival.domain.FestivalRecord;
import com.festival.budgetassist.festival.domain.FestivalType;
import com.festival.budgetassist.festival.domain.Region;
import com.festival.budgetassist.festival.domain.VenueType;

class CandidateSelectorTest {

    private FestivalRecord record(long id, Region region, String district, FestivalType type, VenueType venue) {
        return FestivalRecord.builder()
                .id(id)
                .datasetYear(9999)
                .sourceRowNumber((int) id)
                .festivalName("테스트축제" + id)
                .region(region)
                .regionName(region.getDisplayName())
                .administrativeDistrict(district)
                .festivalType(type)
                .venueType(venue)
                .cycleType(com.festival.budgetassist.festival.domain.CycleType.ANNUAL)
                .budgetStatus(BudgetStatus.CONFIRMED)
                .totalBudgetKrw(100_000_000L)
                .build();
    }

    @Test
    void level1_stopsImmediatelyWhenEnoughDistrictMatches() {
        AlgorithmConfig config = new AlgorithmConfig();
        config.setRecommendedSampleCount(2);
        CandidateSelector selector = new CandidateSelector(config);

        List<FestivalRecord> pool = List.of(
                record(1, Region.SEOUL, "종로구", FestivalType.CULTURE_ART, VenueType.GREEN),
                record(2, Region.SEOUL, "종로구", FestivalType.CULTURE_ART, VenueType.GREEN),
                record(3, Region.BUSAN, "해운대구", FestivalType.CULTURE_ART, VenueType.GREEN)
        );

        CandidateSelectionResult result = selector.select(pool, Region.SEOUL, "종로구", FestivalType.CULTURE_ART, VenueType.GREEN);

        assertEquals(FallbackLevel.SAME_DISTRICT_TYPE_VENUE, result.level());
        assertEquals(2, result.candidates().size());
    }

    @Test
    void noDistrictInput_skipsLevel1() {
        AlgorithmConfig config = new AlgorithmConfig();
        config.setRecommendedSampleCount(1);
        CandidateSelector selector = new CandidateSelector(config);

        List<FestivalRecord> pool = List.of(
                record(1, Region.SEOUL, "종로구", FestivalType.CULTURE_ART, VenueType.GREEN)
        );

        CandidateSelectionResult result = selector.select(pool, Region.SEOUL, null, FestivalType.CULTURE_ART, VenueType.GREEN);

        assertEquals(FallbackLevel.SAME_REGION_TYPE_VENUE, result.level(), "district 입력이 없으면 1단계를 건너뛰어야 함");
    }

    @Test
    void expandsThroughLevelsAndAccumulatesWithoutDuplicates() {
        AlgorithmConfig config = new AlgorithmConfig();
        config.setRecommendedSampleCount(3);
        CandidateSelector selector = new CandidateSelector(config);

        List<FestivalRecord> pool = List.of(
                record(1, Region.SEJONG, "세종시", FestivalType.CULTURE_ART, VenueType.GREEN), // L1,L2,L3 매치
                record(2, Region.SEOUL, "종로구", FestivalType.CULTURE_ART, VenueType.GREEN),   // L3에서만 매치(다른 지역)
                record(3, Region.BUSAN, "해운대구", FestivalType.CULTURE_ART, VenueType.WATERFRONT) // L5에서만 매치(장소유형 다름)
        );

        CandidateSelectionResult result = selector.select(pool, Region.SEJONG, "세종시", FestivalType.CULTURE_ART, VenueType.GREEN);

        // 목표 3건을 채우려면 L1(1건) -> L2(추가 없음, 세종은 광역 자체가 시군구) -> L3(2번 추가)까지 가야 함
        assertTrue(result.candidates().size() >= 2);
        assertEquals(1, result.candidates().stream().filter(r -> r.getId() == 1).count(), "중복 없이 누적되어야 함");
    }

    @Test
    void exhaustsAllLevels_whenPoolIsTiny() {
        AlgorithmConfig config = new AlgorithmConfig();
        config.setRecommendedSampleCount(100); // 어떤 pool로도 도달 불가능한 목표
        CandidateSelector selector = new CandidateSelector(config);

        List<FestivalRecord> pool = List.of(
                record(1, Region.SEJONG, "세종시", FestivalType.CULTURE_ART, VenueType.GREEN)
        );

        CandidateSelectionResult result = selector.select(pool, Region.SEJONG, "세종시", FestivalType.CULTURE_ART, VenueType.GREEN);

        assertEquals(FallbackLevel.GLOBAL_SIMILARITY, result.level(), "표본을 채울 수 없으면 마지막 단계까지 가야 함");
        assertEquals(1, result.candidates().size());
    }

    @Test
    void levelBreakdown_recordsAddedCountPerVisitedLevel() {
        AlgorithmConfig config = new AlgorithmConfig();
        config.setRecommendedSampleCount(2);
        CandidateSelector selector = new CandidateSelector(config);

        List<FestivalRecord> pool = List.of(
                record(1, Region.SEOUL, "종로구", FestivalType.CULTURE_ART, VenueType.GREEN), // L1 매치
                record(2, Region.SEOUL, "중구", FestivalType.CULTURE_ART, VenueType.GREEN)     // L1 불일치(다른 구), L2에서 매치
        );

        CandidateSelectionResult result = selector.select(pool, Region.SEOUL, "종로구", FestivalType.CULTURE_ART, VenueType.GREEN);

        assertEquals(2, result.levelBreakdown().size(), "목표(2건) 도달까지 L1, L2 두 단계를 거쳐야 함");
        LevelContribution first = result.levelBreakdown().get(0);
        LevelContribution second = result.levelBreakdown().get(1);
        assertEquals(FallbackLevel.SAME_DISTRICT_TYPE_VENUE, first.level());
        assertEquals(1, first.added());
        assertEquals(1, first.cumulativeTotal());
        assertEquals(FallbackLevel.SAME_REGION_TYPE_VENUE, second.level());
        assertEquals(1, second.added());
        assertEquals(2, second.cumulativeTotal());
    }
}