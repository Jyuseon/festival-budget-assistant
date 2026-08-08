package com.festival.budgetassist.multiyear.backtest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** S0/S1/S2 festivalSeries 중복 보정 비교 리포트 (지시사항 7~11절 순서). */
final class MultiYearSeriesCorrectionReportFormatter {

    private MultiYearSeriesCorrectionReportFormatter() {
    }

    static List<String> format(Map<MultiYearSeriesCorrectionMode, List<MultiYearFoldCorrectionResult>> byMode,
                                MultiYearSeriesCorrectionMetricsCalculator calculator) {
        List<String> lines = new ArrayList<>();
        lines.add("================ festivalSeries duplicate correction: S0 vs S1 vs S2 ================");
        lines.add("");
        lines.add("--- leakage-safe series 구성 ---");
        lines.add("* 각 fold마다 FestivalSeriesLinkingService.computeSeriesGroupsInMemory()를 그 fold의");
        lines.add("  training pool에만 적용해 series membership을 처음부터 다시 계산한다 - 전체");
        lines.add("  2017~2026 기준으로 만든 기존 FestivalSeriesMembership을 잘라 쓰지 않는다.");
        lines.add("* 규칙(결정적 클러스터링 + fuzzy HIGH + strict chain linking) 자체는 festivalSeries");
        lines.add("  v1과 완전히 같은 코드를 그대로 재사용한다 - 바뀐 게 없다.");
        lines.add("* candidate selection은 S0/S1/S2 사이에 항상 동일하다(같은 코드 경로 공유,");
        lines.add("  MultiYearBacktestService.selectFinalSample) - 재검증은 최종 weight에만 적용된다.");
        lines.add("* seriesFactor 계산에 쓰는 n(series 관측 수)은 해당 fold의 training 기간 안에서만");
        lines.add("  집계한다(평가 대상 연도/미래 연도는 절대 포함하지 않는다).");
        lines.add("");

        for (MultiYearSeriesCorrectionMode mode : MultiYearSeriesCorrectionMode.values()) {
            List<MultiYearFoldCorrectionResult> folds = byMode.get(mode);
            lines.add("================ [%s] ================".formatted(mode));
            for (MultiYearFoldCorrectionResult fold : folds) {
                lines.add("--- %s (training=%d건, 평가=%d건, 최종표본0건제외=%d건) ---".formatted(
                        fold.fold().label(), fold.trainingPoolSize(), fold.evalTargetCount(), fold.evalExcludedNoFinalSample()));
                formatSummary(lines, calculator.summarize(fold.predictions()));
            }

            List<MultiYearSeriesCorrectionPrediction> primaryAll = combine(byMode, mode, true);
            List<MultiYearSeriesCorrectionPrediction> secondaryAll = combine(byMode, mode, false);

            lines.add("--- [%s] Primary 종합(2025+2026) ---".formatted(mode));
            formatSummary(lines, calculator.summarize(primaryAll));
            lines.add("--- [%s] Primary 종합: 예산 규모별(count/MdAPE/MedianALE/medianSignedLogError/medianPredictedActualRatio) ---".formatted(mode));
            for (MultiYearSeriesCorrectionBudgetBucket b : calculator.budgetSizeBreakdown(primaryAll)) {
                lines.add("  %-12s count=%-5d MdAPE=%s MedianALE=%s signedLogErr=%s ratio=%s".formatted(
                        b.bucketLabel(), b.count(), pct(b.medianAbsolutePercentageError()), fmt(b.medianAbsoluteLogError()),
                        fmtSigned(b.medianSignedLogError()), fmt(b.medianPredictedActualRatio())));
            }
            lines.add("--- [%s] Primary 종합: target 과거 series 관측 수 구간별(9절, 평가 분석 전용) ---".formatted(mode));
            for (MultiYearSeriesLengthBucket b : calculator.seriesLengthBreakdown(primaryAll)) {
                lines.add("  %-14s count=%-5d MdAPE=%s MedianALE=%s".formatted(
                        b.bucketLabel(), b.count(), pct(b.medianAbsolutePercentageError()), fmt(b.medianAbsoluteLogError())));
            }
            lines.add("--- [%s] Primary 종합: 예산 규모별 P25~P75 coverage + range width(11절) ---".formatted(mode));
            for (MultiYearRangeCoverageBucket b : calculator.rangeCoverageBreakdown(primaryAll)) {
                lines.add("  %-12s count=%-5d coverage=%s medianRangeWidthRatio=%s".formatted(
                        b.bucketLabel(), b.count(), pct(b.typicalRangeCoverageRatio()), fmt(b.medianRangeWidthRatio())));
            }
            double spearmanPrimary = calculator.v3ErrorSpearmanCorrelation(primaryAll);
            lines.add("--- [%s] Primary 종합: v3 vs |log error| spearman = %s (n=%d) ---".formatted(mode, fmt(spearmanPrimary), primaryAll.size()));

            lines.add("--- [%s] Secondary 2024 종합 ---".formatted(mode));
            formatSummary(lines, calculator.summarize(secondaryAll));
            double spearmanSecondary = calculator.v3ErrorSpearmanCorrelation(secondaryAll);
            lines.add("  v3 vs |log error| spearman = %s (n=%d)".formatted(fmt(spearmanSecondary), secondaryAll.size()));
            lines.add("");
        }

        lines.add("================ S0 대비 S1/S2 prediction이 가장 크게 변한 target Top 20(Primary+Secondary) ================");
        List<MultiYearSeriesCorrectionPrediction> s0All = combine(byMode, MultiYearSeriesCorrectionMode.S0_BASELINE, null);
        List<MultiYearSeriesCorrectionPrediction> s1All = combine(byMode, MultiYearSeriesCorrectionMode.S1_SOFT_SQRT, null);
        List<MultiYearSeriesCorrectionPrediction> s2All = combine(byMode, MultiYearSeriesCorrectionMode.S2_FULL_INVERSE, null);
        for (MultiYearSeriesCorrectionMover m : calculator.topMovers(s0All, s1All, s2All, 20)) {
            lines.add(("  [%d] %s | %s%s | actual=%,d원 | S0=%,d원(APE=%s) S1=%,d원(APE=%s) S2=%,d원(APE=%s) | "
                    + "candidateCount=%d distinctSeries=%d | 최다반복series=\"%s\"(n=%d) | %s").formatted(
                    m.targetYear(), m.festivalName(), m.region(), m.district() == null ? "" : " " + m.district(),
                    m.actualBudget(), m.s0Estimated(), pct(m.s0AbsolutePercentageError()),
                    m.s1Estimated(), pct(m.s1AbsolutePercentageError()), m.s2Estimated(), pct(m.s2AbsolutePercentageError()),
                    m.candidateCount(), m.distinctSeriesCountInSample(), m.mostRepeatedSeriesLabel(),
                    m.mostRepeatedSeriesRecordCount(), m.verdict()));
        }

        lines.add("================ 리포트 종료 ================");
        return lines;
    }

    private static void formatSummary(List<String> lines, MultiYearSeriesCorrectionMetrics m) {
        MultiYearBacktestMetricsSummary s = m.base();
        lines.add("  evaluationCount=%d".formatted(s.evaluationCount()));
        if (s.evaluationCount() == 0) {
            lines.add("    (평가 가능한 건이 없음)");
            return;
        }
        lines.add("  MAE=%,.0f원 MedianAE=%,.0f원 MdAPE=%s P75APE=%s P90APE=%s MedianALE=%s".formatted(
                s.mae(), s.medianAbsoluteError(), pct(s.medianAbsolutePercentageError()),
                pct(s.p75AbsolutePercentageError()), pct(s.p90AbsolutePercentageError()), fmt(s.medianAbsoluteLogError())));
        lines.add("  ±25%%이내=%s ±50%%이내=%s 0.5x~2.0x이내=%s typicalRangeCoverage=%s".formatted(
                pct(s.within25PercentRatio()), pct(s.within50PercentRatio()), pct(s.within2xRatio()),
                pct(s.typicalRangeCoverageRatio())));
        lines.add("  medianSignedLogError=%s (양수=과대예측,음수=과소예측) medianPredictedActualRatio=%s medianRangeWidthRatio=%s".formatted(
                fmtSigned(m.medianSignedLogError()), fmt(m.medianPredictedActualRatio()), fmt(m.medianRangeWidthRatio())));
    }

    private static List<MultiYearSeriesCorrectionPrediction> combine(
            Map<MultiYearSeriesCorrectionMode, List<MultiYearFoldCorrectionResult>> byMode,
            MultiYearSeriesCorrectionMode mode, Boolean primaryOnly) {
        List<MultiYearSeriesCorrectionPrediction> combined = new ArrayList<>();
        for (MultiYearFoldCorrectionResult fold : byMode.get(mode)) {
            if (primaryOnly == null || fold.fold().primary() == primaryOnly) {
                combined.addAll(fold.predictions());
            }
        }
        return combined;
    }

    private static String fmt(double v) {
        return Double.isFinite(v) ? "%.3f".formatted(v) : "N/A";
    }

    private static String fmtSigned(double v) {
        return Double.isFinite(v) ? "%+.3f".formatted(v) : "N/A";
    }

    private static String pct(double v) {
        return Double.isFinite(v) ? "%.1f%%".formatted(v * 100) : "N/A";
    }
}