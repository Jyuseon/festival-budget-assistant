import { API_BASE_URL } from "./api";

export interface CodeName {
  code: string;
  name: string;
}

export interface DurationMeta {
  minimum: number;
  maximumRecommendedInput: number;
}

export interface MetadataResponse {
  regions: CodeName[];
  festivalTypes: CodeName[];
  venueTypes: CodeName[];
  districtsByRegion: Record<string, string[]>;
  duration: DurationMeta;
  datasetYear: number;
}

export interface BudgetEstimateRequestBody {
  regionCode: string;
  district: string | null;
  festivalType: string;
  venueType: string;
  durationDays: number;
}

export interface BudgetRange {
  lowKrw: number;
  highKrw: number;
}

export interface ConfidenceInfo {
  score: number;
  level: "HIGH" | "MEDIUM" | "LOW";
  label: string;
}

export interface SimilarFestivalDto {
  festivalName: string;
  regionName: string;
  districtName: string | null;
  festivalTypeName: string;
  venueTypeName: string;
  actualDurationDays: number | null;
  actualBudgetKrw: number | null;
  durationAdjustedBudgetKrw: number;
  festivalTypeScore: number;
  regionScore: number;
  venueTypeScore: number;
  durationScore: number;
  similarity: number;
  weight: number;
}

export interface BudgetEstimateResponse {
  datasetYear: number;
  algorithmVersion: string;
  weightedAverageBudgetKrw: number;
  estimatedBudgetKrw: number;
  recommendedBudgetKrw: number;
  typicalRange: BudgetRange;
  p50Krw: number;
  p60Krw: number;
  sampleCount: number;
  confidence: ConfidenceInfo;
  fallbackLevel: string;
  fallbackLabel: string;
  basis: string[];
  warnings: string[];
  similarFestivals: SimilarFestivalDto[];
  calculationTrace: string[] | null;
  confidenceBreakdown: ConfidenceBreakdown | null;
}

/** confidenceScore를 구성하는 4개 하위 점수(각 0~1). 개발 모드에서만 채워진다. */
export interface ConfidenceBreakdown {
  sampleScore: number;
  similarityScore: number;
  stabilityScore: number;
  completenessScore: number;
}

export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export async function fetchMetadata(): Promise<MetadataResponse> {
  const res = await fetch(`${API_BASE_URL}/api/v1/metadata`, {
    cache: "no-store",
  });
  if (!res.ok) {
    throw new ApiError(`메타데이터 조회 실패: HTTP ${res.status}`, res.status);
  }
  return res.json();
}

export async function postBudgetEstimate(
  body: BudgetEstimateRequestBody,
): Promise<BudgetEstimateResponse> {
  const res = await fetch(`${API_BASE_URL}/api/v1/budget-estimates`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
    cache: "no-store",
  });

  if (!res.ok) {
    let message = `예산 추정 요청 실패: HTTP ${res.status}`;
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