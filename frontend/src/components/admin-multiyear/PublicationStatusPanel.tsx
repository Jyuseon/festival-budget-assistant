"use client";

import { useState } from "react";
import {
  setMultiYearPublicationStatus,
  type MultiYearAdminPublicationStatusEntry,
  type MultiYearDatasetPublicationStatusValue,
} from "@/lib/multiyearAdminApi";

/**
 * 연도별 "다년도 개최계획 데이터셋 공개 완성" 상태를 확인/설정하는 관리자 전용 패널.
 *
 * {@code PUBLISHED_PLAN_COMPLETE}는 "그 해 축제가 전부 끝났다"는 뜻이 아니라 "그 해 개최계획
 * 데이터셋 원본이 공개 기준으로 완성되어 계획예산 참고자료로 안전하게 쓸 수 있다"는 뜻이다 -
 * 이 값을 켜야만 다년도 계획예산 분석의 "기획연도와 같은 해 데이터 포함" 옵션이 활성화된다.
 * 자동으로 켜지는 로직은 없다 - 운영자가 판단해서 직접 켠다.
 */
export function PublicationStatusPanel({
  entries,
  onChanged,
}: {
  entries: MultiYearAdminPublicationStatusEntry[];
  onChanged: (updated: MultiYearAdminPublicationStatusEntry) => void;
}) {
  const [pendingYear, setPendingYear] = useState<number | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  async function handleToggle(year: number, next: MultiYearDatasetPublicationStatusValue) {
    setPendingYear(year);
    setErrorMessage(null);
    const res = await setMultiYearPublicationStatus(year, next);
    setPendingYear(null);
    if (res.kind === "ok") {
      onChanged(res.data);
    } else {
      setErrorMessage(res.kind === "error" ? res.message : "관리자 API가 비활성화되어 있습니다.");
    }
  }

  return (
    <section className="rounded border border-gray-200 p-4">
      <h2 className="text-sm font-semibold text-gray-700">연도별 개최계획 데이터셋 공개 상태</h2>
      <p className="mt-1 text-xs text-gray-500">
        &ldquo;공개 완성&rdquo;은 그 해 축제가 모두 끝났다는 뜻이 아니라, 그 해 지역축제
        개최계획 데이터셋 원본이 공개 기준으로 완성되어 <b>같은 연도 안에서도</b> 계획예산
        참고자료로 쓸 수 있다는 뜻입니다. 다년도 계획예산 분석의 &ldquo;기획연도 계획자료
        포함&rdquo; 옵션은 여기서 공개 완성으로 표시한 연도에서만 활성화됩니다.
      </p>

      {errorMessage && (
        <p className="mt-2 rounded border border-red-300 bg-red-50 p-2 text-xs text-red-800">{errorMessage}</p>
      )}

      <table className="mt-3 w-full text-sm">
        <thead>
          <tr className="text-left text-gray-500">
            <th className="py-1 pr-2">연도</th>
            <th className="py-1 pr-2">데이터 건수</th>
            <th className="py-1 pr-2">상태</th>
            <th className="py-1 pr-2">공개 완성 표시 시각</th>
            <th className="py-1 pr-2" />
          </tr>
        </thead>
        <tbody>
          {entries.map((e) => {
            const isComplete = e.status === "PUBLISHED_PLAN_COMPLETE";
            const isPending = pendingYear === e.datasetYear;
            return (
              <tr key={e.datasetYear} className="border-t border-gray-100">
                <td className="py-1 pr-2 font-medium">{e.datasetYear}</td>
                <td className="py-1 pr-2 tabular-nums text-gray-500">{e.recordCount.toLocaleString()}건</td>
                <td className="py-1 pr-2">
                  <span
                    className={
                      isComplete
                        ? "rounded bg-emerald-100 px-2 py-0.5 text-xs font-semibold text-emerald-800"
                        : "rounded bg-gray-100 px-2 py-0.5 text-xs font-semibold text-gray-600"
                    }
                  >
                    {isComplete ? "공개 완성" : "부분/미확인"}
                  </span>
                </td>
                <td className="py-1 pr-2 text-xs text-gray-500">
                  {e.publishedAt ? new Date(e.publishedAt).toLocaleString() : "-"}
                </td>
                <td className="py-1 pr-2">
                  <button
                    type="button"
                    disabled={isPending}
                    onClick={() => handleToggle(e.datasetYear, isComplete ? "PARTIAL" : "PUBLISHED_PLAN_COMPLETE")}
                    className="rounded border border-gray-300 px-2 py-1 text-xs font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
                  >
                    {isPending ? "저장 중..." : isComplete ? "부분/미확인으로 되돌리기" : "공개 완성으로 표시"}
                  </button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </section>
  );
}