package com.festival.budgetassist.multiyear.backtest;

import java.util.function.Predicate;

import com.festival.budgetassist.estimate.FallbackLevel;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;

/**
 * {@link FallbackLevel} 6단계 각각의 "이 record가 이 단계 조건을 만족하는가" 판정 로직 - {@link
 * MultiYearCandidateSelector}(V0)의 {@code matcherFor}/{@code typeMatches}를 selector lab의
 * V1~V4가 재사용할 수 있게 별도 유틸리티로 뽑아 놓은 것이다.
 *
 * <p><b>V0({@link MultiYearCandidateSelector}) 자체는 이 클래스를 쓰지 않는다</b> - V0의 기존
 * private 메서드를 건드리면(설령 로직이 100% 동일해도) "V0 공식은 절대 안 건드린다"는 이번 작업의
 * 전제와 충돌할 여지가 있어, 대신 V0의 로직을 그대로 복사해 이 새 유틸리티를 만들고 V0는 원본
 * 그대로 둔다. 판정 규칙 자체는 완전히 동일하다(코드 비교로 확인 가능).</p>
 */
final class MultiYearFallbackTierMatcher {

    private MultiYearFallbackTierMatcher() {
    }

    /** 이 단계가 district 입력을 필수로 요구하는지. */
    static boolean requiresDistrict(FallbackLevel level) {
        return level == FallbackLevel.SAME_DISTRICT_TYPE_VENUE;
    }

    /** 이 단계가 venueType 입력을 필수로 요구하는지. */
    static boolean requiresVenue(FallbackLevel level) {
        return level == FallbackLevel.SAME_DISTRICT_TYPE_VENUE
                || level == FallbackLevel.SAME_REGION_TYPE_VENUE
                || level == FallbackLevel.NATIONWIDE_TYPE_VENUE;
    }

    static Predicate<MultiYearFestivalRecord> matcherFor(FallbackLevel level, MultiYearBacktestQuery target) {
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

    static boolean typeMatches(MultiYearBacktestQuery target, MultiYearFestivalRecord candidate) {
        return MultiYearFeatureResolver.typesOverlap(target.typeTokens(), MultiYearFeatureResolver.resolveTypeTokens(candidate));
    }

    /**
     * candidate가 만족하는 가장 구체적인(ordinal이 가장 작은) {@link FallbackLevel}을 찾는다 - V2/V4
     * selector lab 전략처럼 계층 사다리를 순서대로 훑지 않고 candidate 전체를 한 번에 다루는
     * 전략이, 각 candidate의 "origin level"(v3 data-quality의 local evidence 가중치, 리포트의
     * fallbackStage 표시 등에 쓰임)을 사후에 정확히 태깅할 수 있게 한다. 어떤 단계도 만족하지
     * 않으면(이론상 없음 - GLOBAL_SIMILARITY는 항상 true) null.
     */
    static FallbackLevel mostSpecificLevel(MultiYearBacktestQuery target, MultiYearFestivalRecord candidate,
                                            boolean hasDistrict, boolean hasVenue) {
        for (FallbackLevel level : FallbackLevel.values()) {
            if ((requiresDistrict(level) && !hasDistrict) || (requiresVenue(level) && !hasVenue)) {
                continue;
            }
            if (matcherFor(level, target).test(candidate)) {
                return level;
            }
        }
        return null;
    }
}