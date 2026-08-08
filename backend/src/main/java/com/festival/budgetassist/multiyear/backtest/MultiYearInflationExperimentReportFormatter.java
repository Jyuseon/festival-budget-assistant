package com.festival.budgetassist.multiyear.backtest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** inflation x series-correction 2x2(A/B/C/D) 비교 리포트. */
final class MultiYearInflationExperimentReportFormatter {

    private MultiYearInflationExperimentReportFormatter() {
    }

    static List<String> format(Map<MultiYearInflationExperimentVariant, List<MultiYearFoldCorrectionResult>> byVariant,
                                MultiYearSeriesCorrectionMetricsCalculator calculator,
                                AnnualPriceIndexProvider priceIndexProvider) {
        List<String> lines = new ArrayList<>();
        lines.add("================ Inflation Adjustment x Series Correction: A/B/C/D ================");
        lines.add("");
        lines.add("--- 사용한 CPI(headline, 전국, 2020=100) ---");
        for (AnnualPriceIndex idx : priceIndexProvider.all()) {
            lines.add("  %d년: %.2f  [출처: %s]".formatted(idx.year(), idx.indexValue(), idx.source()));
        }
        lines.add("");
        lines.add("--- 적용 방식 ---");
        lines.add("* inflationAdjustedBudget = originalBudget * CPI(targetYear) / CPI(candidateYear)");
        lines.add("* 순서: 물가보정 -> 기간보정 -> winsorize -> 가중통계. 물가보정/기간보정 둘 다 예산에");
        lines.add("  대한 곱셈 스케일링이라 순서 자체가 최종 candidate 값에 영향을 주지는 않지만(교환법칙),");
        lines.add("  winsorize 모집단(같은 유형 training 전체 log-budget 분포)은 물가보정이 켜졌을 때");
        lines.add("  반드시 물가보정된 값으로 다시 계산한다 - 그렇지 않으면 인플레이션으로 전체적으로");
        lines.add("  커진 candidate 값을 보정 전 기준의 낮은 상한에 부당하게 clip하게 된다.");
        lines.add("* candidate selection(어떤 후보를 뽑는지)은 A/B/C/D 전부 동일하다 - similarity는");
        lines.add("  budget과 무관한 feature(유형/지역/장소/기간)로만 계산되므로 구조적으로 보장된다.");
        lines.add("* CPI(candidateYear)는 항상 training 기간(target year보다 이른 해)의 값만 조회한다 -");
        lines.add("  leakage-safe dataset 구성 자체가 이미 미래 연도를 training pool에서 배제한다.");
        lines.add("");

        for (MultiYearInflationExperimentVariant variant : MultiYearInflationExperimentVariant.values()) {
            List<MultiYearFoldCorrectionResult> folds = byVariant.get(variant);
            lines.add("================ [%s] ================".formatted(variant.label()));
            for (MultiYearFoldCorrectionResult fold : folds) {
                lines.add("--- %s ---".formatted(fold.fold().label()));
                formatSummary(lines, calculator.summarize(fold.predictions()));
            }

            List<MultiYearSeriesCorrectionPrediction> primaryAll = combine(byVariant, variant, true);
            List<MultiYearSeriesCorrectionPrediction> secondaryAll = combine(byVariant, variant, false);

            lines.add("--- Primary 종합(2025+2026) ---");
            formatSummary(lines, calculator.summarize(primaryAll));
            lines.add("--- Primary 종합: 예산 규모별 signed bias(5절) ---");
            for (MultiYearSeriesCorrectionBudgetBucket b : calculator.budgetSizeBreakdown(primaryAll)) {
                lines.add("  %-12s count=%-5d MdAPE=%s MedianALE=%s signedLogErr=%s ratio=%s".formatted(
                        b.bucketLabel(), b.count(), pct(b.medianAbsolutePercentageError()), fmt(b.medianAbsoluteLogError()),
                        fmtSigned(b.medianSignedLogError()), fmt(b.medianPredictedActualRatio())));
            }
            lines.add("--- Secondary 2024 종합 ---");
            formatSummary(lines, calculator.summarize(secondaryAll));
            lines.add("");
        }

        formatFoldConsistency(lines, byVariant, calculator);
        formatSeriesInteraction(lines, byVariant, calculator);

        lines.add("================ 리포트 종료 ================");
        return lines;
    }

    /** 지시사항 6절: 2025/2026에서 같은 방향인지 A 대비 변형별로 나란히 보여준다. */
    private static void formatFoldConsistency(List<String> lines,
                                               Map<MultiYearInflationExperimentVariant, List<MultiYearFoldCorrectionResult>> byVariant,
                                               MultiYearSeriesCorrectionMetricsCalculator calculator) {
        lines.add("================ fold 방향 일관성(2025 vs 2026, 6절) ================");
        for (MultiYearInflationExperimentVariant variant : MultiYearInflationExperimentVariant.values()) {
            List<MultiYearFoldCorrectionResult> folds = byVariant.get(variant);
            MultiYearFoldCorrectionResult f2025 = findFold(folds, 2025);
            MultiYearFoldCorrectionResult f2026 = findFold(folds, 2026);
            if (f2025 == null || f2026 == null) {
                continue;
            }
            MultiYearBacktestMetricsSummary s2025 = calculator.summarize(f2025.predictions()).base();
            MultiYearBacktestMetricsSummary s2026 = calculator.summarize(f2026.predictions()).base();
            lines.add("  [%s] 2025: MdAPE=%s P75APE=%s P90APE=%s MedianALE=%s | 2026: MdAPE=%s P75APE=%s P90APE=%s MedianALE=%s".formatted(
                    variant, pct(s2025.medianAbsolutePercentageError()), pct(s2025.p75AbsolutePercentageError()),
                    pct(s2025.p90AbsolutePercentageError()), fmt(s2025.medianAbsoluteLogError()),
                    pct(s2026.medianAbsolutePercentageError()), pct(s2026.p75AbsolutePercentageError()),
                    pct(s2026.p90AbsolutePercentageError()), fmt(s2026.medianAbsoluteLogError())));
        }
        lines.add("");
    }

    /** 지시사항 7절: A->C(inflation 단독 효과), C->D(inflation 있는 상태에서 S1 효과)를 Primary 종합 기준으로 비교. */
    private static void formatSeriesInteraction(List<String> lines,
                                                 Map<MultiYearInflationExperimentVariant, List<MultiYearFoldCorrectionResult>> byVariant,
                                                 MultiYearSeriesCorrectionMetricsCalculator calculator) {
        lines.add("================ Series interaction(7절): A->C(inflation 단독), C->D(inflation 위에서 S1) ================");
        MultiYearBacktestMetricsSummary a = calculator.summarize(combine(byVariant, MultiYearInflationExperimentVariant.A_S0_INFLATION_OFF, true)).base();
        MultiYearBacktestMetricsSummary b = calculator.summarize(combine(byVariant, MultiYearInflationExperimentVariant.B_S1_INFLATION_OFF, true)).base();
        MultiYearBacktestMetricsSummary c = calculator.summarize(combine(byVariant, MultiYearInflationExperimentVariant.C_S0_INFLATION_ON, true)).base();
        MultiYearBacktestMetricsSummary d = calculator.summarize(combine(byVariant, MultiYearInflationExperimentVariant.D_S1_INFLATION_ON, true)).base();

        lines.add("  A(S0,OFF)  MdAPE=%s P75APE=%s P90APE=%s MedianALE=%s".formatted(
                pct(a.medianAbsolutePercentageError()), pct(a.p75AbsolutePercentageError()), pct(a.p90AbsolutePercentageError()), fmt(a.medianAbsoluteLogError())));
        lines.add("  C(S0,ON)   MdAPE=%s P75APE=%s P90APE=%s MedianALE=%s   [A->C, inflation 단독 효과]".formatted(
                pct(c.medianAbsolutePercentageError()), pct(c.p75AbsolutePercentageError()), pct(c.p90AbsolutePercentageError()), fmt(c.medianAbsoluteLogError())));
        lines.add("  B(S1,OFF)  MdAPE=%s P75APE=%s P90APE=%s MedianALE=%s   [A->B, 참고: 이전 실험의 S1 단독 효과]".formatted(
                pct(b.medianAbsolutePercentageError()), pct(b.p75AbsolutePercentageError()), pct(b.p90AbsolutePercentageError()), fmt(b.medianAbsoluteLogError())));
        lines.add("  D(S1,ON)   MdAPE=%s P75APE=%s P90APE=%s MedianALE=%s   [C->D, inflation 켜진 상태에서 S1 효과]".formatted(
                pct(d.medianAbsolutePercentageError()), pct(d.p75AbsolutePercentageError()), pct(d.p90AbsolutePercentageError()), fmt(d.medianAbsoluteLogError())));
        lines.add("");

        List<MultiYearSeriesCorrectionPrediction> aPreds = combine(byVariant, MultiYearInflationExperimentVariant.A_S0_INFLATION_OFF, true);
        List<MultiYearSeriesCorrectionPrediction> cPreds = combine(byVariant, MultiYearInflationExperimentVariant.C_S0_INFLATION_ON, true);
        List<MultiYearSeriesCorrectionPrediction> dPreds = combine(byVariant, MultiYearInflationExperimentVariant.D_S1_INFLATION_ON, true);

        lines.add("--- A->C: 예산 규모별 signed bias 변화(inflation이 소형 과대예측/대형 과소예측 패턴을 완화하는지) ---");
        formatBiasComparison(lines, calculator.budgetSizeBreakdown(aPreds), calculator.budgetSizeBreakdown(cPreds), "A", "C");
        lines.add("--- C->D: inflation 켜진 상태에서 S1이 tail error/대형 과소예측에 미치는 영향 ---");
        formatBiasComparison(lines, calculator.budgetSizeBreakdown(cPreds), calculator.budgetSizeBreakdown(dPreds), "C", "D");
        lines.add("");
    }

    private static void formatBiasComparison(List<String> lines, List<MultiYearSeriesCorrectionBudgetBucket> before,
                                               List<MultiYearSeriesCorrectionBudgetBucket> after, String beforeLabel, String afterLabel) {
        for (int i = 0; i < before.size(); i++) {
            MultiYearSeriesCorrectionBudgetBucket b = before.get(i);
            MultiYearSeriesCorrectionBudgetBucket a = after.get(i);
            lines.add("  %-12s %s: signedLogErr=%s ratio=%s  ->  %s: signedLogErr=%s ratio=%s".formatted(
                    b.bucketLabel(), beforeLabel, fmtSigned(b.medianSignedLogError()), fmt(b.medianPredictedActualRatio()),
                    afterLabel, fmtSigned(a.medianSignedLogError()), fmt(a.medianPredictedActualRatio())));
        }
    }

    private static MultiYearFoldCorrectionResult findFold(List<MultiYearFoldCorrectionResult> folds, int targetYear) {
        return folds.stream().filter(f -> f.fold().targetYear() == targetYear).findFirst().orElse(null);
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
        lines.add("  ±25%%이내=%s ±50%%이내=%s 0.5x~2.0x이내=%s typicalRangeCoverage=%s medianRangeWidthRatio=%s".formatted(
                pct(s.within25PercentRatio()), pct(s.within50PercentRatio()), pct(s.within2xRatio()),
                pct(s.typicalRangeCoverageRatio()), fmt(m.medianRangeWidthRatio())));
        lines.add("  medianSignedLogError=%s medianPredictedActualRatio=%s".formatted(
                fmtSigned(m.medianSignedLogError()), fmt(m.medianPredictedActualRatio())));
    }

    private static List<MultiYearSeriesCorrectionPrediction> combine(
            Map<MultiYearInflationExperimentVariant, List<MultiYearFoldCorrectionResult>> byVariant,
            MultiYearInflationExperimentVariant variant, boolean primaryOnly) {
        List<MultiYearSeriesCorrectionPrediction> combined = new ArrayList<>();
        for (MultiYearFoldCorrectionResult fold : byVariant.get(variant)) {
            if (fold.fold().primary() == primaryOnly) {
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