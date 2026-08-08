"use client";

import { Fragment, useState } from "react";
import type { MultiYearExperimentalEstimateResponse } from "@/lib/multiyearExperimentalApi";
import { formatKrwCompact, formatKrwExact, formatNumber } from "@/lib/budgetFormat";

type MultiYearState =
  | { kind: "idle" }
  | { kind: "loading" }
  | { kind: "error"; message: string }
  | { kind: "ok"; data: MultiYearExperimentalEstimateResponse };

function SettingBadge({ label, on }: { label: string; on: boolean }) {
  return (
    <div className="flex items-center justify-between rounded border border-gray-200 bg-gray-50 px-2 py-1 text-xs">
      <span className="text-gray-500">{label}</span>
      <span className={on ? "font-semibold text-blue-700" : "font-semibold text-gray-400"}>
        {on ? "ON" : "OFF"}
      </span>
    </div>
  );
}

function MiniCard({ title, valueKrw, subtitle }: { title: string; valueKrw: number; subtitle?: string }) {
  return (
    <div className="rounded border border-gray-200 bg-white p-3">
      <div className="text-xs text-gray-500">{title}</div>
      <div className="mt-1 text-lg font-bold tabular-nums" title={formatKrwExact(valueKrw)}>
        {formatKrwCompact(valueKrw)}
      </div>
      {subtitle && <div className="mt-1 text-xs text-gray-400">{subtitle}</div>}
    </div>
  );
}

const POLICY_LABEL: Record<string, string> = {
  HISTORICAL_ONLY: "과거 계획자료만 사용",
  INCLUDE_PUBLISHED_SAME_YEAR: "기획연도 계획자료 포함",
};

/**
 * "다년도 계획예산 분석" 영역. production 결과({@code productionEstimatedBudgetKrw})와는 완전히
 * 분리된 요청 상태({@link MultiYearState})를 갖는다 - 이 영역이 실패해도 production 결과
 * 렌더링에는 전혀 영향을 주지 않는다.
 *
 * <p>핵심값은 "다년도 추정 계획예산"이다 - "실제로 이 축제가 정확히 이 금액을 쓸 것"이라는
 * 뜻이 아니라, "지금까지 공개된 유사 지역축제 개최계획 데이터를 기준으로 봤을 때 이 정도
 * 계획예산으로 기획을 시작하는 것이 현실적"이라는 뜻이다. {@code experimentalRecommendedBudgetKrw}는
 * "실험 기획 추천예산"이라는 낮은 강조도 라벨로만 보여준다.</p>
 */
export function MultiYearExperimentalSection({
  state,
  productionEstimatedBudgetKrw,
}: {
  state: MultiYearState;
  productionEstimatedBudgetKrw: number | null;
}) {
  const [detailOpen, setDetailOpen] = useState(false);
  const [yearBreakdownOpen, setYearBreakdownOpen] = useState(false);
  const [expandedIndex, setExpandedIndex] = useState<number | null>(null);

  return (
    <section className="rounded border-2 border-dashed border-purple-300 bg-purple-50/40 p-4">
      <header className="mb-3">
        <div className="text-xs font-semibold uppercase tracking-wide text-purple-700">
          다년도 계획예산 분석
        </div>
        <h2 className="mt-1 text-lg font-bold text-gray-800">
          {state.kind === "ok" ? `${state.data.targetYear}년 기획 참고자료` : "Multi-Year Planning"}
        </h2>
      </header>

      {state.kind === "loading" && (
        <div className="rounded border border-gray-200 bg-white p-4 text-sm text-gray-500">
          다년도 계획예산 분석 결과를 계산하는 중입니다...
        </div>
      )}

      {state.kind === "error" && (
        <div className="rounded border border-amber-300 bg-amber-50 p-4 text-sm text-amber-800">
          다년도 실험 결과를 불러오지 못했습니다.
          <div className="mt-1 text-xs text-amber-700">{state.message}</div>
        </div>
      )}

      {state.kind === "ok" && (
        <MultiYearExperimentalContent
          data={state.data}
          productionEstimatedBudgetKrw={productionEstimatedBudgetKrw}
          detailOpen={detailOpen}
          onToggleDetail={() => setDetailOpen((v) => !v)}
          yearBreakdownOpen={yearBreakdownOpen}
          onToggleYearBreakdown={() => setYearBreakdownOpen((v) => !v)}
          expandedIndex={expandedIndex}
          onExpandCandidate={setExpandedIndex}
        />
      )}
    </section>
  );
}

function MultiYearExperimentalContent({
  data,
  productionEstimatedBudgetKrw,
  detailOpen,
  onToggleDetail,
  yearBreakdownOpen,
  onToggleYearBreakdown,
  expandedIndex,
  onExpandCandidate,
}: {
  data: MultiYearExperimentalEstimateResponse;
  productionEstimatedBudgetKrw: number | null;
  detailOpen: boolean;
  onToggleDetail: () => void;
  yearBreakdownOpen: boolean;
  onToggleYearBreakdown: () => void;
  expandedIndex: number | null;
  onExpandCandidate: (idx: number | null) => void;
}) {
  const diffPercent =
    productionEstimatedBudgetKrw && productionEstimatedBudgetKrw !== 0
      ? ((data.estimatedBudgetKrw - productionEstimatedBudgetKrw) / productionEstimatedBudgetKrw) * 100
      : null;

  const isPlanningV1 = data.model === "MULTIYEAR_PLANNING_V1";
  const appliedPolicy = data.appliedReferenceDataPolicy;
  const requestedPolicy = data.requestedReferenceDataPolicy;
  const policyDowngraded = isPlanningV1 && requestedPolicy != null && appliedPolicy != null && requestedPolicy !== appliedPolicy;

  if (data.sampleCount === 0) {
    return (
      <div className="rounded border border-gray-200 bg-white p-4 text-sm text-gray-600">
        조건에 맞는 참고 개최계획 데이터 후보가 없어 다년도 계획예산 분석을 계산할 수 없습니다.
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        <SettingBadge label="CPI 보정" on={data.experimentSettings.inflationAdjusted} />
        <SettingBadge label="Series 중복 보정" on={data.experimentSettings.seriesCorrection !== "NONE"} />
        <SettingBadge label="최근성 보정" on={data.experimentSettings.recencyHalfLife !== null} />
        <SettingBadge label="COVID 보정" on={data.experimentSettings.covidAdjustment} />
      </div>

      <div className="grid grid-cols-2 gap-2 text-sm sm:grid-cols-3">
        <div className="rounded border border-gray-200 bg-white p-2">
          <div className="text-xs text-gray-500">기획연도</div>
          <div className="font-semibold">{data.targetYear}년</div>
        </div>
        <div className="rounded border border-gray-200 bg-white p-2">
          <div className="text-xs text-gray-500">참고 계획 데이터</div>
          <div className="font-semibold">{data.trainingYearFrom}~{data.trainingYearTo}년</div>
        </div>
        {isPlanningV1 && appliedPolicy && (
          <div className="rounded border border-gray-200 bg-white p-2">
            <div className="text-xs text-gray-500">적용된 Reference Policy</div>
            <div className="font-semibold">{POLICY_LABEL[appliedPolicy] ?? appliedPolicy}</div>
            {policyDowngraded && (
              <div className="mt-0.5 text-xs text-amber-600">
                요청한 &ldquo;{POLICY_LABEL[requestedPolicy!] ?? requestedPolicy}&rdquo;이(가) 아직 공개 완성이
                아니어서 자동으로 낮춰 적용됐습니다.
              </div>
            )}
          </div>
        )}
      </div>

      <div className="rounded border border-purple-300 bg-white p-4">
        <div className="text-xs text-gray-500">다년도 추정 계획예산</div>
        <div className="mt-1 text-2xl font-bold tabular-nums text-purple-800" title={formatKrwExact(data.estimatedBudgetKrw)}>
          {formatKrwCompact(data.estimatedBudgetKrw)}
        </div>
        {diffPercent !== null && (
          <div className="mt-1 text-xs text-gray-500">
            기존 2026 추정예산 대비 {diffPercent >= 0 ? "+" : ""}
            {diffPercent.toFixed(1)}% (두 방식은 사용하는 데이터 기준연도가 다릅니다 - 어느 쪽이 더
            정확하다는 의미는 아닙니다)
          </div>
        )}
      </div>

      <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
        <MiniCard title="가중 평균" valueKrw={data.weightedAverageBudgetKrw} />
        <MiniCard
          title="일반적인 계획예산 범위 (P25~P75)"
          valueKrw={data.p25Krw}
          subtitle={`~ ${formatKrwCompact(data.p75Krw)}`}
        />
        <div className="rounded border border-gray-200 bg-white p-3">
          <div className="text-xs text-gray-500">실험 기획 추천예산</div>
          <div className="mt-1 text-lg font-bold tabular-nums text-gray-500" title={formatKrwExact(data.experimentalRecommendedBudgetKrw)}>
            {formatKrwCompact(data.experimentalRecommendedBudgetKrw)}
          </div>
          <div className="mt-1 text-xs text-gray-400">예비비 반영 · 참고용, 확정값 아님</div>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-2 text-sm sm:grid-cols-4">
        <div className="rounded border border-gray-200 bg-white p-2">
          <div className="text-xs text-gray-500">표본</div>
          <div className="font-semibold">{formatNumber(data.sampleCount)}건</div>
        </div>
        <div className="rounded border border-gray-200 bg-white p-2">
          <div className="text-xs text-gray-500">반영 데이터 연도 수</div>
          <div className="font-semibold">
            {data.distinctYearsUsed}개년 ({data.earliestSourceYear ?? "-"}~{data.latestSourceYear ?? "-"})
          </div>
        </div>
        <div className="rounded border border-gray-200 bg-white p-2">
          <div className="text-xs text-gray-500">후보 선정 단계</div>
          <div className="font-semibold">{data.fallbackLevel}</div>
        </div>
        <div className="rounded border border-gray-200 bg-white p-2">
          <div className="text-xs text-gray-500">평균 유사도</div>
          <div className="font-semibold">{data.averageSimilarity.toFixed(3)}</div>
        </div>
      </div>

      {data.effectiveYearCount !== null && (
        <div className="rounded border border-gray-200 bg-white p-2 text-sm">
          <span className="text-gray-500">실질 반영 연도</span>{" "}
          <span className="font-semibold">약 {data.effectiveYearCount.toFixed(1)}개년</span>
          <span className="ml-1 text-xs text-gray-400">
            (표본이 {data.distinctYearsUsed}개년에 걸쳐 있어도, 특정 연도에 쏠려 있으면 이 값은
            더 작게 나옵니다)
          </span>
        </div>
      )}

      <div className="rounded border border-amber-200 bg-amber-50 p-3 text-xs text-amber-800">
        이 결과는 {data.trainingYearFrom}~{data.trainingYearTo}년 공개된 지역축제 개최계획
        데이터를 이용한 연구용 추정값입니다. 현재 다년도 모델은 검증 및 개선 중이며 기존 서비스의
        공식 추천 결과를 대체하지 않습니다. 실제 이 축제가 정확히 이 금액을 쓸 것이라는 뜻이
        아니라, 유사 축제들의 계획예산을 기준으로 봤을 때 이 정도가 현실적인 계획예산 출발점이라는
        의미입니다.
        <br />
        소규모 축제의 과대추정 및 대규모 축제의 과소추정 경향이 확인되어 현재 모델을 계속 검증
        중입니다.
      </div>

      <div className="flex flex-wrap gap-3">
        <button
          type="button"
          className="text-xs font-medium text-purple-700 underline"
          onClick={onToggleDetail}
        >
          {detailOpen ? "유사 축제 상세보기 접기" : "유사 축제 상세보기"}
        </button>
        {data.yearWeightBreakdown && data.yearWeightBreakdown.length > 0 && (
          <button
            type="button"
            className="text-xs font-medium text-purple-700 underline"
            onClick={onToggleYearBreakdown}
          >
            {yearBreakdownOpen ? "연도별 반영 비중 접기" : "연도별 반영 비중 보기"}
          </button>
        )}
      </div>

      {yearBreakdownOpen && data.yearWeightBreakdown && (
        <div className="rounded border border-gray-200 bg-white p-3 text-sm">
          {data.yearWeightBreakdown
            .slice()
            .sort((a, b) => b.weightShare - a.weightShare)
            .map((y) => (
              <div key={y.year} className="flex items-center justify-between border-t border-gray-100 py-1 first:border-t-0">
                <span className="text-gray-600">{y.year}년</span>
                <span className="tabular-nums text-gray-500">{y.candidateCount}건</span>
                <span className="tabular-nums font-semibold">{(y.weightShare * 100).toFixed(1)}%</span>
              </div>
            ))}
        </div>
      )}

      {detailOpen && (
        <div className="overflow-x-auto rounded border border-gray-200 bg-white p-3">
          <table className="w-full min-w-[760px] text-sm">
            <thead>
              <tr className="text-left text-gray-500">
                <th className="py-1 pr-2">연도</th>
                <th className="py-1 pr-2">축제명</th>
                <th className="py-1 pr-2">지역</th>
                <th className="py-1 pr-2">유사도</th>
                <th className="py-1 pr-2">예산</th>
                <th className="py-1 pr-2">기간보정예산</th>
                <th className="py-1 pr-2" />
              </tr>
            </thead>
            <tbody>
              {data.topSimilarFestivals.map((f, idx) => {
                const isExpanded = expandedIndex === idx;
                return (
                  <Fragment key={idx}>
                    <tr className="border-t border-gray-100">
                      <td className="py-1 pr-2 tabular-nums text-gray-400">{f.sourceYear}</td>
                      <td className="py-1 pr-2">{f.festivalName}</td>
                      <td className="py-1 pr-2">
                        {f.region}
                        {f.district ? ` ${f.district}` : ""}
                      </td>
                      <td className="py-1 pr-2 tabular-nums">{f.similarity.toFixed(3)}</td>
                      <td className="py-1 pr-2" title={formatKrwExact(f.originalBudgetKrw)}>
                        {formatKrwCompact(f.originalBudgetKrw)}
                      </td>
                      <td className="py-1 pr-2" title={formatKrwExact(f.durationAdjustedBudgetKrw)}>
                        {formatKrwCompact(f.durationAdjustedBudgetKrw)}
                      </td>
                      <td className="py-1 pr-2">
                        <button
                          type="button"
                          className="text-xs text-purple-600 underline"
                          onClick={() => onExpandCandidate(isExpanded ? null : idx)}
                        >
                          {isExpanded ? "접기" : "상세"}
                        </button>
                      </td>
                    </tr>
                    {isExpanded && (
                      <tr className="border-t border-gray-50 bg-gray-50">
                        <td colSpan={7} className="py-2 pr-2">
                          <div className="flex flex-wrap gap-3 text-xs text-gray-600">
                            <span>유형: {f.festivalType}</span>
                            <span>장소유형: {f.venueType ?? "원본 없음"}</span>
                            <span>개최기간: {f.durationDays !== null ? `${f.durationDays}일` : "원본 없음"}</span>
                            <span>선정 단계: {f.fallbackStage ?? "-"}</span>
                            <span>최종 weight: {f.finalWeight.toFixed(3)}</span>
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
      )}
    </div>
  );
}

export type { MultiYearState };