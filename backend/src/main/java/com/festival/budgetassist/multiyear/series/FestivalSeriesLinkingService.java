package com.festival.budgetassist.multiyear.series;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.festival.budgetassist.multiyear.domain.FestivalSeries;
import com.festival.budgetassist.multiyear.domain.FestivalSeriesMatchCandidate;
import com.festival.budgetassist.multiyear.domain.FestivalSeriesMatchStatus;
import com.festival.budgetassist.multiyear.domain.FestivalSeriesMembership;
import com.festival.budgetassist.multiyear.domain.MatchConfidence;
import com.festival.budgetassist.multiyear.domain.MatchMethod;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;
import com.festival.budgetassist.multiyear.domain.SeriesScope;
import com.festival.budgetassist.multiyear.repository.FestivalSeriesMatchCandidateRepository;
import com.festival.budgetassist.multiyear.repository.FestivalSeriesMembershipRepository;
import com.festival.budgetassist.multiyear.repository.FestivalSeriesRepository;
import com.festival.budgetassist.multiyear.repository.MultiYearFestivalRecordRepository;

/**
 * 2017~2026 {@link MultiYearFestivalRecord} 전체를 대상으로 동일 축제의 연도별 반복 구조를
 * 찾아 {@link FestivalSeries}로 묶는다.
 *
 * <p>파이프라인:</p>
 * <ol>
 *   <li><b>결정적 클러스터링</b>: {@code (scope, normalizedRegion, normalizedDistrict-or-null,
 *       normalizedFestivalName)}이 완전히 같은 행끼리 1차로 묶는다. district가 없는 행은
 *       {@link SeriesScope#REGION_LEVEL}로 별도 처리되며 district가 있는 행과는 이 단계도,
 *       이후 fuzzy 단계도 절대 섞이지 않는다.</li>
 *   <li><b>fuzzy 매칭</b>: 1차 클러스터링에서 혼자 남은(singleton) 행에 대해서만, 같은
 *       (scope, region) 안에서 문자열 유사도 + 보조 신호(시군구/인접연도/축제유형)로 점수를
 *       매긴다. 이 단계의 threshold는 잠정 분석값이며 production 확정치가 아니다.
 *       <ul>
 *         <li>HIGH: 유일한 HIGH 후보일 때만 자동 연결(union). 같은 singleton에 대해 서로 다른
 *             series를 가리키는 HIGH 후보가 2개 이상이면 애매한 것으로 보고 아무것도 연결하지
 *             않는다(오연결 방지가 우선) - 이런 사례는 리포트에 별도로 표시한다.</li>
 *         <li>MEDIUM/LOW: 연결하지 않고 {@link FestivalSeriesMatchCandidate}(검토 목록)에만
 *             기록한다.</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <p>기존 2026 production {@code BudgetEstimatorService}/confidence, {@code
 * MultiYearFestivalRecord}의 예산 필드는 전혀 건드리지 않는다 - 이 서비스는 series 연결과
 * 품질 검증(리포트)만 한다.</p>
 */
@Service
public class FestivalSeriesLinkingService {

    // fuzzy composite score 밴드 (잠정 분석값 - production 확정 threshold 아님)
    static final double HIGH_THRESHOLD = 0.92;
    static final double MEDIUM_THRESHOLD = 0.80;
    static final double LOW_THRESHOLD = 0.65;
    // 이 미만의 순수 이름 유사도는 후보로도 취급하지 않는다(무관한 이름끼리 부가신호만으로 연결되는 것 방지).
    static final double MIN_NAME_SIMILARITY_TO_CONSIDER = 0.55;

    private static final double DISTRICT_MATCH_BONUS = 0.08;
    private static final double DISTRICT_MISMATCH_PENALTY = 0.15;
    private static final double YEAR_ADJACENT_BONUS = 0.05;
    private static final double YEAR_FAR_PENALTY = 0.05;
    private static final int YEAR_FAR_GAP = 6;
    private static final double TYPE_OVERLAP_BONUS = 0.05;
    private static final double TYPE_MISMATCH_PENALTY = 0.08;

    /**
     * 보조 신호(지역/유형/인접연도)는 "이미 강한 이름 유사도"를 보강하는 용도로만 쓴다 - 이름
     * 유사도가 이 floor 미만이면 보조 신호 합산으로 score가 HIGH_THRESHOLD를 넘어도 HIGH로
     * 승격시키지 않고 MEDIUM(검토 목록)으로 남긴다. 짧은 한국어 축제명은 한 글자 차이("봄꽃"
     * vs "벚꽃")로도 Levenshtein 비율이 0.85~0.90까지 나올 수 있는데, 그런 경우까지 지역/유형이
     * 우연히 같다는 이유로 자동 연결하면 서로 다른 축제를 합치는 오연결 위험이 크다.
     */
    static final double HIGH_NAME_SIMILARITY_FLOOR = 0.90;

    private static final int MAX_CANDIDATES_PERSISTED_PER_SOURCE = 3;

    private final MultiYearFestivalRecordRepository recordRepository;
    private final FestivalSeriesRepository seriesRepository;
    private final FestivalSeriesMembershipRepository membershipRepository;
    private final FestivalSeriesMatchCandidateRepository candidateRepository;

    public FestivalSeriesLinkingService(MultiYearFestivalRecordRepository recordRepository,
                                         FestivalSeriesRepository seriesRepository,
                                         FestivalSeriesMembershipRepository membershipRepository,
                                         FestivalSeriesMatchCandidateRepository candidateRepository) {
        this.recordRepository = recordRepository;
        this.seriesRepository = seriesRepository;
        this.membershipRepository = membershipRepository;
        this.candidateRepository = candidateRepository;
    }

    @Transactional
    public FestivalSeriesLinkingReport linkAll() {
        candidateRepository.deleteAllInBatch();
        membershipRepository.deleteAllInBatch();
        seriesRepository.deleteAllInBatch();

        List<MultiYearFestivalRecord> allRecords = recordRepository.findAll().stream()
                .sorted(Comparator.comparing(MultiYearFestivalRecord::getDatasetYear)
                        .thenComparing(r -> r.getSourceRowNumber() == null ? 0 : r.getSourceRowNumber())
                        .thenComparing(MultiYearFestivalRecord::getId))
                .toList();

        // 1) 결정적 클러스터링
        List<Cluster> clusters = buildDeterministicClusters(allRecords);

        // 2) fuzzy 매칭 (singleton 후보에 대해서만) - union-find로 결과를 모은다
        UnionFind uf = new UnionFind(clusters);
        List<AppliedUnion> appliedUnions = new ArrayList<>();
        List<ScoredCandidate> allCandidates = runFuzzyMatching(clusters, uf, appliedUnions);

        // 3) union-find 결과로 최종 series 구성 후 영속화
        List<SeriesBuild> builds = buildFinalSeries(clusters, uf);
        // source/target 양쪽 singleton 모두 같은 병합 점수를 갖도록 채운다(target이 multi-row
        // 클러스터면 어차피 아래에서 읽히지 않으니 무해하다). 한 singleton이 서로 다른 union에
        // 여러 번 target으로 선택된 경우 마지막 값으로 덮어써지는데, 리포트용 근사치라 무방하다.
        Map<Integer, Double> appliedScoreByClusterIndex = new LinkedHashMap<>();
        for (AppliedUnion au : appliedUnions) {
            appliedScoreByClusterIndex.put(au.singletonClusterIndex(), au.score());
            appliedScoreByClusterIndex.put(au.targetClusterIndex(), au.score());
        }

        List<FestivalSeries> savedSeries = new ArrayList<>();
        List<FestivalSeriesMembership> savedMemberships = new ArrayList<>();
        for (SeriesBuild build : builds) {
            FestivalSeries series = seriesRepository.save(build.toEntity());
            savedSeries.add(series);
            for (Cluster c : build.originalClusters()) {
                MatchMethod method;
                Double score = null;
                MatchConfidence confidence = null;
                if (c.members().size() >= 2) {
                    for (MultiYearFestivalRecord r : c.members()) {
                        method = trimmedEquals(r.getFestivalName(), c.modalRawName()) ? MatchMethod.EXACT : MatchMethod.NORMALIZED_EXACT;
                        savedMemberships.add(saveMembership(r, series, method, null, null));
                    }
                } else {
                    MultiYearFestivalRecord onlyMember = c.members().get(0);
                    if (build.originalClusters().size() > 1) {
                        method = MatchMethod.FUZZY;
                        score = appliedScoreByClusterIndex.get(c.index());
                        confidence = MatchConfidence.HIGH;
                    } else {
                        method = MatchMethod.UNMATCHED;
                    }
                    savedMemberships.add(saveMembership(onlyMember, series, method, score, confidence));
                }
            }
        }

        List<FestivalSeriesMatchCandidate> savedCandidates = allCandidates.stream()
                .map(c -> candidateRepository.save(c.toEntity()))
                .toList();

        return FestivalSeriesLinkingReportBuilder.build(allRecords, savedSeries, savedMemberships, savedCandidates);
    }

    private FestivalSeriesMembership saveMembership(MultiYearFestivalRecord record, FestivalSeries series,
                                                      MatchMethod method, Double score, MatchConfidence confidence) {
        return membershipRepository.save(FestivalSeriesMembership.builder()
                .festivalRecord(record)
                .festivalSeries(series)
                .matchMethod(method)
                .matchScore(score)
                .matchConfidence(confidence)
                .build());
    }

    // ------------------------------------------------------------------
    // 1) 결정적 클러스터링
    // ------------------------------------------------------------------

    private List<Cluster> buildDeterministicClusters(List<MultiYearFestivalRecord> allRecords) {
        Map<ClusterKey, List<MultiYearFestivalRecord>> grouped = new LinkedHashMap<>();
        for (MultiYearFestivalRecord r : allRecords) {
            ClusterKey key = ClusterKey.of(r);
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        List<Cluster> clusters = new ArrayList<>(grouped.size());
        int idx = 0;
        for (Map.Entry<ClusterKey, List<MultiYearFestivalRecord>> entry : grouped.entrySet()) {
            clusters.add(new Cluster(idx++, entry.getKey(), entry.getValue()));
        }
        return clusters;
    }

    // ------------------------------------------------------------------
    // 2) fuzzy 매칭
    // ------------------------------------------------------------------

    private List<ScoredCandidate> runFuzzyMatching(List<Cluster> clusters, UnionFind uf, List<AppliedUnion> appliedUnionsOut) {
        Map<BucketKey, List<Cluster>> buckets = new LinkedHashMap<>();
        for (Cluster c : clusters) {
            buckets.computeIfAbsent(new BucketKey(c.key().scope(), c.key().regionKey()), k -> new ArrayList<>()).add(c);
        }

        List<ScoredCandidate> allCandidates = new ArrayList<>();

        for (List<Cluster> bucket : buckets.values()) {
            List<Cluster> singletons = bucket.stream().filter(c -> c.members().size() == 1).toList();
            for (Cluster source : singletons) {
                List<ScoredCandidate> forSource = new ArrayList<>();
                for (Cluster target : bucket) {
                    if (target.index() == source.index()) {
                        continue;
                    }
                    // singleton-singleton 쌍은 인덱스가 작은 쪽만 source로 평가해 중복 계산을 피한다.
                    if (target.members().size() == 1 && target.index() < source.index()) {
                        continue;
                    }
                    ScoredCandidate candidate = score(source, target);
                    if (candidate != null) {
                        forSource.add(candidate);
                    }
                }
                forSource.sort(Comparator.comparingDouble(ScoredCandidate::score).reversed());

                long highCount = forSource.stream().filter(c -> c.band() == MatchConfidence.HIGH).count();
                ScoredCandidate applied = null;
                if (highCount == 1) {
                    applied = forSource.stream().filter(c -> c.band() == MatchConfidence.HIGH).findFirst().orElseThrow();
                }
                // highCount >= 2: 서로 다른 series를 가리키는 HIGH 후보가 둘 이상 -> 애매하므로 연결하지 않는다.

                for (int i = 0; i < forSource.size() && i < MAX_CANDIDATES_PERSISTED_PER_SOURCE; i++) {
                    ScoredCandidate c = forSource.get(i);
                    boolean isApplied = applied != null && c == applied;
                    allCandidates.add(c.withApplied(isApplied));
                }
                // MAX_CANDIDATES_PERSISTED_PER_SOURCE 밖이라도 applied로 선택된 후보는 반드시 기록한다.
                if (applied != null && forSource.indexOf(applied) >= MAX_CANDIDATES_PERSISTED_PER_SOURCE) {
                    allCandidates.add(applied.withApplied(true));
                }

                if (applied != null) {
                    uf.union(source.index(), applied.targetClusterIndex());
                    appliedUnionsOut.add(new AppliedUnion(source.index(), applied.targetClusterIndex(), applied.score()));
                }
            }
        }
        return allCandidates;
    }

    private ScoredCandidate score(Cluster source, Cluster target) {
        String a = FestivalNameNormalizer.fuzzyKey(source.key().normalizedName());
        String b = FestivalNameNormalizer.fuzzyKey(target.key().normalizedName());
        if (LevenshteinSimilarity.lengthBoundedMaxRatio(a, b) < MIN_NAME_SIMILARITY_TO_CONSIDER) {
            return null; // 길이 차이만으로도 min similarity를 넘을 수 없음 - 전체 DP 생략
        }
        double nameSimilarity = LevenshteinSimilarity.ratio(a, b);
        if (nameSimilarity < MIN_NAME_SIMILARITY_TO_CONSIDER) {
            return null;
        }

        double districtSignal = districtSignal(source.key().districtKey(), target.key().districtKey());
        double yearSignal = yearAdjacencySignal(source, target);
        double typeSignal = typeSignal(source, target);

        double score = clamp01(nameSimilarity + districtSignal + yearSignal + typeSignal);
        if (score < LOW_THRESHOLD) {
            return null;
        }
        MatchConfidence band = score >= HIGH_THRESHOLD ? MatchConfidence.HIGH
                : score >= MEDIUM_THRESHOLD ? MatchConfidence.MEDIUM
                : MatchConfidence.LOW;
        if (band == MatchConfidence.HIGH && nameSimilarity < HIGH_NAME_SIMILARITY_FLOOR) {
            // 이름 자체는 약한데 보조 신호 합산으로만 HIGH를 넘긴 경우 - 자동 연결 후보에서 내림.
            band = MatchConfidence.MEDIUM;
        }

        return new ScoredCandidate(source.index(), target.index(),
                source.members().get(0), target.members().get(0),
                nameSimilarity, districtSignal, yearSignal, typeSignal, score, band, false);
    }

    private double districtSignal(String districtA, String districtB) {
        if (districtA == null || districtB == null) {
            return 0.0;
        }
        return districtA.equals(districtB) ? DISTRICT_MATCH_BONUS : -DISTRICT_MISMATCH_PENALTY;
    }

    private double yearAdjacencySignal(Cluster a, Cluster b) {
        int gap = rangeGap(a.firstYear(), a.lastYear(), b.firstYear(), b.lastYear());
        if (gap <= 1) {
            return YEAR_ADJACENT_BONUS;
        }
        if (gap >= YEAR_FAR_GAP) {
            return -YEAR_FAR_PENALTY;
        }
        return 0.0;
    }

    /** 두 [min,max] 연도 구간의 간격. 겹치거나 맞닿으면(gap<=1) 0/1을 반환, 아니면 떨어진 연수. */
    private int rangeGap(int aMin, int aMax, int bMin, int bMax) {
        if (aMax < bMin) {
            return bMin - aMax;
        }
        if (bMax < aMin) {
            return aMin - bMax;
        }
        return 0;
    }

    private double typeSignal(Cluster a, Cluster b) {
        var typesA = splitTypes(a.members().get(0).getFestivalType());
        var typesB = splitTypes(b.members().get(0).getFestivalType());
        if (typesA.isEmpty() || typesB.isEmpty()) {
            return 0.0;
        }
        boolean overlap = typesA.stream().anyMatch(typesB::contains);
        return overlap ? TYPE_OVERLAP_BONUS : -TYPE_MISMATCH_PENALTY;
    }

    private java.util.Set<String> splitTypes(String festivalType) {
        if (festivalType == null || festivalType.isBlank()) {
            return java.util.Set.of();
        }
        return java.util.Set.of(festivalType.split("\\|"));
    }

    private double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private boolean trimmedEquals(String a, String b) {
        return a != null && b != null && a.trim().equals(b.trim());
    }

    // ------------------------------------------------------------------
    // 3) union-find 결과 -> 최종 series 빌드
    // ------------------------------------------------------------------

    private List<SeriesBuild> buildFinalSeries(List<Cluster> clusters, UnionFind uf) {
        Map<Integer, List<Cluster>> byRoot = new LinkedHashMap<>();
        for (Cluster c : clusters) {
            byRoot.computeIfAbsent(uf.find(c.index()), k -> new ArrayList<>()).add(c);
        }

        List<SeriesBuild> builds = new ArrayList<>(byRoot.size());
        for (Map.Entry<Integer, List<Cluster>> entry : byRoot.entrySet()) {
            int rootIndex = entry.getKey();
            List<Cluster> originalClusters = entry.getValue();
            Cluster anchor = clusters.get(rootIndex);

            List<MultiYearFestivalRecord> allMembers = new ArrayList<>();
            originalClusters.forEach(c -> allMembers.addAll(c.members()));
            allMembers.sort(Comparator.comparing(MultiYearFestivalRecord::getDatasetYear).thenComparing(MultiYearFestivalRecord::getId));

            int firstYear = allMembers.stream().mapToInt(MultiYearFestivalRecord::getDatasetYear).min().orElseThrow();
            int lastYear = allMembers.stream().mapToInt(MultiYearFestivalRecord::getDatasetYear).max().orElseThrow();

            FestivalSeriesMatchStatus status;
            if (originalClusters.size() > 1) {
                status = FestivalSeriesMatchStatus.FUZZY_MERGED;
            } else if (anchor.members().size() >= 2) {
                status = FestivalSeriesMatchStatus.DETERMINISTIC;
            } else {
                status = FestivalSeriesMatchStatus.SINGLETON;
            }

            builds.add(new SeriesBuild(anchor, originalClusters, allMembers, firstYear, lastYear, status));
        }
        return builds;
    }

    // ------------------------------------------------------------------
    // union-find (preference: 다행 클러스터 우선, 그다음 이른 연도 우선 -> 결정적 결과)
    // ------------------------------------------------------------------

    /**
     * union 시 어느 쪽을 root로 삼을지는 임의(원소 개수/삽입 순서)가 아니라 "더 나은 대표"
     * 규칙으로 정한다: (1) 원소가 더 많은 클러스터(이미 EXACT/NORMALIZED_EXACT로 묶인 다행
     * 클러스터) 우선 (2) 그다음 더 이른 연도에 처음 관측된 쪽 우선 (3) 그래도 같으면 원본
     * 클러스터 인덱스가 작은 쪽. 이렇게 해야 canonicalName/canonicalRegion/canonicalDistrict가
     * "가장 신뢰할 수 있는" 클러스터의 값으로 결정된다.
     */
    private static final class UnionFind {
        private final int[] parent;
        private final List<Cluster> clusters;

        UnionFind(List<Cluster> clusters) {
            this.clusters = clusters;
            parent = new int[clusters.size()];
            for (int i = 0; i < parent.length; i++) {
                parent[i] = i;
            }
        }

        int find(int x) {
            while (parent[x] != x) {
                parent[x] = parent[parent[x]];
                x = parent[x];
            }
            return x;
        }

        void union(int a, int b) {
            int ra = find(a);
            int rb = find(b);
            if (ra == rb) {
                return;
            }
            if (preferred(ra, rb) == ra) {
                parent[rb] = ra;
            } else {
                parent[ra] = rb;
            }
        }

        private int preferred(int a, int b) {
            Cluster ca = clusters.get(a);
            Cluster cb = clusters.get(b);
            if (ca.members().size() != cb.members().size()) {
                return ca.members().size() > cb.members().size() ? a : b;
            }
            if (ca.firstYear() != cb.firstYear()) {
                return ca.firstYear() < cb.firstYear() ? a : b;
            }
            return a < b ? a : b;
        }
    }

    // ------------------------------------------------------------------
    // 내부 작업용 타입
    // ------------------------------------------------------------------

    record ClusterKey(SeriesScope scope, String regionKey, String districtKey, String normalizedName) {
        static ClusterKey of(MultiYearFestivalRecord r) {
            String region = resolveRegionKey(r);
            String district = resolveDistrictKey(r);
            SeriesScope scope = district != null ? SeriesScope.DISTRICT_LEVEL : SeriesScope.REGION_LEVEL;
            String normalizedName = FestivalNameNormalizer.normalize(r.getFestivalName());
            return new ClusterKey(scope, region, district, normalizedName);
        }
    }

    private static String resolveRegionKey(MultiYearFestivalRecord r) {
        if (r.getRegionCode() != null) {
            return r.getRegionCode().getDisplayName();
        }
        if (r.getRegionText() != null && !r.getRegionText().isBlank()) {
            return r.getRegionText().trim();
        }
        return r.getRegionRaw() == null ? "UNKNOWN" : r.getRegionRaw().trim();
    }

    /**
     * districtText(CSV의 최소 정규화 district 컬럼)를 우선하고, 없으면 districtRaw로
     * 대체한다. 어느 쪽이든 {@link DistrictPlaceholderNormalizer}가 "실제 시군구가 아닌
     * region-level 표현"("-", "본청", "시자체" 등)으로 판정하면 null을 반환해
     * {@link SeriesScope#REGION_LEVEL}로 취급한다 - 이 값은 클러스터링 키 계산에만 쓰이고
     * {@code MultiYearFestivalRecord.districtRaw/districtText} 원본은 절대 바꾸지 않는다.
     */
    private static String resolveDistrictKey(MultiYearFestivalRecord r) {
        String candidate = null;
        if (r.getDistrictText() != null && !r.getDistrictText().isBlank()) {
            candidate = r.getDistrictText().trim();
        } else if (r.getDistrictRaw() != null && !r.getDistrictRaw().isBlank()) {
            candidate = r.getDistrictRaw().trim();
        }
        if (candidate == null || DistrictPlaceholderNormalizer.isRegionLevelPlaceholder(candidate)) {
            return null;
        }
        return candidate;
    }

    record BucketKey(SeriesScope scope, String regionKey) {
    }

    static final class Cluster {
        private final int index;
        private final ClusterKey key;
        private final List<MultiYearFestivalRecord> members;
        private final String modalRawName;
        private final int firstYear;
        private final int lastYear;

        Cluster(int index, ClusterKey key, List<MultiYearFestivalRecord> members) {
            this.index = index;
            this.key = key;
            this.members = members;
            this.modalRawName = computeModalRawName(members);
            this.firstYear = members.stream().mapToInt(MultiYearFestivalRecord::getDatasetYear).min().orElseThrow();
            this.lastYear = members.stream().mapToInt(MultiYearFestivalRecord::getDatasetYear).max().orElseThrow();
        }

        private static String computeModalRawName(List<MultiYearFestivalRecord> members) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (MultiYearFestivalRecord r : members) {
                counts.merge(r.getFestivalName().trim(), 1, Integer::sum);
            }
            String best = null;
            int bestCount = -1;
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                if (e.getValue() > bestCount) {
                    best = e.getKey();
                    bestCount = e.getValue();
                }
            }
            return best;
        }

        int index() {
            return index;
        }

        ClusterKey key() {
            return key;
        }

        List<MultiYearFestivalRecord> members() {
            return members;
        }

        String modalRawName() {
            return modalRawName;
        }

        int firstYear() {
            return firstYear;
        }

        int lastYear() {
            return lastYear;
        }
    }

    record ScoredCandidate(int sourceClusterIndex, int targetClusterIndex,
                            MultiYearFestivalRecord sourceRecord, MultiYearFestivalRecord candidateRecord,
                            double nameSimilarity, double districtSignal, double yearAdjacencySignal, double typeSignal,
                            double score, MatchConfidence band, boolean applied) {

        ScoredCandidate withApplied(boolean value) {
            return new ScoredCandidate(sourceClusterIndex, targetClusterIndex, sourceRecord, candidateRecord,
                    nameSimilarity, districtSignal, yearAdjacencySignal, typeSignal, score, band, value);
        }

        FestivalSeriesMatchCandidate toEntity() {
            return FestivalSeriesMatchCandidate.builder()
                    .sourceRecord(sourceRecord)
                    .candidateRecord(candidateRecord)
                    .nameSimilarity(nameSimilarity)
                    .districtSignal(districtSignal)
                    .yearAdjacencySignal(yearAdjacencySignal)
                    .typeSignal(typeSignal)
                    .score(score)
                    .confidenceBand(band)
                    .applied(applied)
                    .build();
        }
    }

    record AppliedUnion(int singletonClusterIndex, int targetClusterIndex, double score) {
    }

    record SeriesBuild(Cluster anchor, List<Cluster> originalClusters, List<MultiYearFestivalRecord> allMembers,
                        int firstYear, int lastYear, FestivalSeriesMatchStatus status) {

        FestivalSeries toEntity() {
            return FestivalSeries.builder()
                    .canonicalName(anchor.key().normalizedName())
                    .canonicalRegion(anchor.key().regionKey())
                    .canonicalDistrict(anchor.key().districtKey())
                    .firstObservedYear(firstYear)
                    .lastObservedYear(lastYear)
                    .recordCount(allMembers.size())
                    .matchStatus(status)
                    .scope(anchor.key().scope())
                    .build();
        }
    }
}