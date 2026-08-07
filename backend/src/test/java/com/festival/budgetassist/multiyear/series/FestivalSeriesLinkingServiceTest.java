package com.festival.budgetassist.multiyear.series;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.festival.budgetassist.festival.domain.ImportStatus;
import com.festival.budgetassist.festival.domain.Region;
import com.festival.budgetassist.multiyear.domain.BudgetQualityFlag;
import com.festival.budgetassist.multiyear.domain.FestivalSeries;
import com.festival.budgetassist.multiyear.domain.FestivalSeriesMatchStatus;
import com.festival.budgetassist.multiyear.domain.FestivalSeriesMembership;
import com.festival.budgetassist.multiyear.domain.MatchMethod;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;
import com.festival.budgetassist.multiyear.domain.MultiYearImportBatch;
import com.festival.budgetassist.multiyear.domain.SeriesScope;
import com.festival.budgetassist.multiyear.repository.FestivalSeriesMembershipRepository;
import com.festival.budgetassist.multiyear.repository.FestivalSeriesRepository;
import com.festival.budgetassist.multiyear.repository.MultiYearFestivalRecordRepository;
import com.festival.budgetassist.multiyear.repository.MultiYearImportBatchRepository;

/**
 * 소형 가상 fixture로 검증하는 festivalSeries 연결 테스트. 실제 10,198행 CSV는 필요 없다.
 *
 * <p>실제 CSV 전체로 산출하는 통계는 {@code FestivalSeriesLinkingRealDataAnalysisTest}(로컬
 * 전용)에서 별도로 다룬다.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class FestivalSeriesLinkingServiceTest {

    @Autowired
    private FestivalSeriesLinkingService linkingService;
    @Autowired
    private MultiYearFestivalRecordRepository recordRepository;
    @Autowired
    private MultiYearImportBatchRepository batchRepository;
    @Autowired
    private FestivalSeriesRepository seriesRepository;
    @Autowired
    private FestivalSeriesMembershipRepository membershipRepository;

    private MultiYearImportBatch batch;

    @BeforeEach
    void setUp() {
        batch = batchRepository.save(MultiYearImportBatch.builder()
                .originalFileName("fixture.csv")
                .fileHash(UUID.randomUUID().toString().replace("-", "") + "0000000000000000000000")
                .totalRows(0).unitScaleSuspectRows(0).missingOrNonpositiveBudgetRows(0)
                .missingDurationRows(0).covidAffectedRows(0)
                .importedAt(Instant.now()).status(ImportStatus.SUCCESS)
                .build());
    }

    private MultiYearFestivalRecord row(int year, int sourceRow, String name, String regionText, Region regionCode,
                                         String district, String type) {
        return recordRepository.save(MultiYearFestivalRecord.builder()
                .datasetYear(year)
                .sourceRowNumber(sourceRow)
                .sourceSheet("test")
                .festivalName(name)
                .regionRaw(regionText)
                .regionText(regionText)
                .regionCode(regionCode)
                .districtRaw(district)
                .districtText(district)
                .festivalType(type)
                .budgetQualityFlag(BudgetQualityFlag.VALID)
                .covidAffected(false)
                .importBatch(batch)
                .build());
    }

    private FestivalSeriesMembership membershipOf(long recordId) {
        return membershipRepository.findAll().stream()
                .filter(m -> m.getFestivalRecord().getId() == recordId)
                .findFirst()
                .orElseThrow();
    }

    // ------------------------------------------------------------------
    // 1) 결정적 클러스터링: EXACT / NORMALIZED_EXACT
    // ------------------------------------------------------------------

    @Test
    void sameNameRegionDistrict_groupsIntoOneDeterministicSeries() {
        MultiYearFestivalRecord r2017 = row(2017, 10, "제1회 자라섬재즈페스티벌", "경기", Region.GYEONGGI, "가평군", "CULTURE_ART");
        MultiYearFestivalRecord r2018 = row(2018, 11, "제2회 자라섬재즈페스티벌", "경기", Region.GYEONGGI, "가평군", "CULTURE_ART");
        MultiYearFestivalRecord r2019 = row(2019, 12, "자라섬재즈페스티벌", "경기", Region.GYEONGGI, "가평군", "CULTURE_ART");

        FestivalSeriesLinkingReport report = linkingService.linkAll();

        assertEquals(1, seriesRepository.count());
        FestivalSeries series = seriesRepository.findAll().get(0);
        assertEquals("자라섬재즈페스티벌", series.getCanonicalName());
        assertEquals(3, series.getRecordCount());
        assertEquals(FestivalSeriesMatchStatus.DETERMINISTIC, series.getMatchStatus());
        assertEquals(SeriesScope.DISTRICT_LEVEL, series.getScope());

        // 가장 먼저(2017년) 관측된 원문이 대표(modal) 이름이므로 EXACT, 나머지는 NORMALIZED_EXACT.
        assertEquals(MatchMethod.EXACT, membershipOf(r2017.getId()).getMatchMethod());
        assertEquals(MatchMethod.NORMALIZED_EXACT, membershipOf(r2018.getId()).getMatchMethod());
        assertEquals(MatchMethod.NORMALIZED_EXACT, membershipOf(r2019.getId()).getMatchMethod());

        assertEquals(1, report.distinctSeriesCount());
        assertEquals(1, report.seriesWith2PlusYears());
        assertEquals(3, report.maxConsecutiveObservedYears());
    }

    // ------------------------------------------------------------------
    // 2) region-level vs district-level 분리
    // ------------------------------------------------------------------

    @Test
    void regionLevelRowsNeverAutoMergeWithDistrictLevelRowsEvenIfNameAndRegionMatch() {
        row(2017, 1, "경기futurefestival", "경기", Region.GYEONGGI, null, "CULTURE_ART");
        row(2018, 2, "경기futurefestival", "경기", Region.GYEONGGI, null, "CULTURE_ART");
        row(2019, 3, "경기futurefestival", "경기", Region.GYEONGGI, "수원시", "CULTURE_ART");

        linkingService.linkAll();

        assertEquals(2, seriesRepository.count(), "district 없는 2건과 district 있는 1건은 절대 하나로 묶이면 안 됨");
        List<FestivalSeries> all = seriesRepository.findAll();
        FestivalSeries regionLevel = all.stream().filter(s -> s.getScope() == SeriesScope.REGION_LEVEL).findFirst().orElseThrow();
        FestivalSeries districtLevel = all.stream().filter(s -> s.getScope() == SeriesScope.DISTRICT_LEVEL).findFirst().orElseThrow();
        assertEquals(2, regionLevel.getRecordCount());
        assertEquals(1, districtLevel.getRecordCount());
    }

    // ------------------------------------------------------------------
    // 3) 지역이 다르면 절대 자동 연결하지 않음 (fuzzy도 region 내에서만 동작)
    // ------------------------------------------------------------------

    @Test
    void sameNameDifferentRegion_neverAutoMerges() {
        row(2017, 1, "가나다라마바사아자차카", "경기", Region.GYEONGGI, "가평군", "CULTURE_ART");
        row(2017, 2, "가나다라마바사아자차카", "강원", Region.GANGWON, "가평군", "CULTURE_ART");

        linkingService.linkAll();

        assertEquals(2, seriesRepository.count());
        seriesRepository.findAll().forEach(s -> assertEquals(FestivalSeriesMatchStatus.SINGLETON, s.getMatchStatus()));
    }

    // ------------------------------------------------------------------
    // 4) fuzzy HIGH 자동 연결 - 이름 유사도가 충분히 강하고(>=floor) 보조 신호도 우호적
    // ------------------------------------------------------------------

    @Test
    void strongNameSimilarityWithSupportingSignals_autoMergesAsFuzzyHigh() {
        // 11자, 마지막 한 글자만 다름 -> Levenshtein ratio = 1 - 1/11 ≈ 0.909 (floor 0.90 이상)
        MultiYearFestivalRecord a = row(2021, 1, "가나다라마바사아자차카", "전북", Region.JEONBUK, "무주군", "NATURE_ECOLOGY");
        MultiYearFestivalRecord b = row(2022, 2, "가나다라마바사아자차파", "전북", Region.JEONBUK, "무주군", "NATURE_ECOLOGY");

        linkingService.linkAll();

        assertEquals(1, seriesRepository.count(), "district/유형/인접연도가 모두 우호적이면 강한 이름유사도 후보는 자동 연결돼야 함");
        FestivalSeries series = seriesRepository.findAll().get(0);
        assertEquals(FestivalSeriesMatchStatus.FUZZY_MERGED, series.getMatchStatus());
        assertEquals(2, series.getRecordCount());

        FestivalSeriesMembership ma = membershipOf(a.getId());
        FestivalSeriesMembership mb = membershipOf(b.getId());
        assertEquals(MatchMethod.FUZZY, ma.getMatchMethod());
        assertEquals(MatchMethod.FUZZY, mb.getMatchMethod());
        assertEquals(com.festival.budgetassist.multiyear.domain.MatchConfidence.HIGH, ma.getMatchConfidence());
        assertTrue(ma.getMatchScore() != null && ma.getMatchScore() >= FestivalSeriesLinkingService.HIGH_THRESHOLD);
    }

    // ------------------------------------------------------------------
    // 5) 이름 유사도는 있지만 지역/유형이 충돌 + 연도도 멀면 완전히 버려짐(후보조차 안 됨)
    // ------------------------------------------------------------------

    @Test
    void moderateSimilarityWithConflictingSignals_isDiscardedEntirely() {
        // 10자, 마지막 한 글자만 다름 -> ratio = 1 - 1/10 = 0.90
        row(2017, 1, "타파하거너더러머버서", "경기", Region.GYEONGGI, "가평군", "CULTURE_ART");
        row(2026, 2, "타파하거너더러머버소", "경기", Region.GYEONGGI, "여주시", "NATURE_ECOLOGY");

        linkingService.linkAll();

        assertEquals(2, seriesRepository.count(), "district/유형이 충돌하고 연도도 9년이나 떨어지면 자동 연결은 물론 후보로도 남지 않아야 함");
        seriesRepository.findAll().forEach(s -> assertEquals(FestivalSeriesMatchStatus.SINGLETON, s.getMatchStatus()));
    }

    // ------------------------------------------------------------------
    // 6) 이름 유사도가 floor 미만이면 보조 신호가 전부 우호적이어도 자동 연결 금지 -> MEDIUM 검토목록행
    //    (짧은 이름의 한 글자 차이 - "봄꽃축제"/"벚꽃축제" 유형의 위험 사례를 시뮬레이션)
    // ------------------------------------------------------------------

    @Test
    void strongCompositeScoreButWeakNameSimilarity_isNotAutoMergedStaysInMediumReview() {
        // 8자, 마지막 한 글자만 다름 -> ratio = 1 - 1/8 = 0.875 (< HIGH_NAME_SIMILARITY_FLOOR=0.90)
        row(2023, 1, "가하나하다하라하", "서울", Region.SEOUL, "영등포구", "NATURE_ECOLOGY");
        row(2024, 2, "가하나하다하라호", "서울", Region.SEOUL, "영등포구", "NATURE_ECOLOGY");

        FestivalSeriesLinkingReport report = linkingService.linkAll();

        assertEquals(2, seriesRepository.count(), "이름 유사도가 floor 미만이면 지역/유형/연도가 전부 일치해도 자동 연결하면 안 됨");
        seriesRepository.findAll().forEach(s -> assertEquals(FestivalSeriesMatchStatus.SINGLETON, s.getMatchStatus()));

        boolean hasMediumCandidate = report.mediumReviewCandidates().stream()
                .anyMatch(c -> c.sourceFestivalName().equals("가하나하다하라하") || c.sourceFestivalName().equals("가하나하다하라호"));
        assertTrue(hasMediumCandidate, "자동 연결되지 않은 대신 검토 목록(MEDIUM)에는 남아야 함");
    }

    // ------------------------------------------------------------------
    // 7) 같은 singleton이 서로 다른 series 둘 다에 HIGH로 걸리면 - 애매하므로 아무데도 연결하지 않음
    // ------------------------------------------------------------------

    @Test
    void ambiguousMultipleHighCandidates_doesNotAutoLinkToEither() {
        // T1: 2행짜리 결정적 클러스터
        row(2017, 1, "가나다라마바사아자차카", "충남", Region.CHUNGNAM, "보령시", "CULTURE_ART");
        row(2018, 2, "가나다라마바사아자차카", "충남", Region.CHUNGNAM, "보령시", "CULTURE_ART");
        // T2: 또 다른 2행짜리 결정적 클러스터 (T1과는 다른 이름이지만 S와는 둘 다 1글자 차이)
        row(2019, 3, "나나다라마바사아자차카", "충남", Region.CHUNGNAM, "보령시", "CULTURE_ART");
        row(2020, 4, "나나다라마바사아자차카", "충남", Region.CHUNGNAM, "보령시", "CULTURE_ART");
        // S: T1, T2 둘 다에 대해 강한 유사도(0.909) + 동일 district/유형 -> 둘 다 HIGH 후보
        MultiYearFestivalRecord s = row(2021, 5, "마나다라마바사아자차카", "충남", Region.CHUNGNAM, "보령시", "CULTURE_ART");

        FestivalSeriesLinkingReport report = linkingService.linkAll();

        assertEquals(3, seriesRepository.count(), "T1, T2, S(미연결) 세 series로 남아야 함");
        FestivalSeriesMembership sm = membershipOf(s.getId());
        assertEquals(MatchMethod.UNMATCHED, sm.getMatchMethod(), "HIGH 후보가 여러 개면 애매하므로 어느 쪽에도 자동 연결하면 안 됨");

        assertFalse(report.ambiguousMultiHighSingletons().isEmpty());
        assertTrue(report.ambiguousMultiHighSingletons().stream().anyMatch(a -> a.sourceRecordId() == s.getId()));
    }

    // ------------------------------------------------------------------
    // 8) 이름 유사도가 최소 기준(0.55) 미만이면 지역/유형이 같아도 애초에 후보조차 되지 않음
    // ------------------------------------------------------------------

    @Test
    void completelyDifferentNames_neverBecomeCandidates() {
        row(2017, 1, "가나다라마바사", "경기", Region.GYEONGGI, "가평군", "CULTURE_ART");
        row(2018, 2, "완전히다른이름축제", "경기", Region.GYEONGGI, "가평군", "CULTURE_ART");

        FestivalSeriesLinkingReport report = linkingService.linkAll();

        assertEquals(2, seriesRepository.count());
        assertEquals(0, report.highestScoreCandidates().size() + report.mediumReviewCandidates().size(),
                "이름이 너무 다르면 후보 자체가 생성되지 않아야 함");
    }
}