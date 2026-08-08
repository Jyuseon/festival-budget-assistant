package com.festival.budgetassist.multiyear.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 연도(datasetYear)별 "이 해의 다년도 개최계획 데이터가 공개 완료됐는가" 운영자 표시.
 *
 * <p>{@link MultiYearImportBatch}를 재사용하지 않은 이유: 지금까지의 CSV Import는 2017~2026을
 * 통째로 담은 파일 1개를 배치 1건으로 처리해 왔다({@code datasetYears="2017-2026"}처럼 batch가
 * 여러 연도를 동시에 표현한다) - "이 연도가 공개 완료됐는가"는 batch 단위가 아니라 연도 단위
 * 개념이라 자연스럽게 맞물리는 기존 컬럼이 없다. 대신 이 작은 별도 테이블로 연도별 상태만
 * 명시적으로 관리한다.</p>
 *
 * <p>외부 공개 API 자동 확인 기능은 이번 범위에 없다 - 운영자가 수동으로 {@link
 * MultiYearDatasetPublicationStatusValue#PUBLISHED_COMPLETE}로 표시해야 한다. 행이 아예 없는
 * 연도는 {@link com.festival.budgetassist.multiyear.backtest.MultiYearBacktestService}가
 * {@code PARTIAL}과 동일하게(=same-year reference 불가) 취급한다(안전한 기본값).</p>
 */
@Entity
@Table(name = "multi_year_dataset_publication_status", uniqueConstraints = @UniqueConstraint(name = "uk_multiyear_dataset_publication_status_year", columnNames = "dataset_year"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MultiYearDatasetPublicationStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dataset_year", nullable = false)
    private Integer datasetYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MultiYearDatasetPublicationStatusValue status;

    /** status가 PUBLISHED_COMPLETE로 바뀐 시각(운영자가 표시한 시각). PARTIAL이면 null. */
    @Column(name = "published_at")
    private Instant publishedAt;
}