package com.festival.budgetassist.estimate;

import com.festival.budgetassist.festival.domain.FestivalRecord;

/**
 * 후보 하나 + 유사도 점수 + 기간 보정 예산 + winsorize(이상치 완화) 적용 후 예산.
 * {@code adjustedBudgetKrw}는 화면/응답에 노출하는 "기간 보정 예산"이고,
 * {@code winsorizedBudgetKrw}는 통계 계산 내부에서만 쓰는 값이다(원본 데이터를 바꾸지 않음).
 */
record ScoredCandidate(
        FestivalRecord record,
        SimilarityScore score,
        double adjustedBudgetKrw,
        double winsorizedBudgetKrw
) {
}