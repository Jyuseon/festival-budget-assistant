package com.festival.budgetassist.admin.multiyear;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.festival.budgetassist.admin.CategoryCount;
import com.festival.budgetassist.multiyear.domain.BudgetQualityFlag;
import com.festival.budgetassist.multiyear.domain.FestivalSeries;
import com.festival.budgetassist.multiyear.domain.FestivalSeriesMatchStatus;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;
import com.festival.budgetassist.multiyear.repository.FestivalSeriesRepository;
import com.festival.budgetassist.multiyear.repository.MultiYearFestivalRecordRepository;

/**
 * /admin/multiyear-datasets 화면이 사용하는 조회 전용 서비스. 쓰기 메서드가 없다 - 데이터
 * 검증만을 위한 화면이며, 실제 Import는 여전히 {@code MultiYearCsvImportRunner} CLI로만
 * 수행한다. 기존 2026 전용 {@code AdminDatasetQueryService}/production
 * {@code BudgetEstimatorService}는 전혀 참조하지 않는다 - 완전히 분리된 조회 경로다.
 *
 * <p>매 요청마다 {@code multi_year_festival_record}를 다시 집계한다(1만여 건 규모라 메모리
 * 집계로 충분 - 기존 2026 화면과 같은 방식).</p>
 */
@Service
public class MultiYearAdminDatasetQueryService {

    static final int MIN_YEAR = 2017;
    static final int MAX_YEAR = 2026;
    private static final int DEFAULT_SAMPLE_LIMIT = 20;
    private static final int MAX_SAMPLE_LIMIT = 100;

    private final MultiYearFestivalRecordRepository recordRepository;
    private final FestivalSeriesRepository seriesRepository;

    public MultiYearAdminDatasetQueryService(MultiYearFestivalRecordRepository recordRepository,
                                              FestivalSeriesRepository seriesRepository) {
        this.recordRepository = recordRepository;
        this.seriesRepository = seriesRepository;
    }

    private static List<Integer> allYears() {
        List<Integer> years = new ArrayList<>();
        for (int y = MIN_YEAR; y <= MAX_YEAR; y++) {
            years.add(y);
        }
        return years;
    }

    public MultiYearAdminSummaryResponse getSummary() {
        List<MultiYearFestivalRecord> all = recordRepository.findAll();
        if (all.isEmpty()) {
            // 원본 레코드가 없어도 festivalSeries 테이블은 독립적으로 채워져 있을 수 있으므로
            // (예: 이전에 적재했던 데이터로 이미 linking을 돌려둔 경우) seriesStatus는 별도로 확인한다.
            return new MultiYearAdminSummaryResponse(false, 0,
                    allYears().stream().map(MultiYearYearSummary::empty).toList(), getSeriesStatus());
        }

        Map<Integer, List<MultiYearFestivalRecord>> byYear = groupByYear(all);
        List<MultiYearYearSummary> years = allYears().stream()
                .map(y -> summarizeYear(y, byYear.getOrDefault(y, List.of())))
                .toList();

        return new MultiYearAdminSummaryResponse(true, all.size(), years, getSeriesStatus());
    }

    public MultiYearAdminYearDetailResponse getYearDetail(int year) {
        List<MultiYearFestivalRecord> records = recordRepository.findByDatasetYear(year);
        if (records.isEmpty()) {
            return MultiYearAdminYearDetailResponse.unavailable(year);
        }

        int total = records.size();
        int valid = countByFlag(records, BudgetQualityFlag.VALID);
        int unitSuspect = countByFlag(records, BudgetQualityFlag.UNIT_SCALE_SUSPECT);
        int missingOrNonPositive = countByFlag(records, BudgetQualityFlag.MISSING_OR_NONPOSITIVE);
        int durationAvailable = (int) records.stream().filter(r -> r.getDurationDays() != null).count();
        int venueTypeAvailable = (int) records.stream().filter(r -> r.getVenueType() != null).count();
        boolean covidYear = records.stream().anyMatch(MultiYearFestivalRecord::isCovidAffected);

        MultiYearQualityCard qualityCard = new MultiYearQualityCard(
                total, valid, unitSuspect, missingOrNonPositive,
                ratePercent(durationAvailable, total), ratePercent(venueTypeAvailable, total)
        );

        return new MultiYearAdminYearDetailResponse(year, true, qualityCard, computeBudgetStatistics(records), covidYear);
    }

    public MultiYearAdminDistributionsResponse getDistributions(int year) {
        List<MultiYearFestivalRecord> records = recordRepository.findByDatasetYear(year);
        if (records.isEmpty()) {
            return MultiYearAdminDistributionsResponse.unavailable(year);
        }

        List<CategoryCount> regionCounts = countBy(records, MultiYearAdminDatasetQueryService::resolveRegionLabel);
        List<CategoryCount> festivalTypeCounts = countBy(records, r -> r.getFestivalType() == null ? "미분류" : r.getFestivalType());
        boolean venueTypeDataAvailable = records.stream().anyMatch(r -> r.getVenueType() != null);
        List<CategoryCount> venueTypeCounts = venueTypeDataAvailable
                ? countBy(records.stream().filter(r -> r.getVenueType() != null).toList(), r -> r.getVenueType().getDisplayName())
                : List.of();
        List<CategoryCount> budgetQualityFlagCounts = countBy(records, r -> r.getBudgetQualityFlag().name());
        boolean covidYear = records.stream().anyMatch(MultiYearFestivalRecord::isCovidAffected);

        return new MultiYearAdminDistributionsResponse(
                year, true, regionCounts, festivalTypeCounts, venueTypeDataAvailable, venueTypeCounts,
                budgetQualityFlagCounts, covidYear
        );
    }

    public MultiYearAdminSampleResponse getSample(int year, Integer requestedLimit, Integer requestedOffset) {
        int limit = clampLimit(requestedLimit);
        int offset = requestedOffset == null || requestedOffset < 0 ? 0 : requestedOffset;

        List<MultiYearFestivalRecord> records = recordRepository.findByDatasetYear(year);
        if (records.isEmpty()) {
            return MultiYearAdminSampleResponse.unavailable(year, limit, offset);
        }

        List<MultiYearFestivalRecord> sorted = records.stream()
                .sorted(Comparator.comparing(r -> r.getSourceRowNumber() == null ? Integer.MAX_VALUE : r.getSourceRowNumber()))
                .toList();

        List<MultiYearSampleRow> page = sorted.stream()
                .skip(offset)
                .limit(limit)
                .map(this::toSampleRow)
                .toList();

        return new MultiYearAdminSampleResponse(year, true, records.size(), limit, offset, page);
    }

    private int clampLimit(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_SAMPLE_LIMIT;
        }
        return Math.min(requested, MAX_SAMPLE_LIMIT);
    }

    // ------------------------------------------------------------------

    private MultiYearSeriesStatus getSeriesStatus() {
        List<FestivalSeries> allSeries = seriesRepository.findAll();
        if (allSeries.isEmpty()) {
            return MultiYearSeriesStatus.notAnalyzed();
        }
        int singleton = (int) allSeries.stream().filter(s -> s.getMatchStatus() == FestivalSeriesMatchStatus.SINGLETON).count();
        int multiYear = allSeries.size() - singleton;
        return new MultiYearSeriesStatus(true, allSeries.size(), singleton, multiYear);
    }

    private Map<Integer, List<MultiYearFestivalRecord>> groupByYear(List<MultiYearFestivalRecord> all) {
        Map<Integer, List<MultiYearFestivalRecord>> byYear = new LinkedHashMap<>();
        for (MultiYearFestivalRecord r : all) {
            byYear.computeIfAbsent(r.getDatasetYear(), k -> new ArrayList<>()).add(r);
        }
        return byYear;
    }

    private MultiYearYearSummary summarizeYear(int year, List<MultiYearFestivalRecord> records) {
        if (records.isEmpty()) {
            return MultiYearYearSummary.empty(year);
        }
        int total = records.size();
        int positive = (int) records.stream()
                .filter(r -> r.getBudgetTotalMillion() != null && r.getBudgetTotalMillion().compareTo(BigDecimal.ZERO) > 0)
                .count();
        int valid = countByFlag(records, BudgetQualityFlag.VALID);
        int unitSuspect = countByFlag(records, BudgetQualityFlag.UNIT_SCALE_SUSPECT);
        int missingOrNonPositive = countByFlag(records, BudgetQualityFlag.MISSING_OR_NONPOSITIVE);
        int durationAvailable = (int) records.stream().filter(r -> r.getDurationDays() != null).count();
        int venueTypeAvailable = (int) records.stream().filter(r -> r.getVenueType() != null).count();
        int covidAffected = (int) records.stream().filter(MultiYearFestivalRecord::isCovidAffected).count();

        List<Double> validBudgets = validBudgetsMillion(records);
        double median = validBudgets.isEmpty() ? 0.0 : MultiYearQuantile.linear(validBudgets, 0.5);

        return new MultiYearYearSummary(
                year, total, positive, valid, unitSuspect, missingOrNonPositive,
                durationAvailable, ratePercent(durationAvailable, total),
                venueTypeAvailable, ratePercent(venueTypeAvailable, total),
                covidAffected, median
        );
    }

    private MultiYearBudgetStatistics computeBudgetStatistics(List<MultiYearFestivalRecord> records) {
        List<Double> valid = validBudgetsMillion(records);
        if (valid.isEmpty()) {
            return MultiYearBudgetStatistics.empty();
        }
        double mean = valid.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double p25 = MultiYearQuantile.linear(valid, 0.25);
        double median = MultiYearQuantile.linear(valid, 0.5);
        double p75 = MultiYearQuantile.linear(valid, 0.75);
        double p90 = MultiYearQuantile.linear(valid, 0.90);
        double p95 = MultiYearQuantile.linear(valid, 0.95);
        double max = valid.get(valid.size() - 1);

        return new MultiYearBudgetStatistics(valid.size(), mean, p25, median, p75, p90, p95, max);
    }

    /** budgetQualityFlag=VALID 표본만, 오름차순 정렬된 백만원 단위 리스트. UNIT_SCALE_SUSPECT/MISSING_OR_NONPOSITIVE는 제외. */
    private List<Double> validBudgetsMillion(List<MultiYearFestivalRecord> records) {
        return records.stream()
                .filter(r -> r.getBudgetQualityFlag() == BudgetQualityFlag.VALID && r.getBudgetTotalMillion() != null)
                .map(r -> r.getBudgetTotalMillion().doubleValue())
                .sorted()
                .toList();
    }

    private int countByFlag(List<MultiYearFestivalRecord> records, BudgetQualityFlag flag) {
        return (int) records.stream().filter(r -> r.getBudgetQualityFlag() == flag).count();
    }

    private double ratePercent(int part, int total) {
        return total == 0 ? 0.0 : 100.0 * part / total;
    }

    private List<CategoryCount> countBy(List<MultiYearFestivalRecord> records, java.util.function.Function<MultiYearFestivalRecord, String> classifier) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (MultiYearFestivalRecord r : records) {
            counts.merge(classifier.apply(r), 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .map(e -> new CategoryCount(e.getKey(), e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(CategoryCount::count).reversed())
                .toList();
    }

    private static String resolveRegionLabel(MultiYearFestivalRecord r) {
        if (r.getRegionCode() != null) {
            return r.getRegionCode().getDisplayName();
        }
        if (r.getRegionText() != null && !r.getRegionText().isBlank()) {
            return r.getRegionText().trim();
        }
        return r.getRegionRaw() == null ? "미상" : r.getRegionRaw().trim();
    }

    private MultiYearSampleRow toSampleRow(MultiYearFestivalRecord r) {
        return new MultiYearSampleRow(
                r.getDatasetYear(),
                resolveRegionLabel(r),
                r.getDistrictText(),
                r.getFestivalName(),
                r.getFestivalTypeRaw(),
                r.getFestivalType(),
                r.getVenueNameRaw(),
                r.getVenueType() == null ? null : r.getVenueType().getDisplayName(),
                r.getDurationDays(),
                r.getBudgetTotalMillion() == null ? null : r.getBudgetTotalMillion().doubleValue(),
                r.getBudgetQualityFlag().name()
        );
    }
}