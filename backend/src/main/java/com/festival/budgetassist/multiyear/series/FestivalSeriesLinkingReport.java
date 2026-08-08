package com.festival.budgetassist.multiyear.series;

import java.util.List;
import java.util.Map;

import com.festival.budgetassist.multiyear.domain.MatchConfidence;
import com.festival.budgetassist.multiyear.domain.MatchMethod;

/**
 * {@link FestivalSeriesLinkingService#linkAll()} 실행 결과 요약. 순수 데이터 DTO라 콘솔 출력
 * (러너)과 테스트 양쪽에서 재사용한다.
 */
public record FestivalSeriesLinkingReport(
        int totalRecords,
        int distinctSeriesCount,
        int seriesWith1Year,
        int seriesWith2PlusYears,
        int seriesWith5PlusYears,
        int seriesWith8PlusYears,
        int maxConsecutiveObservedYears,
        Map<MatchMethod, Long> matchMethodCounts,
        List<SeriesSummary> top30ByRecordCount,
        List<CandidateSummary> highestScoreCandidates,
        List<CandidateSummary> thresholdNearCandidates,
        List<CandidateSummary> mediumReviewCandidates,
        List<AmbiguousSingleton> ambiguousMultiHighSingletons,
        Map<Integer, Integer> seriesCountByDistinctYearCount,
        DuplicationImpact duplicationImpact,
        Map<MatchConfidence, Long> candidateCountsByBand,
        long appliedCandidateCount,
        List<ChainComponentSummary> chainComponents,
        Map<String, Integer> chainClusterThresholdComparison
) {

    public record YearEntry(int year, List<String> originalFestivalNames) {
    }

    public record SeriesSummary(
            long id,
            String canonicalName,
            String canonicalRegion,
            String canonicalDistrict,
            String scope,
            String matchStatus,
            int recordCount,
            int distinctYearCount,
            int firstYear,
            int lastYear,
            List<YearEntry> years
    ) {
    }

    public record CandidateSummary(
            long sourceRecordId, String sourceFestivalName, int sourceYear, String sourceRegion, String sourceDistrict,
            long candidateRecordId, String candidateFestivalName, int candidateYear, String candidateRegion, String candidateDistrict,
            double nameSimilarity, double districtSignal, double yearAdjacencySignal, double typeSignal,
            double score, MatchConfidence band, boolean applied
    ) {
    }

    /** 같은 singleton이 서로 다른 series를 가리키는 HIGH 후보를 2개 이상 받아 연결을 보류한 사례. */
    public record AmbiguousSingleton(
            long sourceRecordId, String sourceFestivalName, int sourceYear, String sourceRegion, String sourceDistrict,
            List<CandidateSummary> conflictingHighCandidates
    ) {
    }

    /**
     * @param seriesCountByMinYearsPresent   최소 N년 이상 등장한 series 수 (key=N, 7/8/9/10)
     * @param rowShareByMinYearsPresent      그 series들이 전체 10,198행 중 차지하는 비율(%, key=N)
     */
    public record DuplicationImpact(
            Map<Integer, Integer> seriesCountByMinYearsPresent,
            Map<Integer, Double> rowShareByMinYearsPresent
    ) {
    }

    /** strict chain linking이 평가한 연결요소(component) 1개 - 자동 병합됐든 거부됐든 전부 담는다. */
    public record ChainComponentSummary(
            int componentId,
            String canonicalName,
            String region,
            String district,
            String scope,
            List<ChainMember> members,
            List<ChainEdgeSummary> edges,
            double minPairwiseSimilarity,
            double meanPairwiseSimilarity,
            boolean typeConflict,
            boolean districtConflict,
            boolean duplicateYear,
            boolean applied,
            String rejectionReason
    ) {
    }

    public record ChainMember(
            long recordId, int year, String rawFestivalName, String normalizedName,
            String districtRaw, String festivalType, double similarityToAnchor
    ) {
    }

    public record ChainEdgeSummary(
            long recordIdA, int yearA, long recordIdB, int yearB, double nameSimilarity, double score
    ) {
    }
}