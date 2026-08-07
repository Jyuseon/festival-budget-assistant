"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { fetchHealth, type HealthResponse } from "@/lib/api";

type Status = "checking" | "ok" | "error";

export default function Home() {
  const [status, setStatus] = useState<Status>("checking");
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [errorMessage, setErrorMessage] = useState<string>("");

  useEffect(() => {
    fetchHealth()
      .then((data) => {
        setHealth(data);
        setStatus("ok");
      })
      .catch((err) => {
        setErrorMessage(err instanceof Error ? err.message : String(err));
        setStatus("error");
      });
  }, []);

  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 p-8 font-sans">
      <header>
        <h1 className="text-2xl font-bold">축제 예산 추천 어시스트</h1>
        <p className="mt-1 text-sm text-gray-500">개발 환경 초기 설정</p>
      </header>

      <section className="rounded border border-gray-300 p-4">
        <h2 className="mb-2 text-lg font-semibold">백엔드 연동 상태</h2>

        {status === "checking" && <p>백엔드(Spring Boot) 응답 확인 중...</p>}

        {status === "ok" && health && (
          <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
            <dt className="text-gray-500">status</dt>
            <dd>{health.status}</dd>
            <dt className="text-gray-500">service</dt>
            <dd>{health.service}</dd>
            <dt className="text-gray-500">timestamp</dt>
            <dd>{health.timestamp}</dd>
          </dl>
        )}

        {status === "error" && (
          <p className="text-red-600">
            백엔드 연결 실패: {errorMessage}
            <br />
            backend 서버(포트 8080)가 실행 중인지 확인하세요.
          </p>
        )}
      </section>

      <section className="rounded border border-gray-300 p-4">
        <h2 className="mb-2 text-lg font-semibold">관리자 도구</h2>
        <Link
          href="/admin/datasets"
          className="text-sm text-blue-600 underline underline-offset-2"
        >
          축제 데이터 Import 검증 화면 (/admin/datasets)
        </Link>
      </section>

      <section className="rounded border border-gray-300 p-4">
        <h2 className="mb-2 text-lg font-semibold">예산 판단 어시스트</h2>
        <Link
          href="/budget-assistant"
          className="text-sm text-blue-600 underline underline-offset-2"
        >
          축제 예산 추정 화면 (/budget-assistant)
        </Link>
      </section>
    </main>
  );
}