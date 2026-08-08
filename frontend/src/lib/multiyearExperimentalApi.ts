import { API_BASE_URL } from "./api";
import { ApiError, type BudgetEstimateRequestBody } from "./estimateApi";

export interface MultiYearExperimentSettings {
  inflationAdjusted: boolean;
  seriesCorrection: string;
  recencyHalfLife: number | null;
  covidAdjustment: boolean;
}

export interface MultiYearSimilarFestival {
  sourceYear: number;
  festivalName: string;
  region: string;
  district: string | null;
  festivalType: string;
  venueType: string | null;
  durationDays: number | null;
  originalBudgetKrw: number;
  durationAdjustedBudgetKrw: number;
  similarity: number;
  finalWeight: number;
  fallbackStage: string | null;
}

export interface MultiYearYearWeightShare {
  year: number;
  candidateCount: number;
  weightShare: number;
}

/** planningYear를 함께 요청할 때만 의미 있는 정책 - 생략하면 기존 Baseline S0 경로(레거시)로 처리된다. */
export type ReferenceDataPolicy = "HISTORICAL_ONLY" | "INCLUDE_PUBLISHED_SAME_YEAR";

export interface MultiYearExperimentalEstimateResponse {
  model: string;
  targetYear: number;
  trainingYearFrom: number;
  trainingYearTo: number;

  estimatedBudgetKrw: number;
  weightedAverageBudgetKrw: number;
  experimentalRecommendedBudgetKrw: number;
  p25Krw: number;
  p50Krw: number;
  p75Krw: number;

  sampleCount: number;
  distinctYearsUsed: number;
  earliestSourceYear: number | null;
  latestSourceYear: number | null;

  fallbackLevel: string;
  averageSimilarity: number;
  dataQualityV3: number;

  experimentSettings: MultiYearExperimentSettings;
  topSimilarFestivals: MultiYearSimilarFestival[];

  // planningYear를 요청했을 때만 채워진다(레거시 요청이면 전부 null).
  requestedReferenceDataPolicy: ReferenceDataPolicy | null;
  appliedReferenceDataPolicy: ReferenceDataPolicy | null;
  effectiveYearCount: number | null;
  yearWeightBreakdown: MultiYearYearWeightShare[] | null;
}

export interface MultiYearPlanningMetadataResponse {
  availablePlanningYears: number[];
  defaultPlanningYear: number | null;
  publishedPlanCompleteYears: number[];
}

/**
 * production {@code postBudgetEstimate}와 완전히 분리된 다년도 실험 API 호출 - 요청 body 구조는
 * 그대로 재사용한다(같은 화면 입력값을 양쪽에 전달, festivalName은 애초에 없음).
 *
 * @param planningOptions 생략하면 기존 Baseline S0(targetYear=2026 고정, V0) 경로 그대로다 -
 *                         하위호환을 위해 옵셔널이다. planningYear를 지정하면
 *                         MULTIYEAR_PLANNING_V1(CandidateSelectorV1) 경로로 라우팅된다.
 */
export async function postMultiYearExperimentalEstimate(
  body: BudgetEstimateRequestBody,
  planningOptions?: { planningYear: number; referenceDataPolicy: ReferenceDataPolicy },
): Promise<MultiYearExperimentalEstimateResponse> {
  const res = await fetch(`${API_BASE_URL}/api/v1/experimental/multiyear-budget-estimates`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      regionCode: body.regionCode,
      district: body.district,
      festivalType: body.festivalType,
      venueType: body.venueType,
      durationDays: body.durationDays,
      planningYear: planningOptions?.planningYear ?? null,
      referenceDataPolicy: planningOptions?.referenceDataPolicy ?? null,
    }),
    cache: "no-store",
  });

  if (!res.ok) {
    let message = `다년도 실험 추정 요청 실패: HTTP ${res.status}`;
    try {
      const errorBody = (await res.json()) as { message?: string };
      if (errorBody?.message) {
        message = errorBody.message;
      }
    } catch {
      // JSON 파싱 실패 시 기본 메시지 사용
    }
    throw new ApiError(message, res.status);
  }

  return res.json();
}

/** 기획연도 선택지 등 UI 구성용 메타데이터 - 연도를 프론트엔드에 하드코딩하지 않기 위함. */
export async function fetchMultiYearPlanningMetadata(): Promise<MultiYearPlanningMetadataResponse> {
  const res = await fetch(`${API_BASE_URL}/api/v1/experimental/multiyear-planning-metadata`, {
    cache: "no-store",
  });
  if (!res.ok) {
    throw new ApiError(`다년도 계획연도 메타데이터 요청 실패: HTTP ${res.status}`, res.status);
  }
  return res.json();
}