package com.festival.budgetassist.multiyear.domain;

import java.math.BigDecimal;
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

import com.festival.budgetassist.festival.domain.Region;
import com.festival.budgetassist.festival.domain.VenueType;

/**
 * 다년도(2017~2026) sanitized CSV 한 행.
 *
 * <p>{@code claude_multiyear_festival_package/guide/README_FOR_VSCODE_CLAUDE.md} 2절 "DB에서
 * 반드시 보존"이 요구하는 항목을 전부 필드로 담는다: datasetYear, source hash/sheet/row,
 * raw+normalized 축제유형, raw venue+nullable venueType, raw period+nullable duration,
 * raw budget+normalized budget, budgetQualityFlag, covidAffected.</p>
 *
 * <p>기존 2026 전용 {@link com.festival.budgetassist.festival.domain.FestivalRecord}와는
 * 완전히 별도의 엔티티/테이블이다 — 이 클래스를 추가하거나 값을 채워도 기존 2026 production
 * Import/BudgetEstimator/confidence 경로에는 전혀 영향이 없다.</p>
 *
 * <p>구 연도 데이터는 원본에 없는 항목(개최장소유형, 개최주기 코드화, 명시적 총일수 등)이 많다.
 * 이 엔티티는 그런 항목을 억지로 채우지 않고 전부 nullable로 둔다 — "값이 없다"와 "값이
 * OTHER/0"을 구분하는 것이 안내서의 명시적 요구사항이다.</p>
 */
@Entity
@Table(name = "multi_year_festival_record", indexes = {
        @Index(name = "idx_myfr_dataset_year", columnList = "dataset_year"),
        @Index(name = "idx_myfr_import_batch", columnList = "import_batch_id"),
        @Index(name = "idx_myfr_source", columnList = "source_sha256,source_sheet,source_row")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MultiYearFestivalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- 원본 추적 (CSV: dataset_year, source_sheet, source_row, source_sha256) ---

    @Column(name = "dataset_year", nullable = false)
    private Integer datasetYear;

    @Column(name = "source_sheet", length = 100)
    private String sourceSheet;

    @Column(name = "source_row")
    private Integer sourceRowNumber;

    /** 원본 엑셀 파일 바이트의 SHA-256(16진수 64자). manifest/source_manifest.json과 대조 가능. */
    @Column(name = "source_sha256", length = 64)
    private String sourceSha256;

    // --- 지역 ---

    @Column(name = "region_raw", length = 100)
    private String regionRaw;

    /** CSV가 이미 최소 정규화한 지역 표기(예: "서울"). */
    @Column(name = "region_text", length = 50)
    private String regionText;

    /** {@link #regionText}를 {@link Region} enum으로 best-effort 매핑한 값. 매핑 실패 시 null(강제 추론 금지). */
    @Enumerated(EnumType.STRING)
    @Column(name = "region_code", length = 20)
    private Region regionCode;

    @Column(name = "district_raw", length = 100)
    private String districtRaw;

    @Column(name = "district_text", length = 100)
    private String districtText;

    // --- 축제명/유형 ---

    @Column(name = "festival_name", nullable = false, length = 255)
    private String festivalName;

    @Column(name = "festival_type_raw", length = 255)
    private String festivalTypeRaw;

    /**
     * 1차 canonical 매핑 결과. 복합 유형은 {@code "COMMUNITY|LOCAL_SPECIALTY"}처럼 파이프로
     * 연결되어 있고 OTHER/UNKNOWN도 등장하므로, 기존 5종 전용
     * {@link com.festival.budgetassist.festival.domain.FestivalType} enum에는 담지 않는다
     * (guide DATA_DICTIONARY: "최종 분류 확정값이 아님").
     */
    @Column(name = "festival_type", length = 255)
    private String festivalType;

    // --- 개최 장소 ---

    /** CSV venue_raw. */
    @Column(name = "venue_name_raw", length = 500)
    private String venueNameRaw;

    @Column(name = "venue_type_raw", length = 100)
    private String venueTypeRaw;

    /**
     * 2025~2026 원본에만 존재하는 장소 유형. 2017~2024는 원본 자체에 없는 개념이므로 항상 null이고,
     * OTHER(기타)로 강제 매핑하지 않는다 — OTHER는 실제로 "기타"라고 응답된 행에만 쓴다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "venue_type", length = 20)
    private VenueType venueType;

    // --- 개최 기간 ---

    @Column(name = "period_raw", length = 255)
    private String periodRaw;

    /** 원본 총일수 또는 원문에서 명시적으로 읽은 일수만 저장. 없으면 null(0이나 임의값 금지). */
    @Column(name = "duration_days")
    private Integer durationDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "duration_source", length = 20)
    private CsvDurationSource durationSource;

    @Column(name = "duration_note_raw", length = 255)
    private String durationNoteRaw;

    // --- 개최 주기 / 진행 상태 ---

    /**
     * 원문 자유서술이 매우 다양해(예: "격년(매 2회)", "코로나19 상황에 따라 추후 결정") 고정
     * enum으로 강제하지 않고 원문 그대로 보존한다.
     */
    @Column(name = "cycle_raw", length = 255)
    private String cycleRaw;

    @Column(name = "event_mode_raw", length = 255)
    private String eventModeRaw;

    @Column(name = "event_status_raw", length = 255)
    private String eventStatusRaw;

    /** 2020~2021 COVID 영향 연도 표시. 예산 모델에서 포함/제외/저가중치 실험에 사용할 플래그. */
    @Column(name = "covid_affected", nullable = false)
    private boolean covidAffected;

    @Column(name = "first_held_year")
    private Integer firstHeldYear;

    // --- 예산 (원본 단위 백만원 그대로 보존 - KRW 환산은 이후 알고리즘 단계에서) ---

    @Column(name = "budget_total_raw", length = 100)
    private String budgetTotalRaw;

    @Column(name = "budget_total_million", precision = 18, scale = 3)
    private BigDecimal budgetTotalMillion;

    @Column(name = "budget_national_million", precision = 18, scale = 3)
    private BigDecimal budgetNationalMillion;

    @Column(name = "budget_local_million", precision = 18, scale = 3)
    private BigDecimal budgetLocalMillion;

    @Column(name = "budget_other_million", precision = 18, scale = 3)
    private BigDecimal budgetOtherMillion;

    @Enumerated(EnumType.STRING)
    @Column(name = "budget_quality_flag", nullable = false, length = 30)
    private BudgetQualityFlag budgetQualityFlag;

    @Column(name = "budget_quality_note", length = 255)
    private String budgetQualityNote;

    // --- 방문객 ---

    @Column(name = "visitor_total_persons")
    private Long visitorTotalPersons;

    // --- Import 메타데이터 ---

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_batch_id", nullable = false)
    private MultiYearImportBatch importBatch;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}