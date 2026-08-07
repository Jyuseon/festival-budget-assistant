package com.festival.budgetassist.multiyear.csv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * CSV 원문 텍스트 -&gt; {@link MultiYearCsvRawRow} 목록. 헤더는 순서가 아니라 이름으로 매칭한다.
 */
@Component
class MultiYearCsvRowMapper {

    /**
     * @throws MultiYearCsvImportException 헤더 행이 없거나 필수 컬럼이 누락된 경우
     */
    List<MultiYearCsvRawRow> mapRows(String csvText, String fileHash, String originalFileName) {
        String withoutBom = stripBom(csvText);
        List<List<String>> parsedRows = SimpleCsvReader.parse(withoutBom);
        if (parsedRows.isEmpty()) {
            throw new MultiYearCsvImportException("CSV에 헤더 행이 없습니다", fileHash, originalFileName, List.of());
        }

        List<String> header = parsedRows.get(0);
        Map<String, Integer> columnIndex = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            columnIndex.put(header.get(i).trim(), i);
        }

        List<String> missing = MultiYearCsvColumns.REQUIRED_COLUMNS.stream()
                .filter(col -> !columnIndex.containsKey(col))
                .toList();
        if (!missing.isEmpty()) {
            throw new MultiYearCsvImportException(
                    "CSV 헤더에 필수 컬럼이 없습니다: " + String.join(", ", missing),
                    fileHash, originalFileName, missing);
        }

        List<MultiYearCsvRawRow> rows = new ArrayList<>(parsedRows.size() - 1);
        for (int lineNumber = 2; lineNumber <= parsedRows.size(); lineNumber++) {
            List<String> fields = parsedRows.get(lineNumber - 1);
            if (isBlankRow(fields)) {
                continue; // 파일 끝의 빈 줄 등
            }
            Map<String, String> valuesByColumn = new LinkedHashMap<>();
            for (String column : MultiYearCsvColumns.REQUIRED_COLUMNS) {
                int idx = columnIndex.get(column);
                valuesByColumn.put(column, idx < fields.size() ? fields.get(idx) : null);
            }
            rows.add(new MultiYearCsvRawRow(lineNumber, valuesByColumn));
        }
        return rows;
    }

    private boolean isBlankRow(List<String> fields) {
        return fields.stream().allMatch(f -> f == null || f.isBlank());
    }

    private String stripBom(String text) {
        return !text.isEmpty() && text.charAt(0) == '﻿' ? text.substring(1) : text;
    }
}