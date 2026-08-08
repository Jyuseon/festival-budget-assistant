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
import com.festival.budgetassist.multiyear.series.FestivalSeriesLinkingReport.ChainComponentSummary;
import com.festival.budgetassist.multiyear.series.FestivalSeriesLinkingReport.ChainEdgeSummary;
import com.festival.budgetassist.multiyear.series.FestivalSeriesLinkingReport.ChainMember;
import com.festival.budgetassist.multiyear.series.FestivalSeriesLinkingReport.DuplicationImpact;
import com.festival.budgetassist.multiyear.series.FestivalSeriesLinkingReport.SeriesSummary;
import com.festival.budgetassist.multiyear.series.FestivalSeriesLinkingReport.YearEntry;
import com.festival.budgetassist.multiyear.series.FestivalSeriesLinkingService.ChainComponentAudit;
import com.festival.budgetassist.multiyear.series.FestivalSeriesLinkingService.ChainEdge;

/** {@link FestivalSeriesLinkingService#linkAll()}의 영속화 결과를 {@link FestivalSeriesLinkingReport}로 집계한다. */
final class FestivalSeriesLinkingReportBuilder {

    private static final int TOP_N = 50;
    private static final int SAMPLE_N = 20;
    private static final int[] MIN_YEARS_PRESENT_BUCKETS = {7, 8, 9, 10};

    private FestivalSeriesLinkingReportBuilder() {
    }

    static FestivalSeriesLinkingReport build(List<MultiYearFestivalRecord> allRecords,
                                              List<FestivalSeries> allSeries,
                                              List<FestivalSeriesMembership> allMemberships,
                                              List<FestivalSeriesMatchCandidate> allCandidates,
                                              List<ChainComponentAudit> chainAudits,
                                              Map<String, Integer> chainThresholdComparison) {

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

        Set<Long> resolvedByChain = allMemberships.stream()
                .filter(m -> m.getMatchMethod() == MatchMethod.CHAIN_HIGH_CONFIDENCE)
                .map(m -> m.getFestivalRecord().getId())
                .collect(java.util.stream.Collectors.toSet());
        List<AmbiguousSingleton> ambiguous = buildAmbiguousSingletons(candidateSummaries, resolvedByChain);

        List<ChainComponentSummary> chainComponents = chainAudits.stream()
                .map(FestivalSeriesLinkingReportBuilder::toChainComponentSummary)
                .toList();

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
                appliedCount,
                chainComponents,
                chainThresholdComparison
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

    /**
     * applied된 후보가 하나도 없는데 HIGH band 후보가 2개 이상인 source들 - "여러 series 중
     * 하나를 고를 수 없어 보류"된 사례. strict chain linking으로 이후 실제 병합된 source는
     * 더 이상 애매한 게 아니므로(resolvedByChain) 제외한다.
     */
    private static List<AmbiguousSingleton> buildAmbiguousSingletons(List<CandidateSummary> candidateSummaries,
                                                                       Set<Long> resolvedByChain) {
        Map<Long, List<CandidateSummary>> bySource = new LinkedHashMap<>();
        for (CandidateSummary c : candidateSummaries) {
            bySource.computeIfAbsent(c.sourceRecordId(), k -> new ArrayList<>()).add(c);
        }

        List<AmbiguousSingleton> result = new ArrayList<>();
        for (Map.Entry<Long, List<CandidateSummary>> entry : bySource.entrySet()) {
            if (resolvedByChain.contains(entry.getKey())) {
                continue;
            }
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

    private static ChainComponentSummary toChainComponentSummary(ChainComponentAudit audit) {
        String anchorNormalized = FestivalNameNormalizer.normalize(audit.anchor().getFestivalName());
        String anchorFuzzyKey = FestivalNameNormalizer.fuzzyKey(anchorNormalized);

        List<ChainMember> members = audit.members().stream()
                .map(r -> {
                    String normalized = FestivalNameNormalizer.normalize(r.getFestivalName());
                    double simToAnchor = r.getId().equals(audit.anchor().getId())
                            ? 1.0
                            : LevenshteinSimilarity.ratio(anchorFuzzyKey, FestivalNameNormalizer.fuzzyKey(normalized));
                    return new ChainMember(r.getId(), r.getDatasetYear(), r.getFestivalName(), normalized,
                            r.getDistrictText() != null ? r.getDistrictText() : r.getDistrictRaw(), r.getFestivalType(), simToAnchor);
                })
                .sorted(Comparator.comparingInt(ChainMember::year))
                .toList();

        List<ChainEdgeSummary> edges = audit.edges().stream()
                .map(e -> new ChainEdgeSummary(e.recordIdA(), e.yearA(), e.recordIdB(), e.yearB(), e.nameSimilarity(), e.score()))
                .toList();

        MultiYearFestivalRecord anyMember = audit.members().get(0);
        String region = resolveRegionForReport(anyMember);
        String district = resolveDistrictForReport(anyMember);

        return new ChainComponentSummary(
                audit.componentId(), anchorNormalized, region, district,
                anyMember.getDistrictText() == null && (anyMember.getDistrictRaw() == null) ? "REGION_LEVEL" : "DISTRICT_LEVEL",
                members, edges,
                audit.check().minPairwiseSimilarity(), audit.check().meanPairwiseSimilarity(),
                audit.check().typeConflict(), audit.check().districtConflict(), audit.check().duplicateYear(),
                audit.applied(), audit.rejectionReason()
        );
    }

    private static String resolveRegionForReport(MultiYearFestivalRecord r) {
        if (r.getRegionCode() != null) {
            return r.getRegionCode().getDisplayName();
        }
        return r.getRegionText() != null ? r.getRegionText() : r.getRegionRaw();
    }

    private static String resolveDistrictForReport(MultiYearFestivalRecord r) {
        return r.getDistrictText() != null ? r.getDistrictText() : r.getDistrictRaw();
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