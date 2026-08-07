package com.festival.budgetassist.multiyear.csv;

import java.util.List;
import java.util.Map;

/**
 * 한 번의 다년도 CSV Import 실행에 대한 집계 리포트.
 *
 * @param rowCountByYear                연도(dataset_year) -> 저장된 행 수. year_profiles.json의
 *                                       row_count와 대조하는 검증 테스트가 이 값을 사용한다.
 * @param validBudgetRows               budgetQualityFlag = VALID 인 행 수
 * @param unitScaleSuspectRows          budgetQualityFlag = UNIT_SCALE_SUSPECT (알고리즘 후보 제외 대상)
 * @param missingOrNonpositiveBudgetRows budgetQualityFlag = MISSING_OR_NONPOSITIVE
 * @param missingDurationRows           durationDays가 끝내 null인 행 수
 * @param covidAffectedRows             covidAffected = true인 행 수(2020~2021)
 */
public record MultiYearImportSummary(
        int totalRows,
        Map<Integer, Integer> rowCountByYear,
        int validBudgetRows,
        int unitScaleSuspectRows,
        int missingOrNonpositiveBudgetRows,
        int missingDurationRows,
        int covidAffectedRows,
        List<MultiYearRowWarning> warnings
) {
}