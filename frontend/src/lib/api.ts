export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export interface HealthResponse {
  status: string;
  service: string;
  timestamp: string;
}

/**
 * 백엔드(Spring Boot) 헬스체크 API 호출.
 * 개발 환경 연동 확인 및 향후 API 클라이언트 작성 시 참고용.
 */
export async function fetchHealth(): Promise<HealthResponse> {
  const res = await fetch(`${API_BASE_URL}/api/health`, {
    cache: "no-store",
  });

  if (!res.ok) {
    throw new Error(`Health check failed: ${res.status}`);
  }

  return res.json();
}