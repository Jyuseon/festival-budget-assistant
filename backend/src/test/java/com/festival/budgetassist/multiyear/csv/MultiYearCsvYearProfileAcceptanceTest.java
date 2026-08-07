package com.festival.budgetassist.multiyear.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

import com.festival.budgetassist.multiyear.domain.BudgetQualityFlag;
import com.festival.budgetassist.multiyear.repository.MultiYearFestivalRecordRepository;

/**
 * 실제 sanitized CSV(2017~2026, 10,198행)로만 검증하는 로컬 인수 테스트.
 *
 * <p>{@code claude_multiyear_festival_package}는 이 저장소에 커밋되어 있지 않다(개발용 파생
 * 데이터 패키지가 별도 배포됨). {@code FESTIVAL_MULTIYEAR_CSV_PATH} 환경변수가 설정되어 있고
 * 파일이 실제로 존재할 때만 실행되며, 그렇지 않으면 {@link Assumptions}로 조용히 건너뛴다 -
 * CI나 일반 빌드가 이 파일의 부재로 실패하지 않는다. 기존
 * {@code FestivalExcelImporterLocalAcceptanceTest}와 동일한 패턴이다.</p>
 *
 * <p>매니페스트 경로는 기본적으로 CSV 경로 기준 {@code ../../manifest/year_profiles.json}
 * (패키지 레이아웃: {@code data/festival_2017_2026_sanitized.csv},
 * {@code manifest/year_profiles.json})로 추정하며, {@code FESTIVAL_MULTIYEAR_MANIFEST_PATH}로
 * 명시적으로 지정할 수도 있다. JSON 파싱은 jackson 등을 새 의존성으로 추가하지 않기 위해
 * {@link SimpleJsonParser}(테스트 전용 최소 구현)로 한다.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class MultiYearCsvYearProfileAcceptanceTest {

    @Autowired
    private MultiYearCsvImportService importService;
    @Autowired
    private MultiYearFestivalRecordRepository recordRepository;

    @Test
    @SuppressWarnings("unchecked")
    void realCsv_rowCountPerYearMatchesYearProfilesManifest() throws IOException {
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
        assertFalse(result.duplicate(), "테스트 트랜잭션은 매번 롤백되므로 첫 실행이어야 함");

        long totalExpected = 0;
        for (Map.Entry<String, Object> entry : manifest.entrySet()) {
            int year = Integer.parseInt(entry.getKey());
            Map<String, Object> yearProfile = (Map<String, Object>) entry.getValue();

            long expectedRowCount = ((Number) yearProfile.get("row_count")).longValue();
            long actualRowCount = recordRepository.countByDatasetYear(year);
            assertEquals(expectedRowCount, actualRowCount, year + "년 row_count 불일치");

            long expectedUnitScaleSuspect = ((Number) yearProfile.get("budget_unit_suspect_count")).longValue();
            long actualUnitScaleSuspect = recordRepository.countByDatasetYearAndBudgetQualityFlag(year, BudgetQualityFlag.UNIT_SCALE_SUSPECT);
            assertEquals(expectedUnitScaleSuspect, actualUnitScaleSuspect, year + "년 UNIT_SCALE_SUSPECT count 불일치");

            totalExpected += expectedRowCount;
        }

        assertEquals(10, manifest.size(), "2017~2026 10개년이 모두 검증돼야 함");
        assertEquals(totalExpected, recordRepository.count());
    }

    private Path resolveManifestPath(Path csvPath) {
        String manifestOverride = System.getenv("FESTIVAL_MULTIYEAR_MANIFEST_PATH");
        if (manifestOverride != null && !manifestOverride.isBlank()) {
            return Path.of(manifestOverride);
        }
        // data/festival_2017_2026_sanitized.csv -> (패키지 루트)/manifest/year_profiles.json
        Path packageRoot = csvPath.toAbsolutePath().getParent().getParent();
        return packageRoot.resolve("manifest").resolve("year_profiles.json");
    }
}