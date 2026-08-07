package com.festival.budgetassist.admin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.festival.budgetassist.dataset.ImportSummary;
import com.festival.budgetassist.dataset.ReferenceProfileCheck;
import com.festival.budgetassist.festival.domain.BudgetStatus;
import com.festival.budgetassist.festival.domain.DatasetImportBatch;
import com.festival.budgetassist.festival.domain.FestivalRecord;
import com.festival.budgetassist.festival.domain.FestivalType;
import com.festival.budgetassist.festival.domain.ImportStatus;
import com.festival.budgetassist.festival.domain.ImportWarning;
import com.festival.budgetassist.festival.domain.Region;
import com.festival.budgetassist.festival.domain.VenueType;
import com.festival.budgetassist.festival.repository.DatasetImportBatchRepository;
import com.festival.budgetassist.festival.repository.FestivalRecordRepository;
import com.festival.budgetassist.festival.repository.ImportWarningRepository;

/**
 * /admin/datasets 화면이 사용하는 조회 전용 서비스. 배치 시점 스냅샷이 아니라 항상
 * 최신 성공 배치에 연결된 festival_record를 다시 집계한다(수천 건 규모라 메모리 집계로 충분).
 */
@Service
public class AdminDatasetQueryService {

    private static final int SAMPLE_ROW_LIMIT = 10;
    private static final int ISSUE_LIMIT = 200;

    private final DatasetImportBatchRepository datasetImportBatchRepository;
    private final FestivalRecordRepository festivalRecordRepository;
    private final ImportWarningRepository importWarningRepository;

    public AdminDatasetQueryService(DatasetImportBatchRepository datasetImportBatchRepository,
                                     FestivalRecordRepository festivalRecordRepository,
                                     ImportWarningRepository importWarningRepository) {
        this.datasetImportBatchRepository = datasetImportBatchRepository;
        this.festivalRecordRepository = festivalRecordRepository;
        this.importWarningRepository = importWarningRepository;
    }

    public AdminDatasetOverviewResponse getOverview() {
        Optional<DatasetImportBatch> latestAttempt = datasetImportBatchRepository.findFirstByOrderByImportedAtDesc();
        if (latestAttempt.isEmpty()) {
            return AdminDatasetOverviewResponse.empty();
        }
        Optional<DatasetImportBatch> latestSuccess = datasetImportBatchRepository
                .findFirstByStatusOrderByImportedAtDesc(ImportStatus.SUCCESS);
        boolean hasLiveData = latestSuccess.isPresent()
                && festivalRecordRepository.countByDatasetYear(latestSuccess.get().getDatasetYear()) > 0;

        return new AdminDatasetOverviewResponse(
                true,
                BatchInfo.from(latestAttempt.get()),
                hasLiveData,
                latestSuccess.map(BatchInfo::from).orElse(null)
        );
    }

    public AdminDatasetSummaryResponse getSummary() {
        Optional<DatasetImportBatch> batchOpt = latestSuccessBatch();
        if (batchOpt.isEmpty()) {
            return AdminDatasetSummaryResponse.unavailable();
        }
        DatasetImportBatch batch = batchOpt.get();
        List<FestivalRecord> records = festivalRecordRepository.findByImportBatchId(batch.getId());

        int total = records.size();
        int valid = countByStatus(records, BudgetStatus.CONFIRMED);
        int unconfirmed = countByStatus(records, BudgetStatus.UNCONFIRMED);
        int noResponse = countByStatus(records, BudgetStatus.NO_RESPONSE);
        int zero = countByStatus(records, BudgetStatus.ZERO);
        int missingDuration = (int) records.stream().filter(r -> r.getDurationDays() == null).count();
        int regionCount = (int) records.stream().map(FestivalRecord::getRegion).distinct().count();
        int festivalTypeCount = (int) records.stream().map(FestivalRecord::getFestivalType).distinct().count();
        int venueTypeCount = (int) records.stream().map(FestivalRecord::getVenueType).distinct().count();

        ImportSummary summaryForComparison = new ImportSummary(
                total, valid, unconfirmed, noResponse, zero, missingDuration,
                regionCount, festivalTypeCount, venueTypeCount, List.of());
        ReferenceProfileCheck referenceProfileCheck = ReferenceProfileCheck.compare(batch.getDatasetYear(), summaryForComparison);

        return new AdminDatasetSummaryResponse(
                true, BatchInfo.from(batch),
                total, valid, unconfirmed, noResponse, zero, missingDuration,
                regionCount, festivalTypeCount, venueTypeCount,
                referenceProfileCheck
        );
    }

    public AdminDatasetDistributionsResponse getDistributions() {
        Optional<DatasetImportBatch> batchOpt = latestSuccessBatch();
        if (batchOpt.isEmpty()) {
            return AdminDatasetDistributionsResponse.unavailable();
        }
        List<FestivalRecord> records = festivalRecordRepository.findByImportBatchId(batchOpt.get().getId());

        List<CategoryCount> regionCounts = countByRegion(records);
        List<CategoryCount> festivalTypeCounts = countByFestivalType(records);
        List<CategoryCount> venueTypeCounts = countByVenueType(records);
        BudgetStatistics budgetStatistics = computeBudgetStatistics(records);
        List<DurationBucket> durationBuckets = computeDurationBuckets(records);

        return new AdminDatasetDistributionsResponse(true, regionCounts, festivalTypeCounts, venueTypeCounts, budgetStatistics, durationBuckets);
    }

    public AdminDatasetIssuesResponse getIssues() {
        Optional<DatasetImportBatch> batchOpt = latestSuccessBatch();
        if (batchOpt.isEmpty()) {
            return AdminDatasetIssuesResponse.unavailable();
        }
        List<ImportWarning> warnings = importWarningRepository.findByBatchIdOrderBySourceRowNumberAsc(batchOpt.get().getId());
        List<IssueItem> items = warnings.stream()
                .limit(ISSUE_LIMIT)
                .map(w -> new IssueItem(w.getSourceRowNumber(), w.getMessage()))
                .toList();
        return new AdminDatasetIssuesResponse(true, warnings.size(), warnings.size() > ISSUE_LIMIT, items);
    }

    public AdminDatasetSampleResponse getSample() {
        Optional<DatasetImportBatch> batchOpt = latestSuccessBatch();
        if (batchOpt.isEmpty()) {
            return AdminDatasetSampleResponse.unavailable();
        }
        List<FestivalRecord> records = festivalRecordRepository.findByImportBatchId(batchOpt.get().getId());
        List<SampleRow> sampleRows = records.stream()
                .sorted(Comparator.comparing(FestivalRecord::getSourceRowNumber))
                .limit(SAMPLE_ROW_LIMIT)
                .map(this::toSampleRow)
                .toList();

        return new AdminDatasetSampleResponse(
                true, AdminColumnCatalog.LOADED_COLUMNS, AdminColumnCatalog.EXCLUDED_COLUMNS,
                AdminColumnCatalog.PERSONAL_INFO_STATUS_LABEL, sampleRows
        );
    }

    private Optional<DatasetImportBatch> latestSuccessBatch() {
        return datasetImportBatchRepository.findFirstByStatusOrderByImportedAtDesc(ImportStatus.SUCCESS);
    }

    private int countByStatus(List<FestivalRecord> records, BudgetStatus status) {
        return (int) records.stream().filter(r -> r.getBudgetStatus() == status).count();
    }

    private List<CategoryCount> countByRegion(List<FestivalRecord> records) {
        Map<Region, Long> counts = groupCount(records, FestivalRecord::getRegion);
        return counts.entrySet().stream()
                .map(e -> new CategoryCount(e.getKey().name(), e.getKey().getDisplayName(), e.getValue()))
                .sorted(Comparator.comparingLong(CategoryCount::count).reversed())
                .toList();
    }

    private List<CategoryCount> countByFestivalType(List<FestivalRecord> records) {
        Map<FestivalType, Long> counts = groupCount(records, FestivalRecord::getFestivalType);
        return counts.entrySet().stream()
                .map(e -> new CategoryCount(e.getKey().name(), e.getKey().getDisplayName(), e.getValue()))
                .sorted(Comparator.comparingLong(CategoryCount::count).reversed())
                .toList();
    }

    private List<CategoryCount> countByVenueType(List<FestivalRecord> records) {
        Map<VenueType, Long> counts = groupCount(records, FestivalRecord::getVenueType);
        return counts.entrySet().stream()
                .map(e -> new CategoryCount(e.getKey().name(), e.getKey().getDisplayName(), e.getValue()))
                .sorted(Comparator.comparingLong(CategoryCount::count).reversed())
                .toList();
    }

    private <K> Map<K, Long> groupCount(List<FestivalRecord> records, java.util.function.Function<FestivalRecord, K> classifier) {
        Map<K, Long> counts = new LinkedHashMap<>();
        for (FestivalRecord record : records) {
            counts.merge(classifier.apply(record), 1L, Long::sum);
        }
        return counts;
    }

    private BudgetStatistics computeBudgetStatistics(List<FestivalRecord> records) {
        List<Long> confirmedBudgets = records.stream()
                .filter(r -> r.getBudgetStatus() == BudgetStatus.CONFIRMED && r.getTotalBudgetKrw() != null)
                .map(FestivalRecord::getTotalBudgetKrw)
                .sorted()
                .toList();

        if (confirmedBudgets.isEmpty()) {
            return BudgetStatistics.empty();
        }

        double mean = confirmedBudgets.stream().mapToLong(Long::longValue).average().orElse(0);
        double median = Quantile.linear(confirmedBudgets, 0.5);
        double p25 = Quantile.linear(confirmedBudgets, 0.25);
        double p75 = Quantile.linear(confirmedBudgets, 0.75);
        double p90 = Quantile.linear(confirmedBudgets, 0.90);
        long max = confirmedBudgets.get(confirmedBudgets.size() - 1);

        return new BudgetStatistics(confirmedBudgets.size(), mean, median, p25, p75, p90, max);
    }

    private List<DurationBucket> computeDurationBuckets(List<FestivalRecord> records) {
        Map<String, Long> buckets = new LinkedHashMap<>();
        List<String> order = List.of("2일", "3일", "4~7일", "8~14일", "15~30일", "31일 이상", "미기재");
        order.forEach(label -> buckets.put(label, 0L));

        for (FestivalRecord record : records) {
            String label = bucketLabel(record.getDurationDays());
            buckets.merge(label, 1L, Long::sum);
        }

        List<DurationBucket> result = new ArrayList<>();
        order.forEach(label -> result.add(new DurationBucket(label, buckets.get(label))));
        return result;
    }

    private String bucketLabel(Integer durationDays) {
        if (durationDays == null) {
            return "미기재";
        }
        if (durationDays == 2) {
            return "2일";
        }
        if (durationDays == 3) {
            return "3일";
        }
        if (durationDays >= 4 && durationDays <= 7) {
            return "4~7일";
        }
        if (durationDays >= 8 && durationDays <= 14) {
            return "8~14일";
        }
        if (durationDays >= 15 && durationDays <= 30) {
            return "15~30일";
        }
        return "31일 이상";
    }

    private SampleRow toSampleRow(FestivalRecord r) {
        return new SampleRow(
                r.getSourceRowNumber(),
                r.getFestivalName(),
                r.getRegionName(),
                r.getAdministrativeDistrict(),
                r.getFestivalType().getDisplayName(),
                r.getVenueName(),
                r.getVenueType().getDisplayName(),
                r.getDurationDays(),
                r.getDurationSource() == null ? null : r.getDurationSource().name(),
                r.getCycleType().getDisplayName(),
                r.getTotalBudgetKrw(),
                r.getBudgetStatus().name()
        );
    }
}