package com.festival.budgetassist.multiyear.csv;

import java.util.List;

/**
 * {@code guide/DATA_DICTIONARY.md} 기준 sanitized CSV 컬럼명.
 *
 * <p>헤더 순서가 아니라 이름으로 매칭하므로, CSV 컬럼 순서가 바뀌어도 안전하다. 필수 컬럼이
 * 하나라도 없으면 {@link MultiYearCsvImportException}으로 즉시 중단한다.</p>
 */
final class MultiYearCsvColumns {

    static final String DATASET_YEAR = "dataset_year";
    static final String SOURCE_SHEET = "source_sheet";
    static final String SOURCE_ROW = "source_row";
    static final String SOURCE_SHA256 = "source_sha256";
    static final String REGION_RAW = "region_raw";
    static final String REGION = "region";
    static final String DISTRICT_RAW = "district_raw";
    static final String DISTRICT = "district";
    static final String FESTIVAL_NAME = "festival_name";
    static final String FESTIVAL_TYPE_RAW = "festival_type_raw";
    static final String FESTIVAL_TYPE = "festival_type";
    static final String VENUE_RAW = "venue_raw";
    static final String VENUE_TYPE_RAW = "venue_type_raw";
    static final String VENUE_TYPE = "venue_type";
    static final String PERIOD_RAW = "period_raw";
    static final String DURATION_DAYS = "duration_days";
    static final String DURATION_SOURCE = "duration_source";
    static final String DURATION_NOTE_RAW = "duration_note_raw";
    static final String CYCLE = "cycle";
    static final String EVENT_MODE = "event_mode";
    static final String EVENT_STATUS = "event_status";
    static final String COVID_AFFECTED = "covid_affected";
    static final String FIRST_HELD_YEAR = "first_held_year";
    static final String BUDGET_TOTAL_RAW = "budget_total_raw";
    static final String BUDGET_TOTAL_MILLION = "budget_total_million";
    static final String BUDGET_NATIONAL_MILLION = "budget_national_million";
    static final String BUDGET_LOCAL_MILLION = "budget_local_million";
    static final String BUDGET_OTHER_MILLION = "budget_other_million";
    static final String BUDGET_QUALITY_FLAG = "budget_quality_flag";
    static final String BUDGET_QUALITY_NOTE = "budget_quality_note";
    static final String VISITOR_TOTAL_PERSONS = "visitor_total_persons";

    static final List<String> REQUIRED_COLUMNS = List.of(
            DATASET_YEAR, SOURCE_SHEET, SOURCE_ROW, SOURCE_SHA256,
            REGION_RAW, REGION, DISTRICT_RAW, DISTRICT,
            FESTIVAL_NAME, FESTIVAL_TYPE_RAW, FESTIVAL_TYPE,
            VENUE_RAW, VENUE_TYPE_RAW, VENUE_TYPE,
            PERIOD_RAW, DURATION_DAYS, DURATION_SOURCE, DURATION_NOTE_RAW,
            CYCLE, EVENT_MODE, EVENT_STATUS, COVID_AFFECTED, FIRST_HELD_YEAR,
            BUDGET_TOTAL_RAW, BUDGET_TOTAL_MILLION, BUDGET_NATIONAL_MILLION,
            BUDGET_LOCAL_MILLION, BUDGET_OTHER_MILLION,
            BUDGET_QUALITY_FLAG, BUDGET_QUALITY_NOTE, VISITOR_TOTAL_PERSONS
    );

    private MultiYearCsvColumns() {
    }
}