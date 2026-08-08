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

/**
 * 지시사항 1절 "이 조건은 테스트로 고정해줘"를 그대로 구현한 leakage 안전성 테스트.
 *
 * <p>두 층위로 검증한다:</p>
 * <ol>
 *   <li>{@link MultiYearBacktestDatasetBuilder} 단위 - datasetYear 경계값(targetYear-1/targetYear/
 *       targetYear+1)이 정확히 training/평가/제외로 나뉘는지.</li>
 *   <li>{@link MultiYearBacktestService} 종단 - 2024 fold를 먼저 실행해 결과를 기록해 두고, DB에
 *       2025/2026년 record(그것도 일부러 극단적으로 다른 예산을 가진)를 추가로 저장한 뒤 "같은"
 *       2024 fold를 다시 실행해도 training pool 크기와 모든 예측값이 한 글자도 안 바뀌는지 확인한다 -
 *       이게 실제로 안 바뀌어야 "미래 데이터가 과거 fold에 전혀 영향을 주지 않는다"는 요구사항을
 *       만족한다.</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class MultiYearBacktestLeakageTest extends MultiYearBacktestTestSupport {

    @Autowired
    private MultiYearBacktestDatasetBuilder datasetBuilder;
    @Autowired
    private MultiYearBacktestService backtestService;

    @BeforeEach
    void setUp() {
        initBatch();
    }

    @Test
    void datasetBuilder_splitsStrictlyByDatasetYearBoundary_noFutureRowInTrainingOrEval() {
        MultiYearFestivalRecord before = row(2023, 1, "가나다축제A", Region.GYEONGGI, "가평군", "CULTURE_ART", 100);
        MultiYearFestivalRecord sameYear = row(2024, 2, "가나다축제B", Region.GYEONGGI, "가평군", "CULTURE_ART", 100);
        MultiYearFestivalRecord future = row(2025, 3, "가나다축제C", Region.GYEONGGI, "가평군", "CULTURE_ART", 100);

        List<MultiYearFestivalRecord> all = recordRepository.findAll();
        MultiYearBacktestDataset dataset = datasetBuilder.build(all, MultiYearBacktestFold.SECONDARY_2024);

        assertTrue(dataset.trainingPool().stream().anyMatch(r -> r.getId().equals(before.getId())),
                "targetYear-1(2023)은 training에 포함되어야 함");
        assertTrue(dataset.evalTargets().stream().anyMatch(r -> r.getId().equals(sameYear.getId())),
                "targetYear(2024)는 평가 대상에 포함되어야 함");
        assertTrue(dataset.trainingPool().stream().noneMatch(r -> r.getId().equals(future.getId())),
                "targetYear+1(2025)은 training에 들어가면 절대 안 됨");
        assertTrue(dataset.evalTargets().stream().noneMatch(r -> r.getId().equals(future.getId())),
                "targetYear+1(2025)은 평가 대상에도 들어가면 안 됨(이 fold와 무관)");
    }

    @Test
    void addingFutureYearRecords_neverChangesEarlierFoldTrainingPoolOrPredictions() {
        // 2020~2023 training 후보 - 전부 같은 유형/지역이라 서로 후보가 된다.
        for (int year = 2020; year <= 2023; year++) {
            row(year, year, "가나다축제" + year, Region.GYEONGGI, "가평군", "CULTURE_ART", 100 + year);
        }
        // 2024 평가 대상 1건.
        MultiYearFestivalRecord target2024 = row(2024, 100, "가나다축제2024", Region.GYEONGGI, "가평군", "CULTURE_ART", 150);

        List<MultiYearFestivalRecord> beforeFutureAdded = recordRepository.findAll();
        MultiYearFoldResult before = backtestService.runFold(beforeFutureAdded, MultiYearBacktestFold.SECONDARY_2024);
        assertEquals(1, before.predictions().size());
        MultiYearBacktestPrediction beforePrediction = before.predictions().get(0);

        // 미래 연도(2025/2026) record를 "일부러 극단적으로 다른 예산"으로 추가한다 - 만약 leakage가
        // 있다면 training 모집단/winsorize 기준/가중평균이 크게 흔들려야 정상이다.
        row(2025, 200, "가나다축제2025", Region.GYEONGGI, "가평군", "CULTURE_ART", 999_999);
        row(2026, 201, "가나다축제2026", Region.GYEONGGI, "가평군", "CULTURE_ART", 1);

        List<MultiYearFestivalRecord> afterFutureAdded = recordRepository.findAll();
        assertEquals(beforeFutureAdded.size() + 2, afterFutureAdded.size(), "미래 record 2건이 실제로 추가됐는지 확인");

        MultiYearFoldResult after = backtestService.runFold(afterFutureAdded, MultiYearBacktestFold.SECONDARY_2024);

        assertEquals(before.trainingPoolSize(), after.trainingPoolSize(),
                "2024 fold의 training pool 크기는 2025/2026 record 추가 전후로 완전히 같아야 함");
        assertEquals(1, after.predictions().size());
        MultiYearBacktestPrediction afterPrediction = after.predictions().get(0);

        assertEquals(beforePrediction.estimatedBudget(), afterPrediction.estimatedBudget(),
                "미래 record가 추가돼도 2024 예측(estimatedBudget)이 바뀌면 leakage 발생");
        assertEquals(beforePrediction.weightedAverageBudget(), afterPrediction.weightedAverageBudget());
        assertEquals(beforePrediction.p25(), afterPrediction.p25());
        assertEquals(beforePrediction.p75(), afterPrediction.p75());
        assertEquals(beforePrediction.sampleCount(), afterPrediction.sampleCount());
        assertEquals(beforePrediction.distinctSeriesCount(), afterPrediction.distinctSeriesCount(),
                "distinctSeriesCount 진단값도 미래 record에 영향받으면 안 됨");
    }
}
