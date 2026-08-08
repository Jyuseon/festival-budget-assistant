package com.festival.budgetassist.multiyear.backtest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.Set;

import com.festival.budgetassist.festival.domain.FestivalType;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;

/**
 * {@link MultiYearFestivalRecord}에서 baseline backtest가 쓰는 feature를 뽑아내는 순수 함수 모음.
 *
 * <p>district는 {@code multiyear.series.DistrictPlaceholderNormalizer}(region-level placeholder를
 * null로 강등하는 정규화)를 의도적으로 재사용하지 않는다 - 그 클래스는 package-private이라 다른
 * 패키지에서 접근할 수 없고, "가능한 한 현재(순수) 알고리즘 구조"라는 baseline 취지에도 district
 * 문자열을 있는 그대로 비교하는 편이 더 맞다("본청"/"시자체" 같은 placeholder는 단순히 실제
 * district와 일치하지 않아 SAME_DISTRICT_TYPE_VENUE 단계에서 걸러지고 더 넓은 지역 단위로
 * 자연스럽게 fallback된다 - 안전한 방향의 단순화다).</p>
 */
final class MultiYearFeatureResolver {

    private MultiYearFeatureResolver() {
    }

    /** districtText 우선, 없으면 districtRaw. 공백뿐이면 null. */
    static String resolveDistrict(MultiYearFestivalRecord r) {
        String candidate = null;
        if (r.getDistrictText() != null && !r.getDistrictText().isBlank()) {
            candidate = r.getDistrictText().trim();
        } else if (r.getDistrictRaw() != null && !r.getDistrictRaw().isBlank()) {
            candidate = r.getDistrictRaw().trim();
        }
        return candidate;
    }

    /**
     * festivalType 컬럼("CULTURE_ART|NATURE_ECOLOGY"처럼 파이프로 연결된 복합값일 수 있음)을
     * 알려진 {@link FestivalType} 5종 집합으로 파싱한다. OTHER/UNKNOWN이나 매핑 불가 토큰은
     * 조용히 무시한다(강제 매핑 금지 원칙 유지) - 전부 무시되면 빈 Set을 반환한다.
     */
    static Set<FestivalType> resolveTypeTokens(MultiYearFestivalRecord r) {
        Set<FestivalType> result = new LinkedHashSet<>();
        if (r.getFestivalType() == null || r.getFestivalType().isBlank()) {
            return result;
        }
        for (String token : r.getFestivalType().split("\\|")) {
            String trimmed = token.trim();
            try {
                result.add(FestivalType.valueOf(trimmed));
            } catch (IllegalArgumentException ignored) {
                // OTHER/UNKNOWN/매핑 불가 토큰 - 무시(강제 매핑 금지)
            }
        }
        return result;
    }

    static boolean typesOverlap(Set<FestivalType> a, Set<FestivalType> b) {
        for (FestivalType t : a) {
            if (b.contains(t)) {
                return true;
            }
        }
        return false;
    }

    /** budgetTotalMillion(백만원, BigDecimal) -> 원 단위 long. null이면 호출 전에 걸러져야 한다. */
    static long budgetKrw(MultiYearFestivalRecord r) {
        BigDecimal million = r.getBudgetTotalMillion();
        if (million == null) {
            throw new IllegalStateException("budgetTotalMillion이 null인 record는 사전에 제외되어야 합니다: id=" + r.getId());
        }
        return million.multiply(BigDecimal.valueOf(1_000_000)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}