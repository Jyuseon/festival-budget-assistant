/**
 * 2026년 지역축제 개최 계획 원본 파일을 실제로 열어 확인한 기대값.
 * 백엔드 Known2026DatasetProfile과 반드시 값을 맞춰야 한다(중복 정의지만, 화면에서
 * "가이드 기대값" 열을 즉시 보여주기 위해 프론트에도 상수로 둔다).
 *
 * missingDurationRows 참고: R열(총 일수)이 비어 있는 행은 131건이지만, 그중 1건은
 * 시작~종료 날짜가 모두 온전해 날짜로 기간이 계산된다(DataNormalizationService의 fallback).
 * 그래서 최종적으로 durationDays가 null로 남는 행은 130건이 맞다 - 실제 DB로 재검증한 값.
 */
export const KNOWN_2026_PROFILE = {
  datasetYear: 2026,
  totalRows: 1266,
  validBudgetRows: 1238,
  unconfirmedBudgetRows: 22,
  noResponseBudgetRows: 5,
  zeroBudgetRows: 1,
  missingDurationRows: 130,
  missingDurationNote:
    "R열 미기재 131건 중 1건은 날짜로 기간이 계산되어, 최종 미확정은 130건",
  regionCount: 17,
  festivalTypeCount: 5,
  venueTypeCount: 6,
} as const;