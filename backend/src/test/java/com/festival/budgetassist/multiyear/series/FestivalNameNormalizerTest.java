package com.festival.budgetassist.multiyear.series;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class FestivalNameNormalizerTest {

    @Test
    void removesLeadingYearAndRoundMarkers_sameFestivalConverges() {
        assertEquals("무주반딧불축제", FestivalNameNormalizer.normalize("제27회 무주반딧불축제"));
        assertEquals("무주반딧불축제", FestivalNameNormalizer.normalize("제28회 무주반딧불축제"));
        assertEquals("무주반딧불축제", FestivalNameNormalizer.normalize("2025 무주반딧불축제"));
        assertEquals("무주반딧불축제", FestivalNameNormalizer.normalize("2025년 무주반딧불축제"));
    }

    @Test
    void bareRoundMarkerWithoutJe_isRemoved() {
        assertEquals("인삼축제", FestivalNameNormalizer.normalize("10회 인삼축제"));
    }

    @Test
    void yearOrRoundOnlyParenthesesAreStrippedEntirely() {
        assertEquals("무주반딧불축제", FestivalNameNormalizer.normalize("무주반딧불축제(제27회)"));
        assertEquals("무주반딧불축제", FestivalNameNormalizer.normalize("무주반딧불축제 (2025)"));
        assertEquals("무주반딧불축제", FestivalNameNormalizer.normalize("무주반딧불축제(27회)"));
    }

    @Test
    void nonYearRoundParenthesesAreKept_notOverlyAggressive() {
        // 괄호 안이 연도/회차/숫자만이 아니면(의미 있는 부제) 지우지 않는다.
        assertEquals("여름축제(야간개장)", FestivalNameNormalizer.normalize("여름축제(야간개장)"));
    }

    @Test
    void doesNotStripMeaningfulDigitsWithoutRoundContext() {
        // "회"가 없는 숫자(기념일 등)는 회차로 오인해 지우면 안 된다.
        assertEquals("3.1운동 100주년 기념축제", FestivalNameNormalizer.normalize("3.1운동 100주년 기념축제"));
    }

    @Test
    void doesNotUnifyFestivalVsFestivalSpelling() {
        // "축제"/"페스티벌" 표기 차이를 공격적으로 통합하지 않는다 - 서로 다른 문자열로 남아야 한다.
        assertNotEquals(
                FestivalNameNormalizer.normalize("자라섬재즈페스티벌"),
                FestivalNameNormalizer.normalize("자라섬재즈축제"));
    }

    @Test
    void internalWhitespaceIsPreservedNotStripped() {
        // 내부 공백 유무 차이는 여기서 지우지 않는다(EXACT/NORMALIZED_EXACT를 과도하게 넓히지
        // 않기 위함) - fuzzy 단계의 fuzzyKey()에서만 다룬다.
        assertNotEquals(
                FestivalNameNormalizer.normalize("무주 반딧불축제"),
                FestivalNameNormalizer.normalize("무주반딧불축제"));
    }

    @Test
    void fuzzyKeyStripsAllWhitespace() {
        assertEquals(
                FestivalNameNormalizer.fuzzyKey(FestivalNameNormalizer.normalize("무주 반딧불축제")),
                FestivalNameNormalizer.fuzzyKey(FestivalNameNormalizer.normalize("무주반딧불축제")));
    }

    @Test
    void idempotent() {
        String once = FestivalNameNormalizer.normalize("제27회 무주반딧불축제(2025)");
        String twice = FestivalNameNormalizer.normalize(once);
        assertEquals(once, twice);
    }

    @Test
    void blankOrNullInputsAreHandledSafely() {
        assertEquals(null, FestivalNameNormalizer.normalize(null));
        assertEquals("", FestivalNameNormalizer.normalize("   "));
    }

    @Test
    void unrelatedNamesRemainDistinct() {
        // 서로 다른 봄꽃축제/벚꽃축제 - 정규화 후에도 절대 같은 문자열이 되면 안 된다.
        assertNotEquals(
                FestivalNameNormalizer.normalize("제10회 여의도 봄꽃축제"),
                FestivalNameNormalizer.normalize("제10회 진해 군항제"));
    }
}