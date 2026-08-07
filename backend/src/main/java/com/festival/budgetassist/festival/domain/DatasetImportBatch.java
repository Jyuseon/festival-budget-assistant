package com.festival.budgetassist.festival.domain;

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

/**
 * 엑셀 Import 실행 이력. 성공/실패 모두 기록해 감사 추적이 가능하게 한다.
 *
 * <p>동일 파일 해시로 이미 {@link ImportStatus#SUCCESS} 처리된 이력이 있으면 재적재는
 * 완전한 no-op으로 취급하며(이 테이블에도 아무 것도 기록하지 않음), 그 외의 모든 시도(성공/실패)는
 * 감사 목적으로 이 테이블에 기록한다.</p>
 */
@Entity
@Table(name = "dataset_import_batch", uniqueConstraints = @UniqueConstraint(name = "uk_dataset_import_batch_file_hash", columnNames = "file_hash"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatasetImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dataset_year", nullable = false)
    private Integer datasetYear;

    @Column(name = "original_file_name", nullable = false, length = 512)
    private String originalFileName;

    /** 원본 파일 바이트의 SHA-256 해시(16진수 소문자 64자). 파일명이 아닌 내용 기준 식별자. */
    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    /** 예산이 숫자이고 0보다 큰(CONFIRMED) 유효 표본 수. */
    @Column(name = "valid_budget_rows", nullable = false)
    private int validBudgetRows;

    /** totalRows - validBudgetRows (미확정/무응답/0원 등 예산 추정에 쓸 수 없는 행 수). */
    @Column(name = "invalid_rows", nullable = false)
    private int invalidRows;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ImportStatus status;

    /**
     * 전체 집계 리포트(JSON)와 경고 메시지를 담는다. 이름은 가이드 문서의 DatasetImportBatch
     * 스키마를 그대로 따랐지만, 실패 사유뿐 아니라 성공 시의 상세 통계도 함께 저장한다.
     */
    @Lob
    @Column(name = "error_summary")
    private String errorSummary;
}