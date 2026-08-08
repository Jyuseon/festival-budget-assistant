"use client";

import { useEffect, useState } from "react";
import {
  fetchMultiYearSummary,
  fetchMultiYearYearDetail,
  fetchMultiYearDistributions,
  fetchMultiYearSample,
  fetchMultiYearPublicationStatus,
  type MultiYearAdminSummaryResponse,
  type MultiYearAdminYearDetailResponse,
  type MultiYearAdminDistributionsResponse,
  type MultiYearAdminSampleResponse,
  type MultiYearAdminPublicationStatusEntry,
} from "@/lib/multiyearAdminApi";
import { YearFilterBar, type YearFilter } from "@/components/admin-multiyear/YearFilterBar";
import { YearComparisonTable } from "@/components/admin-multiyear/YearComparisonTable";
import { SeriesStatusCard } from "@/components/admin-multiyear/SeriesStatusCard";
import { QualityCards, BudgetStatisticsPanel } from "@/components/admin-multiyear/QualityCards";
import { DistributionPanels } from "@/components/admin-multiyear/DistributionPanels";
import { SampleTable } from "@/components/admin-multiyear/SampleTable";
import { PublicationStatusPanel } from "@/components/admin-multiyear/PublicationStatusPanel";

const SAMPLE_PAGE_SIZE = 20;

type SummaryState =
  | { phase: "loading" }
  | { phase: "disabled" }
  | { phase: "error"; message: string }
  | { phase: "loaded"; data: MultiYearAdminSummaryResponse };

type YearDetailState =
  | { phase: "idle" }
  | { phase: "loading" }
  | { phase: "error"; message: string }
  | {
      phase: "loaded";
      detail: MultiYearAdminYearDetailResponse;
      distributions: MultiYearAdminDistributionsResponse;
    };

type SampleState =
  | { phase: "idle" }
  | { phase: "loading" }
  | { phase: "error"; message: string }
  | { phase: "loaded"; data: MultiYearAdminSampleResponse };

export default function MultiYearDatasetsPage() {
  const [summaryState, setSummaryState] = useState<SummaryState>({ phase: "loading" });
  const [selectedYear, setSelectedYear] = useState<YearFilter>("ALL");
  const [yearDetailState, setYearDetailState] = useState<YearDetailState>({ phase: "idle" });
  const [sampleOffset, setSampleOffset] = useState(0);
  const [sampleState, setSampleState] = useState<SampleState>({ phase: "idle" });
  const [publicationStatusEntries, setPublicationStatusEntries] = useState<MultiYearAdminPublicationStatusEntry[] | null>(null);

  // 최초 1회: 전체 연도 요약 로드
  useEffect(() => {
    let cancelled = false;
    fetchMultiYearSummary().then((res) => {
      if (cancelled) return;
      if (res.kind === "disabled") {
        setSummaryState({ phase: "disabled" });
      } else if (res.kind === "error") {
        setSummaryState({ phase: "error", message: res.message });
      } else {
        setSummaryState({ phase: "loaded", data: res.data });
      }
    });
    return () => {
      cancelled = true;
    };
  }, []);

  // publication status는 요약과 완전히 독립된 요청이다(한쪽 실패가 다른 쪽 렌더링에 영향 없음).
  useEffect(() => {
    let cancelled = false;
    fetchMultiYearPublicationStatus().then((res) => {
      if (cancelled) return;
      if (res.kind === "ok") {
        setPublicationStatusEntries(res.data.years);
      }
    });
    return () => {
      cancelled = true;
    };
  }, []);

  function handlePublicationStatusChanged(updated: MultiYearAdminPublicationStatusEntry) {
    setPublicationStatusEntries((prev) =>
      (prev ?? []).map((e) => (e.datasetYear === updated.datasetYear ? updated : e)),
    );
  }

  // 연도 선택이 바뀌면 상세/분포를 다시 불러온다. "loading"으로의 전환은 선택 이벤트 핸들러
  // (handleSelectYear)에서 이미 동기적으로 해두므로, 이 effect는 순수하게 비동기 fetch와 그
  // 결과 반영만 담당한다(effect 본문에서 동기적으로 setState하지 않음).
  useEffect(() => {
    if (selectedYear === "ALL") {
      return;
    }
    let cancelled = false;

    Promise.all([
      fetchMultiYearYearDetail(selectedYear),
      fetchMultiYearDistributions(selectedYear),
    ]).then(([detailRes, distRes]) => {
      if (cancelled) return;
      if (detailRes.kind !== "ok") {
        setYearDetailState({
          phase: "error",
          message: detailRes.kind === "error" ? detailRes.message : "API가 비활성화되어 있습니다.",
        });
        return;
      }
      if (distRes.kind !== "ok") {
        setYearDetailState({
          phase: "error",
          message: distRes.kind === "error" ? distRes.message : "API가 비활성화되어 있습니다.",
        });
        return;
      }
      setYearDetailState({ phase: "loaded", detail: detailRes.data, distributions: distRes.data });
    });

    return () => {
      cancelled = true;
    };
  }, [selectedYear]);

  // 연도 또는 샘플 페이지가 바뀌면 샘플만 다시 불러온다(상세/분포는 재요청하지 않음).
  // "loading" 전환은 handleSelectYear/handlePrevPage/handleNextPage에서 미리 해둔다.
  useEffect(() => {
    if (selectedYear === "ALL") {
      return;
    }
    let cancelled = false;
    fetchMultiYearSample(selectedYear, SAMPLE_PAGE_SIZE, sampleOffset).then((res) => {
      if (cancelled) return;
      if (res.kind !== "ok") {
        setSampleState({
          phase: "error",
          message: res.kind === "error" ? res.message : "API가 비활성화되어 있습니다.",
        });
        return;
      }
      setSampleState({ phase: "loaded", data: res.data });
    });
    return () => {
      cancelled = true;
    };
  }, [selectedYear, sampleOffset]);

  function handleSelectYear(year: YearFilter) {
    setSelectedYear(year);
    if (year === "ALL") {
      setYearDetailState({ phase: "idle" });
      setSampleState({ phase: "idle" });
      return;
    }
    setYearDetailState({ phase: "loading" });
    setSampleState({ phase: "loading" });
    setSampleOffset(0);
  }

  function handlePrevPage() {
    setSampleState({ phase: "loading" });
    setSampleOffset((o) => Math.max(0, o - SAMPLE_PAGE_SIZE));
  }

  function handleNextPage() {
    setSampleState({ phase: "loading" });
    setSampleOffset((o) => o + SAMPLE_PAGE_SIZE);
  }

  if (process.env.NODE_ENV === "production") {
    return (
      <main className="mx-auto max-w-2xl p-8">
        <p className="text-sm text-gray-500">
          다년도 데이터 검증 화면은 로컬 개발 환경에서만 제공됩니다.
        </p>
      </main>
    );
  }

  return (
    <main className="mx-auto flex max-w-6xl flex-col gap-6 p-8 font-sans">
      <header>
        <h1 className="text-2xl font-bold">다년도(2017~2026) 축제 데이터 검증</h1>
        <p className="mt-1 text-sm text-gray-500">
          /admin/multiyear-datasets — MultiYearFestivalRecord(CSV Import 결과)를 읽기 전용으로
          보여줍니다. 기존 2026 production 예산 계산(/budget-assistant)과는 완전히 분리되어
          있습니다.
        </p>
      </header>

      {summaryState.phase === "loading" && <InfoBox text="데이터를 불러오는 중입니다..." />}
      {summaryState.phase === "disabled" && <DisabledBox />}
      {summaryState.phase === "error" && <ErrorBox message={summaryState.message} />}

      {summaryState.phase === "loaded" && !summaryState.data.available && (
        <NoDataBox />
      )}

      {summaryState.phase === "loaded" && summaryState.data.available && (
        <>
          <YearComparisonTable years={summaryState.data.years} />
          <SeriesStatusCard status={summaryState.data.seriesStatus} />

          {publicationStatusEntries && (
            <PublicationStatusPanel entries={publicationStatusEntries} onChanged={handlePublicationStatusChanged} />
          )}

          <div>
            <h2 className="mb-2 text-sm font-semibold text-gray-700">연도 선택</h2>
            <YearFilterBar selected={selectedYear} onSelect={handleSelectYear} />
          </div>

          {selectedYear !== "ALL" && (
            <YearDetailSection
              year={selectedYear}
              detailState={yearDetailState}
              sampleState={sampleState}
              sampleOffset={sampleOffset}
              onPrevPage={handlePrevPage}
              onNextPage={handleNextPage}
            />
          )}
        </>
      )}
    </main>
  );
}

function YearDetailSection({
  year,
  detailState,
  sampleState,
  onPrevPage,
  onNextPage,
}: {
  year: number;
  detailState: YearDetailState;
  sampleState: SampleState;
  sampleOffset: number;
  onPrevPage: () => void;
  onNextPage: () => void;
}) {
  if (detailState.phase === "loading" || detailState.phase === "idle") {
    return <InfoBox text={`${year}년 데이터를 불러오는 중입니다...`} />;
  }
  if (detailState.phase === "error") {
    return <ErrorBox message={detailState.message} />;
  }
  if (!detailState.detail.available) {
    return (
      <InfoBox text={`${year}년에는 적재된 다년도 데이터가 없습니다.`} tone="warn" />
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <section>
        <h2 className="mb-2 text-sm font-semibold text-gray-700">
          {year}년 데이터 품질 카드
        </h2>
        <QualityCards detail={detailState.detail} />
      </section>

      <section>
        <BudgetStatisticsPanel stats={detailState.detail.budgetStatistics} />
      </section>

      <section>
        <h2 className="mb-2 text-sm font-semibold text-gray-700">{year}년 분포</h2>
        <DistributionPanels distributions={detailState.distributions} />
      </section>

      <section>
        {sampleState.phase === "loaded" ? (
          <SampleTable sample={sampleState.data} onPrevPage={onPrevPage} onNextPage={onNextPage} />
        ) : sampleState.phase === "error" ? (
          <ErrorBox message={sampleState.message} />
        ) : (
          <InfoBox text="샘플 데이터를 불러오는 중입니다..." />
        )}
      </section>
    </div>
  );
}

function InfoBox({ text, tone = "neutral" }: { text: string; tone?: "neutral" | "warn" }) {
  const cls =
    tone === "warn"
      ? "border-amber-300 bg-amber-50 text-amber-800"
      : "border-gray-300 text-gray-500";
  return <div className={`rounded border p-6 text-sm ${cls}`}>{text}</div>;
}

function DisabledBox() {
  return (
    <div className="rounded border border-amber-300 bg-amber-50 p-6 text-sm text-amber-800">
      <p className="font-semibold">관리자 API가 비활성화되어 있습니다.</p>
      <p className="mt-2">
        backend의 <code>application-local.yml</code>에{" "}
        <code>festival.admin-ui.enabled: true</code>가 설정되어 있는지 확인하세요.
      </p>
    </div>
  );
}

function ErrorBox({ message }: { message: string }) {
  return (
    <div className="rounded border border-red-300 bg-red-50 p-6 text-sm text-red-800">
      <p className="font-semibold">API 호출 중 오류가 발생했습니다.</p>
      <p className="mt-2">{message}</p>
      <p className="mt-2 text-red-600">backend 서버(포트 8080)가 실행 중인지 확인하세요.</p>
    </div>
  );
}

function NoDataBox() {
  return (
    <div className="rounded border border-gray-300 bg-gray-50 p-6 text-sm text-gray-700">
      <p className="font-semibold">아직 적재된 다년도 데이터가 없습니다.</p>
      <p className="mt-2">backend 디렉터리에서 CLI로 다년도 CSV Import를 먼저 실행하세요.</p>
      <pre className="mt-3 overflow-x-auto rounded bg-gray-900 p-3 text-xs text-gray-100">
        {`$env:FESTIVAL_MULTIYEAR_CSV_PATH = "C:/.../festival_2017_2026_sanitized.csv"
./mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--import.multiyear-run=true"`}
      </pre>
    </div>
  );
}