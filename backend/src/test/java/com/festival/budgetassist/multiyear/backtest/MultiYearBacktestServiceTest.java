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
import com.festival.budgetassist.multiyear.domain.BudgetQualityFlag;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class MultiYearBacktestServiceTest extends MultiYearBacktestTestSupport {

    @Autowired
    private MultiYearBacktestService backtestService;
    @Autowired
    private MultiYearBacktestDatasetBuilder datasetBuilder;

    @BeforeEach
    void setUp() {
        initBatch();
    }

    @Test
    void unitScaleSuspectAndMissingBudget_excludedFromTrainingAndEval() {
        row(2020, 1, "정상축제A", Region.GYEONGGI, "가평군", "CULTURE_ART", 100);
        row(2021, 2, "이상치축제", Region.GYEONGGI, "가평군", "CULTURE_ART", null, null, 999_999,
                BudgetQualityFlag.UNIT_SCALE_SUSPECT);
        row(2022, 3, "결측축제", Region.GYEONGGI, "가평군", "CULTURE_ART", null, null, 0,
                BudgetQualityFlag.MISSING_OR_NONPOSITIVE);
        row(2023, 4, "정상축제B", Region.GYEONGGI, "가평군", "CULTURE_ART", 120);
        // 2024 평가대상도 품질 불량이면 평가에서 제외되어야 한다.
        row(2024, 5, "이상치평가대상", Region.GYEONGGI, "가평군", "CULTURE_ART", null, null, 888_888,
                BudgetQualityFlag.UNIT_SCALE_SUSPECT);
        row(2024, 6, "정상평가대상", Region.GYEONGGI, "가평군", "CULTURE_ART", 110);

        List<MultiYearFestivalRecord> all = recordRepository.findAll();
        MultiYearBacktestDataset dataset = datasetBuilder.build(all, MultiYearBacktestFold.SECONDARY_2024);

        assertEquals(2, dataset.trainingPool().size(), "품질 불량 2건은 training에서 제외되어야 함");
        assertEquals(1, dataset.evalTargets().size(), "품질 불량 평가대상도 제외되어야 함");
        assertEquals(2, dataset.trainingExcludedLowQuality(), "training 기간의 UNIT_SCALE_SUSPECT+MISSING_OR_NONPOSITIVE 2건");
        assertEquals(1, dataset.evalExcludedLowQuality());

        MultiYearFoldResult result = backtestService.runFold(all, MultiYearBacktestFold.SECONDARY_2024);
        assertEquals(1, result.predictions().size());
        MultiYearBacktestPrediction prediction = result.predictions().get(0);
        assertEquals("정상평가대상", prediction.festivalName());
        assertTrue(prediction.estimatedBudget() > 0);
        assertTrue(prediction.sampleCount() > 0);
        assertTrue(prediction.dataQualityV3() >= 0 && prediction.dataQualityV3() <= 100);
    }

    @Test
    void targetWithoutVenue_skipsVenueTiers_stillGetsRegionLevelFallback() {
        // training: 같은 지역/유형이지만 venue 정보가 있는 candidate만 존재. recommendedSampleCount(기본
        // 20건) 이상을 채워야 SAME_REGION_TYPE 단계에서 표본 목표에 도달해 거기서 멈춘다 - 그래야
        // "venue가 필요한 1~3단계를 건너뛰었다"는 걸 최종 fallbackLevel로도 명확히 확인할 수 있다
        // (그렇지 않으면 표본이 모자라 5/6단계까지 계속 훑는 게 정상 동작이라 이 테스트의 의도와 안 맞는다).
        for (int i = 1; i <= 25; i++) {
            row(2020, i, "훈련축제" + i, Region.GYEONGGI, "이천시", "CULTURE_ART",
                    com.festival.budgetassist.festival.domain.VenueType.GREEN, null, 100 + i, BudgetQualityFlag.VALID);
        }
        // 평가대상은 venue 정보가 없다(옛 데이터 스타일).
        MultiYearFestivalRecord target = row(2024, 100, "평가축제", Region.GYEONGGI, "이천시", "CULTURE_ART", 130);

        List<MultiYearFestivalRecord> all = recordRepository.findAll();
        MultiYearFoldResult result = backtestService.runFold(all, MultiYearBacktestFold.SECONDARY_2024);

        assertEquals(1, result.predictions().size());
        MultiYearBacktestPrediction prediction = result.predictions().get(0);
        assertTrue(prediction.sampleCount() > 0, "venue 정보가 없어도 region/type 기반 fallback으로 후보를 찾아야 함");
        assertEquals("SAME_REGION_TYPE", prediction.fallbackLevel(),
                "target에 venue가 없으면 venue를 요구하는 1~3단계를 건너뛰고 SAME_REGION_TYPE부터 시작해야 함");
    }

    @Test
    void typicalRangeCoverage_trueWhenActualWithinP25P75() {
        for (int i = 1; i <= 8; i++) {
            row(2020, i, "훈련축제" + i, Region.GYEONGGI, "가평군", "CULTURE_ART", 100);
        }
        MultiYearFestivalRecord target = row(2024, 100, "평가축제", Region.GYEONGGI, "가평군", "CULTURE_ART", 100);

        List<MultiYearFestivalRecord> all = recordRepository.findAll();
        MultiYearFoldResult result = backtestService.runFold(all, MultiYearBacktestFold.SECONDARY_2024);

        assertEquals(1, result.predictions().size());
        MultiYearBacktestPrediction prediction = result.predictions().get(0);
        assertTrue(prediction.typicalRangeCoverage(),
                "training 전부가 actual과 똑같은 예산이면 P25~P75 범위 안에 actual이 들어와야 함");
        assertEquals(0.0, prediction.absolutePercentageError(), 1e-6);
    }
}