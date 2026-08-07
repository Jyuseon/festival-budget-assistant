package com.festival.budgetassist.multiyear.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * fuzzy 매칭 과정에서 채점된 모든 후보(HIGH/MEDIUM/LOW)의 감사 로그 - "검토 목록"의 데이터
 * 원천이다. 실제로 연결(merge)에 쓰인 HIGH 후보만 {@code applied=true}이고, MEDIUM/LOW는
 * 항상 {@code applied=false}로 남아 사람이 검토할 수 있게 한다.
 *
 * <p>{@link FestivalSeries}를 직접 참조하지 않고 두 행({@code sourceRecord}/{@code
 * candidateRecord})을 참조한다 - fuzzy merge로 series가 합쳐지며 한쪽 series row가 없어질 수
 * 있는데, 그때도 이 감사 로그가 깨진 참조를 갖지 않도록 하기 위함이다(레코드 자체는 절대
 * 삭제되지 않는다).</p>
 */
@Entity
@Table(name = "festival_series_match_candidate", indexes = {
        @Index(name = "idx_fsmc_source_record", columnList = "source_record_id"),
        @Index(name = "idx_fsmc_confidence_band", columnList = "confidence_band")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FestivalSeriesMatchCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 채점 당시 아직 다른 행과 결정적으로 묶이지 않았던(singleton) 쪽. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_record_id", nullable = false)
    private MultiYearFestivalRecord sourceRecord;

    /** 비교 대상 series를 대표하는 행(그 series의 anchor record). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_record_id", nullable = false)
    private MultiYearFestivalRecord candidateRecord;

    @Column(name = "name_similarity", nullable = false)
    private double nameSimilarity;

    /** district 신호(같으면 +, 다르면 -, 비교 불가면 0) - score에 반영되기 전 raw 보정치. */
    @Column(name = "district_signal", nullable = false)
    private double districtSignal;

    @Column(name = "year_adjacency_signal", nullable = false)
    private double yearAdjacencySignal;

    @Column(name = "type_signal", nullable = false)
    private double typeSignal;

    @Column(name = "score", nullable = false)
    private double score;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence_band", nullable = false, length = 10)
    private MatchConfidence confidenceBand;

    @Column(name = "applied", nullable = false)
    private boolean applied;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}