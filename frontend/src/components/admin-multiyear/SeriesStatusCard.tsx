import type { MultiYearSeriesStatus } from "@/lib/multiyearAdminApi";
import { formatNumber } from "@/lib/budgetFormat";

export function SeriesStatusCard({ status }: { status: MultiYearSeriesStatus }) {
  if (!status.analyzed) {
    return (
      <div className="rounded border border-gray-300 bg-gray-50 p-3 text-sm text-gray-600">
        <h2 className="mb-1 text-sm font-semibold text-gray-700">festivalSeries 연결 상태</h2>
        분석 전 - festivalSeries 연결(FestivalSeriesLinkingService)이 아직 실행되지 않았거나
        결과가 없습니다.
      </div>
    );
  }

  return (
    <div className="rounded border border-gray-300 p-3">
      <h2 className="mb-2 text-sm font-semibold">festivalSeries 연결 상태</h2>
      <div className="grid grid-cols-3 gap-3 text-sm">
        <div>
          <div className="text-xs text-gray-500">distinct series</div>
          <div className="mt-1 text-xl font-semibold tabular-nums">
            {formatNumber(status.distinctSeriesCount)}
          </div>
        </div>
        <div>
          <div className="text-xs text-gray-500">1년만 존재(singleton)</div>
          <div className="mt-1 text-xl font-semibold tabular-nums">
            {formatNumber(status.singletonSeriesCount)}
          </div>
        </div>
        <div>
          <div className="text-xs text-gray-500">2년 이상 존재</div>
          <div className="mt-1 text-xl font-semibold tabular-nums">
            {formatNumber(status.multiYearSeriesCount)}
          </div>
        </div>
      </div>
    </div>
  );
}