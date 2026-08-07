package com.festival.budgetassist.dataset;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.festival.budgetassist.festival.domain.DatasetImportBatch;
import com.festival.budgetassist.festival.domain.FestivalRecord;
import com.festival.budgetassist.festival.domain.ImportStatus;
import com.festival.budgetassist.festival.repository.DatasetImportBatchRepository;

/**
 * 엑셀 Import 오케스트레이터. 파일 형식/시트/헤더 검증 → 전체 파싱·정규화(메모리상) →
 * 검증 통과 시에만 {@link DatasetPersistenceService}에 위임해 DB에 반영한다.
 *
 * <p>이 클래스 자체는 {@code @Transactional}이 아니다 — DB 쓰기가 시작되는 시점은
 * 오직 {@link DatasetPersistenceService#replaceYearData}뿐이고, 그 전까지의 모든 검증은
 * 순수 메모리 연산이라 실패해도 DB에 아무 영향이 없다.</p>
 */
@Component
public class FestivalExcelImporter {

    private static final Logger log = LoggerFactory.getLogger(FestivalExcelImporter.class);

    /** '조사표' 시트 존재는 연도와 무관하게 항상 요구되는 구조적 조건. */
    private static final String EXPECTED_SHEET_NAME = "조사표";

    private static final List<HeaderCheck> HEADER_CHECKS = List.of(
            new HeaderCheck("B5", 4, ExcelColumns.SOURCE_ROW_NUMBER, "연번"),
            new HeaderCheck("C5", 4, ExcelColumns.REGION, "광역자치단체명"),
            new HeaderCheck("E5", 4, ExcelColumns.FESTIVAL_NAME, "축제명"),
            new HeaderCheck("F5", 4, ExcelColumns.FESTIVAL_TYPE, "축제유형"),
            new HeaderCheck("H6", 5, ExcelColumns.VENUE_TYPE, "축제유형"),
            new HeaderCheck("R6", 5, ExcelColumns.DURATION_DAYS, "총일수"),
            new HeaderCheck("V5", 4, ExcelColumns.TOTAL_BUDGET, "예산(백만원)"),
            new HeaderCheck("AA5", 4, ExcelColumns.PREVIOUS_VISITORS, "방문객수(前년)")
    );

    private final FestivalExcelRowMapper rowMapper;
    private final DataNormalizationService normalizationService;
    private final DatasetPersistenceService persistenceService;
    private final DatasetImportBatchRepository datasetImportBatchRepository;

    FestivalExcelImporter(FestivalExcelRowMapper rowMapper,
                           DataNormalizationService normalizationService,
                           DatasetPersistenceService persistenceService,
                           DatasetImportBatchRepository datasetImportBatchRepository) {
        this.rowMapper = rowMapper;
        this.normalizationService = normalizationService;
        this.persistenceService = persistenceService;
        this.datasetImportBatchRepository = datasetImportBatchRepository;
    }

    public ImportResult importFromBytes(byte[] excelBytes, String originalFileName, int datasetYear) {
        String fileHash = sha256Hex(excelBytes);
        log.info("Import 시작: file={}, datasetYear={}, sha256={}", originalFileName, datasetYear, fileHash);

        Optional<DatasetImportBatch> existing = datasetImportBatchRepository
                .findFirstByFileHashAndStatusOrderByImportedAtDesc(fileHash, ImportStatus.SUCCESS);
        if (existing.isPresent()) {
            DatasetImportBatch batch = existing.get();
            log.info("동일 해시({})로 이미 성공 처리된 배치가 있습니다 (batchId={}, importedAt={}). "
                            + "재적재하지 않고 종료합니다.",
                    fileHash, batch.getId(), batch.getImportedAt());
            return ImportResult.duplicate(batch);
        }

        try {
            return doImport(excelBytes, originalFileName, datasetYear, fileHash);
        } catch (ImportValidationException e) {
            recordFailure(fileHash, originalFileName, datasetYear, e.getMessage(), e.getDetails());
            throw e;
        } catch (IOException e) {
            String message = "엑셀 파일을 열 수 없습니다: " + e.getMessage();
            recordFailure(fileHash, originalFileName, datasetYear, message, List.of());
            throw new ImportValidationException(message, fileHash, originalFileName, List.of());
        }
    }

    private ImportResult doImport(byte[] excelBytes, String originalFileName, int datasetYear, String fileHash) throws IOException {
        List<FestivalRecord> records;
        ImportSummary summary;

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(excelBytes))) {
            Sheet sheet = workbook.getSheet(EXPECTED_SHEET_NAME);
            if (sheet == null) {
                throw new ImportValidationException(
                        "'%s' 시트를 찾을 수 없습니다".formatted(EXPECTED_SHEET_NAME), fileHash, originalFileName, List.of());
            }

            List<String> headerErrors = validateHeaders(sheet);
            if (!headerErrors.isEmpty()) {
                throw new ImportValidationException("헤더 구조가 예상과 다릅니다", fileHash, originalFileName, headerErrors);
            }

            List<RawFestivalRow> rawRows = rowMapper.mapAllRows(sheet);
            if (rawRows.isEmpty()) {
                throw new ImportValidationException("데이터 행을 찾을 수 없습니다(9행부터 연번이 비어 있음)", fileHash, originalFileName, List.of());
            }

            List<String> normalizationErrors = new ArrayList<>();
            List<RowWarning> warnings = new ArrayList<>();
            records = new ArrayList<>(rawRows.size());
            for (RawFestivalRow raw : rawRows) {
                NormalizationResult result = normalizationService.normalize(raw, datasetYear);
                normalizationErrors.addAll(result.errors());
                warnings.addAll(result.warnings());
                if (!result.hasErrors()) {
                    records.add(result.record());
                }
            }

            if (!normalizationErrors.isEmpty()) {
                throw new ImportValidationException(
                        "%d개 행에서 필수 항목을 인식하지 못했습니다".formatted(normalizationErrors.size()),
                        fileHash, originalFileName, normalizationErrors);
            }

            summary = buildSummary(records, warnings);
        }

        ReferenceProfileCheck referenceProfileCheck = ReferenceProfileCheck.compare(datasetYear, summary);
        if (referenceProfileCheck.applicable() && !referenceProfileCheck.matches()) {
            log.warn("알려진 {}년 데이터셋 기준값과 차이가 있습니다: {}", datasetYear, referenceProfileCheck.mismatches());
        }

        DatasetImportBatch batchToSave = DatasetImportBatch.builder()
                .datasetYear(datasetYear)
                .originalFileName(originalFileName)
                .fileHash(fileHash)
                .totalRows(summary.totalRows())
                .validBudgetRows(summary.validBudgetRows())
                .invalidRows(summary.totalRows() - summary.validBudgetRows())
                .importedAt(Instant.now())
                .status(ImportStatus.SUCCESS)
                .errorSummary(summary.warnings().isEmpty() ? null : "경고 %d건 (상세는 import_warning 테이블 참고)".formatted(summary.warnings().size()))
                .build();

        DatasetImportBatch savedBatch = persistenceService.replaceYearData(datasetYear, records, batchToSave, summary.warnings());
        log.info("Import 완료: batchId={}, totalRows={}, validBudgetRows={}",
                savedBatch.getId(), summary.totalRows(), summary.validBudgetRows());

        return ImportResult.success(savedBatch, summary, referenceProfileCheck);
    }

    private void recordFailure(String fileHash, String originalFileName, int datasetYear, String message, List<String> details) {
        String errorSummary = details.isEmpty() ? message : message + " :: " + String.join(" | ", details.subList(0, Math.min(details.size(), 20)));
        DatasetImportBatch failedBatch = DatasetImportBatch.builder()
                .datasetYear(datasetYear)
                .originalFileName(originalFileName)
                .fileHash(fileHash)
                .totalRows(0)
                .validBudgetRows(0)
                .invalidRows(0)
                .importedAt(Instant.now())
                .status(ImportStatus.FAILED)
                .errorSummary(errorSummary)
                .build();
        try {
            persistenceService.recordFailedAttempt(failedBatch);
        } catch (Exception persistFailure) {
            // 실패 이력 기록 자체가 실패해도(예: 해시 유니크 제약 충돌) 원래 예외 전파를 막지 않는다.
            log.warn("실패 이력 기록 중 오류 발생: {}", persistFailure.getMessage());
        }
    }

    private List<String> validateHeaders(Sheet sheet) {
        List<String> errors = new ArrayList<>();
        for (HeaderCheck check : HEADER_CHECKS) {
            Row row = sheet.getRow(check.rowIndex());
            String actual = ExcelCellUtils.stringValue(row, check.colIndex());
            String normalizedActual = normalizeHeaderText(actual);
            String normalizedExpected = normalizeHeaderText(check.expected());
            if (!normalizedExpected.equals(normalizedActual)) {
                errors.add("%s 셀: 예상='%s' 실제='%s'".formatted(check.cellRef(), check.expected(), actual));
            }
        }
        return errors;
    }

    private String normalizeHeaderText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }

    private ImportSummary buildSummary(List<FestivalRecord> records, List<RowWarning> warnings) {
        int total = records.size();
        long valid = records.stream().filter(r -> r.getBudgetStatus() == com.festival.budgetassist.festival.domain.BudgetStatus.CONFIRMED).count();
        long unconfirmed = records.stream().filter(r -> r.getBudgetStatus() == com.festival.budgetassist.festival.domain.BudgetStatus.UNCONFIRMED).count();
        long noResponse = records.stream().filter(r -> r.getBudgetStatus() == com.festival.budgetassist.festival.domain.BudgetStatus.NO_RESPONSE).count();
        long zero = records.stream().filter(r -> r.getBudgetStatus() == com.festival.budgetassist.festival.domain.BudgetStatus.ZERO).count();
        long missingDuration = records.stream().filter(r -> r.getDurationDays() == null).count();

        Set<?> regions = records.stream().map(FestivalRecord::getRegion).collect(Collectors.toSet());
        Set<?> festivalTypes = records.stream().map(FestivalRecord::getFestivalType).collect(Collectors.toSet());
        Set<?> venueTypes = records.stream().map(FestivalRecord::getVenueType).collect(Collectors.toSet());

        return new ImportSummary(
                total,
                (int) valid,
                (int) unconfirmed,
                (int) noResponse,
                (int) zero,
                (int) missingDuration,
                regions.size(),
                festivalTypes.size(),
                venueTypes.size(),
                warnings
        );
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다", e);
        }
    }

    private record HeaderCheck(String cellRef, int rowIndex, int colIndex, String expected) {
    }
}