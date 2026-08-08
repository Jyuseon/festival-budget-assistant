package com.festival.budgetassist.multiyear.backtest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * headline 연간 CPI table을 classpath 리소스({@code multiyear-annual-cpi.tsv})에서 읽어온다 -
 * 지시사항: "CPI 값은 코드에서 임의로 생성하지 마라, 별도 입력 데이터로 관리할 수 있게 구조를
 * 만들어라". 탭 구분 3열(year, indexValue, source) 텍스트 파일이라 사람이 직접 검토/갱신하기
 * 쉽고, 코드(자바 소스) 재컴파일 없이 값만 바꿀 수 있다.
 */
@Component
class AnnualPriceIndexProvider {

    private static final String RESOURCE_PATH = "/multiyear-annual-cpi.tsv";

    private final Map<Integer, AnnualPriceIndex> byYear;

    AnnualPriceIndexProvider() {
        this.byYear = load();
    }

    Optional<AnnualPriceIndex> get(int year) {
        return Optional.ofNullable(byYear.get(year));
    }

    /** report 출력용 - 연도 오름차순. */
    List<AnnualPriceIndex> all() {
        return byYear.values().stream()
                .sorted(Comparator.comparingInt(AnnualPriceIndex::year))
                .toList();
    }

    private Map<Integer, AnnualPriceIndex> load() {
        Map<Integer, AnnualPriceIndex> result = new LinkedHashMap<>();
        try (InputStream in = getClass().getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                throw new IllegalStateException("CPI 리소스를 찾을 수 없습니다: " + RESOURCE_PATH);
            }
            List<String> lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
            boolean header = true;
            for (String line : lines) {
                if (header) {
                    header = false;
                    continue; // "year\tindexValue\tsource" 헤더 skip
                }
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("\t", 3);
                if (parts.length < 3) {
                    throw new IllegalStateException("CPI 리소스 형식이 잘못됐습니다(탭 3열 필요): " + line);
                }
                int year = Integer.parseInt(parts[0].trim());
                double indexValue = Double.parseDouble(parts[1].trim());
                String source = parts[2].trim();
                result.put(year, new AnnualPriceIndex(year, indexValue, source));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return result;
    }
}