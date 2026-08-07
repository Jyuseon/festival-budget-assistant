package com.festival.budgetassist.multiyear.csv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.festival.budgetassist.multiyear.domain.MultiYearImportBatch;

/**
 * 로컬 전용 다년도 CSV Import CLI. HTTP로 노출하지 않는다.
 *
 * <p>{@code import.multiyear-run=true}가 명시적으로 전달됐을 때만 동작한다
 * ({@code @ConditionalOnProperty}이므로 이 플래그가 없으면 빈 자체가 생성되지 않는다). 일반 서버
 * 실행이나 기존 {@code import.run=true}(2026 엑셀 Import)와는 완전히 독립적인 플래그다.</p>
 *
 * <p>실행 예:</p>
 * <pre>
 * mvnw spring-boot:run "-Dspring-boot.run.arguments=--import.multiyear-run=true --festival.multiyear-csv.path=C:/path/festival_2017_2026_sanitized.csv"
 * </pre>
 * <p>또는 환경변수 FESTIVAL_MULTIYEAR_CSV_PATH를 미리 설정해두면 인자를 생략할 수 있다.</p>
 */
@Component
@ConditionalOnProperty(prefix = "import", name = "multiyear-run", havingValue = "true")
class MultiYearCsvImportRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MultiYearCsvImportRunner.class);

    private final MultiYearCsvImportService importService;

    @Value("${festival.multiyear-csv.path:}")
    private String csvPath;

    MultiYearCsvImportRunner(MultiYearCsvImportService importService) {
        this.importService = importService;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("================ 다년도(2017~2026) 지역축제 CSV Import ================");
        log.info("csvPath = {}", csvPath == null || csvPath.isBlank() ? "(미지정)" : csvPath);

        if (csvPath == null || csvPath.isBlank()) {
            throw new IllegalArgumentException(
                    "CSV 파일 경로가 지정되지 않았습니다. 환경변수 FESTIVAL_MULTIYEAR_CSV_PATH를 설정하거나 "
                            + "--festival.multiyear-csv.path=<경로> 인자를 전달하세요.");
        }

        Path path = Path.of(csvPath);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("파일을 찾을 수 없습니다: " + path.toAbsolutePath());
        }

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException e) {
            throw new IllegalArgumentException("파일을 읽을 수 없습니다: " + path.toAbsolutePath(), e);
        }

        try {
            MultiYearImportResult result = importService.importFromBytes(bytes, path.getFileName().toString());
            printResult(result);
        } catch (MultiYearCsvImportException e) {
            log.error("Import 실패: {}", e.getMessage());
            e.getDetails().stream().limit(20).forEach(detail -> log.error("  - {}", detail));
            throw e;
        }
    }

    private void printResult(MultiYearImportResult result) {
        if (result.duplicate()) {
            MultiYearImportBatch existing = result.batch();
            log.info("----------------------------------------------------------");
            log.info("동일한 파일(해시 일치)이 이미 Import되어 있어 재적재하지 않았습니다.");
            log.info("기존 batchId    = {}", existing.getId());
            log.info("기존 importedAt = {}", existing.getImportedAt());
            log.info("기존 totalRows  = {}", existing.getTotalRows());
            log.info("----------------------------------------------------------");
            return;
        }

        MultiYearImportSummary summary = result.summary();
        log.info("----------------------------------------------------------");
        log.info("Import 성공. batchId = {}", result.batch().getId());
        log.info("전체 데이터 행                     : {}", summary.totalRows());
        log.info("예산 유효(VALID)행                 : {}", summary.validBudgetRows());
        log.info("예산 UNIT_SCALE_SUSPECT행(알고리즘 제외 대상) : {}", summary.unitScaleSuspectRows());
        log.info("예산 MISSING_OR_NONPOSITIVE행      : {}", summary.missingOrNonpositiveBudgetRows());
        log.info("개최기간 미확정행                  : {}", summary.missingDurationRows());
        log.info("COVID 영향행(2020~2021)            : {}", summary.covidAffectedRows());
        log.info("경고 건수                          : {}", summary.warnings().size());
        log.info("연도별 저장 행 수:");
        for (Map.Entry<Integer, Integer> entry : summary.rowCountByYear().entrySet()) {
            log.info("  {}년 : {}행", entry.getKey(), entry.getValue());
        }
        summary.warnings().stream().limit(20)
                .forEach(w -> log.warn("  - {}년 연번{}: {}", w.datasetYear(), w.sourceRowNumber(), w.message()));
        if (summary.warnings().size() > 20) {
            log.warn("  ... 외 {}건 더 있음", summary.warnings().size() - 20);
        }
        log.info("----------------------------------------------------------");
    }
}