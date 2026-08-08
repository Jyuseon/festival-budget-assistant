package com.festival.budgetassist.multiyear.backtest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.festival.budgetassist.festival.domain.FestivalType;
import com.festival.budgetassist.festival.domain.Region;
import com.festival.budgetassist.festival.domain.VenueType;
import com.festival.budgetassist.multiyear.csv.MultiYearCsvImportService;
import com.festival.budgetassist.multiyear.domain.MultiYearDatasetPublicationStatus;
import com.festival.budgetassist.multiyear.domain.MultiYearDatasetPublicationStatusValue;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;
import com.festival.budgetassist.multiyear.repository.MultiYearDatasetPublicationStatusRepository;
import com.festival.budgetassist.multiyear.repository.MultiYearFestivalRecordRepository;

/**
 * {@link MultiYearBacktestService#estimateForPlanning}을 실제 sanitized CSV(2017~2026)에
 * 돌려 2027 planning / 2026 HISTORICAL_ONLY vs INCLUDE_PUBLISHED_SAME_YEAR 예시를 리포트로
 * 남긴다 (사용자 요청 16절 7~9번 - 실제 데이터 기반 사례).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class MultiYearEstimateForPlanningRealDataAnalysisTest {

    private static final Logger log = LoggerFactory.getLogger(MultiYearEstimateForPlanningRealDataAnalysisTest.class);

    @Autowired
    private MultiYearCsvImportService importService;
    @Autowired
    private MultiYearFestivalRecordRepository recordRepository;
    @Autowired
    private MultiYearBacktestService backtestService;
    @Autowired
    private MultiYearDatasetPublicationStatusRepository publicationStatusRepository;

    @Test
    void realCsv_estimateForPlanningExamples() throws IOException {
        String csvPathValue = System.getenv("FESTIVAL_MULTIYEAR_CSV_PATH");
        Assumptions.assumeTrue(csvPathValue != null && !csvPathValue.isBlank(),
                "FESTIVAL_MULTIYEAR_CSV_PATH 환경변수가 없어 로컬 분석 테스트를 건너뜁니다.");
        Path csvPath = Path.of(csvPathValue);
        Assumptions.assumeTrue(Files.isRegularFile(csvPath),
                "FESTIVAL_MULTIYEAR_CSV_PATH 파일을 찾을 수 없어 건너뜁니다: " + csvPath.toAbsolutePath());

        byte[] bytes = Files.readAllBytes(csvPath);
        importService.importFromBytes(bytes, csvPath.getFileName().toString());
        List<MultiYearFestivalRecord> all = recordRepository.findAll();

        List<String> lines = new ArrayList<>();
        lines.add("================ estimateForPlanning 실데이터 예시 ================");
        lines.add("");

        // 예시 1: 2027 planning, HISTORICAL_ONLY -> 2017~2026 전체(10개년)
        appendExample(lines, "2027 planning / HISTORICAL_ONLY (2017~2026 전체 참고)",
                backtestService.estimateForPlanning(Region.GANGWON, null, Set.of(FestivalType.COMMUNITY), VenueType.OTHER, 10,
                        2027, ReferenceDataPolicy.HISTORICAL_ONLY, all));

        // 예시 2: 2026 planning, HISTORICAL_ONLY -> 2017~2025
        appendExample(lines, "2026 planning / HISTORICAL_ONLY (2017~2025)",
                backtestService.estimateForPlanning(Region.GANGWON, null, Set.of(FestivalType.COMMUNITY), VenueType.OTHER, 10,
                        2026, ReferenceDataPolicy.HISTORICAL_ONLY, all));

        // 예시 3: 2026 planning, INCLUDE_PUBLISHED_SAME_YEAR인데 아직 공개 표시 없음 -> 자동으로 HISTORICAL_ONLY
        appendExample(lines, "2026 planning / INCLUDE_PUBLISHED_SAME_YEAR, 공개상태 미표시 (자동 다운그레이드)",
                backtestService.estimateForPlanning(Region.GANGWON, null, Set.of(FestivalType.COMMUNITY), VenueType.OTHER, 10,
                        2026, ReferenceDataPolicy.INCLUDE_PUBLISHED_SAME_YEAR, all));

        // 예시 4: 2026 dataset을 PUBLISHED_COMPLETE로 표시한 뒤 다시 INCLUDE_PUBLISHED_SAME_YEAR -> 2017~2026
        publicationStatusRepository.save(MultiYearDatasetPublicationStatus.builder()
                .datasetYear(2026).status(MultiYearDatasetPublicationStatusValue.PUBLISHED_COMPLETE).publishedAt(Instant.now())
                .build());
        appendExample(lines, "2026 planning / INCLUDE_PUBLISHED_SAME_YEAR, PUBLISHED_COMPLETE로 표시 후 (2017~2026 포함)",
                backtestService.estimateForPlanning(Region.GANGWON, null, Set.of(FestivalType.COMMUNITY), VenueType.OTHER, 10,
                        2026, ReferenceDataPolicy.INCLUDE_PUBLISHED_SAME_YEAR, all));

        lines.forEach(log::info);
        Path out = Path.of("multiyear-estimateforplanning-examples-report.txt");
        try {
            Files.write(out, lines, StandardCharsets.UTF_8);
            log.info("리포트 파일 저장 완료: {}", out.toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void appendExample(List<String> lines, String label, MultiYearPlanningEstimateResult r) {
        lines.add("---- " + label + " ----");
        lines.add("planningYear=%d requested=%s applied=%s referenceYear=%d~%d".formatted(
                r.planningYear(), r.requestedReferenceDataPolicy(), r.appliedReferenceDataPolicy(),
                r.referenceYearFrom(), r.referenceYearTo()));
        lines.add("estimatedBudgetKrw=%,d weightedAverage=%,d p25=%,d p75=%,d sampleCount=%d".formatted(
                r.estimatedBudgetKrw(), r.weightedAverageBudgetKrw(), r.p25Krw(), r.p75Krw(), r.sampleCount()));
        lines.add("distinctYearsUsed=%d effectiveYearCount=%.2f earliestSourceYear=%s latestSourceYear=%s fallbackLevel=%s".formatted(
                r.distinctYearsUsed(), r.effectiveYearCount(), r.earliestSourceYear(), r.latestSourceYear(), r.fallbackLevel()));
        lines.add("yearWeightBreakdown:");
        r.yearWeightBreakdown().stream()
                .sorted((a, b) -> Double.compare(b.weightShare(), a.weightShare()))
                .forEach(y -> lines.add("  %d -> %d건 / %.1f%%".formatted(y.year(), y.candidateCount(), y.weightShare() * 100)));
        lines.add("");
    }
}