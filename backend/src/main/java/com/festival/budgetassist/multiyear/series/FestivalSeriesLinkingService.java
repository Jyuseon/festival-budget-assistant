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

    // ------------------------------------------------------------------
    // strict chain linking (4단계) 전용 상수 - 일반 fuzzy(HIGH_THRESHOLD=0.92)보다 훨씬 보수적이다.
    // ------------------------------------------------------------------

    /** chain edge 하나가 성립하려면 이름 유사도가 이 이상이어야 한다(일반 fuzzy floor 0.90보다 높음). */
    static final double CHAIN_EDGE_MIN_NAME_SIMILARITY = 0.95;
    /** chain edge의 두 연도는 반드시 인접(gap<=1)해야 한다 - "small year gap"을 문자 그대로 강하게 해석. */
    private static final int CHAIN_EDGE_MAX_YEAR_GAP = 1;
    /**
     * 컴포넌트(체인) 전체를 하나의 series로 최종 확정하려면 "모든" pairwise 이름 유사도의 최솟값이
     * 이 이상이어야 한다 - edge(인접 쌍)만 강해도 체인 양 끝단(A-C)이 실제로는 다른 축제로
     * drift했을 수 있어 별도로 재검사한다. 0.90/0.92/0.95 후보를 비교한 뒤 가장 보수적인 0.95를
     * 그대로 채택했다(분석 결과는 리포트의 chainClusterThresholdComparison 참고).
     */
    static final double CHAIN_CLUSTER_MIN_SIMILARITY = 0.95;

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

        // 4) strict chain linking: ambiguous로 남은 singleton들 사이의 안전한 체인만 조건부 병합
        ChainLinkingResult chainResult = runChainLinking(savedMemberships);
        savedSeries.removeAll(chainResult.removedSeries());
        savedSeries.addAll(chainResult.addedSeries());
        savedMemberships.removeAll(chainResult.removedMemberships());
        savedMemberships.addAll(chainResult.addedMemberships());

        return FestivalSeriesLinkingReportBuilder.build(allRecords, savedSeries, savedMemberships, savedCandidates,
                chainResult.componentAudits(), chainResult.thresholdComparison());
    }

    // ------------------------------------------------------------------
    // 4) strict chain linking
    // ------------------------------------------------------------------

    /**
     * fuzzy 단계(2번)에서 "같은 singleton에 서로 다른 series를 가리키는 HIGH 후보가 2개 이상"이라
     * 보류된(ambiguous) 행들 - 정확히는 여전히 UNMATCHED인 singleton 전체 - 를 대상으로,
     * 훨씬 보수적인 조건(이름 유사도 0.95+, 인접 연도, district/유형 충돌 없음)의 edge만 모아
     * 그래프를 만들고, 연결요소(component) 전체를 다시 한 번 전수 재검증한 뒤에만 병합한다.
     *
     * <p>2번 fuzzy 단계와 달리 "무조건 union-find로 합친다"가 아니라, 컴포넌트 하나하나에 대해
     * (1) 모든 pairwise 이름 유사도 최솟값이 임계값 이상인지 (2) 유형/district가 서로 충돌하는
     * 쌍이 하나도 없는지 (3) 같은 연도가 중복되지 않는지를 다시 확인해서, 하나라도 실패하면
     * 그 컴포넌트 전체를 병합하지 않는다(부분적으로 병합하지 않음 - 전부 아니면 전무).</p>
     *
     * <p>실제 "어떤 컴포넌트를 병합할지" 판단(순수 계산, DB 무관)은 {@link
     * #computeChainComponentAudits}로 분리돼 있다 - {@link #computeSeriesGroupsInMemory}도
     * 같은 메서드를 공유해서, "이 record 목록만으로 series를 다시 계산하면" 항상 정확히 같은
     * chain linking 판단을 재현한다.</p>
     */
    private ChainLinkingResult runChainLinking(List<FestivalSeriesMembership> currentMemberships) {
        List<FestivalSeriesMembership> unmatchedMemberships = currentMemberships.stream()
                .filter(m -> m.getMatchMethod() == MatchMethod.UNMATCHED)
                .toList();

        Map<Long, FestivalSeriesMembership> membershipByRecordId = new LinkedHashMap<>();
        for (FestivalSeriesMembership m : unmatchedMemberships) {
            membershipByRecordId.put(m.getFestivalRecord().getId(), m);
        }

        List<MultiYearFestivalRecord> unmatchedRecords = unmatchedMemberships.stream()
                .map(FestivalSeriesMembership::getFestivalRecord)
                .toList();
        ChainDecision decision = computeChainComponentAudits(unmatchedRecords);

        List<FestivalSeries> removedSeries = new ArrayList<>();
        List<FestivalSeriesMembership> removedMemberships = new ArrayList<>();
        List<FestivalSeries> addedSeries = new ArrayList<>();
        List<FestivalSeriesMembership> addedMemberships = new ArrayList<>();

        for (ChainComponentAudit audit : decision.audits()) {
            if (!audit.applied()) {
                continue;
            }
            List<MultiYearFestivalRecord> members = audit.members();

            for (MultiYearFestivalRecord r : members) {
                FestivalSeriesMembership old = membershipByRecordId.get(r.getId());
                removedMemberships.add(old);
                removedSeries.add(old.getFestivalSeries());
            }
            membershipRepository.deleteAll(members.stream().map(r -> membershipByRecordId.get(r.getId())).toList());
            // festival_record_id에 UNIQUE 제약이 있어서, 같은 record를 가리키는 새 membership을
            // 곧바로 저장하면 Hibernate의 기본 flush 순서(insert가 delete보다 먼저 실행됨) 때문에
            // "삭제되기 전" 상태의 unique 제약을 위반한다 - 반드시 delete를 먼저 DB에 반영(flush)한
            // 뒤에 insert해야 한다.
            membershipRepository.flush();
            seriesRepository.deleteAll(members.stream().map(r -> membershipByRecordId.get(r.getId()).getFestivalSeries()).toList());
            seriesRepository.flush();

            MultiYearFestivalRecord anchor = pickAnchor(members);
            FestivalSeries mergedSeries = seriesRepository.save(FestivalSeries.builder()
                    .canonicalName(FestivalNameNormalizer.normalize(anchor.getFestivalName()))
                    .canonicalRegion(resolveRegionKey(anchor))
                    .canonicalDistrict(resolveDistrictKey(anchor))
                    .firstObservedYear(members.stream().mapToInt(MultiYearFestivalRecord::getDatasetYear).min().orElseThrow())
                    .lastObservedYear(members.stream().mapToInt(MultiYearFestivalRecord::getDatasetYear).max().orElseThrow())
                    .recordCount(members.size())
                    .matchStatus(FestivalSeriesMatchStatus.CHAIN_MERGED)
                    .scope(ClusterKey.of(anchor).scope())
                    .build());
            addedSeries.add(mergedSeries);
            for (MultiYearFestivalRecord r : members) {
                addedMemberships.add(saveMembership(r, mergedSeries, MatchMethod.CHAIN_HIGH_CONFIDENCE,
                        audit.check().meanPairwiseSimilarity(), MatchConfidence.HIGH));
            }
        }

        return new ChainLinkingResult(removedSeries, removedMemberships, addedSeries, addedMemberships,
                decision.audits(), decision.thresholdComparison());
    }

    /**
     * "UNMATCHED인 record 목록"만 입력으로 받아 strict chain linking이 어떤 컴포넌트를
     * 병합할지 결정하는 순수 계산 - DB를 전혀 읽거나 쓰지 않는다. {@link #runChainLinking}과
     * {@link #computeSeriesGroupsInMemory} 둘 다 이 메서드를 공유한다.
     */
    private ChainDecision computeChainComponentAudits(List<MultiYearFestivalRecord> unmatchedRecords) {
        List<Cluster> pool = new ArrayList<>();
        int idx = 0;
        for (MultiYearFestivalRecord r : unmatchedRecords) {
            pool.add(new Cluster(idx++, ClusterKey.of(r), List.of(r)));
        }

        Map<BucketKey, List<Cluster>> buckets = new LinkedHashMap<>();
        for (Cluster c : pool) {
            buckets.computeIfAbsent(new BucketKey(c.key().scope(), c.key().regionKey()), k -> new ArrayList<>()).add(c);
        }

        UnionFind chainUf = new UnionFind(pool);
        List<ChainEdge> allEdges = new ArrayList<>();
        for (List<Cluster> bucket : buckets.values()) {
            for (int i = 0; i < bucket.size(); i++) {
                for (int j = i + 1; j < bucket.size(); j++) {
                    ChainEdge edge = strictChainEdge(bucket.get(i), bucket.get(j));
                    if (edge != null) {
                        allEdges.add(edge);
                        chainUf.union(bucket.get(i).index(), bucket.get(j).index());
                    }
                }
            }
        }

        Map<Integer, List<Cluster>> byRoot = new LinkedHashMap<>();
        for (Cluster c : pool) {
            byRoot.computeIfAbsent(chainUf.find(c.index()), k -> new ArrayList<>()).add(c);
        }

        double[] candidateThresholds = {0.90, 0.92, 0.95};
        Map<String, Integer> thresholdComparison = new LinkedHashMap<>();
        for (double t : candidateThresholds) {
            thresholdComparison.put(fmtThreshold(t), 0);
        }

        List<ChainComponentAudit> audits = new ArrayList<>();
        int componentSeq = 0;
        for (List<Cluster> component : byRoot.values()) {
            if (component.size() < 2) {
                continue; // strict edge가 하나도 없어 혼자 남음 - 감사할 것 없음
            }
            componentSeq++;
            List<MultiYearFestivalRecord> members = component.stream().map(c -> c.members().get(0)).toList();
            PairwiseCheck check = fullPairwiseCheck(members);

            for (double t : candidateThresholds) {
                boolean wouldPass = check.minPairwiseSimilarity() >= t
                        && !check.typeConflict() && !check.districtConflict() && !check.duplicateYear();
                if (wouldPass) {
                    thresholdComparison.merge(fmtThreshold(t), 1, Integer::sum);
                }
            }

            boolean applied = check.minPairwiseSimilarity() >= CHAIN_CLUSTER_MIN_SIMILARITY
                    && !check.typeConflict() && !check.districtConflict() && !check.duplicateYear();
            String rejectionReason = applied ? null : buildRejectionReason(check);

            List<Integer> componentIndexes = component.stream().map(Cluster::index).toList();
            List<ChainEdge> componentEdges = allEdges.stream()
                    .filter(e -> componentIndexes.contains(e.clusterIndexA()) && componentIndexes.contains(e.clusterIndexB()))
                    .toList();

            audits.add(new ChainComponentAudit(componentSeq, members, pickAnchor(members), check, componentEdges, applied, rejectionReason));
        }

        return new ChainDecision(audits, thresholdComparison);
    }

    // ------------------------------------------------------------------
    // leakage-safe backtest 등 외부 소비자를 위한 순수 in-memory 재계산
    // ------------------------------------------------------------------

    /**
     * festivalSeries v1 규칙(결정적 클러스터링 + fuzzy HIGH 자동연결 + strict chain linking)을
     * DB에 전혀 읽거나 쓰지 않고 순수 in-memory로 재현한다 - {@link #linkAll()}과 완전히 같은
     * 알고리즘 코드를 그대로 재사용하지만({@link #buildDeterministicClusters}/{@link
     * #runFuzzyMatching}/{@link #buildFinalSeries}/{@link #computeChainComponentAudits} 공유),
     * 그 결과를 저장하지 않고 바로 반환한다.
     *
     * <p>leakage-safe backtest처럼 "이 record 목록(예: fold의 training 기간만)으로만 series를
     * 다시 계산하고 싶다"는 호출자를 위한 것이다 - 전체 2017~2026으로 만들어진 기존
     * {@code FestivalSeriesMembership}을 잘라 쓰면 미래 record가 transitive bridge 역할을 해
     * 과거 record의 series grouping에 영향을 줄 수 있는데, 이 메서드는 넘겨받은 목록만 보므로
     * 그런 영향이 원천적으로 불가능하다.</p>
     *
     * @return record id -> 이번 호출에서의 최종 소속 series를 나타내는 synthetic 그룹 id. 이
     *         id는 호출마다 새로 매겨지는 임의값이라(DB PK 아님) 다른 호출 결과와 비교하면 안
     *         되고, "이번 호출 안에서 어떤 record끼리 같은 그룹인지"만 의미가 있다.
     */
    public Map<Long, Long> computeSeriesGroupsInMemory(List<MultiYearFestivalRecord> records) {
        List<MultiYearFestivalRecord> sorted = records.stream()
                .sorted(Comparator.comparing(MultiYearFestivalRecord::getDatasetYear)
                        .thenComparing(r -> r.getSourceRowNumber() == null ? 0 : r.getSourceRowNumber())
                        .thenComparing(MultiYearFestivalRecord::getId))
                .toList();

        List<Cluster> clusters = buildDeterministicClusters(sorted);
        UnionFind uf = new UnionFind(clusters);
        runFuzzyMatching(clusters, uf, new ArrayList<>());
        List<SeriesBuild> builds = buildFinalSeries(clusters, uf);

        Map<Long, Long> groupIdByRecordId = new LinkedHashMap<>();
        List<MultiYearFestivalRecord> unmatchedRecords = new ArrayList<>();
        long nextId = 1;
        for (SeriesBuild build : builds) {
            long groupId = nextId++;
            for (MultiYearFestivalRecord r : build.allMembers()) {
                groupIdByRecordId.put(r.getId(), groupId);
            }
            if (build.allMembers().size() == 1) {
                unmatchedRecords.add(build.allMembers().get(0));
            }
        }

        ChainDecision decision = computeChainComponentAudits(unmatchedRecords);
        for (ChainComponentAudit audit : decision.audits()) {
            if (!audit.applied()) {
                continue;
            }
            long groupId = nextId++;
            for (MultiYearFestivalRecord r : audit.members()) {
                groupIdByRecordId.put(r.getId(), groupId);
            }
        }

        return groupIdByRecordId;
    }

    /**
     * 두 singleton(각각 record 1개짜리 Cluster) 사이의 strict chain edge 여부. 일반 fuzzy score()를
     * 재사용하되(band=HIGH 요구), 추가로 이름 유사도 0.95+, district/유형 충돌 없음, 연도 gap<=1을
     * 전부 만족해야 한다 - "small year gap"을 yearAdjacencySignal(gap<=5까지 중립)보다 훨씬 좁게
     * (gap<=1) 강제한다.
     */
    private ChainEdge strictChainEdge(Cluster a, Cluster b) {
        ScoredCandidate candidate = score(a, b);
        if (candidate == null || candidate.band() != MatchConfidence.HIGH) {
            return null;
        }
        if (candidate.nameSimilarity() < CHAIN_EDGE_MIN_NAME_SIMILARITY) {
            return null;
        }
        if (candidate.districtSignal() < 0 || candidate.typeSignal() < 0) {
            return null;
        }
        if (Math.abs(a.firstYear() - b.firstYear()) > CHAIN_EDGE_MAX_YEAR_GAP) {
            return null;
        }
        return new ChainEdge(a.index(), b.index(), a.members().get(0).getId(), a.firstYear(),
                b.members().get(0).getId(), b.firstYear(), candidate.nameSimilarity(), candidate.score());
    }

    /**
     * 컴포넌트(체인 후보) 내부 "모든" 쌍(edge로 연결됐는지와 무관하게)을 다시 채점한다 - A-B, B-C가
     * 각각 강해도 A-C가 실제로는 다른 축제일 수 있는 drift를 잡기 위함이다.
     */
    private PairwiseCheck fullPairwiseCheck(List<MultiYearFestivalRecord> members) {
        List<Double> similarities = new ArrayList<>();
        boolean typeConflict = false;
        boolean districtConflict = false;
        for (int i = 0; i < members.size(); i++) {
            for (int j = i + 1; j < members.size(); j++) {
                MultiYearFestivalRecord ra = members.get(i);
                MultiYearFestivalRecord rb = members.get(j);
                String ka = FestivalNameNormalizer.fuzzyKey(FestivalNameNormalizer.normalize(ra.getFestivalName()));
                String kb = FestivalNameNormalizer.fuzzyKey(FestivalNameNormalizer.normalize(rb.getFestivalName()));
                similarities.add(LevenshteinSimilarity.ratio(ka, kb));

                String da = resolveDistrictKey(ra);
                String db = resolveDistrictKey(rb);
                if (da != null && db != null && !da.equals(db)) {
                    districtConflict = true;
                }
                var typesA = splitTypes(ra.getFestivalType());
                var typesB = splitTypes(rb.getFestivalType());
                if (!typesA.isEmpty() && !typesB.isEmpty() && typesA.stream().noneMatch(typesB::contains)) {
                    typeConflict = true;
                }
            }
        }
        boolean duplicateYear = members.stream().map(MultiYearFestivalRecord::getDatasetYear).distinct().count() < members.size();
        double min = similarities.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double mean = similarities.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        return new PairwiseCheck(min, mean, typeConflict, districtConflict, duplicateYear);
    }

    private MultiYearFestivalRecord pickAnchor(List<MultiYearFestivalRecord> members) {
        return members.stream()
                .min(Comparator.comparing(MultiYearFestivalRecord::getDatasetYear).thenComparing(MultiYearFestivalRecord::getId))
                .orElseThrow();
    }

    private String buildRejectionReason(PairwiseCheck check) {
        List<String> reasons = new ArrayList<>();
        if (check.minPairwiseSimilarity() < CHAIN_CLUSTER_MIN_SIMILARITY) {
            reasons.add("컴포넌트 내 최소 이름유사도 %.3f < %.2f".formatted(check.minPairwiseSimilarity(), CHAIN_CLUSTER_MIN_SIMILARITY));
        }
        if (check.typeConflict()) {
            reasons.add("festivalType 충돌 쌍 존재");
        }
        if (check.districtConflict()) {
            reasons.add("district 충돌 쌍 존재(서로 다른 실제 시군구)");
        }
        if (check.duplicateYear()) {
            reasons.add("같은 연도(datasetYear) 중복 record 존재");
        }
        return String.join("; ", reasons);
    }

    private String fmtThreshold(double t) {
        return "%.2f".formatted(t);
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
                // 모든 다른 클러스터(다행짜리든 다른 singleton이든)를 빠짐없이 평가한다. 예전에는
                // "singleton-singleton 쌍은 인덱스가 작은 쪽만 평가"해서 계산을 아꼈는데, 이게
                // 단순 중복계산 방지가 아니라 실제 정보 누락이었다 - 체인 중간 연도(예: 2018)는
                // 자신보다 index가 작은 2017쪽 후보를 아예 못 보고 2019쪽만 봐서, 실제로는
                // 2017/2019 둘 다에 HIGH로 걸리는 애매한 경우인데도 "유일한 HIGH 후보"로
                // 오인해 곧바로(체인 검증 없이) 자동 병합해버렸다. 대칭적으로 전부 평가해야
                // ambiguous 판정과 strict chain linking의 UNMATCHED 풀 구성이 올바르다.
                List<ScoredCandidate> forSource = new ArrayList<>();
                Map<Integer, Boolean> yearConflictByTargetIndex = new LinkedHashMap<>();
                for (Cluster target : bucket) {
                    if (target.index() == source.index()) {
                        continue;
                    }
                    ScoredCandidate candidate = score(source, target);
                    if (candidate != null) {
                        forSource.add(candidate);
                        yearConflictByTargetIndex.put(target.index(), hasYearOverlap(source, target));
                    }
                }
                forSource.sort(Comparator.comparingDouble(ScoredCandidate::score).reversed());

                // 같은 datasetYear를 공유하는 후보는 이름/지역/유형이 아무리 비슷해도 절대 자동
                // 병합(union) 대상이 될 수 없다 - 서로 다른 두 record가 "같은 연도의 같은 series"에
                // 들어가는 것은 항상 오류다. highCount/applied 판단에서는 제외하되(자동 병합 금지),
                // 검토용 후보 목록(forSource, 저장)에서는 그대로 노출해 사람이 볼 수 있게 한다.
                List<ScoredCandidate> highEligible = forSource.stream()
                        .filter(c -> c.band() == MatchConfidence.HIGH)
                        .filter(c -> !yearConflictByTargetIndex.getOrDefault(c.targetClusterIndex(), false))
                        .toList();
                ScoredCandidate applied = highEligible.size() == 1 ? highEligible.get(0) : null;
                // highEligible.size() >= 2: 서로 다른 series를 가리키는 HIGH 후보가 둘 이상 -> 애매하므로 연결하지 않는다.

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

    /**
     * 두 클러스터가 같은 datasetYear를 하나라도 공유하는지. 공유하면 아무리 이름/지역/유형이
     * 비슷해도 절대 자동 병합해서는 안 된다 - 같은 연도에 서로 다른 record 2개가 하나의
     * series에 들어가는 것은 항상 데이터 오류다(예산 추정 등 다운스트림에서 "그 해 값이 2개"인
     * 모순을 만든다).
     */
    private boolean hasYearOverlap(Cluster a, Cluster b) {
        for (MultiYearFestivalRecord ra : a.members()) {
            for (MultiYearFestivalRecord rb : b.members()) {
                // getDatasetYear()는 Integer(박싱)이라 "=="는 -128~127 캐시 범위 밖(연도는 항상
                // 이 범위 밖)에서 레퍼런스 비교가 되어 버려 값이 같아도 거의 항상 false가 된다 -
                // 반드시 .equals()로 값 비교해야 한다.
                if (ra.getDatasetYear() != null && ra.getDatasetYear().equals(rb.getDatasetYear())) {
                    return true;
                }
            }
        }
        return false;
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

    // ------------------------------------------------------------------
    // 4) strict chain linking 전용 내부 타입
    // ------------------------------------------------------------------

    /** strict 조건(이름유사도 0.95+, district/유형 충돌 없음, 연도 gap<=1, HIGH)을 만족하는 두 record 사이의 edge. */
    record ChainEdge(int clusterIndexA, int clusterIndexB, long recordIdA, int yearA, long recordIdB, int yearB,
                      double nameSimilarity, double score) {
    }

    /** 컴포넌트 전체에 대한 전수 pairwise 재검증 결과. */
    record PairwiseCheck(double minPairwiseSimilarity, double meanPairwiseSimilarity,
                          boolean typeConflict, boolean districtConflict, boolean duplicateYear) {
    }

    /** strict chain linking이 평가한 연결요소 1개(자동 병합됐든 거부됐든 전부 기록). */
    record ChainComponentAudit(int componentId, List<MultiYearFestivalRecord> members, MultiYearFestivalRecord anchor,
                                PairwiseCheck check, List<ChainEdge> edges, boolean applied, String rejectionReason) {
    }

    /** {@link #computeChainComponentAudits}의 순수 계산 결과(DB 무관) - 병합 여부 판단 + threshold 비교용. */
    record ChainDecision(List<ChainComponentAudit> audits, Map<String, Integer> thresholdComparison) {
    }

    record ChainLinkingResult(List<FestivalSeries> removedSeries, List<FestivalSeriesMembership> removedMemberships,
                               List<FestivalSeries> addedSeries, List<FestivalSeriesMembership> addedMemberships,
                               List<ChainComponentAudit> componentAudits, Map<String, Integer> thresholdComparison) {
    }
}