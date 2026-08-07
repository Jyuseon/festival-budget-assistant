import type { ConfidenceBreakdown } from "@/lib/estimateApi";

const COMPONENT_DESCRIPTIONS: {
  key: keyof ConfidenceBreakdown;
  label: string;
  description: string;
}[] = [
  {
    key: "sampleScore",
    label: "표본점수",
    description: "표본 수가 충분한가 (표본수÷25, 최대 1)",
  },
  {
    key: "similarityScore",
    label: "유사도점수",
    description: "선정된 표본이 입력 조건과 실제로 얼마나 비슷한가 (가중평균 similarity)",
  },
  {
    key: "stabilityScore",
    label: "안정성점수",
    description: "표본 예산의 편차가 작은가 (1 − 분산비율, 분산이 크면 0에 가까움)",
  },
  {
    key: "completenessScore",
    label: "완전성점수",
    description: "표본 중 개최기간 값이 실제로 있는 비율",
  },
];

function ConfidenceBreakdownPanel({ breakdown }: { breakdown: ConfidenceBreakdown }) {
  return (
    <div className="mb-3 grid grid-cols-2 gap-2 sm:grid-cols-4">
      {COMPONENT_DESCRIPTIONS.map((c) => (
        <div key={c.key} className="rounded bg-gray-100 p-2">
          <div className="flex items-baseline justify-between">
            <span className="text-xs font-semibold text-gray-700">{c.label}</span>
            <span className="font-mono text-sm font-bold">{breakdown[c.key].toFixed(2)}</span>
          </div>
          <p className="mt-1 text-[11px] leading-tight text-gray-500">{c.description}</p>
        </div>
      ))}
    </div>
  );
}

/**
 * 로컬 개발 모드 전용 계산 상세 패널. calculationTrace/confidenceBreakdown은 백엔드가
 * festival.calculation-trace.enabled=true일 때만 채워서 내려준다(운영 응답에는 아예 없음).
 * 즉 이 컴포넌트는 필드가 존재할 때만 렌더링되므로, 운영 화면에는 자연히 나타나지 않는다.
 */
export function CalculationTracePanel({
  trace,
  confidenceBreakdown,
}: {
  trace: string[];
  confidenceBreakdown: ConfidenceBreakdown | null;
}) {
  if (trace.length === 0 && !confidenceBreakdown) {
    return null;
  }

  return (
    <details className="rounded border border-dashed border-gray-400 p-4 text-sm" open>
      <summary className="cursor-pointer font-semibold text-gray-700">
        계산 상세 (로컬 개발 전용)
      </summary>

      {confidenceBreakdown && <ConfidenceBreakdownPanel breakdown={confidenceBreakdown} />}

      {trace.length > 0 && (
        <ol className="mt-3 list-decimal space-y-1 pl-5 text-gray-600">
          {trace.map((step, idx) => (
            <li key={idx}>{step}</li>
          ))}
        </ol>
      )}
    </details>
  );
}