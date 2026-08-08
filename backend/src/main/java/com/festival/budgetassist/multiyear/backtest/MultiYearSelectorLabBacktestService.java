package com.festival.budgetassist.multiyear.backtest;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;

/**
 * selector 후보(V1~V4 등)를 leakage-safe backtest fold에 그대로 꽂아 baseline(V0)과 정확도를
 * 비교하는 전용 서비스 (11~12절). {@link MultiYearSeriesCorrectionBacktestService}가 weight만
 * 바꿔치기하는 것과 대칭적으로, 이 클래스는 candidate selection 전략만 바꿔치기한다 - 그 이후
 * 채점 공식({@link MultiYearBacktestService#selectFinalSample(MultiYearCandidateSelectionStrategy,
 * MultiYearFestivalRecord, List)}가 내부적으로 호출하는 {@code scoreAndFinalize})과 오차 지표
 * 집계({@link MultiYearBacktestService#aggregate})는 baseline과 100% 동일한 코드를 그대로
 * 재사용한다.
 *
 * <p>inflation/series correction/recency/COVID는 이번 selector 비교 범위 밖이므로 항상 OFF로
 * 고정한다(13절) - {@code aggregate}에 넘기는 weight도 원본 {@code score.weight()} 그대로다.</p>
 */
@Service
class MultiYearSelectorLabBacktestService {

    private final MultiYearBacktestDatasetBuilder datasetBuilder;
    private final MultiYearBacktestService backtestService;

    MultiYearSelectorLabBacktestService(MultiYearBacktestDatasetBuilder datasetBuilder, MultiYearBacktestService backtestService) {
        this.datasetBuilder = datasetBuilder;
        this.backtestService = backtestService;
    }

    MultiYearFoldResult runFold(List<MultiYearFestivalRecord> allRecords, MultiYearBacktestFold fold,
                                 MultiYearCandidateSelectionStrategy strategy) {
        MultiYearBacktestDataset dataset = datasetBuilder.build(allRecords, fold);

        List<MultiYearBacktestPrediction> predictions = new ArrayList<>();
        int noFinalSample = 0;
        for (MultiYearFestivalRecord target : dataset.evalTargets()) {
            MultiYearBacktestService.FinalSample fs = backtestService.selectFinalSample(strategy, target, dataset.trainingPool());
            if (fs == null) {
                noFinalSample++;
                continue;
            }
            double[] weights = fs.finalSample().stream().mapToDouble(c -> c.score().weight()).toArray();
            predictions.add(backtestService.aggregate(target, fs, weights));
        }

        return new MultiYearFoldResult(fold, predictions, dataset.trainingPool().size(), dataset.evalTargets().size(),
                noFinalSample, dataset.trainingExcludedLowQuality(), dataset.trainingExcludedMissingFeature(),
                dataset.evalExcludedLowQuality(), dataset.evalExcludedMissingFeature());
    }
}