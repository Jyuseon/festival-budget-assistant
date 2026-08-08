package com.festival.budgetassist.multiyear.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.festival.budgetassist.festival.domain.Region;
import com.festival.budgetassist.festival.domain.VenueType;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;

/** 지시사항 5절 "available-feature 재정규화" similarity의 핵심 동작을 검증한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class MultiYearSimilarityCalculatorTest extends MultiYearBacktestTestSupport {

    @Autowired
    private MultiYearSimilarityCalculator similarityCalculator;

    @BeforeEach
    void setUp() {
        initBatch();
    }

    @Test
    void bothMissingVenueAndDuration_excludedFromSimilarity_notPenalized() {
        // target/candidate 둘 다 venue/duration이 없다(옛 데이터) - type+region만으로 유사도가
        // 계산되어야 하고, "값이 없어서" 낮은 점수를 받으면 안 된다.
        MultiYearFestivalRecord target = row(2023, 1, "A축제", Region.GYEONGGI, "가평군", "CULTURE_ART", 100);
        MultiYearFestivalRecord candidate = row(2022, 2, "B축제", Region.GYEONGGI, "가평군", "CULTURE_ART", 100);

        MultiYearBacktestQuery query = MultiYearBacktestQuery.from(target);
        MultiYearSimilarityScore score = similarityCalculator.compute(query, candidate);

        assertFalse(score.venueAvailable());
        assertFalse(score.durationAvailable());
        // type(1.0*0.40) + region(same district=1.0*0.25) 만으로 재정규화 -> similarity = 1.0
        assertEquals(1.0, score.similarity(), 1e-9);
    }

    @Test
    void oneHasVenueOtherDoesNot_excludedFromSimilarity() {
        MultiYearFestivalRecord target = row(2023, 1, "A축제", Region.GYEONGGI, "가평군", "CULTURE_ART", 100);
        MultiYearFestivalRecord candidateWithVenue = row(2022, 2, "B축제", Region.GYEONGGI, "가평군", "CULTURE_ART",
                VenueType.GREEN, null, 100, com.festival.budgetassist.multiyear.domain.BudgetQualityFlag.VALID);

        MultiYearBacktestQuery query = MultiYearBacktestQuery.from(target); // target venue=null
        MultiYearSimilarityScore score = similarityCalculator.compute(query, candidateWithVenue);

        assertFalse(score.venueAvailable(), "target에 venue가 없으면 후보가 venue를 가졌어도 그 feature는 제외돼야 함");
        assertEquals(1.0, score.similarity(), 1e-9);
    }

    @Test
    void bothHaveVenue_includedInSimilarity_sameVenueScoresHigherThanDifferent() {
        MultiYearFestivalRecord target = row(2023, 1, "A축제", Region.GYEONGGI, "가평군", "CULTURE_ART",
                VenueType.GREEN, null, 100, com.festival.budgetassist.multiyear.domain.BudgetQualityFlag.VALID);
        MultiYearFestivalRecord sameVenue = row(2022, 2, "B축제", Region.GYEONGGI, "가평군", "CULTURE_ART",
                VenueType.GREEN, null, 100, com.festival.budgetassist.multiyear.domain.BudgetQualityFlag.VALID);
        MultiYearFestivalRecord diffVenue = row(2021, 3, "C축제", Region.GYEONGGI, "가평군", "CULTURE_ART",
                VenueType.WATERFRONT, null, 100, com.festival.budgetassist.multiyear.domain.BudgetQualityFlag.VALID);

        MultiYearBacktestQuery query = MultiYearBacktestQuery.from(target);
        MultiYearSimilarityScore sameScore = similarityCalculator.compute(query, sameVenue);
        MultiYearSimilarityScore diffScore = similarityCalculator.compute(query, diffVenue);

        assertTrue(sameScore.venueAvailable());
        assertTrue(diffScore.venueAvailable());
        assertTrue(sameScore.similarity() > diffScore.similarity(),
                "venue가 둘 다 있을 때는 같은 venue가 다른 venue보다 유사도가 높아야 함");
    }

    @Test
    void differentDistrict_fallsBackToProvinceLevelScore_notZero() {
        MultiYearFestivalRecord target = row(2023, 1, "A축제", Region.GYEONGGI, "가평군", "CULTURE_ART", 100);
        MultiYearFestivalRecord otherDistrict = row(2022, 2, "B축제", Region.GYEONGGI, "여주시", "CULTURE_ART", 100);

        MultiYearBacktestQuery query = MultiYearBacktestQuery.from(target);
        MultiYearSimilarityScore score = similarityCalculator.compute(query, otherDistrict);

        assertTrue(score.regionScore() < 1.0 && score.regionScore() > 0.0,
                "같은 광역지역·다른 시군구는 sameDistrict보다 낮고 0보다는 커야 함(province-level fallback)");
    }
}