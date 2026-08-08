package com.festival.budgetassist.multiyear.experimental;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.festival.budgetassist.festival.domain.FestivalType;
import com.festival.budgetassist.festival.domain.Region;
import com.festival.budgetassist.festival.domain.VenueType;
import com.festival.budgetassist.multiyear.backtest.MultiYearBacktestService;
import com.festival.budgetassist.multiyear.backtest.MultiYearPredictionCandidate;
import com.festival.budgetassist.multiyear.backtest.MultiYearPredictionResult;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;
import com.festival.budgetassist.multiyear.repository.MultiYearFestivalRecordRepository;

/**
 * "다년도 실험 분석" 영역 전용 application service - festivalSeries v1 baseline S0 backtest
 * 계산식을 실제 사용자 입력 1건에 그대로 재사용해 즉석 예측을 만든다.
 *
 * <p><b>production을 대체하지 않는다</b>: 이 서비스는 {@code multiyear} 패키지 하위에만 있고,
 * {@code estimate} 패키지(production {@code BudgetEstimatorService}/{@code CandidateSelector}/
 * {@code SimilarityCalculator}/{@code ConfidenceCalculator}/{@code WeightedStatistics}/
 * {@code DurationAdjuster})는 전혀 import하지 않는다 - 두 계산 경로는 완전히 분리된 채로
 * 나란히 존재한다.</p>
 *
 * <p><b>계산 재사용</b>: 새 공식을 만들지 않는다 - {@link MultiYearBacktestService#predictForQuery}
 * (실제 2024/2025/2026 backtest에 쓰인 것과 동일한 candidate selection/유사도/기간보정/
 * winsorize/가중통계 코드)를 그대로 호출한다. backtest report/metrics 계산 코드({@code
 * MultiYearBacktestMetricsCalculator}, report formatter 등)는 이 경로에 전혀 들어오지 않는다 -
 * {@code predictForQuery}가 반환하는 {@link MultiYearPredictionResult}는 순수 계산 결과일 뿐이다.</p>
 *
 * <p><b>실험 설정 고정</b>: CPI/series correction/recency/COVID 전부 OFF/NONE으로 고정 호출한다
 * ({@code predictForQuery} 자체가 inflation=false로 호출됨, series correction/recency/COVID는
 * {@code predictForQuery} 경로에 애초에 구현돼 있지 않음) - 지시사항 19절.</p>
 */
@Service
public class MultiYearExperimentalEstimateService {

    private static final String MODEL = "MULTIYEAR_BASELINE_S0";

    private final MultiYearFestivalRecordRepository recordRepository;
    private final MultiYearBacktestService backtestService;

    MultiYearExperimentalEstimateService(MultiYearFestivalRecordRepository recordRepository,
                                          MultiYearBacktestService backtestService) {
        this.recordRepository = recordRepository;
        this.backtestService = backtestService;
    }

    public MultiYearExperimentalEstimateResponse estimate(MultiYearExperimentalEstimateRequest request) {
        Region region = parseRegion(request.regionCode());
        FestivalType festivalType = parseFestivalType(request.festivalType());
        VenueType venueType = parseVenueType(request.venueType());
        String district = blankToNull(request.district());

        int targetYear = backtestService.predictionTargetYear();
        // leakage-safe + 성능: targetYear(2026) 이상 연도는 DB 쿼리 단계에서부터 제외한다(지시사항 17절) -
        // predictForQuery 내부의 MultiYearBacktestDatasetBuilder가 다시 한 번 같은 기준으로 걸러내므로
        // 이 사전 필터링이 leakage를 만들 위험은 없다(중복 안전장치일 뿐).
        List<MultiYearFestivalRecord> trainingCandidates = recordRepository.findByDatasetYearLessThan(targetYear);

        MultiYearPredictionResult result = backtestService.predictForQuery(
                region, district, Set.of(festivalType), venueType, request.durationDays(), trainingCandidates);

        return toResponse(result);
    }

    private MultiYearExperimentalEstimateResponse toResponse(MultiYearPredictionResult result) {
        List<MultiYearSimilarFestivalDto> topSimilar = result.topCandidates().stream()
                .map(this::toSimilarFestivalDto)
                .toList();

        return new MultiYearExperimentalEstimateResponse(
                MODEL, result.targetYear(), result.trainingYearFrom(), result.trainingYearTo(),
                result.estimatedBudgetKrw(), result.weightedAverageBudgetKrw(), result.recommendedBudgetKrw(),
                result.p25Krw(), result.p50Krw(), result.p75Krw(),
                result.sampleCount(), result.distinctYearsUsed(), result.earliestSourceYear(), result.latestSourceYear(),
                result.fallbackLevel(), result.averageSimilarity(), result.dataQualityV3(),
                MultiYearExperimentSettingsDto.BASELINE_S0, topSimilar
        );
    }

    private MultiYearSimilarFestivalDto toSimilarFestivalDto(MultiYearPredictionCandidate c) {
        return new MultiYearSimilarFestivalDto(
                c.sourceYear(), c.festivalName(), c.region(), c.district(), c.festivalType(), c.venueType(),
                c.durationDays(), c.originalBudgetKrw(), c.durationAdjustedBudgetKrw(), c.similarity(), c.finalWeight(),
                c.fallbackStage()
        );
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private Region parseRegion(String code) {
        try {
            return Region.valueOf(code);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("인식할 수 없는 지역 코드입니다: " + code);
        }
    }

    private FestivalType parseFestivalType(String code) {
        try {
            return FestivalType.valueOf(code);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("인식할 수 없는 축제 유형 코드입니다: " + code);
        }
    }

    private VenueType parseVenueType(String code) {
        try {
            return VenueType.valueOf(code);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("인식할 수 없는 개최 장소 유형 코드입니다: " + code);
        }
    }
}