package com.festival.budgetassist.multiyear.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
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
import com.festival.budgetassist.multiyear.domain.BudgetQualityFlag;
import com.festival.budgetassist.multiyear.domain.MultiYearDatasetPublicationStatus;
import com.festival.budgetassist.multiyear.domain.MultiYearDatasetPublicationStatusValue;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;
import com.festival.budgetassist.multiyear.repository.MultiYearDatasetPublicationStatusRepository;

/**
 * {@link MultiYearBacktestService#estimateForPlanning}(planningYear 일반화) 검증 - 사용자 요청
 * 8~11/14절. {@link MultiYearPredictForQueryParityTest}와 마찬가지로 {@code
 * MultiYearBacktestTestSupport}의 fixture 빌더를 재사용한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class MultiYearEstimateForPlanningTest extends MultiYearBacktestTestSupport {

    @Autowired
    private MultiYearBacktestService backtestService;
    @Autowired
    private MultiYearDatasetPublicationStatusRepository publicationStatusRepository;

    @BeforeEach
    void setUp() {
        initBatch();
    }

    @Test
    void estimateForPlanning_2027HistoricalOnly_usesFullTenYearRange2017to2026() {
        for (int y = 2017; y <= 2026; y++) {
            row(y, y, "플래닝2027테스트" + y, Region.GYEONGGI, "이천시", "CULTURE_ART", VenueType.GREEN, 5, 100 + y,
                    BudgetQualityFlag.VALID);
        }

        List<MultiYearFestivalRecord> all = recordRepository.findAll();
        MultiYearPlanningEstimateResult result = backtestService.estimateForPlanning(
                Region.GYEONGGI, "이천시", Set.of(FestivalType.CULTURE_ART), VenueType.GREEN, 5,
                2027, ReferenceDataPolicy.HISTORICAL_ONLY, all);

        assertEquals(2027, result.planningYear());
        assertEquals(ReferenceDataPolicy.HISTORICAL_ONLY, result.requestedReferenceDataPolicy());
        assertEquals(ReferenceDataPolicy.HISTORICAL_ONLY, result.appliedReferenceDataPolicy());
        assertEquals(2017, result.referenceYearFrom());
        assertEquals(2026, result.referenceYearTo(), "2027 기획은 2017~2026 전체(10개년)를 참고 데이터 범위로 써야 함");
        assertTrue(result.latestSourceYear() <= 2026, "2027년 데이터는 애초에 없으므로 표본에 섞일 수 없음");
    }

    @Test
    void estimateForPlanning_2026HistoricalOnly_excludes2026() {
        for (int y = 2017; y <= 2026; y++) {
            row(y, y, "플래닝2026테스트" + y, Region.GYEONGGI, "이천시", "CULTURE_ART", VenueType.GREEN, 5, 100 + y,
                    BudgetQualityFlag.VALID);
        }

        List<MultiYearFestivalRecord> all = recordRepository.findAll();
        MultiYearPlanningEstimateResult result = backtestService.estimateForPlanning(
                Region.GYEONGGI, "이천시", Set.of(FestivalType.CULTURE_ART), VenueType.GREEN, 5,
                2026, ReferenceDataPolicy.HISTORICAL_ONLY, all);

        assertEquals(ReferenceDataPolicy.HISTORICAL_ONLY, result.appliedReferenceDataPolicy());
        assertEquals(2025, result.referenceYearTo(), "HISTORICAL_ONLY는 referenceYear < planningYear라 2025까지만");
        assertTrue(result.latestSourceYear() <= 2025, "2026 데이터가 표본에 섞이면 안 됨");
    }

    @Test
    void estimateForPlanning_2026IncludePublishedSameYear_whenPublishedComplete_includes2026() {
        for (int y = 2017; y <= 2026; y++) {
            row(y, y, "플래닝공개테스트" + y, Region.GYEONGGI, "이천시", "CULTURE_ART", VenueType.GREEN, 5, 100 + y,
                    BudgetQualityFlag.VALID);
        }
        publicationStatusRepository.save(MultiYearDatasetPublicationStatus.builder()
                .datasetYear(2026).status(MultiYearDatasetPublicationStatusValue.PUBLISHED_COMPLETE).publishedAt(Instant.now())
                .build());

        List<MultiYearFestivalRecord> all = recordRepository.findAll();
        MultiYearPlanningEstimateResult result = backtestService.estimateForPlanning(
                Region.GYEONGGI, "이천시", Set.of(FestivalType.CULTURE_ART), VenueType.GREEN, 5,
                2026, ReferenceDataPolicy.INCLUDE_PUBLISHED_SAME_YEAR, all);

        assertEquals(ReferenceDataPolicy.INCLUDE_PUBLISHED_SAME_YEAR, result.requestedReferenceDataPolicy());
        assertEquals(ReferenceDataPolicy.INCLUDE_PUBLISHED_SAME_YEAR, result.appliedReferenceDataPolicy(),
                "PUBLISHED_COMPLETE로 표시된 연도는 요청한 정책 그대로 적용돼야 함");
        assertEquals(2026, result.referenceYearTo());
        assertEquals(2026, result.latestSourceYear(), "2026 데이터가 표본에 포함돼야 함(동년 벤치마크)");
    }

    @Test
    void estimateForPlanning_2026IncludePublishedSameYear_withoutPublicationStatus_fallsBackToHistoricalOnly() {
        for (int y = 2017; y <= 2026; y++) {
            row(y, y, "플래닝미공개테스트" + y, Region.GYEONGGI, "이천시", "CULTURE_ART", VenueType.GREEN, 5, 100 + y,
                    BudgetQualityFlag.VALID);
        }
        // publicationStatusRepository에 2026 row를 아예 저장하지 않음(= 미확인/미공개 기본값).

        List<MultiYearFestivalRecord> all = recordRepository.findAll();
        MultiYearPlanningEstimateResult result = backtestService.estimateForPlanning(
                Region.GYEONGGI, "이천시", Set.of(FestivalType.CULTURE_ART), VenueType.GREEN, 5,
                2026, ReferenceDataPolicy.INCLUDE_PUBLISHED_SAME_YEAR, all);

        assertEquals(ReferenceDataPolicy.INCLUDE_PUBLISHED_SAME_YEAR, result.requestedReferenceDataPolicy(),
                "요청한 정책 자체는 그대로 기록돼야 함(조용히 바꾸지 않는다)");
        assertEquals(ReferenceDataPolicy.HISTORICAL_ONLY, result.appliedReferenceDataPolicy(),
                "공개 완료로 표시되지 않았으면 HISTORICAL_ONLY로 낮춰 적용해야 함");
        assertEquals(2025, result.referenceYearTo());
        assertTrue(result.latestSourceYear() <= 2025, "미공개 연도의 동년 데이터가 무조건 포함되면 안 됨");
    }

    @Test
    void estimateForPlanning_2026IncludePublishedSameYear_whenPartial_fallsBackToHistoricalOnly() {
        for (int y = 2017; y <= 2026; y++) {
            row(y, y, "플래닝부분공개테스트" + y, Region.GYEONGGI, "이천시", "CULTURE_ART", VenueType.GREEN, 5, 100 + y,
                    BudgetQualityFlag.VALID);
        }
        publicationStatusRepository.save(MultiYearDatasetPublicationStatus.builder()
                .datasetYear(2026).status(MultiYearDatasetPublicationStatusValue.PARTIAL).build());

        List<MultiYearFestivalRecord> all = recordRepository.findAll();
        MultiYearPlanningEstimateResult result = backtestService.estimateForPlanning(
                Region.GYEONGGI, "이천시", Set.of(FestivalType.CULTURE_ART), VenueType.GREEN, 5,
                2026, ReferenceDataPolicy.INCLUDE_PUBLISHED_SAME_YEAR, all);

        assertEquals(ReferenceDataPolicy.HISTORICAL_ONLY, result.appliedReferenceDataPolicy(), "PARTIAL이면 same-year 사용 불가");
        assertTrue(result.latestSourceYear() <= 2025);
    }

    @Test
    void estimateForPlanning_neverLeaksFutureYearData() {
        for (int y = 2017; y <= 2026; y++) {
            row(y, y, "플래닝누출테스트" + y, Region.GYEONGGI, "이천시", "CULTURE_ART", VenueType.GREEN, 5, 100 + y,
                    BudgetQualityFlag.VALID);
        }
        List<MultiYearFestivalRecord> before = recordRepository.findAll();
        MultiYearPlanningEstimateResult before2028Added = backtestService.estimateForPlanning(
                Region.GYEONGGI, "이천시", Set.of(FestivalType.CULTURE_ART), VenueType.GREEN, 5,
                2027, ReferenceDataPolicy.HISTORICAL_ONLY, before);

        // planningYear(2027)보다도 미래인 2028 극단치 record를 추가 - findAll()을 그대로 넘겨도 새지 않아야 한다.
        row(2028, 999, "플래닝누출2028", Region.GYEONGGI, "이천시", "CULTURE_ART", VenueType.GREEN, 5, 999_999,
                BudgetQualityFlag.VALID);
        List<MultiYearFestivalRecord> afterAll = recordRepository.findAll();
        MultiYearPlanningEstimateResult after2028Added = backtestService.estimateForPlanning(
                Region.GYEONGGI, "이천시", Set.of(FestivalType.CULTURE_ART), VenueType.GREEN, 5,
                2027, ReferenceDataPolicy.HISTORICAL_ONLY, afterAll);

        assertEquals(before2028Added.estimatedBudgetKrw(), after2028Added.estimatedBudgetKrw(),
                "planningYear보다 미래인 record가 findAll()에 섞여 있어도 결과가 바뀌면 안 됨(leakage-safe)");
        assertTrue(after2028Added.latestSourceYear() <= 2026, "2028 record가 표본에 들어가면 안 됨");
    }

    @Test
    void estimateForPlanning_yearWeightBreakdown_sumsToOneAndMatchesSampleComposition() {
        for (int y = 2017; y <= 2025; y++) {
            row(y, y, "가중치분해테스트" + y, Region.GYEONGGI, "이천시", "CULTURE_ART", VenueType.GREEN, 5, 100 + y,
                    BudgetQualityFlag.VALID);
        }

        List<MultiYearFestivalRecord> all = recordRepository.findAll();
        MultiYearPlanningEstimateResult result = backtestService.estimateForPlanning(
                Region.GYEONGGI, "이천시", Set.of(FestivalType.CULTURE_ART), VenueType.GREEN, 5,
                2026, ReferenceDataPolicy.HISTORICAL_ONLY, all);

        assertFalse(result.yearWeightBreakdown().isEmpty());
        double totalShare = result.yearWeightBreakdown().stream().mapToDouble(MultiYearPlanningYearWeightShare::weightShare).sum();
        assertEquals(1.0, totalShare, 0.001, "연도별 weightShare 합은 1.0이어야 함");
        int totalCandidateCount = result.yearWeightBreakdown().stream().mapToInt(MultiYearPlanningYearWeightShare::candidateCount).sum();
        assertEquals(result.sampleCount(), totalCandidateCount, "연도별 candidateCount 합은 전체 sampleCount와 같아야 함");
        assertEquals(result.distinctYearsUsed(), result.yearWeightBreakdown().size());
    }
}