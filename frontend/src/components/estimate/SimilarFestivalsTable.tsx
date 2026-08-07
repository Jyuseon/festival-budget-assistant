"use client";

import { Fragment, useState } from "react";
import type { SimilarFestivalDto } from "@/lib/estimateApi";
import { formatKrwCompact, formatKrwExact } from "@/lib/budgetFormat";

function ScoreBadge({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded bg-gray-100 px-2 py-1 text-xs">
      <span className="text-gray-500">{label}</span>{" "}
      <span className="font-mono font-semibold">{value.toFixed(2)}</span>
    </div>
  );
}

export function SimilarFestivalsTable({ festivals }: { festivals: SimilarFestivalDto[] }) {
  const [expandedIndex, setExpandedIndex] = useState<number | null>(null);

  if (festivals.length === 0) {
    return (
      <div className="rounded border border-gray-300 p-4 text-sm text-gray-500">
        참고할 유사 축제가 없습니다.
      </div>
    );
  }

  return (
    <div className="rounded border border-gray-300 p-4">
      <h3 className="mb-2 text-sm font-semibold">
        유사 축제 Top {festivals.length}
      </h3>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[820px] text-sm">
          <thead>
            <tr className="text-left text-gray-500">
              <th className="py-1 pr-2">#</th>
              <th className="py-1 pr-2">축제명</th>
              <th className="py-1 pr-2">지역</th>
              <th className="py-1 pr-2">유형</th>
              <th className="py-1 pr-2">장소 유형</th>
              <th className="py-1 pr-2">기간</th>
              <th className="py-1 pr-2">실제 예산</th>
              <th className="py-1 pr-2">기간 보정 예산</th>
              <th className="py-1 pr-2">유사도</th>
              <th className="py-1 pr-2" />
            </tr>
          </thead>
          <tbody>
            {festivals.map((f, idx) => {
              const isExpanded = expandedIndex === idx;
              return (
                <Fragment key={idx}>
                  <tr className="border-t border-gray-100">
                    <td className="py-1 pr-2 tabular-nums text-gray-400">{idx + 1}</td>
                    <td className="py-1 pr-2">{f.festivalName}</td>
                    <td className="py-1 pr-2">
                      {f.regionName}
                      {f.districtName ? ` ${f.districtName}` : ""}
                    </td>
                    <td className="py-1 pr-2">{f.festivalTypeName}</td>
                    <td className="py-1 pr-2">{f.venueTypeName}</td>
                    <td className="py-1 pr-2">
                      {f.actualDurationDays === null ? "미확정" : `${f.actualDurationDays}일`}
                    </td>
                    <td className="py-1 pr-2" title={formatKrwExact(f.actualBudgetKrw)}>
                      {formatKrwCompact(f.actualBudgetKrw)}
                    </td>
                    <td className="py-1 pr-2" title={formatKrwExact(f.durationAdjustedBudgetKrw)}>
                      {formatKrwCompact(f.durationAdjustedBudgetKrw)}
                    </td>
                    <td className="py-1 pr-2 tabular-nums">{f.similarity.toFixed(3)}</td>
                    <td className="py-1 pr-2">
                      <button
                        type="button"
                        className="text-xs text-blue-600 underline"
                        onClick={() => setExpandedIndex(isExpanded ? null : idx)}
                      >
                        {isExpanded ? "접기" : "상세"}
                      </button>
                    </td>
                  </tr>
                  {isExpanded && (
                    <tr className="border-t border-gray-50 bg-gray-50">
                      <td colSpan={10} className="py-2 pr-2">
                        <div className="flex flex-wrap gap-2">
                          <ScoreBadge label="유형 점수" value={f.festivalTypeScore} />
                          <ScoreBadge label="지역 점수" value={f.regionScore} />
                          <ScoreBadge label="장소 점수" value={f.venueTypeScore} />
                          <ScoreBadge label="기간 점수" value={f.durationScore} />
                          <ScoreBadge label="최종 similarity" value={f.similarity} />
                          <ScoreBadge label="최종 weight" value={f.weight} />
                        </div>
                      </td>
                    </tr>
                  )}
                </Fragment>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}