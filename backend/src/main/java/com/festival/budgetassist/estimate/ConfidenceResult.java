package com.festival.budgetassist.estimate;

/**
 * level: HIGH/MEDIUM/LOW. label: 화면에 그대로 보여줄 한국어 등급명("높음"/"보통"/"낮음"/"데이터 부족").
 *
 * <p>sampleScore/similarityScore/stabilityScore/completenessScore는 최종 score를 만드는
 * 4개 구성요소를 그대로 보존한 것 - 개발용 계산 상세 화면과 분석 도구(ConfidenceAnalysisRunner)가
 * 이 값들을 그대로 노출해서 신뢰도 공식 자체를 검증할 수 있게 한다.</p>
 */
record ConfidenceResult(
        double score,
        String level,
        String label,
        double sampleScore,
        double similarityScore,
        double stabilityScore,
        double completenessScore
) {
}