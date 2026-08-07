package com.festival.budgetassist.estimate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.festival.budgetassist.festival.domain.FestivalType;
import com.festival.budgetassist.festival.domain.Region;
import com.festival.budgetassist.festival.domain.VenueType;

class SimilarityCalculatorTest {

    private static final double DELTA = 1e-6;

    private final AlgorithmConfig config = new AlgorithmConfig();
    private final SimilarityCalculator calculator = new SimilarityCalculator(config);

    @Test
    void festivalTypeScore() {
        assertEquals(1.00, calculator.festivalTypeScore(FestivalType.CULTURE_ART, FestivalType.CULTURE_ART), DELTA);
        assertEquals(0.10, calculator.festivalTypeScore(FestivalType.CULTURE_ART, FestivalType.NATURE_ECOLOGY), DELTA);
    }

    @Test
    void regionScore_sameDistrict_needsDistrictInput() {
        assertEquals(1.00, calculator.regionScore(Region.SEOUL, "종로구", Region.SEOUL, "종로구"), DELTA);
    }

    @Test
    void regionScore_noDistrictInput_fallsBackToProvinceComparison() {
        // district 입력이 없으면 시군구가 같아도 광역 비교로만 판단한다.
        assertEquals(0.80, calculator.regionScore(Region.SEOUL, null, Region.SEOUL, "종로구"), DELTA);
    }

    @Test
    void regionScore_sameProvinceDifferentDistrict() {
        assertEquals(0.80, calculator.regionScore(Region.SEOUL, "중구", Region.SEOUL, "종로구"), DELTA);
    }

    @Test
    void regionScore_differentProvince() {
        assertEquals(0.30, calculator.regionScore(Region.SEOUL, "중구", Region.BUSAN, "해운대구"), DELTA);
    }

    @Test
    void venueTypeScore() {
        assertEquals(1.00, calculator.venueTypeScore(VenueType.GREEN, VenueType.GREEN), DELTA);
        assertEquals(0.45, calculator.venueTypeScore(VenueType.GREEN, VenueType.UNDECIDED), DELTA);
        assertEquals(0.45, calculator.venueTypeScore(VenueType.UNDECIDED, VenueType.GREEN), DELTA);
        assertEquals(0.25, calculator.venueTypeScore(VenueType.GREEN, VenueType.WATERFRONT), DELTA);
    }

    @Test
    void durationScore_identicalDuration_isMax() {
        assertEquals(1.0, calculator.durationScore(3, 3), DELTA);
    }

    @Test
    void durationScore_isSymmetric() {
        double a = calculator.durationScore(3, 6);
        double b = calculator.durationScore(6, 3);
        assertEquals(a, b, DELTA);
    }

    @Test
    void durationScore_missingCandidateDuration_usesFixedScore() {
        assertEquals(config.getDurationMissingScore(), calculator.durationScore(3, null), DELTA);
    }

    @Test
    void compute_combinesSubScoresWithConfiguredWeights() {
        double type = 1.00; // same
        double region = 0.80; // same province, no district
        double venue = 1.00; // same
        double duration = 1.00; // same days

        double expectedSimilarity = type * config.getFestivalTypeWeight()
                + region * config.getRegionWeight()
                + venue * config.getVenueTypeWeight()
                + duration * config.getDurationWeight();

        var record = com.festival.budgetassist.festival.domain.FestivalRecord.builder()
                .region(Region.SEOUL)
                .administrativeDistrict(null)
                .festivalType(FestivalType.CULTURE_ART)
                .venueType(VenueType.GREEN)
                .durationDays(3)
                .build();

        SimilarityScore score = calculator.compute(Region.SEOUL, null, FestivalType.CULTURE_ART, VenueType.GREEN, 3, record);

        assertEquals(expectedSimilarity, score.similarity(), DELTA);
        assertEquals(expectedSimilarity * expectedSimilarity, score.weight(), DELTA);
    }
}