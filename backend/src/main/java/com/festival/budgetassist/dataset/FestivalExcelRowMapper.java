package com.festival.budgetassist.dataset;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Component;

import com.festival.budgetassist.festival.domain.BudgetStatus;

/**
 * '조사표' 시트의 데이터 행을 {@link RawFestivalRow}로 변환한다.
 * 코드 정규화나 enum 매핑은 하지 않고, 셀 타입 기반의 숫자/문자열/빈값 판별과
 * 예산 상태 분류(원본 셀이 숫자인지, "미확정"/"무응답" 텍스트인지)만 담당한다.
 */
@Component
class FestivalExcelRowMapper {

    /** 1-based 엑셀 9행 = 0-based POI 행 인덱스 8. 실제 파일로 확인한 데이터 시작 위치. */
    static final int DATA_START_ROW_INDEX = 8;

    /**
     * B열(연번)이 비어 있는 첫 행을 만날 때까지 순차적으로 읽는다.
     */
    List<RawFestivalRow> mapAllRows(Sheet sheet) {
        List<RawFestivalRow> rows = new ArrayList<>();
        int rowIndex = DATA_START_ROW_INDEX;
        while (true) {
            Row row = sheet.getRow(rowIndex);
            Integer sourceRowNumber = ExcelCellUtils.integerValue(row, ExcelColumns.SOURCE_ROW_NUMBER);
            if (sourceRowNumber == null) {
                break;
            }
            rows.add(mapRow(row, rowIndex, sourceRowNumber));
            rowIndex++;
        }
        return rows;
    }

    private RawFestivalRow mapRow(Row row, int excelRowIndex, int sourceRowNumber) {
        return RawFestivalRow.builder()
                .excelRowIndex(excelRowIndex + 1)
                .sourceRowNumber(sourceRowNumber)
                .festivalNameRaw(ExcelCellUtils.stringValue(row, ExcelColumns.FESTIVAL_NAME))
                .regionRaw(ExcelCellUtils.stringValue(row, ExcelColumns.REGION))
                .administrativeDistrictRaw(ExcelCellUtils.stringValue(row, ExcelColumns.ADMINISTRATIVE_DISTRICT))
                .festivalTypeRaw(ExcelCellUtils.stringValue(row, ExcelColumns.FESTIVAL_TYPE))
                .venueNameRaw(ExcelCellUtils.stringValue(row, ExcelColumns.VENUE_NAME))
                .venueTypeRaw(ExcelCellUtils.stringValue(row, ExcelColumns.VENUE_TYPE))
                .venueRegionRaw(ExcelCellUtils.stringValue(row, ExcelColumns.VENUE_REGION))
                .venueDistrictRaw(ExcelCellUtils.stringValue(row, ExcelColumns.VENUE_DISTRICT))
                .startYear(ExcelCellUtils.integerValue(row, ExcelColumns.START_YEAR))
                .startMonth(ExcelCellUtils.integerValue(row, ExcelColumns.START_MONTH))
                .startDay(ExcelCellUtils.integerValue(row, ExcelColumns.START_DAY))
                .endYear(ExcelCellUtils.integerValue(row, ExcelColumns.END_YEAR))
                .endMonth(ExcelCellUtils.integerValue(row, ExcelColumns.END_MONTH))
                .endDay(ExcelCellUtils.integerValue(row, ExcelColumns.END_DAY))
                .durationDaysRaw(ExcelCellUtils.integerValue(row, ExcelColumns.DURATION_DAYS))
                .durationNoteRaw(ExcelCellUtils.stringValue(row, ExcelColumns.DURATION_NOTE))
                .cycleRaw(ExcelCellUtils.stringValue(row, ExcelColumns.CYCLE))
                .firstHeldYearNumeric(ExcelCellUtils.integerValue(row, ExcelColumns.FIRST_HELD_YEAR))
                .firstHeldYearTextRaw(textIfNotNumeric(row, ExcelColumns.FIRST_HELD_YEAR))
                .totalBudgetMillion(ExcelCellUtils.decimalValue(row, ExcelColumns.TOTAL_BUDGET))
                .nationalBudgetMillion(ExcelCellUtils.decimalValue(row, ExcelColumns.NATIONAL_BUDGET))
                .localBudgetMillion(ExcelCellUtils.decimalValue(row, ExcelColumns.LOCAL_BUDGET))
                .otherBudgetMillion(ExcelCellUtils.decimalValue(row, ExcelColumns.OTHER_BUDGET))
                .budgetStatus(classifyBudgetStatus(row))
                .previousVisitorsNumeric(ExcelCellUtils.integerValue(row, ExcelColumns.PREVIOUS_VISITORS))
                .previousVisitorsTextRaw(textIfNotNumeric(row, ExcelColumns.PREVIOUS_VISITORS))
                .domesticVisitorsNumeric(ExcelCellUtils.integerValue(row, ExcelColumns.DOMESTIC_VISITORS))
                .domesticVisitorsTextRaw(textIfNotNumeric(row, ExcelColumns.DOMESTIC_VISITORS))
                .foreignVisitorsNumeric(ExcelCellUtils.integerValue(row, ExcelColumns.FOREIGN_VISITORS))
                .foreignVisitorsTextRaw(textIfNotNumeric(row, ExcelColumns.FOREIGN_VISITORS))
                .measurementMethodRaw(ExcelCellUtils.stringValue(row, ExcelColumns.MEASUREMENT_METHOD))
                .build();
    }

    private String textIfNotNumeric(Row row, int colIndex) {
        return ExcelCellUtils.isNumeric(row, colIndex) ? null : ExcelCellUtils.stringValue(row, colIndex);
    }

    /**
     * V열(예산 합계) 상태 분류. 실제 2026년 파일은 항상 숫자 / "미확정" / "무응답" 셋 중
     * 하나였음을 확인했다. 그 외 예상 밖 텍스트가 들어오면 예산 추정에 쓸 수 없다는 점은
     * 분명하므로 보수적으로 NO_RESPONSE로 분류한다(호출부에서 원본 텍스트를 경고로 남긴다).
     */
    private BudgetStatus classifyBudgetStatus(Row row) {
        if (ExcelCellUtils.isNumeric(row, ExcelColumns.TOTAL_BUDGET)) {
            BigDecimal value = ExcelCellUtils.decimalValue(row, ExcelColumns.TOTAL_BUDGET);
            return value.signum() > 0 ? BudgetStatus.CONFIRMED : BudgetStatus.ZERO;
        }
        String text = ExcelCellUtils.stringValue(row, ExcelColumns.TOTAL_BUDGET);
        if ("미확정".equals(text)) {
            return BudgetStatus.UNCONFIRMED;
        }
        return BudgetStatus.NO_RESPONSE;
    }
}