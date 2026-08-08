package com.festival.budgetassist.multiyear.backtest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;

import com.festival.budgetassist.festival.domain.ImportStatus;
import com.festival.budgetassist.festival.domain.Region;
import com.festival.budgetassist.festival.domain.VenueType;
import com.festival.budgetassist.multiyear.domain.BudgetQualityFlag;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;
import com.festival.budgetassist.multiyear.domain.MultiYearImportBatch;
import com.festival.budgetassist.multiyear.repository.MultiYearFestivalRecordRepository;
import com.festival.budgetassist.multiyear.repository.MultiYearImportBatchRepository;

/** backtest 테스트 전용 fixture 빌더 - {@code FestivalSeriesLinkingServiceTest}의 row() 패턴을 확장한다. */
abstract class MultiYearBacktestTestSupport {

    @Autowired
    protected MultiYearFestivalRecordRepository recordRepository;
    @Autowired
    protected MultiYearImportBatchRepository batchRepository;

    protected MultiYearImportBatch batch;

    protected void initBatch() {
        batch = batchRepository.save(MultiYearImportBatch.builder()
                .originalFileName("backtest-fixture.csv")
                .fileHash(UUID.randomUUID().toString().replace("-", "") + "0000000000000000000000")
                .totalRows(0).unitScaleSuspectRows(0).missingOrNonpositiveBudgetRows(0)
                .missingDurationRows(0).covidAffectedRows(0)
                .importedAt(Instant.now()).status(ImportStatus.SUCCESS)
                .build());
    }

    /** venue/duration 없는 옛 데이터 스타일 record(정상 품질). */
    protected MultiYearFestivalRecord row(int year, int sourceRow, String name, Region region, String district,
                                           String festivalType, long budgetMillion) {
        return row(year, sourceRow, name, region, district, festivalType, null, null, budgetMillion, BudgetQualityFlag.VALID);
    }

    protected MultiYearFestivalRecord row(int year, int sourceRow, String name, Region region, String district,
                                           String festivalType, VenueType venueType, Integer durationDays,
                                           long budgetMillion, BudgetQualityFlag qualityFlag) {
        return recordRepository.save(MultiYearFestivalRecord.builder()
                .datasetYear(year)
                .sourceRowNumber(sourceRow)
                .sourceSheet("test")
                .festivalName(name)
                .regionRaw(region.getDisplayName())
                .regionText(region.getDisplayName())
                .regionCode(region)
                .districtRaw(district)
                .districtText(district)
                .festivalType(festivalType)
                .venueType(venueType)
                .durationDays(durationDays)
                .budgetTotalMillion(BigDecimal.valueOf(budgetMillion))
                .budgetQualityFlag(qualityFlag)
                .covidAffected(false)
                .importBatch(batch)
                .build());
    }
}