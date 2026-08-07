package com.festival.budgetassist.multiyear.csv;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.festival.budgetassist.festival.domain.ImportStatus;
import com.festival.budgetassist.multiyear.domain.BudgetQualityFlag;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;
import com.festival.budgetassist.multiyear.domain.MultiYearImportBatch;
import com.festival.budgetassist.multiyear.repository.MultiYearImportBatchRepository;

/**
 * 다년도(2017~2026) sanitized CSV Import 오케스트레이터.
 *
 * <p>구조 검증(필수 컬럼) → 전체 파싱·정규화(메모리상) → 검증 통과 시에만
 * {@link MultiYearCsvPersistenceService}에 위임해 DB에 반영한다. 기존
 * {@link com.festival.budgetassist.dataset.FestivalExcelImporter}(2026 전용)와 완전히 분리된
 * 경로이므로, 이 서비스를 실행해도 2026 production Import/BudgetEstimator/confidence는 전혀
 * 영향을 받지 않는다.</p>
 */
@Component
public class MultiYearCsvImportService {

    private static final Logger log = LoggerFactory.getLogger(MultiYearCsvImportService.class);

    private final MultiYearCsvRowMapper rowMapper;
    private final MultiYearCsvNormalizationService normalizationService;
    private final MultiYearCsvPersistenceService persistenceService;
    private final MultiYearImportBatchRepository batchRepository;

    MultiYearCsvImportService(MultiYearCsvRowMapper rowMapper,
                               MultiYearCsvNormalizationService normalizationService,
                               MultiYearCsvPersistenceService persistenceService,
                               MultiYearImportBatchRepository batchRepository) {
        this.rowMapper = rowMapper;
        this.normalizationService = normalizationService;
        this.persistenceService = persistenceService;
        this.batchRepository = batchRepository;
    }

    public MultiYearImportResult importFromBytes(byte[] csvBytes, String originalFileName) {
        String fileHash = sha256Hex(csvBytes);
        log.info("다년도 CSV Import 시작: file={}, sha256={}", originalFileName, fileHash);

        Optional<MultiYearImportBatch> existing = batchRepository
                .findFirstByFileHashAndStatusOrderByImportedAtDesc(fileHash, ImportStatus.SUCCESS);
        if (existing.isPresent()) {
            MultiYearImportBatch batch = existing.get();
            log.info("동일한 해시({})로 이미 성공 처리된 배치가 있습니다(batchId={}). 재적재하지 않습니다.", fileHash, batch.getId());
            return MultiYearImportResult.duplicate(batch);
        }

        try {
            return doImport(csvBytes, originalFileName, fileHash);
        } catch (MultiYearCsvImportException e) {
            recordFailure(fileHash, originalFileName, e.getMessage(), e.getDetails());
            throw e;
        }
    }

    private MultiYearImportResult doImport(byte[] csvBytes, String originalFileName, String fileHash) {
        String csvText = new String(csvBytes, StandardCharsets.UTF_8);
        List<MultiYearCsvRawRow> rawRows = rowMapper.mapRows(csvText, fileHash, originalFileName);
        if (rawRows.isEmpty()) {
            throw new MultiYearCsvImportException("데이터 행을 찾을 수 없습니다", fileHash, originalFileName, List.of());
        }

        List<String> normalizationErrors = new ArrayList<>();
        List<MultiYearRowWarning> warnings = new ArrayList<>();
        Map<Integer, List<MultiYearFestivalRecord>> recordsByYear = new TreeMap<>();

        for (MultiYearCsvRawRow raw : rawRows) {
            MultiYearRowNormalizationResult result = normalizationService.normalize(raw);
            normalizationErrors.addAll(result.errors());
            warnings.addAll(result.warnings());
            if (!result.hasErrors()) {
                MultiYearFestivalRecord record = result.record();
                recordsByYear.computeIfAbsent(record.getDatasetYear(), y -> new ArrayList<>()).add(record);
            }
        }

        if (!normalizationErrors.isEmpty()) {
            throw new MultiYearCsvImportException(
                    "%d개 행에서 필수 항목을 인식하지 못했습니다".formatted(normalizationErrors.size()),
                    fileHash, originalFileName, normalizationErrors);
        }

        MultiYearImportSummary summary = buildSummary(recordsByYear, warnings);

        MultiYearImportBatch batchToSave = MultiYearImportBatch.builder()
                .originalFileName(originalFileName)
                .fileHash(fileHash)
                .datasetYears(formatYearRange(recordsByYear.keySet()))
                .totalRows(summary.totalRows())
                .unitScaleSuspectRows(summary.unitScaleSuspectRows())
                .missingOrNonpositiveBudgetRows(summary.missingOrNonpositiveBudgetRows())
                .missingDurationRows(summary.missingDurationRows())
                .covidAffectedRows(summary.covidAffectedRows())
                .importedAt(Instant.now())
                .status(ImportStatus.SUCCESS)
                .errorSummary(warnings.isEmpty() ? null : summarizeWarnings(warnings))
                .build();

        MultiYearImportBatch savedBatch = persistenceService.replaceAllYears(recordsByYear, batchToSave);
        log.info("다년도 CSV Import 완료: batchId={}, totalRows={}, years={}",
                savedBatch.getId(), summary.totalRows(), batchToSave.getDatasetYears());

        return MultiYearImportResult.success(savedBatch, summary);
    }

    private void recordFailure(String fileHash, String originalFileName, String message, List<String> details) {
        String errorSummary = details.isEmpty() ? message
                : message + " :: " + String.join(" | ", details.subList(0, Math.min(details.size(), 20)));
        MultiYearImportBatch failedBatch = MultiYearImportBatch.builder()
                .originalFileName(originalFileName)
                .fileHash(fileHash)
                .totalRows(0)
                .unitScaleSuspectRows(0)
                .missingOrNonpositiveBudgetRows(0)
                .missingDurationRows(0)
                .covidAffectedRows(0)
                .importedAt(Instant.now())
                .status(ImportStatus.FAILED)
                .errorSummary(errorSummary)
                .build();
        try {
            persistenceService.recordFailedAttempt(failedBatch);
        } catch (Exception persistFailure) {
            log.warn("실패 이력 기록 중 오류 발생: {}", persistFailure.getMessage());
        }
    }

    private MultiYearImportSummary buildSummary(Map<Integer, List<MultiYearFestivalRecord>> recordsByYear, List<MultiYearRowWarning> warnings) {
        Map<Integer, Integer> rowCountByYear = new TreeMap<>();
        int total = 0;
        int valid = 0;
        int unitScaleSuspect = 0;
        int missingOrNonpositive = 0;
        int missingDuration = 0;
        int covidAffected = 0;

        for (Map.Entry<Integer, List<MultiYearFestivalRecord>> entry : recordsByYear.entrySet()) {
            List<MultiYearFestivalRecord> yearRecords = entry.getValue();
            rowCountByYear.put(entry.getKey(), yearRecords.size());
            total += yearRecords.size();
            for (MultiYearFestivalRecord r : yearRecords) {
                if (r.getBudgetQualityFlag() == BudgetQualityFlag.VALID) {
                    valid++;
                } else if (r.getBudgetQualityFlag() == BudgetQualityFlag.UNIT_SCALE_SUSPECT) {
                    unitScaleSuspect++;
                } else if (r.getBudgetQualityFlag() == BudgetQualityFlag.MISSING_OR_NONPOSITIVE) {
                    missingOrNonpositive++;
                }
                if (r.getDurationDays() == null) {
                    missingDuration++;
                }
                if (r.isCovidAffected()) {
                    covidAffected++;
                }
            }
        }

        return new MultiYearImportSummary(total, rowCountByYear, valid, unitScaleSuspect, missingOrNonpositive, missingDuration, covidAffected, warnings);
    }

    private String formatYearRange(java.util.Set<Integer> years) {
        if (years.isEmpty()) {
            return null;
        }
        return "%d-%d".formatted(java.util.Collections.min(years), java.util.Collections.max(years));
    }

    private String summarizeWarnings(List<MultiYearRowWarning> warnings) {
        List<String> lines = warnings.stream()
                .limit(50)
                .map(w -> "%d년 연번%s: %s".formatted(w.datasetYear(), w.sourceRowNumber(), w.message()))
                .toList();
        String suffix = warnings.size() > 50 ? " ... 외 %d건".formatted(warnings.size() - 50) : "";
        return "경고 %d건 :: %s%s".formatted(warnings.size(), String.join(" | ", lines), suffix);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다", e);
        }
    }
}