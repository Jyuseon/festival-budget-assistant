package com.festival.budgetassist.multiyear.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.festival.budgetassist.festival.domain.FestivalType;
import com.festival.budgetassist.festival.domain.Region;
import com.festival.budgetassist.festival.domain.VenueType;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;

/**
 * {@link MultiYearBacktestService#predictForQuery}(즉석 예측)가 실제 backtest 평가 경로
 * ({@link MultiYearBacktestService#runFold})와 정확히 같은 계산을 재사용하는지 검증한다 -
 * "다년도 실험 분석" UI 작업 지시사항 4/18-A/18-B절의 가장 중요한 regression test다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class MultiYearPredictForQueryParityTest extends MultiYearBacktestTestSupport {

    @Autowired
    private MultiYearBacktestService backtestService;

    @BeforeEach
    void setUp() {
        initBatch();
    }

    @Test
    void predictForQuery_reproducesExactlyTheSameStatsAsBacktestS0() {
        for (int y = 2018; y <= 2025; y++) {
            row(y, y, "패리티테스트축제" + y, Region.GYEONGGI, "이천시", "CULTURE_ART",
                    VenueType.GREEN, 5, 100 + y, com.festival.budgetassist.multiyear.domain.BudgetQualityFlag.VALID);
        }
        // 2026 "미래" 평가대상 - backtest 쪽에서만 실제 target record로 쓰이고, predictForQuery는
        // 이 record의 예산을 전혀 모른 채(query 조건만 뽑아서) 같은 조건으로 예측해야 한다.
        MultiYearFestivalRecord target2026 = row(2026, 100, "패리티테스트평가", Region.GYEONGGI, "이천시", "CULTURE_ART",
                VenueType.GREEN, 5, 200, com.festival.budgetassist.multiyear.domain.BudgetQualityFlag.VALID);

        List<MultiYearFestivalRecord> all = recordRepository.findAll();

        MultiYearFoldResult backtestFoldResult = backtestService.runFold(all, MultiYearBacktestFold.PRIMARY_2026);
        MultiYearBacktestPrediction backtestPrediction = backtestFoldResult.predictions().stream()
                .filter(p -> p.recordId() == target2026.getId())
                .findFirst().orElseThrow();

        List<MultiYearFestivalRecord> trainingOnly = recordRepository.findByDatasetYearLessThan(2026);
        MultiYearPredictionResult liveResult = backtestService.predictForQuery(
                Region.GYEONGGI, "이천시", Set.of(FestivalType.CULTURE_ART), VenueType.GREEN, 5, trainingOnly);

        assertEquals(backtestPrediction.estimatedBudget(), liveResult.estimatedBudgetKrw(),
                "predictForQuery의 estimatedBudget이 backtest S0와 정확히 같아야 함");
        assertEquals(backtestPrediction.weightedAverageBudget(), liveResult.weightedAverageBudgetKrw());
        assertEquals(backtestPrediction.p25(), liveResult.p25Krw());
        assertEquals(backtestPrediction.p75(), liveResult.p75Krw());
        assertEquals(backtestPrediction.sampleCount(), liveResult.sampleCount());
        assertEquals(backtestPrediction.fallbackLevel(), liveResult.fallbackLevel());
    }

    @Test
    void predictForQuery_never2026_addingExtreme2026RecordDoesNotChangeResult() {
        for (int y = 2018; y <= 2025; y++) {
            row(y, y, "누출테스트축제" + y, Region.GYEONGGI, "이천시", "CULTURE_ART",
                    VenueType.GREEN, 5, 100 + y, com.festival.budgetassist.multiyear.domain.BudgetQualityFlag.VALID);
        }

        List<MultiYearFestivalRecord> before = recordRepository.findByDatasetYearLessThan(2026);
        MultiYearPredictionResult before2026Added = backtestService.predictForQuery(
                Region.GYEONGGI, "이천시", Set.of(FestivalType.CULTURE_ART), VenueType.GREEN, 5, before);

        row(2026, 999, "누출테스트축제2026", Region.GYEONGGI, "이천시", "CULTURE_ART",
                VenueType.GREEN, 5, 999_999, com.festival.budgetassist.multiyear.domain.BudgetQualityFlag.VALID);

        // repository 쿼리 자체가 2026을 걸러내는지 확인.
        List<MultiYearFestivalRecord> afterQuery = recordRepository.findByDatasetYearLessThan(2026);
        assertTrue(afterQuery.stream().noneMatch(r -> r.getDatasetYear() == 2026), "쿼리 결과에 2026 record가 있으면 안 됨");

        MultiYearPredictionResult afterQueryResult = backtestService.predictForQuery(
                Region.GYEONGGI, "이천시", Set.of(FestivalType.CULTURE_ART), VenueType.GREEN, 5, afterQuery);
        assertEquals(before2026Added.estimatedBudgetKrw(), afterQueryResult.estimatedBudgetKrw(),
                "쿼리 필터링만으로도 2026 극단치 record가 결과에 영향을 주면 안 됨");

        // 혹시나 findAll() 결과를 그대로 넘기더라도(더 넓은 목록), predictForQuery 내부의
        // MultiYearBacktestDatasetBuilder가 datasetYear<2026 조건을 다시 적용하므로 안전해야 한다.
        List<MultiYearFestivalRecord> allIncluding2026 = recordRepository.findAll();
        MultiYearPredictionResult evenWithAllRecords = backtestService.predictForQuery(
                Region.GYEONGGI, "이천시", Set.of(FestivalType.CULTURE_ART), VenueType.GREEN, 5, allIncluding2026);
        assertEquals(before2026Added.estimatedBudgetKrw(), evenWithAllRecords.estimatedBudgetKrw(),
                "findAll() 결과를 그대로 넘겨도(레포지토리 필터를 우회) datasetBuilder가 2026을 다시 걸러내 결과가 같아야 함");
    }

    @Test
    void predictForQuery_unitScaleSuspectCandidate_excludedFromSampleCount() {
        row(2020, 1, "정상후보", Region.GYEONGGI, "이천시", "CULTURE_ART", VenueType.GREEN, 5, 100,
                com.festival.budgetassist.multiyear.domain.BudgetQualityFlag.VALID);
        row(2021, 2, "이상치후보", Region.GYEONGGI, "이천시", "CULTURE_ART", VenueType.GREEN, 5, 999_999,
                com.festival.budgetassist.multiyear.domain.BudgetQualityFlag.UNIT_SCALE_SUSPECT);
        row(2022, 3, "결측후보", Region.GYEONGGI, "이천시", "CULTURE_ART", VenueType.GREEN, 5, 0,
                com.festival.budgetassist.multiyear.domain.BudgetQualityFlag.MISSING_OR_NONPOSITIVE);

        List<MultiYearFestivalRecord> training = recordRepository.findByDatasetYearLessThan(2026);
        MultiYearPredictionResult result = backtestService.predictForQuery(
                Region.GYEONGGI, "이천시", Set.of(FestivalType.CULTURE_ART), VenueType.GREEN, 5, training);

        assertEquals(1, result.sampleCount(), "품질 불량 2건은 표본에서 제외되고 정상 후보 1건만 남아야 함");
        assertEquals(100_000_000L, result.estimatedBudgetKrw());
    }

    @Test
    void predictForQuery_candidateWithoutVenueOrDuration_stillMatchedViaBroaderFallback() {
        for (int i = 1; i <= 25; i++) {
            // venue/duration 없는 옛 데이터 스타일(row()의 7-인자 오버로드) - 요청에는 venue가
            // 있지만(production 화면처럼 항상 값이 있음) 후보 쪽은 없다.
            row(2020, i, "결측피처후보" + i, Region.GYEONGGI, "이천시", "CULTURE_ART", 100 + i);
        }

        List<MultiYearFestivalRecord> training = recordRepository.findByDatasetYearLessThan(2026);
        MultiYearPredictionResult result = backtestService.predictForQuery(
                Region.GYEONGGI, "이천시", Set.of(FestivalType.CULTURE_ART), VenueType.GREEN, 5, training);

        assertTrue(result.sampleCount() > 0, "후보에 venue/duration이 없어도 region/type 기반 fallback으로 매칭돼야 함");
        assertEquals("SAME_REGION_TYPE", result.fallbackLevel(),
                "venue 없는 후보뿐이면 venue를 요구하는 1~3단계를 건너뛰고 SAME_REGION_TYPE에서 멈춰야 함");
    }
}