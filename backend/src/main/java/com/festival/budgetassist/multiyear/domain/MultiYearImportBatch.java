package com.festival.budgetassist.multiyear.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.festival.budgetassist.festival.domain.ImportStatus;

/**
 * 다년도(2017~2026) sanitized CSV Import 실행 이력.
 *
 * <p>기존 {@link com.festival.budgetassist.festival.domain.DatasetImportBatch}는 연도 1개짜리
 * 원본 엑셀 파일 하나에 대응하는 배치이지만, 이 배치는 여러 연도를 한 번에 담은 CSV 파일 1개
 * 전체에 대응한다. {@code fileHash}가 같으면 완전한 no-op으로 취급하는 것도 동일한 원칙이다.</p>
 *
 * <p>{@link com.festival.budgetassist.festival.domain.ImportStatus}(SUCCESS/FAILED)를 그대로
 * 재사용한다 — 의미가 동일한 값을 새로 정의할 이유가 없다.</p>
 */
@Entity
@Table(name = "multi_year_import_batch", uniqueConstraints = @UniqueConstraint(name = "uk_multi_year_import_batch_file_hash", columnNames = "file_hash"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MultiYearImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_file_name", nullable = false, length = 512)
    private String originalFileName;

    /** CSV 파일 바이트 전체의 SHA-256 해시(16진수 소문자 64자). */
    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    /** 이 CSV에 담긴 연도 범위 표기 (예: "2017-2026"), 감사 로그 가독성용. */
    @Column(name = "dataset_years", length = 100)
    private String datasetYears;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    /** budgetQualityFlag = UNIT_SCALE_SUSPECT 로 알고리즘 후보에서 제외된 행 수. */
    @Column(name = "unit_scale_suspect_rows", nullable = false)
    private int unitScaleSuspectRows;

    /** budgetQualityFlag = MISSING_OR_NONPOSITIVE 행 수. */
    @Column(name = "missing_or_nonpositive_budget_rows", nullable = false)
    private int missingOrNonpositiveBudgetRows;

    /** durationDays가 끝내 null인 행 수(원문에서 확정적으로 읽어내지 못함). */
    @Column(name = "missing_duration_rows", nullable = false)
    private int missingDurationRows;

    @Column(name = "covid_affected_rows", nullable = false)
    private int covidAffectedRows;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ImportStatus status;

    /**
     * {@code @Lob}만으로는 Hibernate가 MySQL에서 {@code TINYTEXT}(255바이트)로 매핑해 실제
     * 경고 요약(한글, 최대 수천 바이트)이 truncation 예외를 일으켰다 - {@code columnDefinition}으로
     * 명시적으로 충분히 큰 타입을 강제한다.
     */
    @Lob
    @Column(name = "error_summary", columnDefinition = "LONGTEXT")
    private String errorSummary;
}