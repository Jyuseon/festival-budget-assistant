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
}

/**
 * production {@code postBudgetEstimate}와 완전히 분리된 다년도 실험 API 호출 - 요청 body 구조는
 * 그대로 재사용한다(같은 화면 입력값을 양쪽에 전달, festivalName은 애초에 없음).
 */
export async function postMultiYearExperimentalEstimate(
  body: BudgetEstimateRequestBody,
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