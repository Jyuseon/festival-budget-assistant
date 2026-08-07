package com.festival.budgetassist.multiyear.series;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.festival.budgetassist.multiyear.domain.FestivalSeries;
import com.festival.budgetassist.multiyear.domain.FestivalSeriesMatchCandidate;
import com.festival.budgetassist.multiyear.domain.FestivalSeriesMembership;
import com.festival.budgetassist.multiyear.domain.MatchConfidence;
import com.festival.budgetassist.multiyear.domain.MatchMethod;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;
import com.festival.budgetassist.multiyear.series.FestivalSeriesLinkingReport.AmbiguousSingleton;
import com.festival.budgetassist.multiyear.series.FestivalSeriesLinkingReport.CandidateSummary;
import com.festival.budgetassist.multiyear.series.FestivalSeriesLinkingReport.DuplicationImpact;
import com.festival.budgetassist.multiyear.series.FestivalSeriesLinkingReport.SeriesSummary;
import com.festival.budgetassist.multiyear.series.FestivalSeriesLinkingReport.YearEntry;

/** {@link FestivalSeriesLinkingService#linkAll()}의 영속화 결과를 {@link FestivalSeriesLinkingReport}로 집계한다. */
final class FestivalSeriesLinkingReportBuilder {

    private static final int TOP_N = 30;
    private static final int SAMPLE_N = 20;
    private static final int[] MIN_YEARS_PRESENT_BUCKETS = {7, 8, 9, 10};

    private FestivalSeriesLinkingReportBuilder() {
    }

    static FestivalSeriesLinkingReport build(List<MultiYearFestivalRecord> allRecords,
                                              List<FestivalSeries> allSeries,
                                              List<FestivalSeriesMembership> allMemberships,
                                              List<FestivalSeriesMatchCandidate> allCandidates) {

        Map<Long, List<FestivalSeriesMembership>> membershipsBySeries = new LinkedHashMap<>();
        for (FestivalSeriesMembership m : allMemberships) {
            membershipsBySeries.computeIfAbsent(m.getFestivalSeries().getId(), k -> new ArrayList<>()).add(m);
        }

        List<SeriesSummary> summaries = allSeries.stream()
                .map(s -> toSummary(s, membershipsBySeries.getOrDefault(s.getId(), List.of())))
                .toList();

        int seriesWith1Year = (int) summaries.stream().filter(s -> s.distinctYearCount() == 1).count();
        int seriesWith2Plus = (int) summaries.stream().filter(s -> s.distinctYearCount() >= 2).count();
        int seriesWith5Plus = (int) summaries.stream().filter(s -> s.distinctYearCount() >= 5).count();
        int seriesWith8Plus = (int) summaries.stream().filter(s -> s.distinctYearCount() >= 8).count();
        int maxConsecutive = summaries.stream().mapToInt(s -> longestConsecutiveRun(s.years())).max().orElse(0);

        Map<MatchMethod, Long> methodCounts = new LinkedHashMap<>();
        for (MatchMethod m : MatchMethod.values()) {
            methodCounts.put(m, 0L);
        }
        for (FestivalSeriesMembership m : allMemberships) {
            methodCounts.merge(m.getMatchMethod(), 1L, Long::sum);
        }

        List<SeriesSummary> top30 = summaries.stream()
                .sorted(Comparator.comparingInt(SeriesSummary::recordCount).reversed()
                        .thenComparing(SeriesSummary::canonicalName))
                .limit(TOP_N)
                .toList();

        List<CandidateSummary> candidateSummaries = allCandidates.stream()
                .map(FestivalSeriesLinkingReportBuilder::toCandidateSummary)
                .toList();

        List<CandidateSummary> highest = candidateSummaries.stream()
                .sorted(Comparator.comparingDouble(CandidateSummary::score).reversed())
                .limit(SAMPLE_N)
                .toList();

        List<CandidateSummary> thresholdNear = candidateSummaries.stream()
                .sorted(Comparator.comparingDouble(c -> Math.abs(c.score() - FestivalSeriesLinkingService.HIGH_THRESHOLD)))
                .limit(SAMPLE_N)
                .toList();

        List<CandidateSummary> mediumReview = candidateSummaries.stream()
                .filter(c -> c.band() == MatchConfidence.MEDIUM)
                .sorted(Comparator.comparingDouble(CandidateSummary::score).reversed())
                .limit(SAMPLE_N)
                .toList();

        List<AmbiguousSingleton> ambiguous = buildAmbiguousSingletons(allCandidates, candidateSummaries);

        Map<Integer, Integer> yearCountHistogram = new TreeMap<>();
        for (SeriesSummary s : summaries) {
            yearCountHistogram.merge(s.distinctYearCount(), 1, Integer::sum);
        }

        DuplicationImpact duplicationImpact = buildDuplicationImpact(summaries, allRecords.size());

        Map<com.festival.budgetassist.multiyear.domain.MatchConfidence, Long> candidateCountsByBand = new LinkedHashMap<>();
        for (var band : com.festival.budgetassist.multiyear.domain.MatchConfidence.values()) {
            candidateCountsByBand.put(band, 0L);
        }
        for (FestivalSeriesMatchCandidate c : allCandidates) {
            candidateCountsByBand.merge(c.getConfidenceBand(), 1L, Long::sum);
        }
        long appliedCount = allCandidates.stream().filter(FestivalSeriesMatchCandidate::isApplied).count();

        return new FestivalSeriesLinkingReport(
                allRecords.size(),
                allSeries.size(),
                seriesWith1Year, seriesWith2Plus, seriesWith5Plus, seriesWith8Plus,
                maxConsecutive,
                methodCounts,
                top30,
                highest, thresholdNear, mediumReview, ambiguous,
                yearCountHistogram,
                duplicationImpact,
                candidateCountsByBand,
                appliedCount
        );
    }

    private static SeriesSummary toSummary(FestivalSeries series, List<FestivalSeriesMembership> memberships) {
        Map<Integer, List<String>> namesByYear = new TreeMap<>();
        for (FestivalSeriesMembership m : memberships) {
            MultiYearFestivalRecord r = m.getFestivalRecord();
            namesByYear.computeIfAbsent(r.getDatasetYear(), k -> new ArrayList<>()).add(r.getFestivalName());
        }
        List<YearEntry> years = namesByYear.entrySet().stream()
                .map(e -> new YearEntry(e.getKey(), e.getValue()))
                .toList();

        return new SeriesSummary(
                series.getId(), series.getCanonicalName(), series.getCanonicalRegion(), series.getCanonicalDistrict(),
                series.getScope().name(), series.getMatchStatus().name(),
                series.getRecordCount(), years.size(), series.getFirstObservedYear(), series.getLastObservedYear(),
                years
        );
    }

    private static int longestConsecutiveRun(List<YearEntry> years) {
        if (years.isEmpty()) {
            return 0;
        }
        Set<Integer> yearSet = new TreeSet<>();
        years.forEach(y -> yearSet.add(y.year()));
        int longest = 1;
        int current = 1;
        Integer prev = null;
        for (int year : yearSet) {
            if (prev != null && year == prev + 1) {
                current++;
            } else {
                current = 1;
            }
            longest = Math.max(longest, current);
            prev = year;
        }
        return longest;
    }

    private static CandidateSummary toCandidateSummary(FestivalSeriesMatchCandidate c) {
        MultiYearFestivalRecord src = c.getSourceRecord();
        MultiYearFestivalRecord cand = c.getCandidateRecord();
        return new CandidateSummary(
                src.getId(), src.getFestivalName(), src.getDatasetYear(), src.getRegionText(), src.getDistrictText(),
                cand.getId(), cand.getFestivalName(), cand.getDatasetYear(), cand.getRegionText(), cand.getDistrictText(),
                c.getNameSimilarity(), c.getDistrictSignal(), c.getYearAdjacencySignal(), c.getTypeSignal(),
                c.getScore(), c.getConfidenceBand(), c.isApplied()
        );
    }

    /** applied된 후보가 하나도 없는데 HIGH band 후보가 2개 이상인 source들 - "여러 series 중 하나를 고를 수 없어 보류"된 사례. */
    private static List<AmbiguousSingleton> buildAmbiguousSingletons(List<FestivalSeriesMatchCandidate> allCandidates,
                                                                       List<CandidateSummary> candidateSummaries) {
        Map<Long, List<CandidateSummary>> bySource = new LinkedHashMap<>();
        for (CandidateSummary c : candidateSummaries) {
            bySource.computeIfAbsent(c.sourceRecordId(), k -> new ArrayList<>()).add(c);
        }

        List<AmbiguousSingleton> result = new ArrayList<>();
        for (Map.Entry<Long, List<CandidateSummary>> entry : bySource.entrySet()) {
            List<CandidateSummary> highOnes = entry.getValue().stream()
                    .filter(c -> c.band() == MatchConfidence.HIGH)
                    .toList();
            boolean anyApplied = entry.getValue().stream().anyMatch(CandidateSummary::applied);
            if (!anyApplied && highOnes.size() >= 2) {
                CandidateSummary first = highOnes.get(0);
                result.add(new AmbiguousSingleton(
                        first.sourceRecordId(), first.sourceFestivalName(), first.sourceYear(),
                        first.sourceRegion(), first.sourceDistrict(), highOnes));
            }
        }
        return result;
    }

    private static DuplicationImpact buildDuplicationImpact(List<SeriesSummary> summaries, int totalRecords) {
        Map<Integer, Integer> seriesCountByMin = new LinkedHashMap<>();
        Map<Integer, Double> rowShareByMin = new LinkedHashMap<>();
        for (int min : MIN_YEARS_PRESENT_BUCKETS) {
            List<SeriesSummary> qualifying = summaries.stream().filter(s -> s.distinctYearCount() >= min).toList();
            int seriesCount = qualifying.size();
            int rowCount = qualifying.stream().mapToInt(SeriesSummary::recordCount).sum();
            seriesCountByMin.put(min, seriesCount);
            rowShareByMin.put(min, totalRecords == 0 ? 0.0 : 100.0 * rowCount / totalRecords);
        }
        return new DuplicationImpact(seriesCountByMin, rowShareByMin);
    }
}