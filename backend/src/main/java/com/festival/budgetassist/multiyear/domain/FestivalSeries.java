package com.festival.budgetassist.multiyear.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 동일 축제로 판단된 {@link MultiYearFestivalRecord}(festival-year row)들의 묶음.
 *
 * <p>{@code MultiYearFestivalRecord}에 festivalSeriesId를 직접 두지 않고 별도 엔티티+
 * {@link FestivalSeriesMembership}로 분리했다 - series 판정은 계속 재실행/수정될 분석
 * 산출물이라, CSV Import로 채워지는 원본 레코드와 생명주기를 분리하는 편이 안전하다
 * (재실행 시 이 테이블들만 지우고 다시 채우면 됨. 원본 레코드는 절대 건드리지 않는다).</p>
 *
 * <p>이 엔티티/연결 로직은 순수 분석 단계다 - {@code MultiYearBudgetEstimator}(아직 없음)나
 * 기존 2026 production {@code BudgetEstimatorService}에는 전혀 연결되어 있지 않다.</p>
 */
@Entity
@Table(name = "festival_series", indexes = {
        @Index(name = "idx_festival_series_canonical_name", columnList = "canonical_name"),
        @Index(name = "idx_festival_series_region", columnList = "canonical_region")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FestivalSeries {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** {@link FestivalNameNormalizer}를 거친 대표 축제명 (연도/회차 등 제거). */
    @Column(name = "canonical_name", nullable = false, length = 255)
    private String canonicalName;

    /** 광역지역 표기(Region enum으로 인식된 경우 표시명, 아니면 CSV 원문 정규화 텍스트). */
    @Column(name = "canonical_region", length = 50)
    private String canonicalRegion;

    /** 시군구. {@link SeriesScope#REGION_LEVEL}이면 항상 null. */
    @Column(name = "canonical_district", length = 100)
    private String canonicalDistrict;

    @Column(name = "first_observed_year", nullable = false)
    private Integer firstObservedYear;

    @Column(name = "last_observed_year", nullable = false)
    private Integer lastObservedYear;

    @Column(name = "record_count", nullable = false)
    private int recordCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false, length = 20)
    private FestivalSeriesMatchStatus matchStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20)
    private SeriesScope scope;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}