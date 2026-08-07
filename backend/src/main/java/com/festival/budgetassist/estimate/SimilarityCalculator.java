package com.festival.budgetassist.estimate;

import org.springframework.stereotype.Component;

import com.festival.budgetassist.festival.domain.FestivalRecord;
import com.festival.budgetassist.festival.domain.FestivalType;
import com.festival.budgetassist.festival.domain.Region;
import com.festival.budgetassist.festival.domain.VenueType;

/**
 * 안내서 9.4절 유사도 계산. 순수 함수 중심으로 구현해 단위 테스트하기 쉽게 만든다.
 * 모든 가중치/하위 점수는 {@link AlgorithmConfig}에서 가져오며 이 클래스에는 숫자를 직접 쓰지 않는다.
 */
@Component
class SimilarityCalculator {

    private final AlgorithmConfig config;

    SimilarityCalculator(AlgorithmConfig config) {
        this.config = config;
    }

    double festivalTypeScore(FestivalType requested, FestivalType candidate) {
        return requested == candidate ? config.getFestivalTypeSameScore() : config.getFestivalTypeDifferentScore();
    }

    double regionScore(Region requestedRegion, String requestedDistrict, Region candidateRegion, String candidateDistrict) {
        boolean districtRequested = requestedDistrict != null && !requestedDistrict.isBlank();
        if (districtRequested && requestedDistrict.equals(candidateDistrict)) {
            return config.getRegionSameDistrictScore();
        }
        if (requestedRegion == candidateRegion) {
            return config.getRegionSameProvinceScore();
        }
        return config.getRegionDifferentProvinceScore();
    }

    double venueTypeScore(VenueType requested, VenueType candidate) {
        if (requested == candidate) {
            return config.getVenueTypeSameScore();
        }
        if (requested == VenueType.UNDECIDED || candidate == VenueType.UNDECIDED) {
            return config.getVenueTypeOneUndecidedScore();
        }
        return config.getVenueTypeDifferentScore();
    }

    /** durationDays가 null인 후보(개최기간 데이터 없음)에는 고정 점수를 준다. */
    double durationScore(Integer requestedDays, Integer candidateDays) {
        if (candidateDays == null) {
            return config.getDurationMissingScore();
        }
        double ratio = (requestedDays + 1.0) / (candidateDays + 1.0);
        return Math.exp(-Math.abs(Math.log(ratio)));
    }

    SimilarityScore compute(Region requestedRegion, String requestedDistrict, FestivalType requestedType,
                             VenueType requestedVenueType, int requestedDurationDays, FestivalRecord candidate) {
        double typeScore = festivalTypeScore(requestedType, candidate.getFestivalType());
        double regionScore = regionScore(requestedRegion, requestedDistrict, candidate.getRegion(), candidate.getAdministrativeDistrict());
        double venueScore = venueTypeScore(requestedVenueType, candidate.getVenueType());
        double durationScore = durationScore(requestedDurationDays, candidate.getDurationDays());

        double similarity = typeScore * config.getFestivalTypeWeight()
                + regionScore * config.getRegionWeight()
                + venueScore * config.getVenueTypeWeight()
                + durationScore * config.getDurationWeight();

        double weight = similarity * similarity;

        return new SimilarityScore(typeScore, regionScore, venueScore, durationScore, similarity, weight);
    }
}