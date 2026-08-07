package com.festival.budgetassist.dataset;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * 자동화 테스트용 소형 가상 엑셀을 메모리상에서 생성한다. 개인정보가 전혀 없는 합성 데이터만
 * 사용하며, 어떤 바이너리 파일도 저장소에 커밋하지 않는다(테스트 실행 시 그때그때 생성).
 *
 * <p>실제 원본 파일의 헤더 레이아웃(행 5~8)을 그대로 재현하되, 데이터 행(9행~)은 전부
 * 가상의 값이다.</p>
 *
 * <p>다른 패키지의 테스트(예: admin 패키지 조회 서비스 테스트)에서도 재사용할 수 있도록
 * public으로 둔다. 프로덕션 코드에서는 참조하지 않는다(테스트 소스셋 전용).</p>
 */
public final class TestWorkbookFactory {

    private TestWorkbookFactory() {
    }

    /**
     * 6개 데이터 행으로 다음 경우를 모두 포함하는 정상 구조의 워크북.
     * <ol>
     *   <li>연번1: 예산 확정, 개최기간 R열 그대로(REPORTED), 방문객 전부 숫자</li>
     *   <li>연번2: 예산 미확정, R열 없음 + 날짜 완전 -> 기간 계산(COMPUTED_FROM_DATES), 방문객 전부 '미집계'</li>
     *   <li>연번3: 예산 무응답, 날짜 불완전 -> 기간 UNKNOWN, 방문객 전부 '모름'</li>
     *   <li>연번4: 예산 0원(ZERO), 개최주기 '최초', 방문객 전부 '최초 개최'</li>
     *   <li>연번5: 예산 확정, 기초자치단체 '-' -> null, 최초개최연도 '미상' -> null+note</li>
     *   <li>연번6: 예산 확정, 종료일 &lt; 시작일(날짜 역전) -> 기간 UNKNOWN + 경고, 외국인 방문객 '모름'</li>
     * </ol>
     */
    public static byte[] buildValidFixture() {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("조사표");
            writeHeaderRows(sheet);

            int r = 8; // 0-based row index 8 = 엑셀 9행
            writeDataRow(sheet, r++, RowSpec.builder()
                    .sourceRowNumber(1).festivalName("테스트축제 하나").region("01. 서울")
                    .administrativeDistrict("중구").festivalType("01. 문화예술").venueName("테스트공원")
                    .venueType("02. 녹지형").venueRegion("01. 서울").venueDistrict("중구")
                    .startYear(2026).startMonth(9).startDay(1).endYear(2026).endMonth(9).endDay(3)
                    .durationDays(3).durationNote(null)
                    .cycle("01. 매년").firstHeldYearNumeric(2010)
                    .totalBudgetMillion(150.0).nationalBudgetMillion(50.0).localBudgetMillion(100.0).otherBudgetMillion(0.0)
                    .previousVisitorsNumeric(1000).domesticVisitorsNumeric(900).foreignVisitorsNumeric(100)
                    .measurementMethod("01. 계측")
                    .build());

            writeDataRow(sheet, r++, RowSpec.builder()
                    .sourceRowNumber(2).festivalName("테스트축제 둘").region("02. 부산")
                    .administrativeDistrict("해운대구").festivalType("02. 자연생태").venueName("테스트해변")
                    .venueType("03. 수변형").venueRegion("02. 부산").venueDistrict("해운대구")
                    .startYear(2026).startMonth(5).startDay(1).endYear(2026).endMonth(5).endDay(3)
                    .durationDays(null).durationNote("미정")
                    .cycle("02. 격년").firstHeldYearNumeric(2015)
                    .totalBudgetText("미확정")
                    .previousVisitorsText("미집계").domesticVisitorsText("미집계").foreignVisitorsText("미집계")
                    .measurementMethod("03. 미집계")
                    .build());

            writeDataRow(sheet, r++, RowSpec.builder()
                    .sourceRowNumber(3).festivalName("테스트축제 셋").region("03. 대구")
                    .administrativeDistrict("중구").festivalType("03. 주민화합").venueName("테스트마당")
                    .venueType("04. 독립형").venueRegion("03. 대구").venueDistrict("중구")
                    .startYear(2026).startMonth(6).startDay(1) // 종료일 없음 -> 날짜 불완전
                    .durationDays(null).durationNote(null)
                    .cycle("03. 일회성").firstHeldYearNumeric(2020)
                    .totalBudgetText("무응답")
                    .previousVisitorsText("모름").domesticVisitorsText("모름").foreignVisitorsText("모름")
                    .measurementMethod("99. 무응답")
                    .build());

            writeDataRow(sheet, r++, RowSpec.builder()
                    .sourceRowNumber(4).festivalName("테스트축제 넷").region("04. 인천")
                    .administrativeDistrict("중구").festivalType("04. 전통역사").venueName("테스트터")
                    .venueType("05. 기타").venueRegion("04. 인천").venueDistrict("중구")
                    .startYear(2026).startMonth(10).startDay(1).endYear(2026).endMonth(10).endDay(2)
                    .durationDays(2).durationNote(null)
                    .cycle("04. 최초").firstHeldYearNumeric(2026)
                    .totalBudgetMillion(0.0).nationalBudgetMillion(0.0).localBudgetMillion(0.0).otherBudgetMillion(0.0)
                    .previousVisitorsText("최초 개최").domesticVisitorsText("최초 개최").foreignVisitorsText("최초 개최")
                    .measurementMethod("02. 추정")
                    .build());

            writeDataRow(sheet, r++, RowSpec.builder()
                    .sourceRowNumber(5).festivalName("테스트축제 다섯").region("05. 광주")
                    .administrativeDistrict("-").festivalType("05. 지역특산물").venueName("테스트장터")
                    .venueType("01. 마을형").venueRegion("05. 광주").venueDistrict("동구")
                    .startYear(2026).startMonth(4).startDay(1).endYear(2026).endMonth(4).endDay(5)
                    .durationDays(5).durationNote(null)
                    .cycle("01. 매년").firstHeldYearText("미상")
                    .totalBudgetMillion(76.5).nationalBudgetMillion(20.0).localBudgetMillion(56.5).otherBudgetMillion(0.0)
                    .previousVisitorsNumeric(500).domesticVisitorsNumeric(480).foreignVisitorsNumeric(20)
                    .measurementMethod("04. 기타")
                    .build());

            writeDataRow(sheet, r++, RowSpec.builder()
                    .sourceRowNumber(6).festivalName("테스트축제 여섯").region("06. 대전")
                    .administrativeDistrict("유성구").festivalType("01. 문화예술").venueName("테스트광장")
                    .venueType("06. 미정").venueRegion("06. 대전").venueDistrict("유성구")
                    .startYear(2026).startMonth(8).startDay(10).endYear(2026).endMonth(8).endDay(5) // 종료 < 시작
                    .durationDays(null).durationNote(null)
                    .cycle("01. 매년").firstHeldYearNumeric(2019)
                    .totalBudgetMillion(300.0).nationalBudgetMillion(100.0).localBudgetMillion(200.0).otherBudgetMillion(0.0)
                    .previousVisitorsNumeric(2000).domesticVisitorsNumeric(1950).foreignVisitorsText("모름")
                    .measurementMethod("01. 계측")
                    .build());

            return toBytes(workbook);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 시트 이름이 '조사표'가 아닌 워크북 (구조 검증 실패 테스트용). */
    public static byte[] buildWithWrongSheetName() {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            writeHeaderRows(sheet);
            return toBytes(workbook);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** F5 헤더 텍스트가 예상과 다른 워크북 (헤더 검증 실패 테스트용). */
    public static byte[] buildWithBadHeader() {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("조사표");
            writeHeaderRows(sheet);
            sheet.getRow(4).getCell(ExcelColumns.FESTIVAL_TYPE).setCellValue("Festival Type");
            return toBytes(workbook);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 헤더는 정상이지만 데이터 행 하나의 축제 유형 코드가 인식 불가능한 워크북 (전체 Import 중단 테스트용). */
    public static byte[] buildWithUnrecognizedFestivalType() {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("조사표");
            writeHeaderRows(sheet);
            writeDataRow(sheet, 8, RowSpec.builder()
                    .sourceRowNumber(1).festivalName("이상한축제").region("01. 서울")
                    .festivalType("99. 알수없음").venueName("어딘가")
                    .venueType("02. 녹지형").cycle("01. 매년")
                    .totalBudgetMillion(100.0)
                    .build());
            return toBytes(workbook);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeHeaderRows(Sheet sheet) {
        setCell(sheet, 4, ExcelColumns.SOURCE_ROW_NUMBER, "연번");
        setCell(sheet, 4, ExcelColumns.REGION, "광역자치단체명");
        setCell(sheet, 4, ExcelColumns.FESTIVAL_NAME, "축제명");
        setCell(sheet, 4, ExcelColumns.FESTIVAL_TYPE, "축제 유형");
        setCell(sheet, 5, ExcelColumns.VENUE_TYPE, "축제 유형");
        setCell(sheet, 5, ExcelColumns.DURATION_DAYS, "총\n일수");
        setCell(sheet, 4, ExcelColumns.TOTAL_BUDGET, "예산(백만원)");
        setCell(sheet, 4, ExcelColumns.PREVIOUS_VISITORS, "방문객수(前년)");
    }

    private static void writeDataRow(Sheet sheet, int rowIndex, RowSpec spec) {
        Row row = sheet.createRow(rowIndex);
        setCellIfPresent(row, ExcelColumns.SOURCE_ROW_NUMBER, spec.sourceRowNumber);
        setCell(row, ExcelColumns.FESTIVAL_NAME, spec.festivalName);
        setCell(row, ExcelColumns.REGION, spec.region);
        setCell(row, ExcelColumns.ADMINISTRATIVE_DISTRICT, spec.administrativeDistrict);
        setCell(row, ExcelColumns.FESTIVAL_TYPE, spec.festivalType);
        setCell(row, ExcelColumns.VENUE_NAME, spec.venueName);
        setCell(row, ExcelColumns.VENUE_TYPE, spec.venueType);
        setCell(row, ExcelColumns.VENUE_REGION, spec.venueRegion);
        setCell(row, ExcelColumns.VENUE_DISTRICT, spec.venueDistrict);
        setCellIfPresent(row, ExcelColumns.START_YEAR, spec.startYear);
        setCellIfPresent(row, ExcelColumns.START_MONTH, spec.startMonth);
        setCellIfPresent(row, ExcelColumns.START_DAY, spec.startDay);
        setCellIfPresent(row, ExcelColumns.END_YEAR, spec.endYear);
        setCellIfPresent(row, ExcelColumns.END_MONTH, spec.endMonth);
        setCellIfPresent(row, ExcelColumns.END_DAY, spec.endDay);
        setCellIfPresent(row, ExcelColumns.DURATION_DAYS, spec.durationDays);
        setCell(row, ExcelColumns.DURATION_NOTE, spec.durationNote);
        setCell(row, ExcelColumns.CYCLE, spec.cycle);
        if (spec.firstHeldYearNumeric != null) {
            setCellIfPresent(row, ExcelColumns.FIRST_HELD_YEAR, spec.firstHeldYearNumeric);
        } else if (spec.firstHeldYearText != null) {
            setCell(row, ExcelColumns.FIRST_HELD_YEAR, spec.firstHeldYearText);
        }

        if (spec.totalBudgetText != null) {
            setCell(row, ExcelColumns.TOTAL_BUDGET, spec.totalBudgetText);
        } else if (spec.totalBudgetMillion != null) {
            setCellIfPresent(row, ExcelColumns.TOTAL_BUDGET, spec.totalBudgetMillion);
            setCellIfPresent(row, ExcelColumns.NATIONAL_BUDGET, spec.nationalBudgetMillion);
            setCellIfPresent(row, ExcelColumns.LOCAL_BUDGET, spec.localBudgetMillion);
            setCellIfPresent(row, ExcelColumns.OTHER_BUDGET, spec.otherBudgetMillion);
        }

        writeVisitorField(row, ExcelColumns.PREVIOUS_VISITORS, spec.previousVisitorsNumeric, spec.previousVisitorsText);
        writeVisitorField(row, ExcelColumns.DOMESTIC_VISITORS, spec.domesticVisitorsNumeric, spec.domesticVisitorsText);
        writeVisitorField(row, ExcelColumns.FOREIGN_VISITORS, spec.foreignVisitorsNumeric, spec.foreignVisitorsText);
        setCell(row, ExcelColumns.MEASUREMENT_METHOD, spec.measurementMethod);
    }

    private static void writeVisitorField(Row row, int col, Integer numeric, String text) {
        if (numeric != null) {
            setCellIfPresent(row, col, numeric);
        } else if (text != null) {
            setCell(row, col, text);
        }
    }

    private static void setCell(Sheet sheet, int rowIndex, int colIndex, String value) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }
        setCell(row, colIndex, value);
    }

    private static void setCell(Row row, int colIndex, String value) {
        if (value == null) {
            return;
        }
        Cell cell = row.createCell(colIndex);
        cell.setCellValue(value);
    }

    private static void setCellIfPresent(Row row, int colIndex, Integer value) {
        if (value == null) {
            return;
        }
        row.createCell(colIndex).setCellValue(value);
    }

    private static void setCellIfPresent(Row row, int colIndex, Double value) {
        if (value == null) {
            return;
        }
        row.createCell(colIndex).setCellValue(value);
    }

    private static byte[] toBytes(Workbook workbook) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static final class RowSpec {
        int sourceRowNumber;
        String festivalName;
        String region;
        String administrativeDistrict;
        String festivalType;
        String venueName;
        String venueType;
        String venueRegion;
        String venueDistrict;
        Integer startYear, startMonth, startDay, endYear, endMonth, endDay;
        Integer durationDays;
        String durationNote;
        String cycle;
        Integer firstHeldYearNumeric;
        String firstHeldYearText;
        Double totalBudgetMillion, nationalBudgetMillion, localBudgetMillion, otherBudgetMillion;
        String totalBudgetText;
        Integer previousVisitorsNumeric, domesticVisitorsNumeric, foreignVisitorsNumeric;
        String previousVisitorsText, domesticVisitorsText, foreignVisitorsText;
        String measurementMethod;

        static Builder builder() {
            return new Builder();
        }

        static final class Builder {
            private final RowSpec spec = new RowSpec();

            Builder sourceRowNumber(int v) { spec.sourceRowNumber = v; return this; }
            Builder festivalName(String v) { spec.festivalName = v; return this; }
            Builder region(String v) { spec.region = v; return this; }
            Builder administrativeDistrict(String v) { spec.administrativeDistrict = v; return this; }
            Builder festivalType(String v) { spec.festivalType = v; return this; }
            Builder venueName(String v) { spec.venueName = v; return this; }
            Builder venueType(String v) { spec.venueType = v; return this; }
            Builder venueRegion(String v) { spec.venueRegion = v; return this; }
            Builder venueDistrict(String v) { spec.venueDistrict = v; return this; }
            Builder startYear(int v) { spec.startYear = v; return this; }
            Builder startMonth(int v) { spec.startMonth = v; return this; }
            Builder startDay(int v) { spec.startDay = v; return this; }
            Builder endYear(int v) { spec.endYear = v; return this; }
            Builder endMonth(int v) { spec.endMonth = v; return this; }
            Builder endDay(int v) { spec.endDay = v; return this; }
            Builder durationDays(Integer v) { spec.durationDays = v; return this; }
            Builder durationNote(String v) { spec.durationNote = v; return this; }
            Builder cycle(String v) { spec.cycle = v; return this; }
            Builder firstHeldYearNumeric(int v) { spec.firstHeldYearNumeric = v; return this; }
            Builder firstHeldYearText(String v) { spec.firstHeldYearText = v; return this; }
            Builder totalBudgetMillion(double v) { spec.totalBudgetMillion = v; return this; }
            Builder nationalBudgetMillion(double v) { spec.nationalBudgetMillion = v; return this; }
            Builder localBudgetMillion(double v) { spec.localBudgetMillion = v; return this; }
            Builder otherBudgetMillion(double v) { spec.otherBudgetMillion = v; return this; }
            Builder totalBudgetText(String v) { spec.totalBudgetText = v; return this; }
            Builder previousVisitorsNumeric(int v) { spec.previousVisitorsNumeric = v; return this; }
            Builder domesticVisitorsNumeric(int v) { spec.domesticVisitorsNumeric = v; return this; }
            Builder foreignVisitorsNumeric(int v) { spec.foreignVisitorsNumeric = v; return this; }
            Builder previousVisitorsText(String v) { spec.previousVisitorsText = v; return this; }
            Builder domesticVisitorsText(String v) { spec.domesticVisitorsText = v; return this; }
            Builder foreignVisitorsText(String v) { spec.foreignVisitorsText = v; return this; }
            Builder measurementMethod(String v) { spec.measurementMethod = v; return this; }

            RowSpec build() { return spec; }
        }
    }
}