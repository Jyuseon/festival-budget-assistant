package com.festival.budgetassist.multiyear.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.festival.budgetassist.festival.domain.Region;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class MultiYearInflationExperimentServiceTest extends MultiYearBacktestTestSupport {

    @Autowired
    private MultiYearBacktestService backtestService;
    @Autowired
    private MultiYearSeriesCorrectionBacktestService correctionService;
    @Autowired
    private AnnualPriceIndexProvider priceIndexProvider;

    @BeforeEach
    void setUp() {
        initBatch();
    }

    @Test
    void inflationScalesEstimateExactlyByCpiRatio_whenNoWinsorizeClippingOccurs() {
        // 후보 1건뿐이라(모집단=후보 자신) winsorize 상/하한이 그 값 자체와 같아 clip이 no-op이다 -
        // 이 상황에서는 inflationFactor를 그대로 곱한 값이 estimatedBudget이어야 한다(가중
        // 기하평균은 표본이 1건이면 그 값 자체).
        MultiYearFestivalRecord candidate2020 = row(2020, 1, "물가테스트축제", Region.GYEONGGI, "가평군", "CULTURE_ART", 100); // 100백만원
        MultiYearFestivalRecord target2024 = row(2024, 2, "물가테스트축제평가", Region.GYEONGGI, "가평군", "CULTURE_ART", 150);

        List<MultiYearFestivalRecord> all = recordRepository.findAll();
        MultiYearFoldCorrectionResult withoutInflation = correctionService.runFold(all, MultiYearBacktestFold.SECONDARY_2024,
                MultiYearSeriesCorrectionMode.S0_BASELINE, false);
        MultiYearFoldCorrectionResult withInflation = correctionService.runFold(all, MultiYearBacktestFold.SECONDARY_2024,
                MultiYearSeriesCorrectionMode.S0_BASELINE, true);

        assertEquals(1, withoutInflation.predictions().size());
        assertEquals(1, withInflation.predictions().size());

        long estimatedWithout = withoutInflation.predictions().get(0).estimatedBudget();
        long estimatedWith = withInflation.predictions().get(0).estimatedBudget();

        double expectedFactor = priceIndexProvider.get(2024).orElseThrow().indexValue()
                / priceIndexProvider.get(2020).orElseThrow().indexValue();
        long expectedEstimatedWith = Math.round(estimatedWithout * expectedFactor);

        assertEquals(100_000_000L, estimatedWithout, "물가보정 없으면 후보 원본 예산 그대로여야 함");
        assertEquals(expectedEstimatedWith, estimatedWith, 1,
                "물가보정 있으면 CPI(2024)/CPI(2020) 비율만큼 정확히 커져야 함(winsorize clip 없는 상황)");
        assertTrue(estimatedWith > estimatedWithout, "2020->2024는 물가가 올랐으므로 보정 후 값이 더 커야 함");
    }

    @Test
    void candidateSelectionIsIdenticalRegardlessOfInflation() {
        for (int y = 2018; y <= 2023; y++) {
            row(y, y, "선정테스트축제" + y, Region.GYEONGGI, "이천시", "CULTURE_ART", 100 + y);
        }
        MultiYearFestivalRecord target = row(2024, 100, "선정테스트평가", Region.GYEONGGI, "이천시", "CULTURE_ART", 130);

        List<MultiYearFestivalRecord> all = recordRepository.findAll();
        MultiYearBacktestService.FinalSample withoutInflation = backtestService.selectFinalSample(target, all, false);
        MultiYearBacktestService.FinalSample withInflation = backtestService.selectFinalSample(target, all, true);

        List<Long> idsWithout = withoutInflation.finalSample().stream().map(c -> c.record().getId()).toList();
        List<Long> idsWith = withInflation.finalSample().stream().map(c -> c.record().getId()).toList();

        assertEquals(idsWithout, idsWith, "물가보정 켜짐/꺼짐과 무관하게 선택된 candidate(순서 포함)가 완전히 같아야 함");
    }

    @Test
    void addingFutureYearRecord_neverChangesInflationAdjustedPastFoldResult() {
        MultiYearFestivalRecord candidate2020 = row(2020, 1, "누출테스트축제", Region.GYEONGGI, "가평군", "CULTURE_ART", 100);
        MultiYearFestivalRecord target2024 = row(2024, 2, "누출테스트평가", Region.GYEONGGI, "가평군", "CULTURE_ART", 150);

        List<MultiYearFestivalRecord> before = recordRepository.findAll();
        MultiYearFoldCorrectionResult beforeResult = correctionService.runFold(before, MultiYearBacktestFold.SECONDARY_2024,
                MultiYearSeriesCorrectionMode.S0_BASELINE, true);
        long beforeEstimate = beforeResult.predictions().get(0).estimatedBudget();

        // 2026년 CPI는 2024 fold에서 절대 조회되면 안 된다 - 미래 record를 추가해도 결과가
        // 바뀌지 않아야 한다(leakage 없음의 실질적 증거).
        row(2026, 300, "누출테스트축제", Region.GYEONGGI, "가평군", "CULTURE_ART", 999_999);

        List<MultiYearFestivalRecord> after = recordRepository.findAll();
        MultiYearFoldCorrectionResult afterResult = correctionService.runFold(after, MultiYearBacktestFold.SECONDARY_2024,
                MultiYearSeriesCorrectionMode.S0_BASELINE, true);
        long afterEstimate = afterResult.predictions().get(0).estimatedBudget();

        assertEquals(beforeEstimate, afterEstimate, "2024 fold의 물가보정 결과는 미래 record 추가 전후로 완전히 같아야 함");
    }
}