package com.festival.budgetassist.estimate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.festival.budgetassist.festival.domain.BudgetStatus;
import com.festival.budgetassist.festival.domain.CycleType;
import com.festival.budgetassist.festival.domain.DatasetImportBatch;
import com.festival.budgetassist.festival.domain.FestivalRecord;
import com.festival.budgetassist.festival.domain.FestivalType;
import com.festival.budgetassist.festival.domain.ImportStatus;
import com.festival.budgetassist.festival.domain.Region;
import com.festival.budgetassist.festival.domain.VenueType;
import com.festival.budgetassist.festival.repository.DatasetImportBatchRepository;
import com.festival.budgetassist.festival.repository.FestivalRecordRepository;

/**
 * BudgetEstimatorService의 배선(fallback -> 유사도 -> 기간보정 -> 통계 -> 신뢰도)이
 * 실제로 맞물려 동작하는지 검증한다. 원본 엑셀 없이 FestivalRecord를 직접 저장한
 * 합성 데이터를 쓴다(개인정보 없음, 소규모).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class BudgetEstimatorServiceTest {

    @Autowired
    private BudgetEstimatorService estimatorService;
    @Autowired
    private FestivalRecordRepository festivalRecordRepository;
    @Autowired
    private DatasetImportBatchRepository datasetImportBatchRepository;

    private DatasetImportBatch saveBatch(int datasetYear) {
        DatasetImportBatch batch = DatasetImportBatch.builder()
                .datasetYear(datasetYear)
                .originalFileName("synthetic-test.xlsx")
                .fileHash("test-hash-" + datasetYear)
                .totalRows(0)
                .validBudgetRows(0)
                .invalidRows(0)
                .importedAt(Instant.now())
                .status(ImportStatus.SUCCESS)
                .build();
        return datasetImportBatchRepository.save(batch);
    }

    private FestivalRecord record(DatasetImportBatch batch, int datasetYear, int sourceRowNumber, Region region, String district,
                                   FestivalType type, VenueType venue, Integer durationDays, long budgetKrw) {
        return FestivalRecord.builder()
                .datasetYear(datasetYear)
                .sourceRowNumber(sourceRowNumber)
                .festivalName("합성축제" + sourceRowNumber)
                .region(region)
                .regionName(region.getDisplayName())
                .administrativeDistrict(district)
                .festivalType(type)
                .venueName("테스트장소")
                .venueType(venue)
                .durationDays(durationDays)
                .cycleType(CycleType.ANNUAL)
                .budgetStatus(BudgetStatus.CONFIRMED)
                .totalBudgetKrw(budgetKrw)
                .importBatch(batch)
                .build();
    }

    @Test
    void estimate_withReasonableSample_returnsInternallyConsistentResult() {
        int year = 8801;
        DatasetImportBatch batch = saveBatch(year);
        festivalRecordRepository.saveAll(List.of(
                record(batch, year, 1, Region.SEOUL, "종로구", FestivalType.CULTURE_ART, VenueType.GREEN, 2, 80_000_000L),
                record(batch, year, 2, Region.SEOUL, "중구", FestivalType.CULTURE_ART, VenueType.GREEN, 3, 100_000_000L),
                record(batch, year, 3, Region.SEOUL, "종로구", FestivalType.CULTURE_ART, VenueType.GREEN, 3, 120_000_000L),
                record(batch, year, 4, Region.SEOUL, "강남구", FestivalType.CULTURE_ART, VenueType.GREEN, 4, 150_000_000L),
                record(batch, year, 5, Region.SEOUL, "마포구", FestivalType.CULTURE_ART, VenueType.GREEN, 5, 200_000_000L)
        ));

        BudgetEstimateRequest request = new BudgetEstimateRequest("SEOUL", null, "CULTURE_ART", "GREEN", 3);
        BudgetEstimateResponse response = estimatorService.estimate(request);

        assertEquals(year, response.datasetYear());
        assertEquals("v1.0.0", response.algorithmVersion());
        assertEquals(5, response.sampleCount());
        assertTrue(response.weightedAverageBudgetKrw() > 0);
        assertTrue(response.estimatedBudgetKrw() > 0);
        assertTrue(response.recommendedBudgetKrw() > 0);
        assertTrue(response.typicalRange().lowKrw() <= response.typicalRange().highKrw());
        assertTrue(response.confidence().score() >= 0 && response.confidence().score() <= 100);
        assertEquals(5, response.similarFestivals().size());
        assertNotNull(response.fallbackLevel());
        assertNotNull(response.fallbackLabel());
        assertFalse(response.basis().isEmpty());
        assertFalse(response.warnings().isEmpty(), "기준연도 안내 문구는 항상 있어야 함");

        // 계산 상세는 테스트 프로필에서 festival.calculation-trace.enabled=true 이므로 채워져야 한다.
        assertNotNull(response.calculationTrace());
        assertFalse(response.calculationTrace().isEmpty());
    }

    @Test
    void estimate_sparseRegion_stillProducesUsableResultViaFallback() {
        int year = 8802;
        DatasetImportBatch batch = saveBatch(year);
        // 세종에는 딱 1건만 있고, 나머지는 전국 각지에 흩어진 동일 유형 데이터.
        festivalRecordRepository.saveAll(List.of(
                record(batch, year, 1, Region.SEJONG, "세종시", FestivalType.CULTURE_ART, VenueType.GREEN, 3, 90_000_000L),
                record(batch, year, 2, Region.SEOUL, "종로구", FestivalType.CULTURE_ART, VenueType.GREEN, 3, 150_000_000L),
                record(batch, year, 3, Region.BUSAN, "해운대구", FestivalType.CULTURE_ART, VenueType.WATERFRONT, 4, 130_000_000L),
                record(batch, year, 4, Region.DAEGU, "중구", FestivalType.CULTURE_ART, VenueType.VILLAGE, 2, 70_000_000L)
        ));

        BudgetEstimateRequest request = new BudgetEstimateRequest("SEJONG", null, "CULTURE_ART", "GREEN", 3);
        BudgetEstimateResponse response = estimatorService.estimate(request);

        assertTrue(response.sampleCount() >= 1);
        assertTrue(response.recommendedBudgetKrw() > 0);
        // 세종 하나만으로는 목표 표본 수를 못 채우므로 1단계(district)보다 넓은 단계까지 갔어야 한다.
        assertTrue(FallbackLevel.valueOf(response.fallbackLevel()).getOrder() >= FallbackLevel.SAME_REGION_TYPE_VENUE.getOrder());
    }

    @Test
    void estimate_noDataImported_throwsIllegalState() {
        BudgetEstimateRequest request = new BudgetEstimateRequest("SEOUL", null, "CULTURE_ART", "GREEN", 3);
        assertThrows(IllegalStateException.class, () -> estimatorService.estimate(request));
    }

    @Test
    void estimate_invalidRegionCode_throwsIllegalArgument() {
        int year = 8803;
        saveBatch(year); // 데이터가 최소 1건이라도 있어야 datasetYear 체크를 통과해서 코드 검증까지 감
        BudgetEstimateRequest request = new BudgetEstimateRequest("ATLANTIS", null, "CULTURE_ART", "GREEN", 3);
        assertThrows(IllegalArgumentException.class, () -> estimatorService.estimate(request));
    }
}