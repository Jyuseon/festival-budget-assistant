package com.festival.budgetassist.multiyear.csv;

import java.util.ArrayList;
import java.util.List;

/**
 * 의존성 없는 최소 RFC4180 CSV 파서.
 *
 * <p>따옴표(")로 감싼 필드 안의 콤마/개행을 그대로 보존하고, {@code ""}를 이스케이프된 따옴표 한
 * 글자로 해석한다. 이 프로젝트의 sanitized CSV는 신뢰할 수 있는 파이프라인 산출물이라 별도
 * 라이브러리(commons-csv 등)를 추가하는 대신 이 정도 크기의 파서로 충분하다고 판단했다.</p>
 */
final class SimpleCsvReader {

    private SimpleCsvReader() {
    }

    /**
     * 전체 CSV 텍스트를 행 단위(각 행은 필드 리스트)로 파싱한다. 선행 UTF-8 BOM은 호출부에서
     * 이미 제거되어 있다고 가정한다.
     */
    static List<List<String>> parse(String csvText) {
        List<List<String>> rows = new ArrayList<>();
        List<String> currentRow = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean rowHasContent = false;

        int length = csvText.length();
        int i = 0;
        while (i < length) {
            char c = csvText.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < length && csvText.charAt(i + 1) == '"') {
                        field.append('"');
                        i += 2;
                        continue;
                    }
                    inQuotes = false;
                    i++;
                    continue;
                }
                field.append(c);
                i++;
                continue;
            }

            switch (c) {
                case '"' -> {
                    inQuotes = true;
                    rowHasContent = true;
                    i++;
                }
                case ',' -> {
                    currentRow.add(field.toString());
                    field.setLength(0);
                    rowHasContent = true;
                    i++;
                }
                case '\r' -> i++; // CRLF의 \r은 무시하고 다음 \n에서 행을 종료한다
                case '\n' -> {
                    currentRow.add(field.toString());
                    field.setLength(0);
                    rows.add(currentRow);
                    currentRow = new ArrayList<>();
                    rowHasContent = false;
                    i++;
                }
                default -> {
                    field.append(c);
                    rowHasContent = true;
                    i++;
                }
            }
        }

        // 마지막 줄에 개행이 없는 경우 마무리
        if (rowHasContent || field.length() > 0 || !currentRow.isEmpty()) {
            currentRow.add(field.toString());
            rows.add(currentRow);
        }

        return rows;
    }
}