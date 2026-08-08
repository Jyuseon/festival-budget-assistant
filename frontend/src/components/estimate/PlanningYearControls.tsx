"use client";

import type { MultiYearPlanningMetadataResponse, ReferenceDataPolicy } from "@/lib/multiyearExperimentalApi";

/**
 * "다년도 계획예산 분석"의 기획연도/참고 데이터 정책 입력 - production 입력 폼(EstimateForm)과는
 * 별도로 다년도 영역에만 속한다. 선택 가능한 연도는 metadata(백엔드가 보유 데이터의 최신 연도
 * 기준으로 계산)에서 그대로 받아 렌더링할 뿐, 프론트엔드에 연도를 하드코딩하지 않는다.
 */
export function PlanningYearControls({
  metadata,
  planningYear,
  referenceDataPolicy,
  onPlanningYearChange,
  onReferenceDataPolicyChange,
}: {
  metadata: MultiYearPlanningMetadataResponse;
  planningYear: number;
  referenceDataPolicy: ReferenceDataPolicy;
  onPlanningYearChange: (year: number) => void;
  onReferenceDataPolicyChange: (policy: ReferenceDataPolicy) => void;
}) {
  const isSameYearPublished = metadata.publishedPlanCompleteYears.includes(planningYear);

  return (
    <section className="rounded border border-purple-200 bg-purple-50/30 p-4">
      <h2 className="text-sm font-semibold text-purple-800">다년도 계획예산 분석 설정</h2>
      <p className="mt-1 text-xs text-gray-500">
        아래 값은 기존 예산 추정(production)에는 영향을 주지 않고, 다년도 계획예산 분석에만
        적용됩니다.
      </p>

      <div className="mt-3 grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div>
          <label className="block text-xs font-medium text-gray-600" htmlFor="planning-year-select">
            축제 기획 연도
          </label>
          <select
            id="planning-year-select"
            value={planningYear}
            onChange={(e) => onPlanningYearChange(Number(e.target.value))}
            className="mt-1 w-full rounded border border-gray-300 px-2 py-1.5 text-sm"
          >
            {metadata.availablePlanningYears.map((year) => (
              <option key={year} value={year}>
                {year}년
              </option>
            ))}
          </select>
        </div>

        <div>
          <span className="block text-xs font-medium text-gray-600">참고 계획 데이터 범위</span>
          <div className="mt-1 flex flex-col gap-2">
            <label className="flex items-start gap-2 text-xs text-gray-700">
              <input
                type="radio"
                name="reference-data-policy"
                className="mt-0.5"
                checked={referenceDataPolicy === "HISTORICAL_ONLY"}
                onChange={() => onReferenceDataPolicyChange("HISTORICAL_ONLY")}
              />
              <span>
                <span className="font-medium">과거 계획자료만 사용</span>
                <br />
                기획연도 이전에 공개된 개최계획 데이터만 참고합니다.
              </span>
            </label>
            <label
              className={`flex items-start gap-2 text-xs ${isSameYearPublished ? "text-gray-700" : "text-gray-400"}`}
            >
              <input
                type="radio"
                name="reference-data-policy"
                className="mt-0.5"
                disabled={!isSameYearPublished}
                checked={referenceDataPolicy === "INCLUDE_PUBLISHED_SAME_YEAR"}
                onChange={() => onReferenceDataPolicyChange("INCLUDE_PUBLISHED_SAME_YEAR")}
              />
              <span>
                <span className="font-medium">기획연도 계획자료 포함</span>
                <br />
                기획연도 전체 개최계획이 이미 공개된 경우 같은 연도의 다른 축제 계획도 함께
                참고합니다.
                {!isSameYearPublished && (
                  <span className="block text-amber-600">
                    {planningYear}년 데이터셋이 아직 공개 완성으로 표시되지 않아 선택할 수
                    없습니다(선택해도 자동으로 과거 계획자료만 사용으로 적용됩니다).
                  </span>
                )}
              </span>
            </label>
          </div>
        </div>
      </div>
    </section>
  );
}