import type {
  AdminDatasetDistributionsResponse,
  CategoryCount,
} from "@/lib/adminApi";
import { formatKrwCompact, formatKrwExact, formatNumber } from "@/lib/budgetFormat";

function CategoryTable({
  title,
  rows,
}: {
  title: string;
  rows: CategoryCount[];
}) {
  const total = rows.reduce((sum, r) => sum + r.count, 0);
  return (
    <div className="rounded border border-gray-300 p-3">
      <h3 className="mb-2 text-sm font-semibold">
        {title} <span className="text-gray-400">(종류 {rows.length}개, 합계 {formatNumber(total)}건)</span>
      </h3>
      <div className="max-h-64 overflow-y-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-gray-500">
              <th className="py-1">항목</th>
              <th className="py-1 text-right">건수</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.code} className="border-t border-gray-100">
                <td className="py-1">{r.displayName}</td>
                <td className="py-1 text-right tabular-nums">{formatNumber(r.count)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export function DistributionTables({
  distributions,
}: {
  distributions: AdminDatasetDistributionsResponse;
}) {
  const stats = distributions.budgetStatistics;

  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
        <CategoryTable title="지역별 건수" rows={distributions.regionCounts} />
        <CategoryTable title="축제 유형별 건수" rows={distributions.festivalTypeCounts} />
        <CategoryTable title="장소 유형별 건수" rows={distributions.venueTypeCounts} />
      </div>

      <div className="rounded border border-gray-300 p-3">
        <h3 className="mb-2 text-sm font-semibold">
          예산 통계 <span className="text-gray-400">(예산 확정 표본 {formatNumber(stats.sampleCount)}건 기준)</span>
        </h3>
        {stats.sampleCount === 0 ? (
          <p className="text-sm text-gray-500">확정 예산 표본이 없습니다.</p>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-gray-500">
                <th className="py-1">평균</th>
                <th className="py-1">중앙값</th>
                <th className="py-1">P25</th>
                <th className="py-1">P75</th>
                <th className="py-1">P90</th>
                <th className="py-1">최댓값</th>
              </tr>
            </thead>
            <tbody>
              <tr className="border-t border-gray-100">
                <td className="py-1" title={formatKrwExact(stats.meanKrw)}>{formatKrwCompact(stats.meanKrw)}</td>
                <td className="py-1" title={formatKrwExact(stats.medianKrw)}>{formatKrwCompact(stats.medianKrw)}</td>
                <td className="py-1" title={formatKrwExact(stats.p25Krw)}>{formatKrwCompact(stats.p25Krw)}</td>
                <td className="py-1" title={formatKrwExact(stats.p75Krw)}>{formatKrwCompact(stats.p75Krw)}</td>
                <td className="py-1" title={formatKrwExact(stats.p90Krw)}>{formatKrwCompact(stats.p90Krw)}</td>
                <td className="py-1" title={formatKrwExact(stats.maxKrw)}>{formatKrwCompact(stats.maxKrw)}</td>
              </tr>
            </tbody>
          </table>
        )}
      </div>

      <div className="rounded border border-gray-300 p-3">
        <h3 className="mb-2 text-sm font-semibold">개최기간 구간별 건수</h3>
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-gray-500">
              {distributions.durationBuckets.map((b) => (
                <th key={b.label} className="py-1 pr-3">{b.label}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            <tr className="border-t border-gray-100">
              {distributions.durationBuckets.map((b) => (
                <td key={b.label} className="py-1 pr-3 tabular-nums">{formatNumber(b.count)}</td>
              ))}
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  );
}