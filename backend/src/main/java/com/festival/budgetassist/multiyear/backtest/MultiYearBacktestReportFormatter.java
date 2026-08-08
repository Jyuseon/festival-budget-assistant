package com.festival.budgetassist.multiyear.backtest;

import java.util.ArrayList;
import java.util.List;

/** {@link MultiYearBacktestService} 결과를 사람이 읽을 텍스트 리포트로 만든다 (지시사항 14절 순서). */
final class MultiYearBacktestReportFormatter {

    private MultiYearBacktestReportFormatter() {
    }

    static List<String> format(List<MultiYearFoldResult> foldResults, MultiYearBacktestMetricsCalculator metrics,
                                MultiYearDataQualityV3Calculator v3Calculator) {
        List<String> lines = new ArrayList<>();

        lines.add("================ Leakage-safe Multi-Year Baseline Backtest ================");
        lines.add("");
        lines.add("--- leakage 방지 방식 ---");
        lines.add("* 각 fold는 datasetYear < targetYear인 record만 training pool로, datasetYear == targetYear인");
        lines.add("  record만 평가 대상으로 삼는다(MultiYearBacktestDatasetBuilder, 단일 패스 필터).");
        lines.add("* winsorize 기준값(같은 festivalType 모집단 log-budget P5/P95)도 training pool에서만 계산한다.");
        lines.add("* festivalSeries distinctSeriesCount 진단 컬럼도 매 예측마다 그 예측의 최종 후보 표본(전부");
        lines.add("  training pool에서만 나온 record)만 입력으로 받는 순수 함수로 계산한다 - DB나 미래 연도를");
        lines.add("  다시 조회하지 않는다.");
        lines.add("* MultiYearBacktestLeakageTest가 이 보장을 테스트로 고정한다: 미래 연도 record를 DB에");
        lines.add("  추가/제거해도 과거 fold의 training pool 크기·distinctSeriesCount·예측값이 전혀 바뀌지");
        lines.add("  않음을 검증한다.");
        lines.add("");

        for (MultiYearFoldResult fold : foldResults) {
            formatFold(lines, fold, metrics);
        }

        List<MultiYearBacktestPrediction> primaryAll = combinePredictions(foldResults, true);
        List<MultiYearBacktestPrediction> secondaryAll = combinePredictions(foldResults, false);

        lines.add("================ Primary 종합(2025+2026 합산) ================");
        formatSummary(lines, "Primary 종합", metrics.summarize(primaryAll));
        lines.add("");
        lines.add("--- Primary 종합: 예산 규모별 성능 ---");
        formatBudgetSizeBreakdown(lines, metrics.budgetSizeBreakdown(primaryAll));
        lines.add("--- Primary 종합: festivalType별 성능 ---");
        formatTypeBreakdown(lines, metrics.typeBreakdown(primaryAll));
        lines.add("--- Primary 종합: region별 성능 ---");
        formatRegionBreakdown(lines, metrics.regionBreakdown(primaryAll));
        lines.add("--- Primary 종합: v3 data-quality score 구간별 MdAPE ---");
        formatV3Breakdown(lines, metrics.v3Breakdown(primaryAll));
        double spearmanPrimary = metrics.v3ErrorSpearmanCorrelation(primaryAll, v3Calculator);
        lines.add("--- Primary 종합: v3 score vs 절대 log 예측오차 Spearman 상관계수 ---");
        lines.add("  spearman = %s (음수면 v3가 높을수록 error가 낮은 바람직한 방향, n=%d)".formatted(
                fmt(spearmanPrimary), primaryAll.size()));
        lines.add("");

        lines.add("================ Secondary(2024) - Primary와 절대 섞지 않음 ================");
        formatSummary(lines, "Secondary 2024", metrics.summarize(secondaryAll));
        lines.add("--- Secondary 2024: 예산 규모별 성능 ---");
        formatBudgetSizeBreakdown(lines, metrics.budgetSizeBreakdown(secondaryAll));
        lines.add("--- Secondary 2024: festivalType별 성능 ---");
        formatTypeBreakdown(lines, metrics.typeBreakdown(secondaryAll));
        lines.add("--- Secondary 2024: region별 성능 ---");
        formatRegionBreakdown(lines, metrics.regionBreakdown(secondaryAll));
        double spearmanSecondary = metrics.v3ErrorSpearmanCorrelation(secondaryAll, v3Calculator);
        lines.add("--- Secondary 2024: v3 score vs 절대 log 예측오차 Spearman 상관계수 ---");
        lines.add("  spearman = %s (n=%d)".formatted(fmt(spearmanSecondary), secondaryAll.size()));
        lines.add("");

        lines.add("================ 가장 큰 실패 사례 Top 20 (Primary+Secondary 전체, |log error| 기준) ================");
        List<MultiYearBacktestPrediction> everything = new ArrayList<>(primaryAll);
        everything.addAll(secondaryAll);
        for (MultiYearBacktestPrediction p : metrics.topFailures(everything, 20)) {
            lines.add(formatPredictionLine(p));
        }

        lines.add("================ 리포트 종료 ================");
        return lines;
    }

    private static void formatFold(List<String> lines, MultiYearFoldResult fold, MultiYearBacktestMetricsCalculator metrics) {
        lines.add("================ %s ================".formatted(fold.fold().label()));
        lines.add("training pool: %d건 (제외: 데이터품질 %d건, feature결측 %d건)".formatted(
                fold.trainingPoolSize(), fold.trainingExcludedLowQuality(), fold.trainingExcludedMissingFeature()));
        lines.add("평가 대상: %d건 (제외: 데이터품질 %d건, feature결측 %d건, 최종표본0건 %d건) -> 실제 평가 %d건".formatted(
                fold.evalTargetCount(), fold.evalExcludedLowQuality(), fold.evalExcludedMissingFeature(),
                fold.evalExcludedNoFinalSample(), fold.predictions().size()));
        formatSummary(lines, fold.fold().label(), metrics.summarize(fold.predictions()));
        lines.add("");
    }

    private static void formatSummary(List<String> lines, String label, MultiYearBacktestMetricsSummary s) {
        lines.add("[%s] evaluationCount=%d".formatted(label, s.evaluationCount()));
        if (s.evaluationCount() == 0) {
            lines.add("  (평가 가능한 건이 없음)");
            return;
        }
        lines.add("  MAE=%,.0f원  MedianAE=%,.0f원  MedianAPE=%s  P75APE=%s  P90APE=%s  MedianALE=%s".formatted(
                s.mae(), s.medianAbsoluteError(), pct(s.medianAbsolutePercentageError()),
                pct(s.p75AbsolutePercentageError()), pct(s.p90AbsolutePercentageError()), fmt(s.medianAbsoluteLogError())));
        lines.add("  ±25%%이내=%s  ±50%%이내=%s  0.5x~2.0x이내=%s  typicalRangeCoverage(P25~P75)=%s".formatted(
                pct(s.within25PercentRatio()), pct(s.within50PercentRatio()), pct(s.within2xRatio()),
                pct(s.typicalRangeCoverageRatio())));
    }

    private static void formatBudgetSizeBreakdown(List<String> lines, List<MultiYearBudgetSizeBucketMetrics> rows) {
        for (MultiYearBudgetSizeBucketMetrics r : rows) {
            lines.add("  %-12s count=%-5d MdAPE=%s".formatted(r.bucketLabel(), r.count(), pct(r.medianAbsolutePercentageError())));
        }
    }

    private static void formatTypeBreakdown(List<String> lines, List<MultiYearTypeMetrics> rows) {
        for (MultiYearTypeMetrics r : rows) {
            lines.add("  %-18s count=%-5d MdAPE=%s MedianALE=%s%s".formatted(
                    r.festivalType(), r.count(), pct(r.medianAbsolutePercentageError()), fmt(r.medianAbsoluteLogError()),
                    r.smallSample() ? "  [표본 부족 - 참고용]" : ""));
        }
    }

    private static void formatRegionBreakdown(List<String> lines, List<MultiYearRegionMetrics> rows) {
        for (MultiYearRegionMetrics r : rows) {
            lines.add("  %-10s count=%-5d MdAPE=%s MedianALE=%s%s".formatted(
                    r.region(), r.count(), pct(r.medianAbsolutePercentageError()), fmt(r.medianAbsoluteLogError()),
                    r.smallSample() ? "  [표본 부족 - 참고용]" : ""));
        }
    }

    private static void formatV3Breakdown(List<String> lines, List<MultiYearV3BucketMetrics> rows) {
        for (MultiYearV3BucketMetrics r : rows) {
            lines.add("  v3=%-8s count=%-5d MdAPE=%s".formatted(r.bucketLabel(), r.count(), pct(r.medianAbsolutePercentageError())));
        }
    }

    private static String formatPredictionLine(MultiYearBacktestPrediction p) {
        return ("  [%d] %s | %s%s / %s / %s / %s일 | actual=%,d원 estimated=%,d원 | APE=%s ALE=%s | "
                + "sampleCount=%d fallback=%s v3=%.1f distinctSeries=%d").formatted(
                p.targetYear(), p.festivalName(), p.region(), p.district() == null ? "" : " " + p.district(),
                p.festivalType(), p.venueType() == null ? "-" : p.venueType(), p.durationDays() == null ? "-" : p.durationDays(),
                p.actualBudget(), p.estimatedBudget(), pct(p.absolutePercentageError()), fmt(p.absoluteLogError()),
                p.sampleCount(), p.fallbackLevel(), p.dataQualityV3(), p.distinctSeriesCount());
    }

    private static List<MultiYearBacktestPrediction> combinePredictions(List<MultiYearFoldResult> folds, boolean primary) {
        List<MultiYearBacktestPrediction> combined = new ArrayList<>();
        for (MultiYearFoldResult f : folds) {
            if (f.fold().primary() == primary) {
                combined.addAll(f.predictions());
            }
        }
        return combined;
    }

    private static String fmt(double v) {
        return Double.isFinite(v) ? "%.3f".formatted(v) : "N/A";
    }

    private static String pct(double v) {
        return Double.isFinite(v) ? "%.1f%%".formatted(v * 100) : "N/A";
    }
}