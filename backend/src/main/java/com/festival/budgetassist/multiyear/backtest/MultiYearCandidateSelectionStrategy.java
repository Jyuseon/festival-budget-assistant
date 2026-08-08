package com.festival.budgetassist.multiyear.backtest;

import java.util.List;

import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;

/**
 * 후보 선정(계층형 fallback 등) 전략 인터페이스 - CandidateSelector concentration 분석 및 개선안
 * 비교("selector lab") 전용 추상화다.
 *
 * <p>{@link MultiYearCandidateSelector}(V0, 지금까지 baseline S0/실험 API가 실제로 쓰는 유일한
 * "실서비스 경로" 구현체)가 이 인터페이스의 첫 구현체이고, {@code MultiYearSelectorLabV1YearCap}/
 * {@code V2DiversifiedTopK}/{@code V3MinDistinctYears}/{@code V4Hybrid}는 전부 분석/backtest
 * 전용 구현체다 - production({@code /budget-assistant} 다년도 실험 섹션 포함) 어디에서도 V1~V4를
 * 참조하지 않는다.</p>
 *
 * <p>어느 구현체를 쓰든 선정 이후 단계({@link MultiYearBacktestService#scoreAndFinalize})는
 * 완전히 동일한 유사도/기간보정/winsorize/threshold/상위 N건 컷 공식을 그대로 적용한다 - "선정
 * 전략만 바뀌고 채점 공식은 절대 바뀌지 않는다"가 이 인터페이스를 둔 유일한 목적이다.</p>
 */
interface MultiYearCandidateSelectionStrategy {

    MultiYearCandidateSelectionResult select(List<MultiYearFestivalRecord> trainingPool, MultiYearBacktestQuery target);
}