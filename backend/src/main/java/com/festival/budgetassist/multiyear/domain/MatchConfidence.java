package com.festival.budgetassist.multiyear.domain;

/**
 * {@link MatchMethod#FUZZY} 연결/후보의 신뢰도 등급. HIGH만 실제로 자동 연결되고,
 * MEDIUM/LOW는 {@link FestivalSeriesMatchCandidate}(검토 목록)에만 기록되고 연결되지 않는다.
 *
 * <p>이번 단계의 점수 구간(threshold)은 분석용 잠정치이며 production 값으로 확정된 것이 아니다.</p>
 */
public enum MatchConfidence {
    HIGH,
    MEDIUM,
    LOW
}