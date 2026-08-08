package com.festival.budgetassist.multiyear.backtest;

import java.util.Set;

import com.festival.budgetassist.festival.domain.FestivalType;
import com.festival.budgetassist.festival.domain.Region;
import com.festival.budgetassist.festival.domain.VenueType;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;

/**
 * backtest 평가 대상 1건을 "이 조건으로 예산을 조회했다면"이라는 요청(query)으로 변환한 값.
 * production {@code BudgetEstimateRequest}에 대응하지만, 옛 데이터는 venueType/durationDays가
 * 원천적으로 없을 수 있어(그 해에는 그런 개념 자체가 없음) 둘 다 nullable이다 - null이면
 * {@link MultiYearSimilarityCalculator}/{@link MultiYearCandidateSelector}가 그 feature를
 * "요청하지 않음"으로 취급해 해당 feature가 필요한 단계/가중치를 건너뛴다.
 */
record MultiYearBacktestQuery(
        Region regionCode,
        String district,
        Set<FestivalType> typeTokens,
        VenueType venueType,
        Integer durationDays
) {

    static MultiYearBacktestQuery from(MultiYearFestivalRecord target) {
        return new MultiYearBacktestQuery(
                target.getRegionCode(),
                MultiYearFeatureResolver.resolveDistrict(target),
                MultiYearFeatureResolver.resolveTypeTokens(target),
                target.getVenueType(),
                target.getDurationDays()
        );
    }
}