package com.festival.budgetassist.estimate;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * 예산 추정 알고리즘의 모든 매직넘버를 한곳에 모아둔 설정 객체 (안내서 9~10장 + 사용자 요청).
 * {@code festival.algorithm.*} 프로퍼티로 application.yml에서 값을 바꿀 수 있으며,
 * 여기 적힌 기본값은 안내서에 명시된 초깃값을 그대로 옮긴 것이다.
 *
 * <p>알고리즘 동작을 바꿀 때는 이 클래스만 고치면 되고, 서비스 코드에는 숫자를 직접 쓰지 않는다.</p>
 */
@Component
@ConfigurationProperties(prefix = "festival.algorithm")
@Getter
@Setter
public class AlgorithmConfig {

    /** 응답에 그대로 노출되는 알고리즘 버전. 계산식이 바뀌면 반드시 올린다. */
    private String version = "v1.0.0";

    // --- 유사도 가중치 (합이 1.0이어야 한다, 안내서 9.4) ---
    private double festivalTypeWeight = 0.40;
    private double regionWeight = 0.25;
    private double venueTypeWeight = 0.20;
    private double durationWeight = 0.15;

    // --- 유사도 하위 점수 ---
    private double festivalTypeSameScore = 1.00;
    private double festivalTypeDifferentScore = 0.10;

    private double regionSameDistrictScore = 1.00;
    private double regionSameProvinceScore = 0.80;
    private double regionDifferentProvinceScore = 0.30;

    private double venueTypeSameScore = 1.00;
    private double venueTypeOneUndecidedScore = 0.45;
    private double venueTypeDifferentScore = 0.25;

    /** 개최기간 데이터가 없는 후보에게 고정으로 부여하는 기간 점수. */
    private double durationMissingScore = 0.35;

    /** 이 값 미만인 후보는 최종 표본에서 제외한다. */
    private double similarityMinThreshold = 0.35;

    // --- 개최기간에 따른 예산 보정 (안내서 9.5) ---
    private double durationElasticity = 0.55;
    private double durationRatioClampMin = 0.5;
    private double durationRatioClampMax = 2.0;

    // --- 표본 크기 (안내서 9.7) ---
    private int minSampleCount = 8;
    private int recommendedSampleCount = 20;
    private int maxSampleCount = 50;

    // --- 예비비 (안내서 9.8) ---
    private double contingencyBaseRate = 0.05;
    private double contingencyMaxExtraRate = 0.10;

    // --- 신뢰도 가중치 (안내서 10장) ---
    private double confidenceSampleWeight = 0.30;
    private double confidenceSimilarityWeight = 0.40;
    private double confidenceStabilityWeight = 0.20;
    private double confidenceCompletenessWeight = 0.10;

    private double confidenceHighThreshold = 80;
    private double confidenceMediumThreshold = 60;

    /** 표본이 이 값 미만이면 신뢰도 점수를 confidenceLowSampleCapScore로 상한한다. */
    private int confidenceLowSampleCap = 5;
    private double confidenceLowSampleCapScore = 55;

    /** 표본이 이 값 미만이면 "데이터 부족" 경고를 붙인다. */
    private int confidenceInsufficientSampleThreshold = 3;

    /** sampleScore = min(sampleCount / 이 값, 1). */
    private double confidenceSampleScoreDivisor = 25;

    /** dispersionRatio = min((P75-P25)/max(P50,1), 이 값) / 이 값. */
    private double confidenceDispersionCap = 1.5;

    // --- 이상치 완화 (안내서 9.6) ---
    private double winsorizeLowerPercentile = 0.05;
    private double winsorizeUpperPercentile = 0.95;

    /** 추천 기준 예산에 사용하는 백분위 (안내서 9.8: recommendedBase = max(estimatedBudget, weightedP60)). */
    private double recommendedBasePercentile = 0.60;

    // =====================================================================
    // Confidence v1.1 후보 (비교 전용). legacy 신뢰도 공식은 위 confidence* 필드로
    // 그대로 유지되고, 아래 값들은 ConfidenceCalculator.calculateV11에서만 쓰인다.
    // 예산 추정 자체(유사도/기간보정/winsorize/추천예산/등급 threshold)에는 영향 없음.
    // =====================================================================

    /** effectiveSampleScore = min(effectiveSampleSize / 이 값, 1). */
    private double confidenceV11EffectiveSampleDivisor = 30;

    private double confidenceV11SampleWeight = 0.20;
    private double confidenceV11SimilarityWeight = 0.35;
    private double confidenceV11StabilityWeight = 0.25;
    private double confidenceV11CompletenessWeight = 0.10;
    private double confidenceV11ScopeWeight = 0.10;

    // --- fallback 단계별 scopeScore (v1.1에서 confidence 계산에 사용, v1.2에서는 미사용).
    // v1.2 비교 결과 confidence에서는 제외했지만, 설명용 API(scopeWeightBreakdown)에는
    // 이 숫자 자체가 필요 없고 fallback 단계 이름만 쓰인다 - 그래도 값은 남겨둔다
    // (v1.1을 계속 비교 대상으로 유지하기 위함, v1.3에서 재검토 가능). ---
    private double scopeScoreSameDistrictTypeVenue = 1.00;
    private double scopeScoreSameRegionTypeVenue = 0.90;
    private double scopeScoreNationwideTypeVenue = 0.70;
    private double scopeScoreSameRegionType = 0.60;
    private double scopeScoreNationwideType = 0.45;
    private double scopeScoreGlobalSimilarity = 0.30;

    // =====================================================================
    // Confidence v1.2 후보 (비교 전용). v1.1과 같은 effectiveSampleScore/log 기반
    // stabilityScore/completenessScore를 그대로 재사용하되, scopeScore 항을 완전히
    // 제외하고 남은 4개 요소로만 재가중한다(합 1.00).
    // =====================================================================
    private double confidenceV12SampleWeight = 0.20;
    private double confidenceV12SimilarityWeight = 0.40;
    private double confidenceV12StabilityWeight = 0.30;
    private double confidenceV12CompletenessWeight = 0.10;
}