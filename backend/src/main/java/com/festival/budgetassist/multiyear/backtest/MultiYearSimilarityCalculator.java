package com.festival.budgetassist.multiyear.backtest;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.festival.budgetassist.estimate.AlgorithmConfig;
import com.festival.budgetassist.festival.domain.FestivalType;
import com.festival.budgetassist.festival.domain.VenueType;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;

/**
 * baseline backtest 전용 "available-feature 재정규화" 유사도 계산 (지시사항 5절).
 *
 * <p>production {@code SimilarityCalculator}는 venueType이 없으면 고정된 낮은 점수(one-undecided
 * 취급)를, durationDays가 없으면 고정된 낮은 점수(durationMissingScore)를 준다 - 이는 production
 * 데이터(2026 단일 연도)에서는 두 필드가 항상 값을 갖는(UNDECIDED조차 "값이 있는" 상태) 전제 하의
 * 설계다. 다년도 데이터는 2017~2024 대부분 venueType 개념 자체가 없고(컬럼이 비어 있음), duration도
 * 원문에 명시적 일수가 없는 행이 흔하다 - 이런 "값이 아예 없음"에 고정 페널티 점수를 강제로 주면
 * 옛 데이터일수록 부당하게 낮은 유사도를 받는다.
 *
 * <p>그래서 이 클래스는 두 record 모두 해당 feature 값을 가진 경우에만 그 feature를 similarity
 * 합산에 포함시키고, 없으면 그 feature의 가중치까지 합계에서 완전히 제외해 나머지 가중치로
 * 재정규화한다:
 * {@code similarity = sum(featureScore*weight for available) / sum(weight for available)}.
 * festivalType/region은 두 데이터셋 모두 항상 값을 가지므로(비어 있으면 애초에 후보/평가대상
 * 풀에서 제외됨 - {@link MultiYearFeatureResolver}) 항상 포함된다.</p>
 *
 * <p>가중치/하위 점수 자체는 전부 {@link AlgorithmConfig}(production과 동일한 공개 설정 Bean)에서
 * 읽어 온다 - production similarity 코드는 전혀 건드리지 않지만 같은 숫자를 그대로 재사용한다.</p>
 */
@Component
class MultiYearSimilarityCalculator {

    private final AlgorithmConfig config;

    MultiYearSimilarityCalculator(AlgorithmConfig config) {
        this.config = config;
    }

    MultiYearSimilarityScore compute(MultiYearBacktestQuery target, MultiYearFestivalRecord candidate) {
        double typeScore = typeScore(target.typeTokens(), MultiYearFeatureResolver.resolveTypeTokens(candidate));
        double regionScore = regionScore(target, candidate);

        boolean venueAvailable = target.venueType() != null && candidate.getVenueType() != null;
        double venueScore = venueAvailable ? venueTypeScore(target.venueType(), candidate.getVenueType()) : 0.0;

        boolean durationAvailable = target.durationDays() != null && candidate.getDurationDays() != null;
        double durationScore = durationAvailable ? durationScore(target.durationDays(), candidate.getDurationDays()) : 0.0;

        double weightSum = config.getFestivalTypeWeight() + config.getRegionWeight()
                + (venueAvailable ? config.getVenueTypeWeight() : 0.0)
                + (durationAvailable ? config.getDurationWeight() : 0.0);
        double scoreSum = typeScore * config.getFestivalTypeWeight() + regionScore * config.getRegionWeight()
                + (venueAvailable ? venueScore * config.getVenueTypeWeight() : 0.0)
                + (durationAvailable ? durationScore * config.getDurationWeight() : 0.0);

        double similarity = weightSum > 0 ? scoreSum / weightSum : 0.0;
        double weight = similarity * similarity;

        return new MultiYearSimilarityScore(typeScore, regionScore, venueAvailable, venueScore,
                durationAvailable, durationScore, similarity, weight);
    }

    private double typeScore(Set<FestivalType> requested, Set<FestivalType> candidate) {
        return MultiYearFeatureResolver.typesOverlap(requested, candidate)
                ? config.getFestivalTypeSameScore() : config.getFestivalTypeDifferentScore();
    }

    private double regionScore(MultiYearBacktestQuery target, MultiYearFestivalRecord candidate) {
        boolean districtRequested = target.district() != null;
        String candidateDistrict = MultiYearFeatureResolver.resolveDistrict(candidate);
        if (districtRequested && target.district().equals(candidateDistrict)) {
            return config.getRegionSameDistrictScore();
        }
        if (target.regionCode() == candidate.getRegionCode()) {
            return config.getRegionSameProvinceScore();
        }
        return config.getRegionDifferentProvinceScore();
    }

    private double venueTypeScore(VenueType requested, VenueType candidate) {
        if (requested == candidate) {
            return config.getVenueTypeSameScore();
        }
        if (requested == VenueType.UNDECIDED || candidate == VenueType.UNDECIDED) {
            return config.getVenueTypeOneUndecidedScore();
        }
        return config.getVenueTypeDifferentScore();
    }

    private double durationScore(int requestedDays, int candidateDays) {
        double ratio = (requestedDays + 1.0) / (candidateDays + 1.0);
        return Math.exp(-Math.abs(Math.log(ratio)));
    }
}