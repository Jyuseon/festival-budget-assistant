package com.festival.budgetassist.estimate;

import java.util.List;
import java.util.Map;

import com.festival.budgetassist.festival.domain.FestivalRecord;

/**
 * {@code level}은 목표 표본 수를 채우기 위해 실제로 사용한 가장 넓은 fallback 단계다.
 * {@code levelBreakdown}은 실제로 거쳐간 각 단계에서 몇 건이 새로 추가됐는지의 기록이고,
 * {@code originLevelByCandidateId}는 각 후보 하나하나가 "몇 단계에서 처음 들어왔는지"를
 * 기록한 것이다(둘 다 선택 로직 자체에는 영향을 주지 않는 관찰용 부가 정보 - 어떤 후보가
 * 뽑히는지, 언제 멈추는지는 전혀 바뀌지 않는다).
 */
record CandidateSelectionResult(
        FallbackLevel level,
        List<FestivalRecord> candidates,
        List<LevelContribution> levelBreakdown,
        Map<Long, FallbackLevel> originLevelByCandidateId
) {
}