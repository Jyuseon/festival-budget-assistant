package com.festival.budgetassist.multiyear.series;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.festival.budgetassist.multiyear.series.FestivalSeriesLinkingReport.AmbiguousSingleton;
import com.festival.budgetassist.multiyear.series.FestivalSeriesLinkingReport.CandidateSummary;
import com.festival.budgetassist.multiyear.series.FestivalSeriesLinkingReport.ChainComponentSummary;
import com.festival.budgetassist.multiyear.series.FestivalSeriesLinkingReport.ChainEdgeSummary;
import com.festival.budgetassist.multiyear.series.FestivalSeriesLinkingReport.ChainMember;
import com.festival.budgetassist.multiyear.series.FestivalSeriesLinkingReport.SeriesSummary;
import com.festival.budgetassist.multiyear.series.FestivalSeriesLinkingReport.YearEntry;

/**
 * {@link FestivalSeriesLinkingReport}를 사람이 읽을 텍스트 라인으로 변환한다. 로컬 CLI
 * 러너({@link FestivalSeriesLinkingRunner})와 실데이터 분석 테스트 양쪽이 이 포맷터를
 * 공유해서, "실제로 계산된 값"을 눈으로 검증할 수 있는 동일한 출력을 만든다.
 */
public final class FestivalSeriesLinkingReportFormatter {

    private FestivalSeriesLinkingReportFormatter() {
    }

    public static List<String> format(FestivalSeriesLinkingReport r) {
        List<String> lines = new ArrayList<>();

        lines.add("================ festivalSeries 연결 리포트 ================");
        lines.add("전체 festival-year rows: %d".formatted(r.totalRecords()));
        lines.add("distinct festivalSeries 수: %d".formatted(r.distinctSeriesCount()));
        lines.add("1년만 존재하는 series 수: %d".formatted(r.seriesWith1Year()));
        lines.add("2년 이상 존재하는 series 수: %d".formatted(r.seriesWith2PlusYears()));
        lines.add("5년 이상 존재하는 series 수: %d".formatted(r.seriesWith5PlusYears()));
        lines.add("8년 이상 존재하는 series 수: %d".formatted(r.seriesWith8PlusYears()));
        lines.add("최대 연속 관측 연도 수: %d".formatted(r.maxConsecutiveObservedYears()));

        lines.add("--- matchMethod별 건수/비율 (n=%d) ---".formatted(r.totalRecords()));
        r.matchMethodCounts().forEach((method, count) ->
                lines.add("  %s: %d건 (%.1f%%)".formatted(method, count, 100.0 * count / r.totalRecords())));

        lines.add("--- distinctYearCount 히스토그램 ---");
        r.seriesCountByDistinctYearCount().forEach((years, count) ->
                lines.add("  %d년 등장: series %d개".formatted(years, count)));

        lines.add("--- 다년도 중복 영향 분석 (동일 series 반복 등장이 표본에서 차지하는 비중) ---");
        r.duplicationImpact().seriesCountByMinYearsPresent().forEach((min, seriesCount) -> {
            double share = r.duplicationImpact().rowShareByMinYearsPresent().get(min);
            lines.add("  %d년 이상 등장한 series: %d개 -> 그 series들의 row가 전체의 %.1f%% 차지".formatted(min, seriesCount, share));
        });

        lines.add("--- recordCount 상위 %d series ---".formatted(r.top30ByRecordCount().size()));
        int rank = 1;
        for (SeriesSummary s : r.top30ByRecordCount()) {
            lines.add("  #%d [id=%d] %s | %s%s | scope=%s status=%s | %d~%d년(%d개년, recordCount=%d)".formatted(
                    rank++, s.id(), s.canonicalName(), s.canonicalRegion(),
                    s.canonicalDistrict() == null ? "" : " " + s.canonicalDistrict(),
                    s.scope(), s.matchStatus(), s.firstYear(), s.lastYear(), s.distinctYearCount(), s.recordCount()));
            for (YearEntry ye : s.years()) {
                lines.add("      %d년: %s".formatted(ye.year(), String.join(" / ", ye.originalFestivalNames())));
            }
        }

        lines.add("--- fuzzy 후보 전체 건수(밴드별, 표본이 아닌 실제 총합) ---".formatted());
        r.candidateCountsByBand().forEach((band, count) -> lines.add("  %s: %d건".formatted(band, count)));
        lines.add("  실제 자동 연결(applied)된 후보: %d건".formatted(r.appliedCandidateCount()));

        lines.add("--- fuzzy 후보 score 최고 %d건 ---".formatted(r.highestScoreCandidates().size()));
        r.highestScoreCandidates().forEach(c -> lines.add(formatCandidate(c)));

        lines.add("--- fuzzy 후보 threshold(HIGH=%.2f) 근접 %d건 ---".formatted(
                FestivalSeriesLinkingService.HIGH_THRESHOLD, r.thresholdNearCandidates().size()));
        r.thresholdNearCandidates().forEach(c -> lines.add(formatCandidate(c)));

        lines.add("--- fuzzy MEDIUM(검토 목록) %d건 ---".formatted(r.mediumReviewCandidates().size()));
        r.mediumReviewCandidates().forEach(c -> lines.add(formatCandidate(c)));

        lines.add("--- strict chain linking: cluster-level 최소 이름유사도 threshold 후보 비교 ---");
        lines.add("  (edge 자체는 항상 nameSim>=%.2f 요구 - 아래는 컴포넌트 전체 최소 pairwise 유사도 재검사 threshold만 비교)"
                .formatted(FestivalSeriesLinkingService.CHAIN_EDGE_MIN_NAME_SIMILARITY));
        r.chainClusterThresholdComparison().forEach((threshold, count) ->
                lines.add("  threshold=%s: %d개 컴포넌트가 통과(district/유형 충돌·연도중복 없음 기준) -> 실제 적용값은 %.2f(가장 보수적)".formatted(
                        threshold, count, FestivalSeriesLinkingService.CHAIN_CLUSTER_MIN_SIMILARITY)));

        long chainApplied = r.chainComponents().stream().filter(ChainComponentSummary::applied).count();
        long chainRejected = r.chainComponents().size() - chainApplied;
        lines.add("--- strict chain linking 결과: 평가한 컴포넌트 %d개 (적용 %d / 거부 %d) ---".formatted(
                r.chainComponents().size(), chainApplied, chainRejected));

        lines.add("--- chain으로 자동 병합된 컴포넌트 %d개 (전수) ---".formatted(chainApplied));
        r.chainComponents().stream().filter(ChainComponentSummary::applied).forEach(c -> formatChainComponent(lines, c));

        lines.add("--- chain 후보였으나 안전장치에 걸려 거부된 컴포넌트 %d개 (전수) ---".formatted(chainRejected));
        r.chainComponents().stream().filter(c -> !c.applied()).forEach(c -> formatChainComponent(lines, c));

        lines.add("--- (chain linking 이후) 여전히 애매해서 자동 연결을 보류한 사례: %d건 ---".formatted(r.ambiguousMultiHighSingletons().size()));
        for (AmbiguousSingleton a : r.ambiguousMultiHighSingletons()) {
            lines.add("  [%d] %s (%d년, %s%s)".formatted(a.sourceRecordId(), a.sourceFestivalName(), a.sourceYear(),
                    a.sourceRegion(), a.sourceDistrict() == null ? "" : " " + a.sourceDistrict()));
            a.conflictingHighCandidates().forEach(c -> lines.add("      후보: " + formatCandidate(c)));
        }

        lines.add("================ 리포트 종료 ================");
        return lines;
    }

    private static void formatChainComponent(List<String> lines, ChainComponentSummary c) {
        lines.add("  컴포넌트 #%d: %s | %s%s (%s) | min=%.3f mean=%.3f | typeConflict=%s districtConflict=%s duplicateYear=%s%s".formatted(
                c.componentId(), c.canonicalName(), c.region(), c.district() == null ? "" : " " + c.district(), c.scope(),
                c.minPairwiseSimilarity(), c.meanPairwiseSimilarity(),
                c.typeConflict(), c.districtConflict(), c.duplicateYear(),
                c.applied() ? "" : " | 거부 사유: " + c.rejectionReason()));
        for (ChainMember m : c.members()) {
            lines.add("      [%d] %d년 %s (normalized=%s, district=%s, type=%s, 대표행 유사도=%.3f)".formatted(
                    m.recordId(), m.year(), m.rawFestivalName(), m.normalizedName(),
                    m.districtRaw() == null ? "-" : m.districtRaw(), m.festivalType() == null ? "-" : m.festivalType(),
                    m.similarityToAnchor()));
        }
        for (ChainEdgeSummary e : c.edges()) {
            lines.add("      edge: [%d](%d년) <-> [%d](%d년) nameSim=%.3f score=%.3f".formatted(
                    e.recordIdA(), e.yearA(), e.recordIdB(), e.yearB(), e.nameSimilarity(), e.score()));
        }
    }

    private static String formatCandidate(CandidateSummary c) {
        return ("  [%d] %s(%d년,%s%s) <-> [%d] %s(%d년,%s%s) | score=%.3f(%s%s) "
                + "| nameSim=%.3f district=%+.2f year=%+.2f type=%+.2f").formatted(
                c.sourceRecordId(), c.sourceFestivalName(), c.sourceYear(), c.sourceRegion(),
                c.sourceDistrict() == null ? "" : " " + c.sourceDistrict(),
                c.candidateRecordId(), c.candidateFestivalName(), c.candidateYear(), c.candidateRegion(),
                c.candidateDistrict() == null ? "" : " " + c.candidateDistrict(),
                c.score(), c.band(), c.applied() ? ",applied" : "",
                c.nameSimilarity(), c.districtSignal(), c.yearAdjacencySignal(), c.typeSignal());
    }
}