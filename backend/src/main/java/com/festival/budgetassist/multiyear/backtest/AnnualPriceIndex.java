package com.festival.budgetassist.multiyear.backtest;

/**
 * 연도별 전국 headline CPI(소비자물가지수) 1건. inflation adjustment backtest 실험 전용 입력
 * 데이터 - 코드에서 임의로 생성하지 않고 {@code multiyear-annual-cpi.tsv}(classpath 리소스)에서
 * 읽어온다({@link AnnualPriceIndexProvider}). 문화서비스 CPI/지역별 CPI/인건비 지수 등은 아직
 * 다루지 않는다 - headline 연평균 CPI 하나만 쓴다.
 *
 * @param year 연도
 * @param indexValue CPI 지수값(2020=100 기준)
 * @param source 출처(가능하면 원본 URL/발표기관 - report에 그대로 노출된다)
 */
record AnnualPriceIndex(int year, double indexValue, String source) {
}
