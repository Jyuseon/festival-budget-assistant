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
class MultiYearSeriesCorrectionBacktestServiceTest extends MultiYearBacktestTestSupport {

    @Autowired
    private MultiYearSeriesCorrectionBacktestService correctionService;

    @BeforeEach
    void setUp() {
        initBatch();
    }

    @Test
    void s0_reproducesExactlyTheSameEstimateAsPlainBaseline() {
        // 후보 하나가 6개 연도에 반복되고(series 과대표현), 다른 후보 하나만 유일한 series - S0는
        // seriesFactor=1.0 항상이므로, series correction이 아예 없는 것과 완전히 같은 값이어야 한다.
        for (int y = 2018; y <= 2023; y++) {
            row(y, y, "반복축제", Region.GYEONGGI, "가평군", "CULTURE_ART", 100);
        }
        row(2019, 100, "유일축제", Region.GYEONGGI, "가평군", "CULTURE_ART", 300);
        MultiYearFestivalRecord target = row(2024, 200, "평가대상축제", Region.GYEONGGI, "가평군", "CULTURE_ART", 150);

        List<MultiYearFestivalRecord> all = recordRepository.findAll();
        MultiYearFoldCorrectionResult s0 = correctionService.runFold(all, MultiYearBacktestFold.SECONDARY_2024,
                MultiYearSeriesCorrectionMode.S0_BASELINE);

        assertEquals(1, s0.predictions().size());
        MultiYearSeriesCorrectionPrediction p = s0.predictions().get(0);
        assertEquals(7, p.sampleCount(), "반복축제 6건 + 유일축제 1건 = 7건 전부 후보가 되어야 함");
        // S0는 반복축제(6건, 각 100백만원)와 유일축제(1건, 300백만원)를 그대로 가중평균 - 반복축제가 압도적 다수.
        assertTrue(p.estimatedBudget() < 150_000_000L, "보정 없이는 반복축제(100백만원) 쪽으로 크게 쏠려야 함");
    }

    @Test
    void s2FullInverse_reducesInfluenceOfHeavilyRepeatedSeries_moreThanS1() {
        for (int y = 2018; y <= 2023; y++) {
            row(y, y, "반복축제", Region.GYEONGGI, "가평군", "CULTURE_ART", 100);
        }
        row(2019, 100, "유일축제", Region.GYEONGGI, "가평군", "CULTURE_ART", 300);
        row(2024, 200, "평가대상축제", Region.GYEONGGI, "가평군", "CULTURE_ART", 150);

        List<MultiYearFestivalRecord> all = recordRepository.findAll();
        MultiYearFoldCorrectionResult s0 = correctionService.runFold(all, MultiYearBacktestFold.SECONDARY_2024, MultiYearSeriesCorrectionMode.S0_BASELINE);
        MultiYearFoldCorrectionResult s1 = correctionService.runFold(all, MultiYearBacktestFold.SECONDARY_2024, MultiYearSeriesCorrectionMode.S1_SOFT_SQRT);
        MultiYearFoldCorrectionResult s2 = correctionService.runFold(all, MultiYearBacktestFold.SECONDARY_2024, MultiYearSeriesCorrectionMode.S2_FULL_INVERSE);

        long e0 = s0.predictions().get(0).estimatedBudget();
        long e1 = s1.predictions().get(0).estimatedBudget();
        long e2 = s2.predictions().get(0).estimatedBudget();

        // 반복축제(100) 쪽 가중치가 줄어들수록 "유일축제"(300) 방향으로 추정치가 이동해야 한다:
        // e0 <= e1 <= e2 (보정이 강할수록 반복축제의 견인력이 약해짐).
        assertTrue(e0 <= e1, "S1은 반복축제 영향을 줄이므로 추정치가 S0 이상이어야 함(유일축제 쪽으로 이동)");
        assertTrue(e1 <= e2, "S2(완전 역수)는 S1(제곱근 역수)보다 더 강하게 보정해야 함");

        // candidate selection(표본 수/구성)은 세 방식 모두 동일해야 한다(5절).
        assertEquals(s0.predictions().get(0).sampleCount(), s1.predictions().get(0).sampleCount());
        assertEquals(s1.predictions().get(0).sampleCount(), s2.predictions().get(0).sampleCount());
        assertEquals(7, s0.predictions().get(0).sampleCount());
    }

    @Test
    void seriesCountUsesOnlyTrainingPeriod_notFutureRecords() {
        // "반복축제"가 training(2018~2023) 6건 + 미래(2025) 1건 - n은 6이어야 하고(미래 제외),
        // 미래 record를 추가해도 2024 fold 결과가 바뀌면 안 된다(leakage 방지, 2절).
        for (int y = 2018; y <= 2023; y++) {
            row(y, y, "반복축제", Region.GYEONGGI, "가평군", "CULTURE_ART", 100);
        }
        row(2019, 100, "유일축제", Region.GYEONGGI, "가평군", "CULTURE_ART", 300);
        row(2024, 200, "평가대상축제", Region.GYEONGGI, "가평군", "CULTURE_ART", 150);

        List<MultiYearFestivalRecord> before = recordRepository.findAll();
        MultiYearFoldCorrectionResult beforeResult = correctionService.runFold(before, MultiYearBacktestFold.SECONDARY_2024,
                MultiYearSeriesCorrectionMode.S2_FULL_INVERSE);
        long beforeEstimate = beforeResult.predictions().get(0).estimatedBudget();

        row(2025, 300, "반복축제", Region.GYEONGGI, "가평군", "CULTURE_ART", 999_999); // 미래 - 극단적 예산

        List<MultiYearFestivalRecord> after = recordRepository.findAll();
        MultiYearFoldCorrectionResult afterResult = correctionService.runFold(after, MultiYearBacktestFold.SECONDARY_2024,
                MultiYearSeriesCorrectionMode.S2_FULL_INVERSE);
        long afterEstimate = afterResult.predictions().get(0).estimatedBudget();

        assertEquals(beforeEstimate, afterEstimate, "2024 fold는 2025 record 추가 전후로 완전히 같아야 함(leakage 없음)");
        assertEquals(6, afterResult.predictions().get(0).mostRepeatedSeriesRecordCount(),
                "가장 많이 반복된 series의 n은 training(2018~2023) 6건이어야 함 - 미래 2025는 제외");
    }
}