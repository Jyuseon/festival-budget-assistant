import { API_BASE_URL } from "./api";

export type ImportStatusValue = "SUCCESS" | "FAILED";

export interface BatchInfo {
  batchId: number;
  datasetYear: number;
  originalFileName: string;
  fileHash: string;
  status: ImportStatusValue;
  importedAt: string;
  totalRows: number;
  validBudgetRows: number;
  invalidRows: number;
  failureMessage: string | null;
}

export interface AdminDatasetOverviewResponse {
  hasAnyAttempt: boolean;
  latestAttempt: BatchInfo | null;
  hasLiveData: boolean;
  latestSuccess: BatchInfo | null;
}

export interface ReferenceProfileCheck {
  applicable: boolean;
  matches: boolean;
  mismatches: string[];
}

export interface AdminDatasetSummaryResponse {
  available: boolean;
  batch: BatchInfo | null;
  totalRows: number;
  validBudgetRows: number;
  unconfirmedBudgetRows: number;
  noResponseBudgetRows: number;
  zeroBudgetRows: number;
  missingDurationRows: number;
  regionCount: number;
  festivalTypeCount: number;
  venueTypeCount: number;
  referenceProfileCheck: ReferenceProfileCheck | null;
}

export interface CategoryCount {
  code: string;
  displayName: string;
  count: number;
}

export interface BudgetStatistics {
  sampleCount: number;
  meanKrw: number;
  medianKrw: number;
  p25Krw: number;
  p75Krw: number;
  p90Krw: number;
  maxKrw: number;
}

export interface DurationBucket {
  label: string;
  count: number;
}

export interface AdminDatasetDistributionsResponse {
  available: boolean;
  regionCounts: CategoryCount[];
  festivalTypeCounts: CategoryCount[];
  venueTypeCounts: CategoryCount[];
  budgetStatistics: BudgetStatistics;
  durationBuckets: DurationBucket[];
}

export interface IssueItem {
  sourceRowNumber: number | null;
  message: string;
}

export interface AdminDatasetIssuesResponse {
  available: boolean;
  totalWarnings: number;
  truncated: boolean;
  issues: IssueItem[];
}

export interface SampleRow {
  sourceRowNumber: number;
  festivalName: string;
  regionName: string;
  administrativeDistrict: string | null;
  festivalTypeName: string;
  venueName: string | null;
  venueTypeName: string;
  durationDays: number | null;
  durationSource: string | null;
  cycleTypeName: string;
  totalBudgetKrw: number | null;
  budgetStatus: string;
}

export interface AdminDatasetSampleResponse {
  available: boolean;
  loadedColumns: string[];
  excludedColumns: string[];
  personalInfoStatusLabel: string;
  sampleRows: SampleRow[];
}

export type AdminApiResult<T> =
  | { kind: "ok"; data: T }
  | { kind: "disabled" }
  | { kind: "error"; message: string };

async function fetchAdmin<T>(path: string): Promise<AdminApiResult<T>> {
  try {
    const res = await fetch(`${API_BASE_URL}${path}`, { cache: "no-store" });

    // festival.admin-ui.enabled=false면 컨트롤러 빈 자체가 없어 항상 404가 온다.
    if (res.status === 404) {
      return { kind: "disabled" };
    }
    if (!res.ok) {
      return { kind: "error", message: `HTTP ${res.status}` };
    }
    const data = (await res.json()) as T;
    return { kind: "ok", data };
  } catch (err) {
    return {
      kind: "error",
      message: err instanceof Error ? err.message : String(err),
    };
  }
}

export const fetchOverview = () =>
  fetchAdmin<AdminDatasetOverviewResponse>("/api/v1/admin/datasets/latest");

export const fetchSummary = () =>
  fetchAdmin<AdminDatasetSummaryResponse>(
    "/api/v1/admin/datasets/latest/summary",
  );

export const fetchDistributions = () =>
  fetchAdmin<AdminDatasetDistributionsResponse>(
    "/api/v1/admin/datasets/latest/distributions",
  );

export const fetchIssues = () =>
  fetchAdmin<AdminDatasetIssuesResponse>(
    "/api/v1/admin/datasets/latest/issues",
  );

export const fetchSample = () =>
  fetchAdmin<AdminDatasetSampleResponse>(
    "/api/v1/admin/datasets/latest/sample",
  );