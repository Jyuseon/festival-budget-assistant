import { fetchAdmin, type CategoryCount } from "./adminApi";

/**
 * /admin/multiyear-datasets 전용 API 클라이언트. 기존 2026 전용 /admin/datasets
 * (lib/adminApi.ts)와는 완전히 분리된 백엔드 경로(/api/v1/admin/multiyear-datasets)를 부른다 -
 * production /budget-assistant 경로(lib/estimateApi.ts)와도 무관하다.
 */

export interface MultiYearYearSummary {
  datasetYear: number;
  totalCount: number;
  positiveBudgetCount: number;
  validBudgetCount: number;
  budgetUnitSuspectCount: number;
  missingOrNonPositiveBudgetCount: number;
  durationAvailableCount: number;
  durationAvailableRatePercent: number;
  venueTypeAvailableCount: number;
  venueTypeAvailableRatePercent: number;
  covidAffectedCount: number;
  medianValidBudgetMillion: number;
}

export interface MultiYearSeriesStatus {
  analyzed: boolean;
  distinctSeriesCount: number;
  singletonSeriesCount: number;
  multiYearSeriesCount: number;
}

export interface MultiYearAdminSummaryResponse {
  available: boolean;
  totalRecords: number;
  years: MultiYearYearSummary[];
  seriesStatus: MultiYearSeriesStatus;
}

export interface MultiYearQualityCard {
  totalCount: number;
  validBudgetCount: number;
  budgetUnitSuspectCount: number;
  missingOrNonPositiveBudgetCount: number;
  durationAvailableRatePercent: number;
  venueTypeAvailableRatePercent: number;
}

export interface MultiYearBudgetStatistics {
  sampleCount: number;
  meanMillion: number;
  p25Million: number;
  medianMillion: number;
  p75Million: number;
  p90Million: number;
  p95Million: number;
  maxMillion: number;
}

export interface MultiYearAdminYearDetailResponse {
  year: number;
  available: boolean;
  qualityCard: MultiYearQualityCard;
  budgetStatistics: MultiYearBudgetStatistics;
  covidAffectedYear: boolean;
}

export interface MultiYearAdminDistributionsResponse {
  year: number;
  available: boolean;
  regionCounts: CategoryCount[];
  festivalTypeCounts: CategoryCount[];
  venueTypeDataAvailable: boolean;
  venueTypeCounts: CategoryCount[];
  budgetQualityFlagCounts: CategoryCount[];
  covidAffectedYear: boolean;
}

export interface MultiYearSampleRow {
  year: number;
  region: string;
  district: string | null;
  festivalName: string;
  festivalTypeRaw: string | null;
  festivalType: string | null;
  venueNameRaw: string | null;
  venueType: string | null;
  durationDays: number | null;
  budgetTotalMillion: number | null;
  budgetQualityFlag: string;
}

export interface MultiYearAdminSampleResponse {
  year: number;
  available: boolean;
  totalCountForYear: number;
  limit: number;
  offset: number;
  rows: MultiYearSampleRow[];
}

const BASE = "/api/v1/admin/multiyear-datasets";

export const fetchMultiYearSummary = () =>
  fetchAdmin<MultiYearAdminSummaryResponse>(`${BASE}/summary`);

export const fetchMultiYearYearDetail = (year: number) =>
  fetchAdmin<MultiYearAdminYearDetailResponse>(`${BASE}/years/${year}`);

export const fetchMultiYearDistributions = (year: number) =>
  fetchAdmin<MultiYearAdminDistributionsResponse>(
    `${BASE}/years/${year}/distributions`,
  );

export const fetchMultiYearSample = (
  year: number,
  limit: number,
  offset: number,
) =>
  fetchAdmin<MultiYearAdminSampleResponse>(
    `${BASE}/years/${year}/sample?limit=${limit}&offset=${offset}`,
  );