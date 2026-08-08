package com.festival.budgetassist.multiyear.backtest;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.festival.budgetassist.multiyear.domain.BudgetQualityFlag;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;

/**
 * fold별 leakage-safe 데이터셋을 만든다 (지시사항 1절).
 *
 * <p><b>미래 데이터 누수 금지</b>: {@code allRecords}를 딱 한 번만 훑으면서
 * {@code datasetYear < fold.targetYear}인 것만 training, {@code datasetYear == fold.targetYear}인
 * 것만 평가 대상으로 나눈다 - training/평가 목록 둘 다 fold.targetYear 이후 연도의 record를 원천적으로
 * 포함할 수 없다(그 이후 어떤 계산 단계도 이 필터를 우회해서 원본 {@code allRecords}나 repository를
 * 다시 조회하지 않는다 - {@link MultiYearBacktestService}는 이 메서드가 만든
 * {@link MultiYearBacktestDataset}만 받아서 동작한다). festivalSeries 진단 통계
 * ({@link MultiYearBacktestSeriesDiagnostics})도 이 trainingPool만 입력으로 받으므로 동일하게
 * 안전하다.</p>
 *
 * <p><b>데이터 품질 제외</b>(지시사항 4절): training/평가 양쪽 모두 {@code UNIT_SCALE_SUSPECT},
 * {@code MISSING_OR_NONPOSITIVE}는 제외한다. 원본 record는 DB에서 전혀 삭제하지 않는다 - 여기서는
 * in-memory 필터링만 한다.</p>
 *
 * <p><b>feature 결측 제외</b>: region({@code regionCode})이나 festivalType(알려진 5종 토큰이 하나도
 * 없음)을 알 수 없는 record는 similarity/후보선정 자체가 성립하지 않아 training/평가 양쪽에서
 * 제외한다(둘 다 명시적으로 로그/카운트에 남긴다 - 지시사항 5절 "강제로 점수를 주지 마"의 연장:
 * 아예 판단 불가능한 경우는 강제로 채우지 않고 빼는 쪽을 택한다).</p>
 */
@Component
class MultiYearBacktestDatasetBuilder {

    MultiYearBacktestDataset build(List<MultiYearFestivalRecord> allRecords, MultiYearBacktestFold fold) {
        List<MultiYearFestivalRecord> trainingPool = new ArrayList<>();
        List<MultiYearFestivalRecord> evalTargets = new ArrayList<>();
        int trainingExcludedLowQuality = 0;
        int trainingExcludedMissingFeature = 0;
        int evalExcludedLowQuality = 0;
        int evalExcludedMissingFeature = 0;

        for (MultiYearFestivalRecord r : allRecords) {
            if (r.getDatasetYear() == null) {
                continue;
            }
            boolean isTraining = r.getDatasetYear() < fold.targetYear();
            boolean isEval = r.getDatasetYear().equals(fold.targetYear());
            if (!isTraining && !isEval) {
                continue; // fold와 무관한 연도 - training/평가 어느 쪽에도 넣지 않는다.
            }

            boolean lowQuality = r.getBudgetQualityFlag() != BudgetQualityFlag.VALID;
            boolean missingFeature = r.getRegionCode() == null || MultiYearFeatureResolver.resolveTypeTokens(r).isEmpty();

            if (isTraining) {
                if (lowQuality) {
                    trainingExcludedLowQuality++;
                } else if (missingFeature) {
                    trainingExcludedMissingFeature++;
                } else {
                    trainingPool.add(r);
                }
            } else {
                if (lowQuality) {
                    evalExcludedLowQuality++;
                } else if (missingFeature) {
                    evalExcludedMissingFeature++;
                } else {
                    evalTargets.add(r);
                }
            }
        }

        return new MultiYearBacktestDataset(fold, trainingPool, evalTargets,
                trainingExcludedLowQuality, trainingExcludedMissingFeature,
                evalExcludedLowQuality, evalExcludedMissingFeature);
    }
}