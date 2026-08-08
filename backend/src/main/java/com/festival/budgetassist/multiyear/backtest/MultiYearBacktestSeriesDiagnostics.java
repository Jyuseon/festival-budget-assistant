package com.festival.budgetassist.multiyear.backtest;

import java.util.List;

import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;
import com.festival.budgetassist.multiyear.series.FestivalNameNormalizer;

/**
 * backtest 예측 1건의 {@code distinctSeriesCount} 진단 컬럼을 계산하는 순수 함수.
 *
 * <p><b>festivalSeries v1({@code FestivalSeriesLinkingService}, district 정규화 + fuzzy HIGH +
 * strict chain linking)의 간소화된 근사치</b>다 - 결정적 클러스터링(정규화 이름 + region +
 * district 완전 일치)만 재현하고 fuzzy/chain 단계는 재현하지 않는다. 이유는 두 가지다:</p>
 * <ol>
 *   <li>이 값은 순수 참고용 진단 컬럼일 뿐 baseline 추정 계산(가중평균/유사도/신뢰도) 어디에도
 *       쓰이지 않는다 - "series 중복 보정"은 이번 baseline 단계에서 명시적으로 제외된 항목이라,
 *       지금 당장 완벽히 정밀할 필요가 없다(다음 단계에서 실제 보정을 구현할 때 정밀도를 높인다).</li>
 *   <li>fuzzy/chain 단계 코드({@code LevenshteinSimilarity}/{@code DistrictPlaceholderNormalizer})는
 *       {@code multiyear.series} 패키지 안에 package-private으로 갇혀 있어 재사용할 수 없고,
 *       이미 확정(freeze)된 festivalSeries v1 서비스 코드 자체는 "series linking 규칙을 변경하지
 *       마라"는 지시에 따라 전혀 건드리지 않는다.</li>
 * </ol>
 * <p>결정적 클러스터링만 쓰므로 실제 v1 series 수보다 같거나 약간 더 많게(과소 병합) 나올 수
 * 있다 - 안전한 방향의 근사치다.</p>
 *
 * <p><b>leakage 안전성</b>: 이 클래스는 주어진 {@code List<MultiYearFestivalRecord>}만 보는 순수
 * 함수다 - 호출자가 이미 cutoff(datasetYear &lt; targetYear)로 걸러낸 목록을 넘기기만 하면, 이
 * 함수 자체는 미래 데이터를 볼 방법이 없다(DB를 직접 조회하지 않음).</p>
 */
final class MultiYearBacktestSeriesDiagnostics {

    private MultiYearBacktestSeriesDiagnostics() {
    }

    record GroupKey(String region, String district, String normalizedName) {
    }

    static GroupKey keyOf(MultiYearFestivalRecord r) {
        String region = r.getRegionCode() != null ? r.getRegionCode().name() : "UNKNOWN_REGION";
        String district = MultiYearFeatureResolver.resolveDistrict(r);
        String normalizedName = FestivalNameNormalizer.normalize(r.getFestivalName());
        return new GroupKey(region, district, normalizedName);
    }

    /** 주어진 pool(예: 어떤 예측의 최종 후보 표본) 안에서 결정적 클러스터링 기준 distinct series 수. */
    static long distinctSeriesCount(List<MultiYearFestivalRecord> pool) {
        return pool.stream().map(MultiYearBacktestSeriesDiagnostics::keyOf).distinct().count();
    }
}