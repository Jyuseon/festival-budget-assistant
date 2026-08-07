import type { MultiYearYearSummary } from "@/lib/multiyearAdminApi";
import { formatMillionKrwCompact, formatNumber } from "@/lib/budgetFormat";

/**
 * 2017~2026 전체 연도 요약 표 - 항목 2("전체 연도 요약")와 항목 9("전체 연도 비교")를
 * 하나의 표로 겸한다. "전체" 필터가 선택돼 있을 때도, 특정 연도가 선택돼 있을 때도 항상
 * 화면 상단에 보여서 한눈에 비교할 수 있게 한다.
 */
export function YearComparisonTable({ years }: { years: MultiYearYearSummary[] }) {
  const totalAll = years.reduce((sum, y) => sum + y.totalCount, 0);

  return (
    <div className="rounded border border-gray-300 p-3">
      <h2 className="mb-2 text-sm font-semibold">
        연도별 요약 (2017~2026){" "}
        <span className="text-gray-400">(합계 {formatNumber(totalAll)}건)</span>
      </h2>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[960px] text-sm">
          <thead>
            <tr className="text-left text-gray-500">
              <th className="py-1 pr-3">연도</th>
              <th className="py-1 pr-3 text-right">축제 수</th>
              <th className="py-1 pr-3 text-right">정상 예산</th>
              <th className="py-1 pr-3 text-right">단위 의심</th>
              <th className="py-1 pr-3 text-right">누락/0원</th>
              <th className="py-1 pr-3 text-right">예산 중앙값</th>
              <th className="py-1 pr-3 text-right">기간 확보율</th>
              <th className="py-1 pr-3 text-right">장소유형 확보율</th>
              <th className="py-1 pr-3 text-right">COVID 영향</th>
            </tr>
          </thead>
          <tbody>
            {years.map((y) => (
              <tr
                key={y.datasetYear}
                className={`border-t border-gray-100 ${
                  y.budgetUnitSuspectCount > 0 ? "bg-amber-50" : ""
                }`}
              >
                <td className="py-1 pr-3 font-medium tabular-nums">{y.datasetYear}</td>
                <td className="py-1 pr-3 text-right tabular-nums">{formatNumber(y.totalCount)}</td>
                <td className="py-1 pr-3 text-right tabular-nums">{formatNumber(y.validBudgetCount)}</td>
                <td className="py-1 pr-3 text-right tabular-nums">
                  {y.budgetUnitSuspectCount > 0 ? (
                    <span className="rounded bg-amber-200 px-1.5 py-0.5 font-semibold text-amber-900">
                      {formatNumber(y.budgetUnitSuspectCount)}
                    </span>
                  ) : (
                    formatNumber(y.budgetUnitSuspectCount)
                  )}
                </td>
                <td className="py-1 pr-3 text-right tabular-nums">
                  {formatNumber(y.missingOrNonPositiveBudgetCount)}
                </td>
                <td
                  className="py-1 pr-3 text-right tabular-nums"
                  title={`${y.medianValidBudgetMillion.toLocaleString("ko-KR")} million KRW`}
                >
                  {formatMillionKrwCompact(y.medianValidBudgetMillion)}
                </td>
                <td className="py-1 pr-3 text-right tabular-nums">
                  {y.totalCount === 0 ? "-" : `${y.durationAvailableRatePercent.toFixed(1)}%`}
                </td>
                <td className="py-1 pr-3 text-right tabular-nums">
                  {y.totalCount === 0
                    ? "-"
                    : y.venueTypeAvailableCount === 0
                      ? "해당없음"
                      : `${y.venueTypeAvailableRatePercent.toFixed(1)}%`}
                </td>
                <td className="py-1 pr-3 text-right tabular-nums">
                  {y.covidAffectedCount > 0 ? (
                    <span className="rounded bg-blue-100 px-1.5 py-0.5 text-blue-800">
                      {formatNumber(y.covidAffectedCount)}
                    </span>
                  ) : (
                    "-"
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="mt-2 text-xs text-gray-400">
        노란 배경 = 해당 연도에 budgetQualityFlag=UNIT_SCALE_SUSPECT(예산 단위 의심) 행이
        있음. 장소유형 확보율의 &quot;해당없음&quot;은 2017~2024처럼 원본에 장소유형 항목
        자체가 없는 연도입니다.
      </p>
    </div>
  );
}