import type { BudgetEstimateResponse } from "@/lib/estimateApi";
import { formatKrwCompact, formatKrwExact, formatNumber } from "@/lib/budgetFormat";

function ResultCard({
  title,
  valueKrw,
  emphasis,
  subtitle,
}: {
  title: string;
  valueKrw: number;
  emphasis?: boolean;
  subtitle?: string;
}) {
  return (
    <div
      className={`rounded border p-4 ${emphasis ? "border-blue-400 bg-blue-50" : "border-gray-300"}`}
    >
      <div className="text-xs text-gray-500">{title}</div>
      <div
        className={`mt-1 text-2xl font-bold tabular-nums ${emphasis ? "text-blue-800" : ""}`}
        title={formatKrwExact(valueKrw)}
      >
        {formatKrwCompact(valueKrw)}
      </div>
      {subtitle && <div className="mt-1 text-xs text-gray-400">{subtitle}</div>}
    </div>
  );
}

const CONFIDENCE_COLOR: Record<string, string> = {
  HIGH: "text-green-700 bg-green-50 border-green-300",
  MEDIUM: "text-amber-700 bg-amber-50 border-amber-300",
  LOW: "text-red-700 bg-red-50 border-red-300",
};

export function EstimateResultCards({ result }: { result: BudgetEstimateResponse }) {
  const confidenceStyle =
    CONFIDENCE_COLOR[result.confidence.level] ?? "text-gray-700 bg-gray-50 border-gray-300";

  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <ResultCard title="추천 예산" valueKrw={result.recommendedBudgetKrw} emphasis subtitle="예비비 반영" />
        <ResultCard title="통계 추정 예산" valueKrw={result.estimatedBudgetKrw} subtitle="가중 기하평균" />
        <ResultCard title="유사 축제 가중 평균" valueKrw={result.weightedAverageBudgetKrw} />
        <ResultCard
          title="일반 예산 범위 (P25~P75)"
          valueKrw={result.typicalRange.lowKrw}
          subtitle={`~ ${formatKrwCompact(result.typicalRange.highKrw)}`}
        />
      </div>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4 text-sm">
        <div className={`rounded border p-3 ${confidenceStyle}`}>
          <div className="text-xs opacity-70">추정 신뢰도</div>
          <div className="mt-1 font-semibold">
            {result.confidence.label} ({result.confidence.score.toFixed(1)}점)
          </div>
        </div>
        <div className="rounded border border-gray-300 p-3">
          <div className="text-xs text-gray-500">사용 표본 수</div>
          <div className="mt-1 font-semibold">{formatNumber(result.sampleCount)}건</div>
        </div>
        <div className="rounded border border-gray-300 p-3">
          <div className="text-xs text-gray-500">데이터 기준연도</div>
          <div className="mt-1 font-semibold">{result.datasetYear}년</div>
        </div>
        <div className="rounded border border-gray-300 p-3">
          <div className="text-xs text-gray-500">알고리즘 버전</div>
          <div className="mt-1 font-semibold">{result.algorithmVersion}</div>
        </div>
      </div>

      <div className="rounded border border-gray-300 p-3 text-sm">
        <div className="font-semibold text-gray-700">fallback 정보</div>
        <p className="mt-1 text-gray-600">{result.fallbackLabel}</p>
        <p className="mt-1 text-xs text-gray-400">단계 코드: {result.fallbackLevel}</p>
      </div>

      {result.basis.length > 0 && (
        <div className="rounded border border-gray-300 p-3 text-sm">
          <div className="font-semibold text-gray-700">판단 근거</div>
          <ul className="mt-1 list-disc pl-5 text-gray-600">
            {result.basis.map((b) => (
              <li key={b}>{b}</li>
            ))}
          </ul>
        </div>
      )}

      {result.warnings.length > 0 && (
        <div className="rounded border border-amber-300 bg-amber-50 p-3 text-sm text-amber-800">
          <div className="font-semibold">warnings</div>
          <ul className="mt-1 list-disc pl-5">
            {result.warnings.map((w) => (
              <li key={w}>{w}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}