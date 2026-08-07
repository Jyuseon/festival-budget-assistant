package com.festival.budgetassist.festival.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Import를 막지는 않지만 사람이 확인해야 하는 행 단위 데이터 품질 이슈.
 * /admin/datasets/latest/issues API가 이 테이블을 그대로 조회한다.
 */
@Entity
@Table(name = "import_warning", indexes = {
        @Index(name = "idx_import_warning_batch", columnList = "batch_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportWarning {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private DatasetImportBatch batch;

    /** 엑셀 B열(연번). 행에 국한되지 않는 경고라면 null일 수 있다(현재는 항상 채워짐). */
    @Column(name = "source_row_number")
    private Integer sourceRowNumber;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}