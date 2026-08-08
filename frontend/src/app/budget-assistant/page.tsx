"use client";

import { useEffect, useState } from "react";
import {
  fetchMetadata,
  postBudgetEstimate,
  ApiError,
  type MetadataResponse,
  type BudgetEstimateResponse,
} from "@/lib/estimateApi";
import { postMultiYearExperimentalEstimate } from "@/lib/multiyearExperimentalApi";
import { EstimateForm, type EstimateFormValues } from "@/components/estimate/EstimateForm";
import { EstimateResultCards } from "@/components/estimate/EstimateResultCards";
import { SimilarFestivalsTable } from "@/components/estimate/SimilarFestivalsTable";
import { CalculationTracePanel } from "@/components/estimate/CalculationTracePanel";
import { MultiYearExperimentalSection, type MultiYearState } from "@/components/estimate/MultiYearExperimentalSection";

type MetadataState =
  | { kind: "loading" }
  | { kind: "error"; message: string }
  | { kind: "ok"; data: MetadataResponse };

type EstimateState =
  | { kind: "idle" }
  | { kind: "loading" }
  | { kind: "error"; message: string }
  | { kind: "ok"; data: BudgetEstimateResponse };

export default function BudgetAssistantPage() {
  const [metadataState, setMetadataState] = useState<MetadataState>({ kind: "loading" });
  const [form, setForm] = useState<EstimateFormValues>({
    regionCode: "",
    district: "",
    festivalType: "",
    venueType: "",
    durationDays: 3,
  });
  const [estimateState, setEstimateState] = useState<EstimateState>({ kind: "idle" });
  const [multiYearState, setMultiYearState] = useState<MultiYearState>({ kind: "idle" });

  useEffect(() => {
    let cancelled = false;

    fetchMetadata()
      .then((data) => {
        if (cancelled) return;
        setMetadataState({ kind: "ok", data });
        setForm({
          regionCode: data.regions[0]?.code ?? "",
          district: "",
          festivalType: data.festivalTypes[0]?.code ?? "",
          venueType: data.venueTypes[0]?.code ?? "",
          durationDays: Math.max(data.duration.minimum, 3),
        });
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        setMetadataState({
          kind: "error",
          message: err instanceof Error ? err.message : String(err),
        });
      });

    return () => {
      cancelled = true;
    };
  }, []);

  async function handleSubmit() {
    const requestBody = {
      regionCode: form.regionCode,
      district: form.district || null,
      festivalType: form.festivalType,
      venueType: form.venueType,
      durationDays: Number(form.durationDays),
    };

    // production과 다년도 실험 두 요청은 서로 강하게 결합하지 않는다(지시사항 16절) - 각자
    // 독립된 상태를 가지므로 한쪽이 실패해도 다른 쪽 렌더링에는 전혀 영향이 없다.
    setEstimateState({ kind: "loading" });
    setMultiYearState({ kind: "loading" });

    void postBudgetEstimate(requestBody)
      .then((data) => setEstimateState({ kind: "ok", data }))
      .catch((err: unknown) => {
        const message =
          err instanceof ApiError ? err.message : err instanceof Error ? err.message : String(err);
        setEstimateState({ kind: "error", message });
      });

    void postMultiYearExperimentalEstimate(requestBody)
      .then((data) => setMultiYearState({ kind: "ok", data }))
      .catch((err: unknown) => {
        const message =
          err instanceof ApiError ? err.message : err instanceof Error ? err.message : String(err);
        setMultiYearState({ kind: "error", message });
      });
  }

  return (
    <main className="mx-auto flex max-w-5xl flex-col gap-6 p-8 font-sans">
      <header>
        <h1 className="text-2xl font-bold">축제 예산 판단 어시스트</h1>
        <p className="mt-1 text-sm text-gray-500">
          /budget-assistant — 조건을 입력하면 유사 축제 데이터 기반 참고 예산을 계산합니다.
        </p>
      </header>

      {metadataState.kind === "loading" && (
        <div className="rounded border border-gray-300 p-6 text-sm text-gray-500">
          메타데이터를 불러오는 중입니다...
        </div>
      )}

      {metadataState.kind === "error" && (
        <div className="rounded border border-red-300 bg-red-50 p-6 text-sm text-red-800">
          <p className="font-semibold">메타데이터를 불러오지 못했습니다.</p>
          <p className="mt-2">{metadataState.message}</p>
          <p className="mt-2 text-red-600">backend 서버(포트 8080)가 실행 중인지 확인하세요.</p>
        </div>
      )}

      {metadataState.kind === "ok" && metadataState.data.datasetYear === 0 && (
        <div className="rounded border border-gray-300 bg-gray-50 p-6 text-sm text-gray-700">
          <p className="font-semibold">아직 Import된 데이터가 없습니다.</p>
          <p className="mt-2">
            backend 디렉터리에서 CLI로 Import를 먼저 실행한 뒤 이 페이지를 새로고침하세요.
          </p>
        </div>
      )}

      {metadataState.kind === "ok" && metadataState.data.datasetYear > 0 && (
        <>
          <EstimateForm
            metadata={metadataState.data}
            values={form}
            onChange={setForm}
            onSubmit={handleSubmit}
            submitting={estimateState.kind === "loading"}
          />

          {estimateState.kind !== "idle" && (
            <div className="flex items-center gap-3 text-xs font-semibold text-gray-400">
              <span className="h-px flex-1 bg-gray-300" />
              <span>기존 2026 기준 · 현재 서비스 계산</span>
              <span className="h-px flex-1 bg-gray-300" />
            </div>
          )}

          {estimateState.kind === "loading" && (
            <div className="rounded border border-gray-300 p-6 text-sm text-gray-500">
              계산 중입니다...
            </div>
          )}

          {estimateState.kind === "error" && (
            <div className="rounded border border-red-300 bg-red-50 p-6 text-sm text-red-800">
              <p className="font-semibold">예산 추정에 실패했습니다.</p>
              <p className="mt-2">{estimateState.message}</p>
            </div>
          )}

          {estimateState.kind === "ok" && (
            <div className="flex flex-col gap-4">
              <EstimateResultCards result={estimateState.data} />
              <SimilarFestivalsTable festivals={estimateState.data.similarFestivals} />
              {(estimateState.data.calculationTrace || estimateState.data.confidenceBreakdown) && (
                <CalculationTracePanel
                  trace={estimateState.data.calculationTrace ?? []}
                  confidenceBreakdown={estimateState.data.confidenceBreakdown}
                />
              )}
            </div>
          )}

          {multiYearState.kind !== "idle" && (
            <>
              <div className="mt-2 flex items-center gap-3 text-xs font-semibold text-purple-400">
                <span className="h-px flex-1 bg-purple-200" />
                <span>다년도 실험 분석 · 2017~2025 → 2026</span>
                <span className="h-px flex-1 bg-purple-200" />
              </div>
              <MultiYearExperimentalSection
                state={multiYearState}
                productionEstimatedBudgetKrw={
                  estimateState.kind === "ok" ? estimateState.data.estimatedBudgetKrw : null
                }
              />
            </>
          )}
        </>
      )}
    </main>
  );
}