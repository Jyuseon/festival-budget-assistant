import type {
  MultiYearAdminYearDetailResponse,
  MultiYearBudgetStatistics,
} from "@/lib/multiyearAdminApi";
import { formatMillionKrwCompact, formatNumber } from "@/lib/budgetFormat";

function Card({
  label,
  value,
  highlight,
  note,
}: {
  label: string;
  value: string;
  highlight?: boolean;
  note?: string;
}) {
  return (
    <div
      className={`rounded border p-3 ${
        highlight ? "border-amber-400 bg-amber-50" : "border-gray-300"
      }`}
    >
      <div className="text-xs text-gray-500">{label}</div>
      <div
        className={`mt-1 text-2xl font-semibold tabular-nums ${
          highlight ? "text-amber-900" : ""
        }`}
      >
        {value}
      </div>
      {note && <div className="mt-1 text-xs text-gray-400">{note}</div>}
    </div>
  );
}

/** 항목 5: 선택한 연도의 데이터 품질 카드 6종. */
export function QualityCards({
  detail,
}: {
  detail: MultiYearAdminYearDetailResponse;
}) {
  const q = detail.qualityCard;
  return (
    <div className="flex flex-col gap-3">
      {detail.covidAffectedYear && (
        <div className="rounded border border-blue-300 bg-blue-50 p-2 text-sm text-blue-800">
          {detail.year}년은 COVID 영향 연도로 표시된 행이 있습니다(covidAffected=true).
        </div>
      )}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        <Card label="총 데이터" value={formatNumber(q.totalCount)} />
        <Card label="정상 예산(VALID)" value={formatNumber(q.validBudgetCount)} />
        <Card
          label="예산 단위 의심(UNIT_SCALE_SUSPECT)"
          value={formatNumber(q.budgetUnitSuspectCount)}
          highlight={q.budgetUnitSuspectCount > 0}
          note={
            q.budgetUnitSuspectCount > 0
              ? "정상 예산 통계에서 제외됨 - 원본 값은 자동 보정하지 않았습니다."
              : undefined
          }
        />
        <Card
          label="예산 누락/0원(MISSING_OR_NONPOSITIVE)"
          value={formatNumber(q.missingOrNonPositiveBudgetCount)}
        />
        <Card
          label="기간(duration) 확보율"
          value={`${q.durationAvailableRatePercent.toFixed(1)}%`}
        />
        <Card
          label="장소유형(venueType) 확보율"
          value={
            q.venueTypeAvailableRatePercent === 0 && q.totalCount > 0
              ? "해당없음"
              : `${q.venueTypeAvailableRatePercent.toFixed(1)}%`
          }
          note={
            q.venueTypeAvailableRatePercent === 0
              ? "이 연도 원본에는 장소유형 항목이 없습니다(2025~2026만 존재)."
              : undefined
          }
        />
      </div>
    </div>
  );
}

/** 항목 4: 예산 통계(mean/P25/P50/P75/P90/P95/max) - UNIT_SCALE_SUSPECT/MISSING_OR_NONPOSITIVE 제외. */
export function BudgetStatisticsPanel({ stats }: { stats: MultiYearBudgetStatistics }) {
  if (stats.sampleCount === 0) {
    return (
      <div className="rounded border border-gray-300 p-3 text-sm text-gray-500">
        정상 예산(VALID) 표본이 없어 통계를 계산할 수 없습니다.
      </div>
    );
  }

  const rows: { label: string; million: number }[] = [
    { label: "평균(mean)", million: stats.meanMillion },
    { label: "P25", million: stats.p25Million },
    { label: "P50(중앙값)", million: stats.medianMillion },
    { label: "P75", million: stats.p75Million },
    { label: "P90", million: stats.p90Million },
    { label: "P95", million: stats.p95Million },
    { label: "최댓값", million: stats.maxMillion },
  ];

  return (
    <div className="rounded border border-gray-300 p-3">
      <h2 className="mb-2 text-sm font-semibold">
        예산 통계{" "}
        <span className="text-gray-400">
          (정상 예산 VALID 표본 {formatNumber(stats.sampleCount)}건 기준, UNIT_SCALE_SUSPECT·MISSING_OR_NONPOSITIVE 제외)
        </span>
      </h2>
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-gray-500">
              {rows.map((r) => (
                <th key={r.label} className="py-1 pr-3">
                  {r.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            <tr className="border-t border-gray-100">
              {rows.map((r) => (
                <td
                  key={r.label}
                  className="py-1 pr-3 tabular-nums"
                  title={`${r.million.toLocaleString("ko-KR", { maximumFractionDigits: 1 })} million KRW`}
                >
                  {formatMillionKrwCompact(r.million)}
                </td>
              ))}
            </tr>
          </tbody>
        </table>
      </div>
      <p className="mt-2 text-xs text-gray-400">
        원본 DB 값은 백만원 단위 그대로 유지됩니다(예: {stats.medianMillion.toFixed(1)} million
        KRW → {formatMillionKrwCompact(stats.medianMillion)}). 화면에서만 원 단위로
        변환해 표시합니다.
      </p>
    </div>
  );
}