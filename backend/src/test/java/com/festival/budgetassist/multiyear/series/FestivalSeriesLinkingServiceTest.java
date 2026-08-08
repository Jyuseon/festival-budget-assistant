package com.festival.budgetassist.multiyear.series;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    // ------------------------------------------------------------------
    // 9) district placeholder 정규화: "본청"/"시자체"/"-" 등은 서로 다른 값이 아니라 전부
    //    REGION_LEVEL로 취급돼야 한다 (실제 "대구포크페스티벌" 사례 재현).
    // ------------------------------------------------------------------

    @Test
    void regionLevelDistrictPlaceholders_areTreatedAsTheSameRegionLevelSeriesNotMismatched() {
        MultiYearFestivalRecord a = row(2020, 1, "대구포크페스티벌", "대구", Region.DAEGU, "시자체", "CULTURE_ART");
        MultiYearFestivalRecord b = row(2022, 2, "대구포크페스티벌", "대구", Region.DAEGU, "본청", "CULTURE_ART");
        MultiYearFestivalRecord c = row(2023, 3, "대구포크페스티벌", "대구", Region.DAEGU, "-", "CULTURE_ART");

        linkingService.linkAll();

        assertEquals(1, seriesRepository.count(), "시자체/본청/-는 전부 같은 REGION_LEVEL 키로 묶여야 함");
        FestivalSeries series = seriesRepository.findAll().get(0);
        assertEquals(SeriesScope.REGION_LEVEL, series.getScope());
        assertEquals(FestivalSeriesMatchStatus.DETERMINISTIC, series.getMatchStatus());
        assertEquals(3, series.getRecordCount());
        assertEquals(MatchMethod.EXACT, membershipOf(a.getId()).getMatchMethod());
        assertEquals(MatchMethod.EXACT, membershipOf(b.getId()).getMatchMethod());
        assertEquals(MatchMethod.EXACT, membershipOf(c.getId()).getMatchMethod());
    }

    @Test
    void realDistrictsWithSuffixNoiseOrTypos_areNotDemotedToRegionLevel() {
        // "중구청"은 placeholder가 아니라 실제 "중구" + 접미어라 null(REGION_LEVEL)로 강등되면
        // 안 된다. (이름/유형/연도가 전부 같으면 fuzzy가 별도로 이 둘을 이어붙일 수는 있지만,
        // 그건 이 테스트의 관심사가 아니다 - district 문자열 자체가 placeholder로 오인돼
        // REGION_LEVEL로 떨어지지 않는지만 확인한다.)
        row(2020, 1, "축제A", "대구", Region.DAEGU, "중구청", "CULTURE_ART");
        row(2020, 2, "축제A", "대구", Region.DAEGU, "중구", "CULTURE_ART");

        linkingService.linkAll();

        seriesRepository.findAll().forEach(s ->
                assertEquals(SeriesScope.DISTRICT_LEVEL, s.getScope(), "\"중구청\"/\"중구\" 모두 실제 시군구라 REGION_LEVEL로 강등되면 안 됨"));
    }

    // ------------------------------------------------------------------
    // 10) strict chain linking - 이름 유사도가 25자 중 1글자만 달라 0.96(>=0.95)이 되도록
    //     합성 문자열을 쓴다("가"를 24번 반복 + 끝자리 1글자). 연도 gap을 1 이하로 유지해야만
    //     edge가 생기므로, A(2017)-B(2018)-C(2019)는 각각 인접 쌍만 직접 edge가 되고
    //     A-C(gap=2)는 edge 시도 자체가 없다 - 그래도 A-C 유사도가 0.95 이상이면(끝자리만
    //     다르므로 항상 0.96) 전체 재검증에서 안전하게 통과해야 한다.
    // ------------------------------------------------------------------

    private static String chainBase() {
        return "가".repeat(24);
    }

    @Test
    void strictChain_threeRecordsFormingASafeChain_mergeIntoOneChainMergedSeries() {
        MultiYearFestivalRecord a = row(2017, 1, chainBase() + "1", "경기", Region.GYEONGGI, "가평군", "CULTURE_ART");
        MultiYearFestivalRecord b = row(2018, 2, chainBase() + "2", "경기", Region.GYEONGGI, "가평군", "CULTURE_ART");
        MultiYearFestivalRecord c = row(2019, 3, chainBase() + "3", "경기", Region.GYEONGGI, "가평군", "CULTURE_ART");

        FestivalSeriesLinkingReport report = linkingService.linkAll();

        assertEquals(1, seriesRepository.count(), "A-B, B-C가 각각 안전한 edge이고 A-C도 재검증을 통과하므로 하나로 합쳐져야 함");
        FestivalSeries series = seriesRepository.findAll().get(0);
        assertEquals(FestivalSeriesMatchStatus.CHAIN_MERGED, series.getMatchStatus());
        assertEquals(3, series.getRecordCount());
        assertEquals(MatchMethod.CHAIN_HIGH_CONFIDENCE, membershipOf(a.getId()).getMatchMethod());
        assertEquals(MatchMethod.CHAIN_HIGH_CONFIDENCE, membershipOf(b.getId()).getMatchMethod());
        assertEquals(MatchMethod.CHAIN_HIGH_CONFIDENCE, membershipOf(c.getId()).getMatchMethod());
        assertEquals(com.festival.budgetassist.multiyear.domain.MatchConfidence.HIGH, membershipOf(a.getId()).getMatchConfidence());

        boolean auditedAsApplied = report.chainComponents().stream()
                .anyMatch(comp -> comp.applied() && comp.members().stream().anyMatch(m -> m.recordId() == a.getId()));
        assertTrue(auditedAsApplied, "적용된 컴포넌트는 리포트 전수 목록에 남아야 함");
    }

    @Test
    void strictChain_sameDatasetYearInComponent_isRejectedAndStaysUnmatched() {
        // 같은 2020년에 서로 다른 record 2건 - 이름이 매우 유사해도(0.96) 같은 연도가
        // 하나의 series/체인에 들어가면 안 된다.
        MultiYearFestivalRecord a = row(2020, 1, chainBase() + "1", "경기", Region.GYEONGGI, "가평군", "CULTURE_ART");
        MultiYearFestivalRecord b = row(2020, 2, chainBase() + "2", "경기", Region.GYEONGGI, "가평군", "CULTURE_ART");

        linkingService.linkAll();

        assertEquals(2, seriesRepository.count(), "같은 연도 중복이라 병합되면 안 됨");
        assertEquals(MatchMethod.UNMATCHED, membershipOf(a.getId()).getMatchMethod());
        assertEquals(MatchMethod.UNMATCHED, membershipOf(b.getId()).getMatchMethod());
    }

    @Test
    void strictChain_typeConflictBetweenNonAdjacentMembers_isRejected() {
        // A(2017,CULTURE_ART) - B(2018,CULTURE_ART|NATURE_ECOLOGY) - C(2019,NATURE_ECOLOGY)
        // A-B, B-C는 각각 유형이 겹쳐 edge가 되지만, A-C는 직접 비교되지 않았을 뿐 실제로는
        // 유형이 전혀 겹치지 않는다 - 컴포넌트 전체 재검증에서 잡아내 거부해야 한다.
        MultiYearFestivalRecord a = row(2017, 1, chainBase() + "1", "경기", Region.GYEONGGI, "가평군", "CULTURE_ART");
        row(2018, 2, chainBase() + "2", "경기", Region.GYEONGGI, "가평군", "CULTURE_ART|NATURE_ECOLOGY");
        MultiYearFestivalRecord c = row(2019, 3, chainBase() + "3", "경기", Region.GYEONGGI, "가평군", "NATURE_ECOLOGY");

        linkingService.linkAll();

        assertEquals(3, seriesRepository.count(), "type 충돌이 감지되면 컴포넌트 전체를 병합하지 않아야 함");
        assertEquals(MatchMethod.UNMATCHED, membershipOf(a.getId()).getMatchMethod());
        assertEquals(MatchMethod.UNMATCHED, membershipOf(c.getId()).getMatchMethod());
    }

    @Test
    void strictChain_nameDriftAcrossChain_isRejectedByFullPairwiseRecheck() {
        // A와 B는 1글자만 다르고(20자 중 1글자, ratio=0.95), B와 C도 1글자만 다르지만
        // 서로 다른 자리라 A와 C는 2글자가 달라진다(ratio=0.90 < 0.95 cluster 임계값).
        // A-B, B-C는 각각 edge가 되지만(연도도 인접) A-C는 재검증에서 걸려야 한다.
        String posBase = "가나다라마바사아자차카타파하거너더러머버"; // 20자, 인덱스 0..19
        String a = posBase; // 기준
        String b = "갸" + posBase.substring(1); // 0번째 글자만 교체
        String c = "갸냐" + posBase.substring(2); // 0,1번째 글자 교체(각각 b, a 대비 1글자 차이 누적)

        MultiYearFestivalRecord ra = row(2017, 1, a, "경기", Region.GYEONGGI, "가평군", "CULTURE_ART");
        row(2018, 2, b, "경기", Region.GYEONGGI, "가평군", "CULTURE_ART");
        MultiYearFestivalRecord rc = row(2019, 3, c, "경기", Region.GYEONGGI, "가평군", "CULTURE_ART");

        FestivalSeriesLinkingReport report = linkingService.linkAll();

        assertEquals(3, seriesRepository.count(), "체인 양 끝단이 실제로는 이름이 충분히 다르므로(0.90) 병합하면 안 됨");
        assertEquals(MatchMethod.UNMATCHED, membershipOf(ra.getId()).getMatchMethod());
        assertEquals(MatchMethod.UNMATCHED, membershipOf(rc.getId()).getMatchMethod());

        boolean rejectedForSimilarity = report.chainComponents().stream()
                .anyMatch(comp -> !comp.applied() && comp.members().size() == 3
                        && comp.rejectionReason() != null && comp.rejectionReason().contains("이름유사도"));
        assertTrue(rejectedForSimilarity, "거부 사유에 최소 이름유사도 미달이 기록돼야 함");
    }

    @Test
    void strictChain_directDistrictConflict_neverFormsAnEdgeAtAll() {
        // 서로 다른 실제 시군구(가평군 vs 여주시) - 이름이 아무리 비슷해도 district가 다르면
        // strict edge 자체가 생기지 않아야 한다(district compatible 조건).
        MultiYearFestivalRecord a = row(2017, 1, chainBase() + "1", "경기", Region.GYEONGGI, "가평군", "CULTURE_ART");
        MultiYearFestivalRecord b = row(2018, 2, chainBase() + "2", "경기", Region.GYEONGGI, "여주시", "CULTURE_ART");

        linkingService.linkAll();

        assertEquals(2, seriesRepository.count());
        assertEquals(MatchMethod.UNMATCHED, membershipOf(a.getId()).getMatchMethod());
        assertEquals(MatchMethod.UNMATCHED, membershipOf(b.getId()).getMatchMethod());
    }

    @Test
    void strictChain_largeYearGap_neverFormsAnEdge() {
        // A(2017)-B(2018)-C(2020): 셋 다 이름/지역/유형이 서로 HIGH급으로 비슷해서(nameSim 0.96)
        // fuzzy 2단계에서는 전부 서로에게 ambiguous(HIGH 후보 2개 이상)라 아무도 자동 병합되지
        // 않고 그대로 chain 풀로 넘어온다. chain 4단계에서는 A-B(gap=1)만 edge가 되고,
        // B-C(gap=2)/A-C(gap=3)는 이름/지역/유형이 전부 충분한데도 "small year gap"(gap<=1)
        // 조건 하나 때문에 edge가 형성되면 안 된다 - 그 결과 A,B만 하나의 chain series로
        // 합쳐지고 C는 계속 UNMATCHED singleton으로 남아야 한다.
        // (참고: 2-node만 있는 단순 케이스 - 예를 들어 A/C만 있고 gap=2 - 는 서로가 유일한
        // HIGH 후보라 애초에 ambiguous가 아니어서 chain 단계 진입 전에 일반 FUZZY로 병합된다.
        // 그건 chain의 gap 제한과 무관한 정상 동작이라 여기서 검증할 대상이 아니다.)
        MultiYearFestivalRecord a = row(2017, 1, chainBase() + "1", "경기", Region.GYEONGGI, "가평군", "CULTURE_ART");
        MultiYearFestivalRecord b = row(2018, 2, chainBase() + "2", "경기", Region.GYEONGGI, "가평군", "CULTURE_ART");
        MultiYearFestivalRecord c = row(2020, 3, chainBase() + "3", "경기", Region.GYEONGGI, "가평군", "CULTURE_ART");

        linkingService.linkAll();

        assertEquals(2, seriesRepository.count(), "A-B만 chain edge가 되고 B-C/A-C는 gap>1이라 edge가 안 되므로 A,B 병합 + C 단독이어야 함");
        assertEquals(MatchMethod.CHAIN_HIGH_CONFIDENCE, membershipOf(a.getId()).getMatchMethod());
        assertEquals(MatchMethod.CHAIN_HIGH_CONFIDENCE, membershipOf(b.getId()).getMatchMethod());
        assertEquals(membershipOf(a.getId()).getFestivalSeries().getId(), membershipOf(b.getId()).getFestivalSeries().getId());
        assertEquals(MatchMethod.UNMATCHED, membershipOf(c.getId()).getMatchMethod(), "C는 A/B 어느 쪽과도 gap<=1이 아니라 chain edge가 없어야 함");
    }

    @Test
    void strictChain_thresholdComparisonReportsCountsForEachCandidateThreshold() {
        row(2017, 1, chainBase() + "1", "경기", Region.GYEONGGI, "가평군", "CULTURE_ART");
        row(2018, 2, chainBase() + "2", "경기", Region.GYEONGGI, "가평군", "CULTURE_ART");
        row(2019, 3, chainBase() + "3", "경기", Region.GYEONGGI, "가평군", "CULTURE_ART");

        FestivalSeriesLinkingReport report = linkingService.linkAll();

        assertEquals(3, report.chainClusterThresholdComparison().size());
        assertTrue(report.chainClusterThresholdComparison().containsKey("0.90"));
        assertTrue(report.chainClusterThresholdComparison().containsKey("0.92"));
        assertTrue(report.chainClusterThresholdComparison().containsKey("0.95"));
        assertTrue(report.chainClusterThresholdComparison().get("0.90") >= report.chainClusterThresholdComparison().get("0.95"),
                "threshold가 낮을수록(느슨할수록) 통과하는 컴포넌트 수가 같거나 많아야 함");
    }

    // ------------------------------------------------------------------
    // 11) computeSeriesGroupsInMemory - linkAll()과 정확히 같은 partition을 재현해야 함
    //     (leakage-safe backtest의 fold-local series 재계산이 이 메서드에 의존한다)
    // ------------------------------------------------------------------

    @Test
    void computeSeriesGroupsInMemory_reproducesExactSamePartitionAsLinkAll() {
        // DETERMINISTIC(정규화 이름 완전일치) 2건
        MultiYearFestivalRecord d1 = row(2017, 1, "동일축제", "경남", Region.GYEONGNAM, "진주시", "CULTURE_ART");
        MultiYearFestivalRecord d2 = row(2018, 2, "동일축제", "경남", Region.GYEONGNAM, "진주시", "CULTURE_ART");
        // FUZZY(유일한 HIGH 후보) 2건
        MultiYearFestivalRecord f1 = row(2017, 3, "봄꽃축제 행사", "충북", Region.CHUNGBUK, "청주시", "NATURE_ECOLOGY");
        MultiYearFestivalRecord f2 = row(2018, 4, "봄꽃축제행사", "충북", Region.CHUNGBUK, "청주시", "NATURE_ECOLOGY");
        // strict chain(3건 안전한 체인)
        MultiYearFestivalRecord c1 = row(2017, 5, chainBase() + "1", "경기", Region.GYEONGGI, "가평군", "CULTURE_ART");
        MultiYearFestivalRecord c2 = row(2018, 6, chainBase() + "2", "경기", Region.GYEONGGI, "가평군", "CULTURE_ART");
        MultiYearFestivalRecord c3 = row(2019, 7, chainBase() + "3", "경기", Region.GYEONGGI, "가평군", "CULTURE_ART");
        // 완전히 무관한 UNMATCHED singleton
        MultiYearFestivalRecord u1 = row(2020, 8, "아무관계없는행사", "제주", Region.JEJU, "서귀포시", "COMMUNITY");

        linkingService.linkAll();
        Map<Long, Long> officialGroupBySeriesId = new LinkedHashMap<>();
        for (MultiYearFestivalRecord r : List.of(d1, d2, f1, f2, c1, c2, c3, u1)) {
            officialGroupBySeriesId.put(r.getId(), membershipOf(r.getId()).getFestivalSeries().getId());
        }

        Map<Long, Long> inMemoryGroup = linkingService.computeSeriesGroupsInMemory(recordRepository.findAll());

        // 두 partition이 "record id -> group id" 자체는(합성 id라) 다를 수 있지만, "어떤 record끼리
        // 같은 그룹인가"는 완전히 같아야 한다 - 모든 쌍에 대해 같은 그룹 여부를 비교한다.
        List<MultiYearFestivalRecord> all = List.of(d1, d2, f1, f2, c1, c2, c3, u1);
        for (MultiYearFestivalRecord a : all) {
            for (MultiYearFestivalRecord b : all) {
                boolean officialSame = officialGroupBySeriesId.get(a.getId()).equals(officialGroupBySeriesId.get(b.getId()));
                boolean inMemorySame = inMemoryGroup.get(a.getId()).equals(inMemoryGroup.get(b.getId()));
                assertEquals(officialSame, inMemorySame,
                        "record %d/%d의 '같은 series 여부'가 linkAll()과 computeSeriesGroupsInMemory 사이에 달라짐"
                                .formatted(a.getId(), b.getId()));
            }
        }

        // 구체적으로 기대하는 그룹 크기도 확인(그냥 "같다"만 보는 것보다 더 강한 검증).
        assertEquals(inMemoryGroup.get(d1.getId()), inMemoryGroup.get(d2.getId()));
        assertEquals(inMemoryGroup.get(f1.getId()), inMemoryGroup.get(f2.getId()));
        assertEquals(inMemoryGroup.get(c1.getId()), inMemoryGroup.get(c2.getId()));
        assertEquals(inMemoryGroup.get(c2.getId()), inMemoryGroup.get(c3.getId()));
        assertNotEquals(inMemoryGroup.get(u1.getId()), inMemoryGroup.get(d1.getId()));
        assertNotEquals(inMemoryGroup.get(u1.getId()), inMemoryGroup.get(f1.getId()));
        assertNotEquals(inMemoryGroup.get(u1.getId()), inMemoryGroup.get(c1.getId()));
    }
}