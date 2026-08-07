package com.festival.budgetassist.multiyear.csv;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.festival.budgetassist.festival.domain.Region;
import com.festival.budgetassist.festival.domain.VenueType;
import com.festival.budgetassist.multiyear.domain.BudgetQualityFlag;
import com.festival.budgetassist.multiyear.domain.CsvDurationSource;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;

/**
 * {@link MultiYearCsvRawRow} -&gt; {@link MultiYearFestivalRecord} 정규화.
 *
 * <p>안내서 4장의 명시적 요구사항을 코드로 지킨다:</p>
 * <ul>
 *   <li>venueType이 비어 있으면 null로 남기고 OTHER로 강제 매핑하지 않는다.</li>
 *   <li>durationDays가 비어 있으면 0이나 임의값을 만들지 않고 null로 둔다.</li>
 *   <li>budgetQualityFlag = UNIT_SCALE_SUSPECT 행도 원본 그대로 저장한다(자동 /1000 금지) -
 *       알고리즘 후보 제외 여부는 조회 쿼리(repository) 단에서 처리한다.</li>
 *   <li>festivalType은 복합/OTHER/UNKNOWN이 섞여 있어 원문 그대로 문자열로 저장하고 enum으로
 *       강제하지 않는다.</li>
 * </ul>
 *
 * <p>필수 식별 항목(datasetYear, sourceRow, festivalName, budgetQualityFlag)을 인식하지 못하면
 * 이 행을 조용히 버리지 않고 에러로 모아 반환한다 - 호출부가 전체 Import 중단 여부를 판단한다.
 * 그 외 항목은 파싱 실패 시 경고로 남기고 null 처리한다.</p>
 */
@Component
class MultiYearCsvNormalizationService {

    MultiYearRowNormalizationResult normalize(MultiYearCsvRawRow raw) {
        List<String> errors = new ArrayList<>();
        List<MultiYearRowWarning> warnings = new ArrayList<>();
        String rowLabel = "CSV %d행".formatted(raw.lineNumber());

        Integer datasetYear = parseRequiredInt(raw.get(MultiYearCsvColumns.DATASET_YEAR), rowLabel, "dataset_year", errors);
        Integer sourceRow = parseOptionalInt(raw.get(MultiYearCsvColumns.SOURCE_ROW), null, rowLabel, "source_row", warnings, datasetYear);

        String festivalName = raw.get(MultiYearCsvColumns.FESTIVAL_NAME);
        if (festivalName == null) {
            errors.add(rowLabel + ": festival_name이 비어 있음");
        }

        BudgetQualityFlag budgetQualityFlag = parseBudgetQualityFlag(raw, rowLabel, errors);

        if (!errors.isEmpty()) {
            return new MultiYearRowNormalizationResult(null, errors, warnings);
        }

        CsvDurationSource durationSource = parseDurationSource(raw, rowLabel, datasetYear, sourceRow, warnings);

        MultiYearFestivalRecord.MultiYearFestivalRecordBuilder builder = MultiYearFestivalRecord.builder()
                .datasetYear(datasetYear)
                .sourceSheet(raw.get(MultiYearCsvColumns.SOURCE_SHEET))
                .sourceRowNumber(sourceRow)
                .sourceSha256(raw.get(MultiYearCsvColumns.SOURCE_SHA256))
                .regionRaw(raw.get(MultiYearCsvColumns.REGION_RAW))
                .regionText(raw.get(MultiYearCsvColumns.REGION))
                .regionCode(resolveRegion(raw.get(MultiYearCsvColumns.REGION), raw.get(MultiYearCsvColumns.REGION_RAW)).orElse(null))
                .districtRaw(raw.get(MultiYearCsvColumns.DISTRICT_RAW))
                .districtText(raw.get(MultiYearCsvColumns.DISTRICT))
                .festivalName(festivalName)
                .festivalTypeRaw(raw.get(MultiYearCsvColumns.FESTIVAL_TYPE_RAW))
                .festivalType(raw.get(MultiYearCsvColumns.FESTIVAL_TYPE))
                .venueNameRaw(raw.get(MultiYearCsvColumns.VENUE_RAW))
                .venueTypeRaw(raw.get(MultiYearCsvColumns.VENUE_TYPE_RAW))
                // 비어 있으면 null 그대로 - 절대 OTHER로 강제 매핑하지 않는다.
                .venueType(resolveVenueType(raw.get(MultiYearCsvColumns.VENUE_TYPE), rowLabel, datasetYear, sourceRow, warnings))
                .periodRaw(raw.get(MultiYearCsvColumns.PERIOD_RAW))
                // 비어 있으면 null 그대로 - 0이나 임의값을 만들지 않는다.
                .durationDays(parseOptionalInt(raw.get(MultiYearCsvColumns.DURATION_DAYS), null, rowLabel, "duration_days", warnings, datasetYear))
                .durationSource(durationSource)
                .durationNoteRaw(raw.get(MultiYearCsvColumns.DURATION_NOTE_RAW))
                .cycleRaw(raw.get(MultiYearCsvColumns.CYCLE))
                .eventModeRaw(raw.get(MultiYearCsvColumns.EVENT_MODE))
                .eventStatusRaw(raw.get(MultiYearCsvColumns.EVENT_STATUS))
                .covidAffected(parseBoolean(raw.get(MultiYearCsvColumns.COVID_AFFECTED)))
                .firstHeldYear(parseOptionalInt(raw.get(MultiYearCsvColumns.FIRST_HELD_YEAR), null, rowLabel, "first_held_year", warnings, datasetYear))
                .budgetTotalRaw(raw.get(MultiYearCsvColumns.BUDGET_TOTAL_RAW))
                .budgetTotalMillion(parseOptionalDecimal(raw.get(MultiYearCsvColumns.BUDGET_TOTAL_MILLION), rowLabel, "budget_total_million", warnings, datasetYear, sourceRow))
                .budgetNationalMillion(parseOptionalDecimal(raw.get(MultiYearCsvColumns.BUDGET_NATIONAL_MILLION), rowLabel, "budget_national_million", warnings, datasetYear, sourceRow))
                .budgetLocalMillion(parseOptionalDecimal(raw.get(MultiYearCsvColumns.BUDGET_LOCAL_MILLION), rowLabel, "budget_local_million", warnings, datasetYear, sourceRow))
                .budgetOtherMillion(parseOptionalDecimal(raw.get(MultiYearCsvColumns.BUDGET_OTHER_MILLION), rowLabel, "budget_other_million", warnings, datasetYear, sourceRow))
                .budgetQualityFlag(budgetQualityFlag)
                .budgetQualityNote(raw.get(MultiYearCsvColumns.BUDGET_QUALITY_NOTE))
                .visitorTotalPersons(parseOptionalLong(raw.get(MultiYearCsvColumns.VISITOR_TOTAL_PERSONS), rowLabel, "visitor_total_persons", warnings, datasetYear, sourceRow));

        return new MultiYearRowNormalizationResult(builder.build(), errors, warnings);
    }

    private BudgetQualityFlag parseBudgetQualityFlag(MultiYearCsvRawRow raw, String rowLabel, List<String> errors) {
        String value = raw.get(MultiYearCsvColumns.BUDGET_QUALITY_FLAG);
        if (value == null) {
            errors.add(rowLabel + ": budget_quality_flag가 비어 있음");
            return null;
        }
        try {
            return BudgetQualityFlag.valueOf(value);
        } catch (IllegalArgumentException e) {
            errors.add(rowLabel + ": 인식할 수 없는 budget_quality_flag 값 '%s'".formatted(value));
            return null;
        }
    }

    private CsvDurationSource parseDurationSource(MultiYearCsvRawRow raw, String rowLabel, Integer datasetYear, Integer sourceRow, List<MultiYearRowWarning> warnings) {
        String value = raw.get(MultiYearCsvColumns.DURATION_SOURCE);
        if (value == null) {
            return null;
        }
        try {
            return CsvDurationSource.valueOf(value);
        } catch (IllegalArgumentException e) {
            warnings.add(new MultiYearRowWarning(datasetYear, sourceRow,
                    rowLabel + ": 인식할 수 없는 duration_source 값 '%s' → null 처리".formatted(value)));
            return null;
        }
    }

    /**
     * region(정규화 텍스트) → region_raw → "OO특별자치시/도" 등 접미사가 붙은 표기 순으로 시도한다.
     * 셋 다 실패하면 강제로 추론하지 않고 null로 남긴다(regionRaw/regionText 원문은 그대로 보존됨).
     */
    private Optional<Region> resolveRegion(String regionText, String regionRaw) {
        Optional<Region> byText = Region.fromSourceValue(regionText);
        if (byText.isPresent()) {
            return byText;
        }
        Optional<Region> byRaw = Region.fromSourceValue(regionRaw);
        if (byRaw.isPresent()) {
            return byRaw;
        }
        String candidate = regionText != null ? regionText : regionRaw;
        if (candidate == null) {
            return Optional.empty();
        }
        String trimmed = candidate.trim();
        for (Region region : Region.values()) {
            if (trimmed.startsWith(region.getDisplayName())) {
                return Optional.of(region);
            }
        }
        return Optional.empty();
    }

    private VenueType resolveVenueType(String venueTypeText, String rowLabel, Integer datasetYear, Integer sourceRow, List<MultiYearRowWarning> warnings) {
        if (venueTypeText == null) {
            return null;
        }
        Optional<VenueType> matched = VenueType.fromSourceValue(venueTypeText);
        if (matched.isEmpty()) {
            warnings.add(new MultiYearRowWarning(datasetYear, sourceRow,
                    rowLabel + ": 인식할 수 없는 venue_type 값 '%s' → null 처리".formatted(venueTypeText)));
            return null;
        }
        return matched.get();
    }

    private boolean parseBoolean(String value) {
        return "True".equalsIgnoreCase(value);
    }

    private Integer parseRequiredInt(String value, String rowLabel, String fieldName, List<String> errors) {
        if (value == null) {
            errors.add(rowLabel + ": " + fieldName + "이(가) 비어 있음");
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            errors.add(rowLabel + ": " + fieldName + " 값이 숫자가 아님 '%s'".formatted(value));
            return null;
        }
    }

    private Integer parseOptionalInt(String value, Integer fallback, String rowLabel, String fieldName,
                                      List<MultiYearRowWarning> warnings, Integer datasetYear) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            warnings.add(new MultiYearRowWarning(datasetYear, null,
                    rowLabel + ": " + fieldName + " 값이 숫자가 아님 '%s' → null 처리".formatted(value)));
            return fallback;
        }
    }

    private Long parseOptionalLong(String value, String rowLabel, String fieldName,
                                    List<MultiYearRowWarning> warnings, Integer datasetYear, Integer sourceRow) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            warnings.add(new MultiYearRowWarning(datasetYear, sourceRow,
                    rowLabel + ": " + fieldName + " 값이 숫자가 아님 '%s' → null 처리".formatted(value)));
            return null;
        }
    }

    private BigDecimal parseOptionalDecimal(String value, String rowLabel, String fieldName,
                                             List<MultiYearRowWarning> warnings, Integer datasetYear, Integer sourceRow) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            warnings.add(new MultiYearRowWarning(datasetYear, sourceRow,
                    rowLabel + ": " + fieldName + " 값이 숫자가 아님 '%s' → null 처리".formatted(value)));
            return null;
        }
    }
}