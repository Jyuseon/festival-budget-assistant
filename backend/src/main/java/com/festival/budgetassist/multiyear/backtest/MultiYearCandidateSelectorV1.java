package com.festival.budgetassist.multiyear.backtest;

import java.util.List;

import org.springframework.stereotype.Component;

import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;

/**
 * 다년도 Planning Assistant 경로("selector lab" 분석에서 확정된 후보)의 첫 정식 selector.
 *
 * <p>{@code MultiYearSelectorLabV4Hybrid}(cap=year concentration 상한, qualityLossBudget=품질
 * 손실 허용폭)를 파라미터 cap=0.50/qualityLossBudget=0.05로 고정한 것이다. 이 값은 9개 조합
 * (cap 0.40/0.50/0.60 x qualityLossBudget 0.05/0.10/0.15) sensitivity 비교에서 다음 기준으로
 * 선택했다:
 * <ul>
 *   <li>cap은 0.40~0.60 범위에서 결과가 거의 동일해(composition/backtest 전부) 중간값 0.50을
 *       그대로 채택 - 특정 cap 값에 결과가 민감하지 않다는 뜻이라 임의성이 적다.</li>
 *   <li>qualityLossBudget은 0.05가 0.10/0.15보다 <b>대형(1B~3B, &gt;3B) 축제 과소추정을 거의
 *       악화시키지 않는다</b>(2026 fold medianSignedLogError: 1B~3B -1.53 vs -1.70,
 *       &gt;3B -2.57 vs -2.76 - loss=0.10/0.15는 V0(-1.68/-2.58) 대비 더 나빠지지만 loss=0.05는
 *       거의 그대로이거나 오히려 소폭 개선된다), 동시에 MdAPE(64.5% vs V0 69.2%)와 소형(&lt;=100M)
 *       과대추정 완화(+0.957 vs V0 +1.094)도 충분히 달성한다. loss=0.10/0.15는 P75/P90 APE를
 *       조금 더 낮추지만(93.7%/290% vs 96.4%/307%) 그 대가로 대형축제 편향을 악화시키므로,
 *       "대형축제 과소추정을 지나치게 악화시키지 않는다"는 선정 기준을 우선해 0.05를 택했다.</li>
 * </ul>
 *
 * <p>{@code /budget-assistant}에 이미 연결된 기존 "다년도 실험 분석"(Baseline S0,
 * {@link MultiYearBacktestService#predictForQuery})은 여전히 V0({@link
 * MultiYearCandidateSelector})만 쓴다 - 이 클래스는 아직 그 경로에 연결되지 않았고, 새로 만든
 * {@link MultiYearBacktestService#estimateForPlanning}에서만 쓰인다. 기존 2026 production
 * CandidateSelector({@code com.festival.budgetassist.estimate.CandidateSelector})는 이
 * 클래스와 완전히 무관하다.</p>
 */
@Component
class MultiYearCandidateSelectorV1 implements MultiYearCandidateSelectionStrategy {

    static final double YEAR_CONCENTRATION_CAP = 0.50;
    static final double QUALITY_LOSS_BUDGET = 0.05;

    private final MultiYearSelectorLabV4Hybrid delegate;

    MultiYearCandidateSelectorV1(com.festival.budgetassist.estimate.AlgorithmConfig config,
                                  MultiYearSimilarityCalculator similarityCalculator) {
        this.delegate = new MultiYearSelectorLabV4Hybrid(config, similarityCalculator, YEAR_CONCENTRATION_CAP, QUALITY_LOSS_BUDGET);
    }

    @Override
    public MultiYearCandidateSelectionResult select(List<MultiYearFestivalRecord> trainingPool, MultiYearBacktestQuery target) {
        return delegate.select(trainingPool, target);
    }
}