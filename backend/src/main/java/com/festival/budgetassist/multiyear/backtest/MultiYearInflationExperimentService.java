package com.festival.budgetassist.multiyear.backtest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;
import com.festival.budgetassist.multiyear.repository.MultiYearFestivalRecordRepository;

/**
 * inflation adjustment x festivalSeries 중복 보정(S1) 2x2 비교 실험.
 *
 * <p>A(S0+OFF)/B(S1+OFF)/C(S0+ON)/D(S1+ON) 네 조합을 전부 실행한다. inflation과 series
 * correction은 서로 직교하는 축이다 - {@link MultiYearSeriesCorrectionBacktestService#runFold}가
 * 이미 이 둘을 독립적으로 받는다(series correction은 최종 weight만, inflation은 candidate budget
 * 값만 바꾸고 candidate selection 자체는 둘 다 건드리지 않는다) - 그래서 A/B/C/D 네 실행 모두
 * "동일 evaluation records, 동일 candidate pool"이라는 지시사항 4절 요구가 구조적으로 보장된다.</p>
 */
@Service
class MultiYearInflationExperimentService {

    private final MultiYearFestivalRecordRepository recordRepository;
    private final MultiYearSeriesCorrectionBacktestService correctionService;

    MultiYearInflationExperimentService(MultiYearFestivalRecordRepository recordRepository,
                                         MultiYearSeriesCorrectionBacktestService correctionService) {
        this.recordRepository = recordRepository;
        this.correctionService = correctionService;
    }

    Map<MultiYearInflationExperimentVariant, List<MultiYearFoldCorrectionResult>> runAll() {
        List<MultiYearFestivalRecord> allRecords = recordRepository.findAll();
        Map<MultiYearInflationExperimentVariant, List<MultiYearFoldCorrectionResult>> byVariant = new LinkedHashMap<>();
        for (MultiYearInflationExperimentVariant variant : MultiYearInflationExperimentVariant.values()) {
            List<MultiYearFoldCorrectionResult> foldResults = new ArrayList<>();
            for (MultiYearBacktestFold fold : MultiYearBacktestFold.all()) {
                foldResults.add(correctionService.runFold(allRecords, fold, variant.seriesMode(), variant.inflationOn()));
            }
            byVariant.put(variant, foldResults);
        }
        return byVariant;
    }
}