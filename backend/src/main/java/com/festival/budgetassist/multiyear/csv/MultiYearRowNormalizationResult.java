package com.festival.budgetassist.multiyear.csv;

import java.util.List;

import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;

/**
 * 한 행에 대한 정규화 결과. {@code errors}가 비어 있지 않으면 필수 항목(dataset_year, source_row
 * 숫자 형식, festival_name, budget_quality_flag) 인식에 실패한 것이고 {@code record}는 null이다.
 */
record MultiYearRowNormalizationResult(MultiYearFestivalRecord record, List<String> errors, List<MultiYearRowWarning> warnings) {

    boolean hasErrors() {
        return !errors.isEmpty();
    }
}