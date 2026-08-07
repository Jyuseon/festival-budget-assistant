import type { MultiYearAdminSampleResponse } from "@/lib/multiyearAdminApi";
import { formatMillionKrwCompact, formatNumber } from "@/lib/budgetFormat";

/**
 * 항목 7: read-only 샘플 테이블. 전체 연도 데이터(최대 1,266건)를 한 번에 내려주지 않도록
 * limit/offset 기반 페이지네이션을 쓴다 - 백엔드도 limit을 100건으로 강제 상한한다.
 */
export function SampleTable({
  sample,
  onPrevPage,
  onNextPage,
}: {
  sample: MultiYearAdminSampleResponse;
  onPrevPage: () => void;
  onNextPage: () => void;
}) {
  const start = sample.totalCountForYear === 0 ? 0 : sample.offset + 1;
  const end = Math.min(sample.offset + sample.rows.length, sample.totalCountForYear);
  const hasPrev = sample.offset > 0;
  const hasNext = sample.offset + sample.rows.length < sample.totalCountForYear;

  return (
    <div className="rounded border border-gray-300 p-3">
      <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-sm font-semibold">
          {sample.year}년 데이터 샘플{" "}
          <span className="text-gray-400">
            ({formatNumber(start)}~{formatNumber(end)} / 전체 {formatNumber(sample.totalCountForYear)}건)
          </span>
        </h2>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={onPrevPage}
            disabled={!hasPrev}
            className="rounded border border-gray-300 px-2 py-1 text-xs disabled:opacity-40"
          >
            이전 {sample.limit}건
          </button>
          <button
            type="button"
            onClick={onNextPage}
            disabled={!hasNext}
            className="rounded border border-gray-300 px-2 py-1 text-xs disabled:opacity-40"
          >
            다음 {sample.limit}건
          </button>
        </div>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full min-w-[960px] text-sm">
          <thead>
            <tr className="text-left text-gray-500">
              <th className="py-1 pr-2">연도</th>
              <th className="py-1 pr-2">지역</th>
              <th className="py-1 pr-2">시군구</th>
              <th className="py-1 pr-2">축제명</th>
              <th className="py-1 pr-2">유형(raw)</th>
              <th className="py-1 pr-2">유형(정규화)</th>
              <th className="py-1 pr-2">장소명(raw)</th>
              <th className="py-1 pr-2">장소유형</th>
              <th className="py-1 pr-2">기간</th>
              <th className="py-1 pr-2">예산</th>
              <th className="py-1 pr-2">예산 품질</th>
            </tr>
          </thead>
          <tbody>
            {sample.rows.map((row, idx) => (
              <tr
                key={`${row.year}-${sample.offset}-${idx}`}
                className={`border-t border-gray-100 ${
                  row.budgetQualityFlag === "UNIT_SCALE_SUSPECT" ? "bg-amber-50" : ""
                }`}
              >
                <td className="py-1 pr-2 tabular-nums">{row.year}</td>
                <td className="py-1 pr-2">{row.region}</td>
                <td className="py-1 pr-2">{row.district ?? "-"}</td>
                <td className="py-1 pr-2">{row.festivalName}</td>
                <td className="py-1 pr-2 text-gray-500">{row.festivalTypeRaw ?? "-"}</td>
                <td className="py-1 pr-2">{row.festivalType ?? "-"}</td>
                <td className="py-1 pr-2 text-gray-500">{row.venueNameRaw ?? "-"}</td>
                <td className="py-1 pr-2">{row.venueType ?? "-"}</td>
                <td className="py-1 pr-2">
                  {row.durationDays === null ? "미확정" : `${row.durationDays}일`}
                </td>
                <td
                  className="py-1 pr-2"
                  title={
                    row.budgetTotalMillion === null
                      ? undefined
                      : `${row.budgetTotalMillion.toLocaleString("ko-KR")} million KRW`
                  }
                >
                  {row.budgetTotalMillion === null
                    ? "-"
                    : formatMillionKrwCompact(row.budgetTotalMillion)}
                </td>
                <td className="py-1 pr-2">
                  {row.budgetQualityFlag === "UNIT_SCALE_SUSPECT" ? (
                    <span className="rounded bg-amber-200 px-1.5 py-0.5 text-xs font-semibold text-amber-900">
                      {row.budgetQualityFlag}
                    </span>
                  ) : (
                    row.budgetQualityFlag
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}