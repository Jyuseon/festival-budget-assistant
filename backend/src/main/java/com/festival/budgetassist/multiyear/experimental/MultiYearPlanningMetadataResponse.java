package com.festival.budgetassist.multiyear.experimental;

import java.util.List;

/**
 * GET /api/v1/experimental/multiyear-planning-metadata - `/budget-assistant` 다년도 계획예산
 * 분석 UI가 "기획연도" 선택지를 하드코딩하지 않고 채우기 위한 메타데이터.
 *
 * @param availablePlanningYears 현재 데이터로 planningYear로 선택 가능한 연도 목록
 *                                (= [보유 데이터 최신연도, +1] - 사용자 요청: "향후 데이터가
 *                                추가되더라도 가능한 연도를 하드코딩하지 않는 구조"). 예: 2026이
 *                                최신이면 [2026, 2027].
 * @param defaultPlanningYear UI 초기 선택값(= availablePlanningYears의 첫 값, 보통 "현재
 *                              연도" planning).
 * @param publishedPlanCompleteYears {@code MultiYearDatasetPublicationStatusValue
 *                                    .PUBLISHED_PLAN_COMPLETE}로 표시된 연도 목록 - UI가
 *                                    {@code INCLUDE_PUBLISHED_SAME_YEAR} 옵션을 미리 활성/
 *                                    비활성화하는 데 쓴다(요청을 보내 보지 않고도 판단 가능).
 */
public record MultiYearPlanningMetadataResponse(
        List<Integer> availablePlanningYears,
        Integer defaultPlanningYear,
        List<Integer> publishedPlanCompleteYears
) {
}