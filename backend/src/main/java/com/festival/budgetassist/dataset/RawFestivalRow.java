package com.festival.budgetassist.dataset;

import java.math.BigDecimal;

import com.festival.budgetassist.festival.domain.BudgetStatus;

import lombok.Builder;
import lombok.Value;

/**
 * 엑셀 한 행을 최대한 원본에 가깝게(코드 변환/enum 매핑 없이) 옮겨 담은 중간 표현.
 *
 * <p>{@link FestivalExcelRowMapper}가 만들고, {@link DataNormalizationService}가 이걸 받아
 * {@link com.festival.budgetassist.festival.domain.FestivalRecord}로 정규화한다.</p>
 *
 * <p>AI~AN(담당자·연락처·비고)열은 애초에 이 클래스에 필드 자체가 없다 — 파싱 단계부터
 * 개인정보를 메모리에 올리지 않는다.</p>
 */
@Value
@Builder
public class RawFestivalRow {

    /** 엑셀상의 물리적 행 번호 (오류 메시지용). */
    int excelRowIndex;

    /** B열 연번. null이면 데이터 종료 신호. */
    Integer sourceRowNumber;

    String festivalNameRaw;
    String regionRaw;
    String administrativeDistrictRaw;
    String festivalTypeRaw;
    String venueNameRaw;
    String venueTypeRaw;
    String venueRegionRaw;
    String venueDistrictRaw;

    Integer startYear;
    Integer startMonth;
    Integer startDay;
    Integer endYear;
    Integer endMonth;
    Integer endDay;

    /** R열이 숫자였을 때만 값이 있음. */
    Integer durationDaysRaw;
    String durationNoteRaw;

    String cycleRaw;

    /** U열이 숫자였을 때만 값이 있음("미상" 등 텍스트면 null이고 firstHeldYearTextRaw에 원문이 담김). */
    Integer firstHeldYearNumeric;
    String firstHeldYearTextRaw;

    /** V~Y열: 숫자 셀일 때만 값이 있고, 단위는 백만원(원본 그대로) — 원 단위 변환은 정규화 단계에서 한다. */
    BigDecimal totalBudgetMillion;
    BigDecimal nationalBudgetMillion;
    BigDecimal localBudgetMillion;
    BigDecimal otherBudgetMillion;

    /** V열의 셀 타입(숫자/미확정/무응답)을 보고 RowMapper가 분류한 예산 상태. */
    BudgetStatus budgetStatus;

    Integer previousVisitorsNumeric;
    String previousVisitorsTextRaw;
    Integer domesticVisitorsNumeric;
    String domesticVisitorsTextRaw;
    Integer foreignVisitorsNumeric;
    String foreignVisitorsTextRaw;

    String measurementMethodRaw;
}