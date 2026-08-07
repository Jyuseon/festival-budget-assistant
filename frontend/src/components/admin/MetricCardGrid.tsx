import type { AdminDatasetSummaryResponse } from "@/lib/adminApi";
import { KNOWN_2026_PROFILE } from "@/lib/knownDatasetProfile";
import { formatNumber } from "@/lib/budgetFormat";

interface Metric {
  label: string;
  value: number;
  expected?: number;
  note?: string;
}

function MetricCard({ label, value, expected, note }: Metric) {
  const hasExpected = expected !== undefined;
  const matches = hasExpected && value === expected;

  return (
    <div className="rounded border border-gray-300 p-3">
      <div className="text-xs text-gray-500">{label}</div>
      <div className="mt-1 text-2xl font-semibold tabular-nums">
        {formatNumber(value)}
      </div>
      {hasExpected && (
        <div
          className={`mt-1 text-xs ${matches ? "text-green-700" : "text-red-600 font-medium"}`}
        >
          가이드 기대값 {formatNumber(expected)}
          {matches ? " · 일치" : ` · 불일치(차이 ${formatNumber(value - expected)})`}
        </div>
      )}
      {note && <div className="mt-1 text-xs text-gray-400">{note}</div>}
    </div>
  );
}

export function MetricCardGrid({
  summary,
}: {
  summary: AdminDatasetSummaryResponse;
}) {
  // 배치의 기준연도가 2026일 때만 알려진 기대값과 비교한다(다른 연도는 기대값이 없음).
  const compareToKnownProfile = summary.batch?.datasetYear === KNOWN_2026_PROFILE.datasetYear;
  const expected = compareToKnownProfile ? KNOWN_2026_PROFILE : undefined;

  const metrics: Metric[] = [
    { label: "전체 축제 행 수", value: summary.totalRows, expected: expected?.totalRows },
    {
      label: "예산 유효(0원 초과) 행 수",
      value: summary.validBudgetRows,
      expected: expected?.validBudgetRows,
    },
    {
      label: "예산 미확정",
      value: summary.unconfirmedBudgetRows,
      expected: expected?.unconfirmedBudgetRows,
    },
    {
      label: "예산 무응답",
      value: summary.noResponseBudgetRows,
      expected: expected?.noResponseBudgetRows,
    },
    { label: "0원 예산", value: summary.zeroBudgetRows, expected: expected?.zeroBudgetRows },
    {
      label: "개최기간 누락",
      value: summary.missingDurationRows,
      expected: expected?.missingDurationRows,
      note: expected ? KNOWN_2026_PROFILE.missingDurationNote : undefined,
    },
    { label: "광역지역 수", value: summary.regionCount, expected: expected?.regionCount },
    {
      label: "축제 유형 수",
      value: summary.festivalTypeCount,
      expected: expected?.festivalTypeCount,
    },
    {
      label: "장소 유형 수",
      value: summary.venueTypeCount,
      expected: expected?.venueTypeCount,
    },
  ];

  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
      {metrics.map((m) => (
        <MetricCard key={m.label} {...m} />
      ))}
    </div>
  );
}