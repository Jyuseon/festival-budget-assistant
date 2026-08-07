package com.festival.budgetassist.dataset;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.festival.budgetassist.festival.domain.BudgetStatus;
import com.festival.budgetassist.festival.domain.CycleType;
import com.festival.budgetassist.festival.domain.DurationSource;
import com.festival.budgetassist.festival.domain.FestivalRecord;
import com.festival.budgetassist.festival.domain.FestivalType;
import com.festival.budgetassist.festival.domain.Region;
import com.festival.budgetassist.festival.domain.VenueType;
import com.festival.budgetassist.festival.domain.VisitorCountStatus;
import com.festival.budgetassist.festival.domain.VisitorMeasurementMethod;

/**
 * {@link RawFestivalRow} → {@link FestivalRecord} 정규화.
 *
 * <p>책임: 코드 접두사 제거, 지역/축제유형/장소유형/개최주기 enum 변환, 예산 백만원→원 변환,
 * 개최기간 계산(R열 우선 → 날짜 계산 → null), 특수값의 null 처리.</p>
 *
 * <p>지역·축제유형·장소유형·개최주기·축제명은 예산 추정 알고리즘의 핵심 분류 키이므로
 * 인식에 실패하면 이 행을 조용히 버리지 않고 {@link NormalizationResult#errors()}에 담아
 * 반환한다 — 호출부(Importer)가 이를 보고 전체 Import를 중단할지 판단한다.</p>
 *
 * <p>그 외 부수적인 품질 이슈(기간 계산 불가, 예산 합계 불일치, 방문객수 미인식 값 등)는
 * {@link RowWarning}으로 모아 {@link NormalizationResult#warnings()}에 담는다 — Import를
 * 막지는 않지만 관리자 화면(/admin/datasets)의 "데이터 품질 문제가 있는 행"에 표시된다.</p>
 */
@Component
class DataNormalizationService {

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000L);

    NormalizationResult normalize(RawFestivalRow raw, int datasetYear) {
        List<String> errors = new ArrayList<>();
        List<RowWarning> warnings = new ArrayList<>();
        Integer sourceRowNumber = raw.getSourceRowNumber();
        String rowLabel = "행 %d(연번 %d)".formatted(raw.getExcelRowIndex(), sourceRowNumber);

        String festivalName = trimToNull(raw.getFestivalNameRaw());
        if (festivalName == null) {
            errors.add(rowLabel + ": 축제명이 비어 있음");
        }

        Optional<Region> region = Region.fromSourceValue(raw.getRegionRaw());
        if (region.isEmpty()) {
            errors.add(rowLabel + ": 인식할 수 없는 지역 코드 '%s'".formatted(raw.getRegionRaw()));
        }

        Optional<FestivalType> festivalType = FestivalType.fromSourceValue(raw.getFestivalTypeRaw());
        if (festivalType.isEmpty()) {
            errors.add(rowLabel + ": 인식할 수 없는 축제 유형 코드 '%s'".formatted(raw.getFestivalTypeRaw()));
        }

        Optional<VenueType> venueType = VenueType.fromSourceValue(raw.getVenueTypeRaw());
        if (venueType.isEmpty()) {
            errors.add(rowLabel + ": 인식할 수 없는 개최 장소 유형 코드 '%s'".formatted(raw.getVenueTypeRaw()));
        }

        Optional<CycleType> cycleType = CycleType.fromSourceValue(raw.getCycleRaw());
        if (cycleType.isEmpty()) {
            errors.add(rowLabel + ": 인식할 수 없는 개최 주기 코드 '%s'".formatted(raw.getCycleRaw()));
        }

        if (!errors.isEmpty()) {
            return new NormalizationResult(null, errors, warnings);
        }

        DurationCalcResult duration = computeDuration(raw);
        if (duration.warning() != null) {
            warnings.add(new RowWarning(sourceRowNumber, duration.warning()));
        }

        BudgetAmounts budgetAmounts = convertBudget(raw, sourceRowNumber, warnings);

        FestivalRecord.FestivalRecordBuilder builder = FestivalRecord.builder()
                .datasetYear(datasetYear)
                .sourceRowNumber(sourceRowNumber)
                .festivalName(festivalName)
                .region(region.get())
                .regionName(region.get().getDisplayName())
                .administrativeDistrict(normalizeDash(raw.getAdministrativeDistrictRaw()))
                .festivalType(festivalType.get())
                .venueName(trimToNull(raw.getVenueNameRaw()))
                .venueType(venueType.get())
                .venueRegion(stripCodePrefix(raw.getVenueRegionRaw()))
                .venueDistrict(trimToNull(raw.getVenueDistrictRaw()))
                .startYear(raw.getStartYear())
                .startMonth(raw.getStartMonth())
                .startDay(raw.getStartDay())
                .endYear(raw.getEndYear())
                .endMonth(raw.getEndMonth())
                .endDay(raw.getEndDay())
                .durationDays(duration.days())
                .durationSource(duration.source())
                .durationNote(trimToNull(raw.getDurationNoteRaw()))
                .cycleType(cycleType.get())
                .firstHeldYear(raw.getFirstHeldYearNumeric())
                .firstHeldYearNote(raw.getFirstHeldYearNumeric() == null ? trimToNull(raw.getFirstHeldYearTextRaw()) : null)
                .totalBudgetKrw(budgetAmounts.total())
                .nationalBudgetKrw(budgetAmounts.national())
                .localBudgetKrw(budgetAmounts.local())
                .otherBudgetKrw(budgetAmounts.other())
                .budgetStatus(raw.getBudgetStatus());

        VisitorField previous = resolveVisitorField(raw.getPreviousVisitorsNumeric(), raw.getPreviousVisitorsTextRaw(), sourceRowNumber, "전년도 전체 방문객수", warnings);
        VisitorField domestic = resolveVisitorField(raw.getDomesticVisitorsNumeric(), raw.getDomesticVisitorsTextRaw(), sourceRowNumber, "내국인 방문객수", warnings);
        VisitorField foreign = resolveVisitorField(raw.getForeignVisitorsNumeric(), raw.getForeignVisitorsTextRaw(), sourceRowNumber, "외국인 방문객수", warnings);

        builder.previousVisitors(previous.value())
                .previousVisitorsStatus(previous.status())
                .domesticVisitors(domestic.value())
                .domesticVisitorsStatus(domestic.status())
                .foreignVisitors(foreign.value())
                .foreignVisitorsStatus(foreign.status());

        Optional<VisitorMeasurementMethod> measurementMethod = VisitorMeasurementMethod.fromSourceValue(raw.getMeasurementMethodRaw());
        if (measurementMethod.isEmpty() && raw.getMeasurementMethodRaw() != null) {
            warnings.add(new RowWarning(sourceRowNumber, "인식할 수 없는 계측 방법 값 '%s' → null 처리".formatted(raw.getMeasurementMethodRaw())));
        }
        builder.visitorMeasurementMethod(measurementMethod.orElse(null));

        return new NormalizationResult(builder.build(), errors, warnings);
    }

    private DurationCalcResult computeDuration(RawFestivalRow raw) {
        if (raw.getDurationDaysRaw() != null) {
            return new DurationCalcResult(raw.getDurationDaysRaw(), DurationSource.REPORTED, null);
        }

        boolean allDateComponentsPresent = raw.getStartYear() != null && raw.getStartMonth() != null && raw.getStartDay() != null
                && raw.getEndYear() != null && raw.getEndMonth() != null && raw.getEndDay() != null;

        if (!allDateComponentsPresent) {
            return new DurationCalcResult(null, DurationSource.UNKNOWN, null);
        }

        try {
            LocalDate start = LocalDate.of(raw.getStartYear(), raw.getStartMonth(), raw.getStartDay());
            LocalDate end = LocalDate.of(raw.getEndYear(), raw.getEndMonth(), raw.getEndDay());
            long days = ChronoUnit.DAYS.between(start, end) + 1;
            if (days < 1) {
                return new DurationCalcResult(null, DurationSource.UNKNOWN,
                        "종료일이 시작일보다 빠름(%s ~ %s) → 기간 미계산".formatted(start, end));
            }
            return new DurationCalcResult((int) days, DurationSource.COMPUTED_FROM_DATES, null);
        } catch (DateTimeException e) {
            return new DurationCalcResult(null, DurationSource.UNKNOWN,
                    "날짜 성분이 유효하지 않음(%s) → 기간 미계산".formatted(e.getMessage()));
        }
    }

    private BudgetAmounts convertBudget(RawFestivalRow raw, Integer sourceRowNumber, List<RowWarning> warnings) {
        Long total = toWon(raw.getTotalBudgetMillion(), sourceRowNumber, "예산 합계", warnings);
        Long national = toWon(raw.getNationalBudgetMillion(), sourceRowNumber, "국비", warnings);
        Long local = toWon(raw.getLocalBudgetMillion(), sourceRowNumber, "지방비", warnings);
        Long other = toWon(raw.getOtherBudgetMillion(), sourceRowNumber, "기타", warnings);

        if (total != null && national != null && local != null && other != null) {
            long sum = national + local + other;
            if (Math.abs(sum - total) > 1) {
                warnings.add(new RowWarning(sourceRowNumber,
                        "예산 합계 불일치 (합계=%d원, 국비+지방비+기타=%d원)".formatted(total, sum)));
            }
        }
        return new BudgetAmounts(total, national, local, other);
    }

    private Long toWon(BigDecimal million, Integer sourceRowNumber, String fieldName, List<RowWarning> warnings) {
        if (million == null) {
            return null;
        }
        try {
            return million.multiply(MILLION).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException e) {
            warnings.add(new RowWarning(sourceRowNumber, "%s 금액 변환 실패(%s백만원) → null 처리".formatted(fieldName, million)));
            return null;
        }
    }

    private VisitorField resolveVisitorField(Integer numeric, String textRaw, Integer sourceRowNumber, String fieldName, List<RowWarning> warnings) {
        if (numeric != null) {
            return new VisitorField(numeric, null);
        }
        if (textRaw == null) {
            return new VisitorField(null, null);
        }
        Optional<VisitorCountStatus> status = VisitorCountStatus.fromSourceValue(textRaw);
        if (status.isEmpty()) {
            warnings.add(new RowWarning(sourceRowNumber, "인식할 수 없는 %s 값 '%s' → null 처리".formatted(fieldName, textRaw)));
            return new VisitorField(null, null);
        }
        return new VisitorField(null, status.get());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeDash(String value) {
        String trimmed = trimToNull(value);
        return "-".equals(trimmed) ? null : trimmed;
    }

    private String stripCodePrefix(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.replaceFirst("^\\d{1,2}\\.\\s*", "");
    }

    private record DurationCalcResult(Integer days, DurationSource source, String warning) {
    }

    private record BudgetAmounts(Long total, Long national, Long local, Long other) {
    }

    private record VisitorField(Integer value, VisitorCountStatus status) {
    }
}