package com.festival.budgetassist.multiyear.backtest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;

/**
 * 2026 실제 개최계획 데이터(region x festivalType x venueType x durationDays 조합)에서
 * selector concentration 분석용 벤치마크 쿼리 목록을 만든다 (5절: "2026 실제 데이터에 존재하는
 * 조합을 이용해서 가능한 한 충분한 케이스를 돌려줘").
 *
 * <p>district=null 벤치마크(광역 단위 조회)와 district가 있는 벤치마크(실제 서비스에서 시군구를
 * 입력한 경우)를 따로 만든다 - 두 경우 {@link MultiYearCandidateSelector}의 fallback 사다리
 * 시작점이 다르므로(district 있으면 1단계부터, 없으면 4단계부터) concentration 양상도 다르게
 * 나타날 수 있다.</p>
 */
final class MultiYearSelectorLabBenchmarkBuilder {

    private MultiYearSelectorLabBenchmarkBuilder() {
    }

    /** district를 항상 null로 고정한 벤치마크 - (region, typeTokens, venueType, durationDays) 조합으로 중복 제거. */
    static List<MultiYearBacktestQuery> buildNoDistrictBenchmark(List<MultiYearFestivalRecord> records2026) {
        Map<String, MultiYearBacktestQuery> byKey = new LinkedHashMap<>();
        for (MultiYearFestivalRecord r : records2026) {
            var typeTokens = MultiYearFeatureResolver.resolveTypeTokens(r);
            if (r.getRegionCode() == null || typeTokens.isEmpty() || r.getVenueType() == null || r.getDurationDays() == null) {
                continue;
            }
            MultiYearBacktestQuery query = new MultiYearBacktestQuery(r.getRegionCode(), null, typeTokens, r.getVenueType(), r.getDurationDays());
            byKey.putIfAbsent(keyOf(query), query);
        }
        return new ArrayList<>(byKey.values());
    }

    /** district가 실제로 있는 2026 record만 - (region, district, typeTokens, venueType, durationDays) 조합으로 중복 제거. */
    static List<MultiYearBacktestQuery> buildDistrictBenchmark(List<MultiYearFestivalRecord> records2026) {
        Map<String, MultiYearBacktestQuery> byKey = new LinkedHashMap<>();
        for (MultiYearFestivalRecord r : records2026) {
            String district = MultiYearFeatureResolver.resolveDistrict(r);
            var typeTokens = MultiYearFeatureResolver.resolveTypeTokens(r);
            if (r.getRegionCode() == null || district == null || typeTokens.isEmpty()
                    || r.getVenueType() == null || r.getDurationDays() == null) {
                continue;
            }
            MultiYearBacktestQuery query = new MultiYearBacktestQuery(r.getRegionCode(), district, typeTokens, r.getVenueType(), r.getDurationDays());
            byKey.putIfAbsent(keyOf(query), query);
        }
        return new ArrayList<>(byKey.values());
    }

    private static String keyOf(MultiYearBacktestQuery query) {
        String typeKey = query.typeTokens().stream().map(Enum::name).sorted().reduce((a, b) -> a + "+" + b).orElse("");
        return query.regionCode() + "|" + (query.district() == null ? "-" : query.district()) + "|" + typeKey
                + "|" + query.venueType() + "|" + query.durationDays();
    }
}