package com.festival.budgetassist.admin.multiyear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.festival.budgetassist.multiyear.csv.MultiYearCsvImportService;
import com.festival.budgetassist.multiyear.csv.MultiYearImportResult;
import com.festival.budgetassist.multiyear.csv.SimpleJsonParser;

/**
 * 실제 sanitized CSV(2017~2026)를 Import한 뒤 {@code GET .../years/{year}} 예산 통계
 * (mean/P25/P50/P75/P90/P95/max)가 {@code manifest/year_profiles.json}의
 * {@code budget_million_stats_excluding_flagged_unit_suspects}와 일치하는지 검증한다.
 *
 * <p>2025/2026 median이 화면에서 "-"로 보이던 문제(실제로는 API/DB/CSV 전부 정상이었고
 * 재현되지 않았다 - 아래 회귀 리포트 참고)를 다시 만들지 않기 위한 영구 회귀 테스트다.
 * {@code FESTIVAL_MULTIYEAR_CSV_PATH} 환경변수가 없으면 건너뛴다(CI/일반 빌드는 영향 없음) -
 * {@code MultiYearCsvYearProfileAcceptanceTest}와 동일한 패턴이다.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class MultiYearAdminBudgetStatisticsAcceptanceTest {

    /** 소수점 반올림으로 인한 오차 허용 범위(백만원). manifest 값은 소수 셋째자리까지 반올림되어 있다. */
    private static final double DELTA = 0.06;

    @Autowired
    private MultiYearCsvImportService importService;
    @Autowired
    private MultiYearAdminDatasetQueryService queryService;

    @Test
    @SuppressWarnings("unchecked")
    void realCsv_budgetStatisticsMatchYearProfilesManifestForEveryYear() throws IOException {
        String csvPathValue = System.getenv("FESTIVAL_MULTIYEAR_CSV_PATH");
        Assumptions.assumeTrue(csvPathValue != null && !csvPathValue.isBlank(),
                "FESTIVAL_MULTIYEAR_CSV_PATH 환경변수가 없어 로컬 인수 테스트를 건너뜁니다.");

        Path csvPath = Path.of(csvPathValue);
        Assumptions.assumeTrue(Files.isRegularFile(csvPath),
                "FESTIVAL_MULTIYEAR_CSV_PATH 파일을 찾을 수 없어 건너뜁니다: " + csvPath.toAbsolutePath());

        Path manifestPath = resolveManifestPath(csvPath);
        Assumptions.assumeTrue(Files.isRegularFile(manifestPath),
                "year_profiles.json을 찾을 수 없어 건너뜁니다: " + manifestPath.toAbsolutePath());

        String manifestText = Files.readString(manifestPath, StandardCharsets.UTF_8);
        Map<String, Object> manifest = (Map<String, Object>) SimpleJsonParser.parse(manifestText);

        byte[] bytes = Files.readAllBytes(csvPath);
        MultiYearImportResult result = importService.importFromBytes(bytes, csvPath.getFileName().toString());
        assertTrue(!result.duplicate(), "테스트 트랜잭션은 매번 롤백되므로 첫 실행이어야 함");

        int checkedYears = 0;
        for (Map.Entry<String, Object> entry : manifest.entrySet()) {
            int year = Integer.parseInt(entry.getKey());
            Map<String, Object> yearProfile = (Map<String, Object>) entry.getValue();
            Map<String, Object> expectedStats =
                    (Map<String, Object>) yearProfile.get("budget_million_stats_excluding_flagged_unit_suspects");
            long expectedSampleCount = ((Number) yearProfile.get("positive_budget_count_excluding_flagged_unit_suspects")).longValue();

            MultiYearAdminYearDetailResponse detail = queryService.getYearDetail(year);
            assertTrue(detail.available(), year + "년 데이터가 없음");
            MultiYearBudgetStatistics stats = detail.budgetStatistics();

            String label = "%d년".formatted(year);
            assertEquals(expectedSampleCount, stats.sampleCount(), label + " sampleCount(=validBudgetCount) 불일치");
            assertEquals(num(expectedStats, "mean"), stats.meanMillion(), DELTA, label + " mean 불일치");
            assertEquals(num(expectedStats, "p25"), stats.p25Million(), DELTA, label + " P25 불일치");
            assertEquals(num(expectedStats, "median"), stats.medianMillion(), DELTA, label + " P50/median 불일치");
            assertEquals(num(expectedStats, "p75"), stats.p75Million(), DELTA, label + " P75 불일치");
            assertEquals(num(expectedStats, "p90"), stats.p90Million(), DELTA, label + " P90 불일치");
            assertEquals(num(expectedStats, "p95"), stats.p95Million(), DELTA, label + " P95 불일치");
            assertEquals(num(expectedStats, "max"), stats.maxMillion(), DELTA, label + " max 불일치");

            checkedYears++;
        }

        assertEquals(10, checkedYears, "2017~2026 10개년이 모두 검증돼야 함");
    }

    /** 2025/2026도 다른 연도와 동일하게 median이 실제 숫자로 채워지는지 명시적으로 재확인(회귀 방지). */
    @Test
    void realCsv_2025And2026MedianAreNonZeroAndMatchExpectedValues() throws IOException {
        String csvPathValue = System.getenv("FESTIVAL_MULTIYEAR_CSV_PATH");
        Assumptions.assumeTrue(csvPathValue != null && !csvPathValue.isBlank(),
                "FESTIVAL_MULTIYEAR_CSV_PATH 환경변수가 없어 로컬 인수 테스트를 건너뜁니다.");
        Path csvPath = Path.of(csvPathValue);
        Assumptions.assumeTrue(Files.isRegularFile(csvPath),
                "FESTIVAL_MULTIYEAR_CSV_PATH 파일을 찾을 수 없어 건너뜁니다: " + csvPath.toAbsolutePath());

        byte[] bytes = Files.readAllBytes(csvPath);
        importService.importFromBytes(bytes, csvPath.getFileName().toString());

        MultiYearAdminSummaryResponse summary = queryService.getSummary();
        MultiYearYearSummary y2025 = summary.years().stream().filter(y -> y.datasetYear() == 2025).findFirst().orElseThrow();
        MultiYearYearSummary y2026 = summary.years().stream().filter(y -> y.datasetYear() == 2026).findFirst().orElseThrow();

        assertEquals(1193, y2025.validBudgetCount());
        assertEquals(1238, y2026.validBudgetCount());
        assertEquals(200.0, y2025.medianValidBudgetMillion(), DELTA);
        assertEquals(204.5, y2026.medianValidBudgetMillion(), DELTA);
    }

    private double num(Map<String, Object> stats, String key) {
        return ((Number) stats.get(key)).doubleValue();
    }

    private Path resolveManifestPath(Path csvPath) {
        String manifestOverride = System.getenv("FESTIVAL_MULTIYEAR_MANIFEST_PATH");
        if (manifestOverride != null && !manifestOverride.isBlank()) {
            return Path.of(manifestOverride);
        }
        Path packageRoot = csvPath.toAbsolutePath().getParent().getParent();
        return packageRoot.resolve("manifest").resolve("year_profiles.json");
    }
}