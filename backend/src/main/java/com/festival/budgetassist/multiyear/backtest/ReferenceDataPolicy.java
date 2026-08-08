package com.festival.budgetassist.multiyear.backtest;

/**
 * Budget Planning Assistant가 어떤 연도까지의 참고 개최계획 데이터(reference planning data)를
 * 쓸지 결정하는 정책. {@code planningYear}(기획하려는 연도)를 기준으로 한다 - backtest의
 * {@code targetYear}/leakage-safe 평가 정책과는 별개다({@link
 * MultiYearBacktestService#estimateForPlanning}만 이 정책을 쓰고, {@link
 * MultiYearBacktestDatasetBuilder}/{@link MultiYearBacktestFold} 기반 backtest 경로는 항상
 * {@code datasetYear < targetYear}만 쓴다 - 절대 섞이지 않는다).
 */
public enum ReferenceDataPolicy {

    /** referenceYear &lt; planningYear. 예: 2027 기획 -> 2017~2026 전체를 참고 데이터로 사용. */
    HISTORICAL_ONLY,

    /**
     * referenceYear &lt;= planningYear. planningYear 자체의 데이터셋이 이미 "공개 완료"
     * ({@link MultiYearDatasetPublicationStatusValue#PUBLISHED_COMPLETE})인 경우에만 유효하다 -
     * 그렇지 않으면 {@link MultiYearBacktestService#estimateForPlanning}이 자동으로
     * {@link #HISTORICAL_ONLY}로 낮춰 적용하고, 응답의 {@code appliedReferenceDataPolicy}에 그
     * 사실을 그대로 드러낸다(요청한 정책을 조용히 다른 정책으로 바꿔치기하지 않는다).
     */
    INCLUDE_PUBLISHED_SAME_YEAR
}