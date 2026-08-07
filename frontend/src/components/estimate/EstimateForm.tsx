import type { MetadataResponse } from "@/lib/estimateApi";

export interface EstimateFormValues {
  regionCode: string;
  district: string;
  festivalType: string;
  venueType: string;
  durationDays: number;
}

export function EstimateForm({
  metadata,
  values,
  onChange,
  onSubmit,
  submitting,
}: {
  metadata: MetadataResponse;
  values: EstimateFormValues;
  onChange: (values: EstimateFormValues) => void;
  onSubmit: () => void;
  submitting: boolean;
}) {
  const districtOptions = metadata.districtsByRegion[values.regionCode] ?? [];

  return (
    <form
      className="grid grid-cols-1 gap-4 rounded border border-gray-300 p-4 sm:grid-cols-2 lg:grid-cols-5"
      onSubmit={(e) => {
        e.preventDefault();
        onSubmit();
      }}
    >
      <label className="flex flex-col gap-1 text-sm">
        <span className="text-gray-600">광역지역 *</span>
        <select
          className="rounded border border-gray-300 p-2"
          value={values.regionCode}
          onChange={(e) =>
            onChange({ ...values, regionCode: e.target.value, district: "" })
          }
          required
        >
          {metadata.regions.map((r) => (
            <option key={r.code} value={r.code}>
              {r.name}
            </option>
          ))}
        </select>
      </label>

      <label className="flex flex-col gap-1 text-sm">
        <span className="text-gray-600">시군구 (선택)</span>
        <select
          className="rounded border border-gray-300 p-2"
          value={values.district}
          onChange={(e) => onChange({ ...values, district: e.target.value })}
        >
          <option value="">선택 안 함</option>
          {districtOptions.map((d) => (
            <option key={d} value={d}>
              {d}
            </option>
          ))}
        </select>
      </label>

      <label className="flex flex-col gap-1 text-sm">
        <span className="text-gray-600">축제 유형 *</span>
        <select
          className="rounded border border-gray-300 p-2"
          value={values.festivalType}
          onChange={(e) =>
            onChange({ ...values, festivalType: e.target.value })
          }
          required
        >
          {metadata.festivalTypes.map((t) => (
            <option key={t.code} value={t.code}>
              {t.name}
            </option>
          ))}
        </select>
      </label>

      <label className="flex flex-col gap-1 text-sm">
        <span className="text-gray-600">개최 장소 유형 *</span>
        <select
          className="rounded border border-gray-300 p-2"
          value={values.venueType}
          onChange={(e) => onChange({ ...values, venueType: e.target.value })}
          required
        >
          {metadata.venueTypes.map((v) => (
            <option key={v.code} value={v.code}>
              {v.name}
            </option>
          ))}
        </select>
      </label>

      <label className="flex flex-col gap-1 text-sm">
        <span className="text-gray-600">
          개최 총일수 * (최소 {metadata.duration.minimum}일)
        </span>
        <input
          type="number"
          className="rounded border border-gray-300 p-2"
          min={metadata.duration.minimum}
          max={metadata.duration.maximumRecommendedInput}
          value={values.durationDays}
          onChange={(e) =>
            onChange({ ...values, durationDays: Number(e.target.value) })
          }
          required
        />
      </label>

      <div className="sm:col-span-2 lg:col-span-5">
        <button
          type="submit"
          disabled={submitting}
          className="rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {submitting ? "계산 중..." : "예산 추정하기"}
        </button>
      </div>
    </form>
  );
}