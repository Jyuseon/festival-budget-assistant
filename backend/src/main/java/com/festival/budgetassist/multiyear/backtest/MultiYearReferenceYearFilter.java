package com.festival.budgetassist.multiyear.backtest;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.festival.budgetassist.multiyear.domain.BudgetQualityFlag;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;

/**
 * Budget Planning Assistant 전용 reference pool 필터 - {@link MultiYearBacktestDatasetBuilder}와
 * 의도적으로 완전히 분리된 별도 클래스다.
 *
 * <p><b>왜 {@link MultiYearBacktestDatasetBuilder}를 재사용/확장하지 않았는가</b>: 그 클래스는
 * {@link MultiYearBacktestFold}(고정된 backtest 평가 시점)를 기준으로 "{@code datasetYear <
 * fold.targetYear()}"만 만드는 leakage-safe 연구용 컴포넌트다. Planning Assistant의
 * {@link ReferenceDataPolicy#INCLUDE_PUBLISHED_SAME_YEAR}(= {@code referenceYear <=
 * planningYear})처럼 backtest에는 존재하지 않는 "≤" 개념을 섞어 넣으면 그 클래스를 호출하는
 * 모든 backtest 코드(fold 비교/selector lab 등)에 실수로 leakage 위험을 심을 수 있다 - "Backtest
 * fold와 실제 Planning Assistant의 year selection 로직은 분리한다"는 요구사항을 코드 구조로
 * 강제하기 위해 필터 로직 자체를 별도로 둔다(데이터 품질/필수 feature 판정 기준만 동일하게
 * 복사했다 - {@link MultiYearBacktestDatasetBuilder}와 판정 기준이 달라지면 안 되므로).</p>
 */
@Component
class MultiYearReferenceYearFilter {

    /**
     * @param allRecords 필터링 전 전체 record(어느 연도든 포함해도 안전 - 이 메서드가 직접 연도 컷을 건다)
     * @param planningYear 기획하려는 연도
     * @param includeSameYear true면 {@code datasetYear <= planningYear}, false면 {@code datasetYear < planningYear}
     */
    List<MultiYearFestivalRecord> filter(List<MultiYearFestivalRecord> allRecords, int planningYear, boolean includeSameYear) {
        List<MultiYearFestivalRecord> result = new ArrayList<>();
        for (MultiYearFestivalRecord r : allRecords) {
            if (r.getDatasetYear() == null) {
                continue;
            }
            boolean withinReferenceWindow = includeSameYear ? r.getDatasetYear() <= planningYear : r.getDatasetYear() < planningYear;
            if (!withinReferenceWindow) {
                continue;
            }
            boolean lowQuality = r.getBudgetQualityFlag() != BudgetQualityFlag.VALID;
            boolean missingFeature = r.getRegionCode() == null || MultiYearFeatureResolver.resolveTypeTokens(r).isEmpty();
            if (lowQuality || missingFeature) {
                continue;
            }
            result.add(r);
        }
        return result;
    }
}