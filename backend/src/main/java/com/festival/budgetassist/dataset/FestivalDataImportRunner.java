package com.festival.budgetassist.dataset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.festival.budgetassist.festival.domain.DatasetImportBatch;

/**
 * 로컬 전용 엑셀 Import CLI. HTTP로 노출하지 않는다(관리자 API는 이후 기능).
 *
 * <p>{@code import.run=true}가 명시적으로 전달됐을 때만 동작한다({@code @ConditionalOnProperty}
 * 이므로 이 플래그가 없으면 빈 자체가 생성되지 않는다). 일반 서버 실행(`mvnw spring-boot:run`)에는
 * 전혀 관여하지 않는다.</p>
 *
 * <p>실행 예:</p>
 * <pre>
 * mvnw spring-boot:run "-Dspring-boot.run.arguments=--import.run=true --festival.excel.path=C:/path/to.xlsx"
 * </pre>
 * <p>또는 환경변수 FESTIVAL_EXCEL_PATH를 미리 설정해두면 인자를 생략할 수 있다
 * (Spring의 relaxed binding으로 festival.excel.path 프로퍼티에 자동 매핑된다).</p>
 */
@Component
@ConditionalOnProperty(prefix = "import", name = "run", havingValue = "true")
class FestivalDataImportRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(FestivalDataImportRunner.class);

    private final FestivalExcelImporter importer;

    @Value("${festival.excel.path:}")
    private String excelPath;

    @Value("${import.dataset-year:2026}")
    private int datasetYear;

    FestivalDataImportRunner(FestivalExcelImporter importer) {
        this.importer = importer;
    }

    @Override
    public void run(String... args) throws Exception {
        printBanner();

        if (excelPath == null || excelPath.isBlank()) {
            throw new IllegalArgumentException(
                    "엑셀 파일 경로가 지정되지 않았습니다. 환경변수 FESTIVAL_EXCEL_PATH를 설정하거나 "
                            + "--festival.excel.path=<경로> 인자를 전달하세요.");
        }

        Path path = Path.of(excelPath);
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
            ImportResult result = importer.importFromBytes(bytes, path.getFileName().toString(), datasetYear);
            printResult(result);
        } catch (ImportValidationException e) {
            log.error("Import 실패: {}", e.getMessage());
            e.getDetails().forEach(detail -> log.error("  - {}", detail));
            throw e;
        }
    }

    private void printBanner() {
        log.info("================ 지역축제 예산 데이터 Import ================");
        log.info("datasetYear = {}", datasetYear);
        log.info("excelPath   = {}", excelPath == null || excelPath.isBlank() ? "(미지정)" : excelPath);
    }

    private void printResult(ImportResult result) {
        if (result.duplicate()) {
            DatasetImportBatch existing = result.batch();
            log.info("----------------------------------------------------------");
            log.info("동일한 파일(해시 일치)이 이미 Import되어 있어 재적재하지 않았습니다.");
            log.info("기존 batchId     = {}", existing.getId());
            log.info("기존 importedAt  = {}", existing.getImportedAt());
            log.info("기존 totalRows   = {}", existing.getTotalRows());
            log.info("기존 validBudgetRows = {}", existing.getValidBudgetRows());
            log.info("----------------------------------------------------------");
            return;
        }

        ImportSummary summary = result.summary();
        DatasetImportBatch batch = result.batch();
        ReferenceProfileCheck check = result.referenceProfileCheck();

        log.info("----------------------------------------------------------");
        log.info("Import 성공. batchId = {}", batch.getId());
        log.info("파일 해시(SHA-256)      : {}", batch.getFileHash());
        log.info("전체 데이터 행           : {}", summary.totalRows());
        log.info("예산 유효(CONFIRMED)행   : {}", summary.validBudgetRows());
        log.info("예산 미확정행            : {}", summary.unconfirmedBudgetRows());
        log.info("예산 무응답행            : {}", summary.noResponseBudgetRows());
        log.info("예산 0원행               : {}", summary.zeroBudgetRows());
        log.info("개최기간 미확정행        : {}", summary.missingDurationRows());
        log.info("광역지역 종류 수         : {}", summary.regionCount());
        log.info("축제유형 종류 수         : {}", summary.festivalTypeCount());
        log.info("개최장소유형 종류 수     : {}", summary.venueTypeCount());
        log.info("경고 건수                : {}", summary.warnings().size());
        summary.warnings().stream().limit(20)
                .forEach(w -> log.warn("  - 연번{}: {}", w.sourceRowNumber(), w.message()));
        if (summary.warnings().size() > 20) {
            log.warn("  ... 외 {}건 더 있음(전체 목록은 /admin/datasets 또는 import_warning 테이블 참고)", summary.warnings().size() - 20);
        }

        if (check.applicable()) {
            if (check.matches()) {
                log.info("참조 기준값(2026년 알려진 프로필) 일치 여부 : 일치");
            } else {
                log.warn("참조 기준값(2026년 알려진 프로필) 일치 여부 : 불일치 -> {}", check.mismatches());
            }
        } else {
            log.info("참조 기준값 비교 : 해당 연도({})에 대한 알려진 기준값 없음 - 비교 생략", datasetYear);
        }
        log.info("----------------------------------------------------------");
    }
}