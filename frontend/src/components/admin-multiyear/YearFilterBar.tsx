const YEARS = Array.from({ length: 10 }, (_, i) => 2017 + i);

export type YearFilter = "ALL" | number;

export function YearFilterBar({
  selected,
  onSelect,
}: {
  selected: YearFilter;
  onSelect: (year: YearFilter) => void;
}) {
  return (
    <div className="flex flex-wrap gap-2">
      <FilterButton label="전체" active={selected === "ALL"} onClick={() => onSelect("ALL")} />
      {YEARS.map((y) => (
        <FilterButton
          key={y}
          label={String(y)}
          active={selected === y}
          onClick={() => onSelect(y)}
        />
      ))}
    </div>
  );
}

function FilterButton({
  label,
  active,
  onClick,
}: {
  label: string;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`rounded border px-3 py-1 text-sm transition-colors ${
        active
          ? "border-blue-600 bg-blue-600 text-white"
          : "border-gray-300 bg-white text-gray-700 hover:bg-gray-50"
      }`}
    >
      {label}
    </button>
  );
}