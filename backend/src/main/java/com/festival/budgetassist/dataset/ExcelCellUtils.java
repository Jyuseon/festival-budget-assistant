package com.festival.budgetassist.dataset;

import java.math.BigDecimal;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;

/**
 * POI {@link Cell} 값을 안전하게 읽기 위한 유틸리티.
 * 셀이 없거나 빈 문자열이면 null을 반환해서, "값 없음"과 "빈 문자열"을 구분하지 않고 통일한다.
 */
final class ExcelCellUtils {

    private ExcelCellUtils() {
    }

    /** 셀이 존재하고 문자열(또는 숫자를 문자열로 취급 가능한) 타입일 때 trim된 값을, 아니면 null을 반환한다. */
    static String stringValue(Row row, int colIndex) {
        if (row == null) {
            return null;
        }
        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            return null;
        }
        String raw = switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> formatNumericAsString(cell.getNumericCellValue());
            case BLANK -> null;
            default -> null;
        };
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 셀이 숫자 타입일 때만 정수로 변환해서 반환한다(반올림). 문자열/빈 셀이면 null. */
    static Integer integerValue(Row row, int colIndex) {
        Double numeric = rawNumericValue(row, colIndex);
        return numeric == null ? null : (int) Math.round(numeric);
    }

    /** 셀이 숫자 타입일 때만 BigDecimal로 변환해서 반환한다(원본 그대로, 단위 변환 없음). 아니면 null. */
    static BigDecimal decimalValue(Row row, int colIndex) {
        Double numeric = rawNumericValue(row, colIndex);
        return numeric == null ? null : BigDecimal.valueOf(numeric);
    }

    /** 셀이 숫자 타입인지 여부. */
    static boolean isNumeric(Row row, int colIndex) {
        return rawNumericValue(row, colIndex) != null;
    }

    private static Double rawNumericValue(Row row, int colIndex) {
        if (row == null) {
            return null;
        }
        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        return null;
    }

    private static String formatNumericAsString(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}