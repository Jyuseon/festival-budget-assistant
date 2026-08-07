import type { CategoryCount } from "@/lib/adminApi";
import type { MultiYearAdminDistributionsResponse } from "@/lib/multiyearAdminApi";
import { formatNumber } from "@/lib/budgetFormat";

function CategoryTable({ title, rows, emptyNote }: { title: string; rows: CategoryCount[]; emptyNote?: string }) {
  const total = rows.reduce((sum, r) => sum + r.count, 0);
  return (
    <div className="rounded border border-gray-300 p-3">
      <h3 className="mb-2 text-sm font-semibold">
        {title}{" "}
        <span className="text-gray-400">
          (종류 {rows.length}개, 합계 {formatNumber(total)}건)
        </span>
      </h3>
      {rows.length === 0 ? (
        <p className="text-sm text-gray-500">{emptyNote ?? "데이터 없음"}</p>
      ) : (
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
      )}
    </div>
  );
}

/** 항목 6: region/festivalType/venueType/budgetQualityFlag 분포. */
export function DistributionPanels({
  distributions,
}: {
  distributions: MultiYearAdminDistributionsResponse;
}) {
  return (
    <div className="flex flex-col gap-3">
      {distributions.covidAffectedYear && (
        <div className="rounded border border-blue-300 bg-blue-50 p-2 text-sm text-blue-800">
          {distributions.year}년은 covidAffected 데이터가 있는 연도입니다(2020~2021).
        </div>
      )}
      <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
        <CategoryTable title="지역별 건수" rows={distributions.regionCounts} />
        <CategoryTable title="축제 유형별 건수" rows={distributions.festivalTypeCounts} />
        <CategoryTable
          title="장소유형별 건수"
          rows={distributions.venueTypeCounts}
          emptyNote={
            distributions.venueTypeDataAvailable
              ? "데이터 없음"
              : "이 연도 원본에는 장소유형 항목이 없습니다(2025~2026만 존재)."
          }
        />
        <CategoryTable title="예산 품질 플래그별 건수" rows={distributions.budgetQualityFlagCounts} />
      </div>
    </div>
  );
}