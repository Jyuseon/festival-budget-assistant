"use client";

import { useEffect, useState } from "react";
import {
  fetchOverview,
  fetchSummary,
  fetchDistributions,
  fetchIssues,
  fetchSample,
  type AdminDatasetOverviewResponse,
  type AdminDatasetSummaryResponse,
  type AdminDatasetDistributionsResponse,
  type AdminDatasetIssuesResponse,
  type AdminDatasetSampleResponse,
} from "@/lib/adminApi";
import { formatDateTime } from "@/lib/budgetFormat";
import { MetricCardGrid } from "@/components/admin/MetricCardGrid";
import { DistributionTables } from "@/components/admin/DistributionTables";
import {
  IssuesTable,
  SampleAndColumnCatalog,
} from "@/components/admin/IssuesAndSample";

type LoadState =
  | { phase: "loading" }
  | { phase: "disabled" }
  | { phase: "error"; message: string }
  | {
      phase: "loaded";
      overview: AdminDatasetOverviewResponse;
      summary: AdminDatasetSummaryResponse;
      distributions: AdminDatasetDistributionsResponse;
      issues: AdminDatasetIssuesResponse;
      sample: AdminDatasetSampleResponse;
    };

export default function AdminDatasetsPage() {
  const [state, setState] = useState<LoadState>({ phase: "loading" });

  useEffect(() => {
    let cancelled = false;

    async function load() {
      const [overviewRes, summaryRes, distributionsRes, issuesRes, sampleRes] =
        await Promise.all([
          fetchOverview(),
          fetchSummary(),
          fetchDistributions(),
          fetchIssues(),
          fetchSample(),
        ]);

      if (cancelled) return;

      if (overviewRes.kind === "disabled") {
        setState({ phase: "disabled" });
        return;
      }
      if (overviewRes.kind === "error") {
        setState({ phase: "error", message: overviewRes.message });
        return;
      }
      const rest = [summaryRes, distributionsRes, issuesRes, sampleRes];
      const firstFailure = rest.find((r) => r.kind !== "ok");
      if (firstFailure) {
        setState({
          phase: "error",
          message:
            firstFailure.kind === "error"
              ? firstFailure.message
              : "일부 API 응답을 가져오지 못했습니다.",
        });
        return;
      }

      setState({
        phase: "loaded",
        overview: overviewRes.data,
        summary: (summaryRes as { kind: "ok"; data: AdminDatasetSummaryResponse }).data,
        distributions: (distributionsRes as { kind: "ok"; data: AdminDatasetDistributionsResponse }).data,
        issues: (issuesRes as { kind: "ok"; data: AdminDatasetIssuesResponse }).data,
        sample: (sampleRes as { kind: "ok"; data: AdminDatasetSampleResponse }).data,
      });
    }

    load().catch((err) => {
      if (!cancelled) {
        setState({
          phase: "error",
          message: err instanceof Error ? err.message : String(err),
        });
      }
    });

    return () => {
      cancelled = true;
    };
  }, []);

  if (process.env.NODE_ENV === "production") {
    return (
      <main className="mx-auto max-w-2xl p-8">
        <p className="text-sm text-gray-500">
          관리자 데이터 검증 화면은 로컬 개발 환경에서만 제공됩니다.
        </p>
      </main>
    );
  }

  return (
    <main className="mx-auto flex max-w-5xl flex-col gap-6 p-8 font-sans">
      <header>
        <h1 className="text-2xl font-bold">축제 데이터 Import 검증</h1>
        <p className="mt-1 text-sm text-gray-500">
          /admin/datasets — 실제 Import는 CLI로만 수행하며, 이 화면은 읽기
          전용입니다.
        </p>
      </header>

      {state.phase === "loading" && <LoadingState />}
      {state.phase === "disabled" && <DisabledState />}
      {state.phase === "error" && <ErrorState message={state.message} />}
      {state.phase === "loaded" && <LoadedDashboard state={state} />}
    </main>
  );
}

function LoadingState() {
  return (
    <div className="rounded border border-gray-300 p-6 text-sm text-gray-500">
      데이터를 불러오는 중입니다...
    </div>
  );
}

function DisabledState() {
  return (
    <div className="rounded border border-amber-300 bg-amber-50 p-6 text-sm text-amber-800">
      <p className="font-semibold">관리자 API가 비활성화되어 있습니다.</p>
      <p className="mt-2">
        backend의 <code>application-local.yml</code>에{" "}
        <code>festival.admin-ui.enabled: true</code>가 설정되어 있는지
        확인하세요.
      </p>
    </div>
  );
}

function ErrorState({ message }: { message: string }) {
  return (
    <div className="rounded border border-red-300 bg-red-50 p-6 text-sm text-red-800">
      <p className="font-semibold">API 호출 중 오류가 발생했습니다.</p>
      <p className="mt-2">{message}</p>
      <p className="mt-2 text-red-600">
        backend 서버(포트 8080)가 실행 중인지 확인하세요.
      </p>
    </div>
  );
}

function NoDataYetState() {
  return (
    <div className="rounded border border-gray-300 bg-gray-50 p-6 text-sm text-gray-700">
      <p className="font-semibold">아직 Import된 데이터가 없습니다.</p>
      <p className="mt-2">backend 디렉터리에서 CLI로 Import를 먼저 실행하세요.</p>
      <pre className="mt-3 overflow-x-auto rounded bg-gray-900 p-3 text-xs text-gray-100">
        {`$env:FESTIVAL_EXCEL_PATH = "C:/.../2026년 지역축제 개최 계획 현황(공개용).xlsx"
./mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--import.run=true"`}
      </pre>
    </div>
  );
}

function LoadedDashboard({
  state,
}: {
  state: Extract<LoadState, { phase: "loaded" }>;
}) {
  const { overview, summary, distributions, issues, sample } = state;

  if (!overview.hasAnyAttempt) {
    return <NoDataYetState />;
  }

  return (
    <div className="flex flex-col gap-6">
      <StatusBanner overview={overview} summary={summary} issues={issues} />

      {summary.available && (
        <section>
          <MetricCardGrid summary={summary} />
        </section>
      )}

      {distributions.available && (
        <section>
          <DistributionTables distributions={distributions} />
        </section>
      )}

      {issues.available && (
        <section>
          <IssuesTable issues={issues} />
        </section>
      )}

      {sample.available && (
        <section>
          <SampleAndColumnCatalog sample={sample} />
        </section>
      )}
    </div>
  );
}

function StatusBanner({
  overview,
  summary,
  issues,
}: {
  overview: AdminDatasetOverviewResponse;
  summary: AdminDatasetSummaryResponse;
  issues: AdminDatasetIssuesResponse;
}) {
  const attempt = overview.latestAttempt;
  if (!attempt) {
    return null;
  }
  const isFailed = attempt.status === "FAILED";
  const mismatch =
    summary.referenceProfileCheck?.applicable === true &&
    summary.referenceProfileCheck.matches === false;

  return (
    <div className="flex flex-col gap-3">
      {isFailed && (
        <div className="rounded border border-red-300 bg-red-50 p-4 text-sm text-red-800">
          <p className="font-semibold">
            최근 Import 시도가 실패했습니다 ({formatDateTime(attempt.importedAt)})
          </p>
          <p className="mt-1">{attempt.failureMessage}</p>
          {overview.hasLiveData && overview.latestSuccess && (
            <p className="mt-2 text-red-700">
              단, 기존 데이터는 그대로 유지됩니다 — 현재 서비스 중인 데이터는{" "}
              {formatDateTime(overview.latestSuccess.importedAt)}에 성공한
              배치(batchId={overview.latestSuccess.batchId}) 기준입니다.
            </p>
          )}
        </div>
      )}

      {!isFailed && (
        <div className="rounded border border-green-300 bg-green-50 p-4 text-sm text-green-800">
          <p className="font-semibold">
            최근 Import 성공 ({formatDateTime(attempt.importedAt)})
          </p>
          <p className="mt-1 text-green-700">
            파일: {attempt.originalFileName} · 해시: {attempt.fileHash.slice(0, 16)}
            ... · 기준연도: {attempt.datasetYear}
          </p>
        </div>
      )}

      {mismatch && (
        <div className="rounded border border-amber-300 bg-amber-50 p-4 text-sm text-amber-800">
          <p className="font-semibold">
            현재 데이터가 알려진 기준값과 일치하지 않습니다.
          </p>
          <ul className="mt-1 list-disc pl-4">
            {summary.referenceProfileCheck?.mismatches.map((m) => (
              <li key={m}>{m}</li>
            ))}
          </ul>
        </div>
      )}

      {issues.available && issues.totalWarnings > 0 && (
        <div className="rounded border border-amber-300 bg-amber-50 p-3 text-sm text-amber-800">
          데이터 품질 경고 {issues.totalWarnings}건이 있습니다. 아래 표를
          확인하세요.
        </div>
      )}

      {!overview.hasLiveData && !isFailed && (
        <div className="rounded border border-gray-300 bg-gray-50 p-3 text-sm text-gray-600">
          성공한 Import 이력이 없어 표시할 데이터가 없습니다.
        </div>
      )}
    </div>
  );
}