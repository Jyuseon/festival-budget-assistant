import type {
  AdminDatasetIssuesResponse,
  AdminDatasetSampleResponse,
} from "@/lib/adminApi";
import { formatKrwCompact, formatKrwExact, formatNumber } from "@/lib/budgetFormat";

export function IssuesTable({ issues }: { issues: AdminDatasetIssuesResponse }) {
  return (
    <div className="rounded border border-gray-300 p-3">
      <h3 className="mb-2 text-sm font-semibold">
        데이터 품질 문제가 있는 행{" "}
        <span className={issues.totalWarnings > 0 ? "text-amber-700" : "text-gray-400"}>
          ({formatNumber(issues.totalWarnings)}건)
        </span>
      </h3>

      {issues.totalWarnings === 0 ? (
        <p className="text-sm text-gray-500">
          경고 없음 - Import를 막지 않는 부수적 품질 이슈가 하나도 없었습니다.
        </p>
      ) : (
        <>
          <div className="max-h-72 overflow-y-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-gray-500">
                  <th className="py-1 pr-3">연번</th>
                  <th className="py-1">내용</th>
                </tr>
              </thead>
              <tbody>
                {issues.issues.map((issue, idx) => (
                  <tr key={idx} className="border-t border-gray-100 align-top">
                    <td className="py-1 pr-3 tabular-nums">{issue.sourceRowNumber ?? "-"}</td>
                    <td className="py-1">{issue.message}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {issues.truncated && (
            <p className="mt-2 text-xs text-gray-400">
              상위 {issues.issues.length}건만 표시했습니다 (전체 {formatNumber(issues.totalWarnings)}건).
            </p>
          )}
        </>
      )}
    </div>
  );
}

export function SampleAndColumnCatalog({
  sample,
}: {
  sample: AdminDatasetSampleResponse;
}) {
  return (
    <div className="flex flex-col gap-4">
      <div className="rounded border border-green-300 bg-green-50 p-3">
        <p className="text-sm font-semibold text-green-800">
          {sample.personalInfoStatusLabel}
        </p>
        <p className="mt-1 text-xs text-green-700">
          담당자 소속/부서/직급·직책/성명, 연락처, 자유서술 비고(AN, AE)는 애초에
          코드 상에서 읽지 않으므로 DB·API·화면 어디에도 나타나지 않습니다.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
        <div className="rounded border border-gray-300 p-3">
          <h3 className="mb-2 text-sm font-semibold">실제 적재 컬럼</h3>
          <ul className="list-disc space-y-1 pl-4 text-sm text-gray-700">
            {sample.loadedColumns.map((c) => (
              <li key={c}>{c}</li>
            ))}
          </ul>
        </div>
        <div className="rounded border border-gray-300 p-3">
          <h3 className="mb-2 text-sm font-semibold">제외된 컬럼</h3>
          <ul className="list-disc space-y-1 pl-4 text-sm text-gray-700">
            {sample.excludedColumns.map((c) => (
              <li key={c}>{c}</li>
            ))}
          </ul>
        </div>
      </div>

      <div className="rounded border border-gray-300 p-3">
        <h3 className="mb-2 text-sm font-semibold">
          적재 데이터 샘플 (개인정보 제거, 상위 {sample.sampleRows.length}건)
        </h3>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[720px] text-sm">
            <thead>
              <tr className="text-left text-gray-500">
                <th className="py-1 pr-2">연번</th>
                <th className="py-1 pr-2">축제명</th>
                <th className="py-1 pr-2">지역</th>
                <th className="py-1 pr-2">유형</th>
                <th className="py-1 pr-2">장소유형</th>
                <th className="py-1 pr-2">기간</th>
                <th className="py-1 pr-2">예산</th>
                <th className="py-1 pr-2">예산상태</th>
              </tr>
            </thead>
            <tbody>
              {sample.sampleRows.map((row) => (
                <tr key={row.sourceRowNumber} className="border-t border-gray-100">
                  <td className="py-1 pr-2 tabular-nums">{row.sourceRowNumber}</td>
                  <td className="py-1 pr-2">{row.festivalName}</td>
                  <td className="py-1 pr-2">{row.regionName}</td>
                  <td className="py-1 pr-2">{row.festivalTypeName}</td>
                  <td className="py-1 pr-2">{row.venueTypeName}</td>
                  <td className="py-1 pr-2">
                    {row.durationDays === null ? "미확정" : `${row.durationDays}일`}
                  </td>
                  <td className="py-1 pr-2" title={formatKrwExact(row.totalBudgetKrw)}>
                    {formatKrwCompact(row.totalBudgetKrw)}
                  </td>
                  <td className="py-1 pr-2">{row.budgetStatus}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}