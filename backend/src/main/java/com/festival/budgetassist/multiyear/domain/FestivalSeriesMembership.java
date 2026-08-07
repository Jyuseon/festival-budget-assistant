package com.festival.budgetassist.multiyear.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.CreationTimestamp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@link MultiYearFestivalRecord} 한 행이 어떤 {@link FestivalSeries}에, 어떤 방법/점수로
 * 연결됐는지. 행마다 정확히 1개씩 존재한다(festivalRecordId에 유니크 제약).
 */
@Entity
@Table(name = "festival_series_membership",
        uniqueConstraints = @UniqueConstraint(name = "uk_fsm_festival_record", columnNames = "festival_record_id"),
        indexes = {
                @Index(name = "idx_fsm_series", columnList = "festival_series_id"),
                @Index(name = "idx_fsm_match_method", columnList = "match_method")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FestivalSeriesMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "festival_record_id", nullable = false)
    private MultiYearFestivalRecord festivalRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "festival_series_id", nullable = false)
    private FestivalSeries festivalSeries;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_method", nullable = false, length = 20)
    private MatchMethod matchMethod;

    /** EXACT/NORMALIZED_EXACT/UNMATCHED는 null. FUZZY(HIGH로 실제 연결된 경우)만 채워진다. */
    @Column(name = "match_score")
    private Double matchScore;

    /** FUZZY일 때만 채워진다(항상 HIGH - MEDIUM/LOW는 연결되지 않고 후보로만 남는다). */
    @Enumerated(EnumType.STRING)
    @Column(name = "match_confidence", length = 10)
    private MatchConfidence matchConfidence;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}