package com.festival.budgetassist.multiyear.backtest;

/**
 * {@link MultiYearPredictionResult#topCandidates}의 항목 1건 - 어떤 과거 축제가 이번 예측에
 * 얼마나 기여했는지 사람이 확인할 수 있게 하는 debugging/설명용 정보.
 *
 * @param sourceYear 후보의 datasetYear
 * @param venueType 원본에 없으면 null 그대로(강제로 OTHER/UNKNOWN으로 채우지 않음)
 * @param durationDays 원본에 없으면 null 그대로
 * @param originalBudgetKrw 후보의 원본 예산(물가/기간보정 전)
 * @param durationAdjustedBudgetKrw 기간보정 후 예산(winsorize 전 - winsorize는 표본 전체 통계에만 영향, 개별 후보 표시값에는 반영하지 않음)
 * @param finalWeight 최종 집계에 실제로 쓰인 weight(= similarity^2)
 * @param fallbackStage 이 후보가 처음 선택된 candidate selection 단계
 */
public record MultiYearPredictionCandidate(
        int sourceYear,
        String festivalName,
        String region,
        String district,
        String festivalType,
        String venueType,
        Integer durationDays,
        long originalBudgetKrw,
        long durationAdjustedBudgetKrw,
        double similarity,
        double finalWeight,
        String fallbackStage
) {
}