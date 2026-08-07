package com.festival.budgetassist.festival.domain;

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
 * 지역축제 개최 계획 1건 (원본 엑셀 '조사표' 시트 1개 데이터 행).
 *
 * <p>안내서 3.3절/7.1절 및 사용자 결정에 따라 다음은 이 엔티티에 절대 포함하지 않는다.</p>
 * <ul>
 *   <li>AF~AH 주최 전담조직 정보 (가이드 7.1 DB 모델에도 없음)</li>
 *   <li>Z 국비지원 부처명 (가이드 7.1 DB 모델에도 없음)</li>
 *   <li>K 개최지 읍면동 (가이드가 MVP 미사용으로 명시)</li>
 *   <li>AE 방문객 비고, AI~AN 담당자·연락처·비고 (개인정보 및 자유서술)</li>
 * </ul>
 */
@Entity
@Table(name = "festival_record", indexes = {
        @Index(name = "idx_festival_record_dataset_year", columnList = "dataset_year"),
        @Index(name = "idx_festival_record_import_batch", columnList = "import_batch_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FestivalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dataset_year", nullable = false)
    private Integer datasetYear;

    /** 원본 엑셀 B열(연번). 엑셀 물리 행 번호가 아니라 원본 데이터의 일련번호다. */
    @Column(name = "source_row_number", nullable = false)
    private Integer sourceRowNumber;

    @Column(name = "festival_name", nullable = false, length = 255)
    private String festivalName;

    // --- 주관 지역 (C, D열) ---

    @Enumerated(EnumType.STRING)
    @Column(name = "region_code", nullable = false, length = 20)
    private Region region;

    /** 원본 표기 그대로의 지역명 (예: "서울"). region enum의 표시명과 항상 같지만 원본 보존 원칙에 따라 별도 저장. */
    @Column(name = "region_name", nullable = false, length = 20)
    private String regionName;

    /** 기초자치단체명. 원본이 "-"인 126건은 null로 정규화한다. */
    @Column(name = "administrative_district", length = 50)
    private String administrativeDistrict;

    // --- 축제 유형 및 개최 장소 (F, G, H, I, J열) ---

    @Enumerated(EnumType.STRING)
    @Column(name = "festival_type", nullable = false, length = 30)
    private FestivalType festivalType;

    @Column(name = "venue_name", length = 255)
    private String venueName;

    @Enumerated(EnumType.STRING)
    @Column(name = "venue_type", nullable = false, length = 20)
    private VenueType venueType;

    /** 개최지 시도 (I열, 코드 접두사 제거 후 표기명). */
    @Column(name = "venue_region", length = 20)
    private String venueRegion;

    /** 개최지 시군구 (J열). */
    @Column(name = "venue_district", length = 50)
    private String venueDistrict;

    // --- 개최 기간 (L~S열) ---

    @Column(name = "start_year")
    private Integer startYear;
    @Column(name = "start_month")
    private Integer startMonth;
    @Column(name = "start_day")
    private Integer startDay;

    @Column(name = "end_year")
    private Integer endYear;
    @Column(name = "end_month")
    private Integer endMonth;
    @Column(name = "end_day")
    private Integer endDay;

    /** 총 개최 일수. R열 우선, 없으면 시작~종료일로 계산, 그래도 불가하면 null. */
    @Column(name = "duration_days")
    private Integer durationDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "duration_source", length = 20)
    private DurationSource durationSource;

    /** 개최기간 비고(S열) 원문. "미정", "주 1회" 등 자유서술이 섞여 있어 정규화하지 않고 그대로 보존. */
    @Column(name = "duration_note", length = 255)
    private String durationNote;

    // --- 개최 주기 (T, U열) ---

    @Enumerated(EnumType.STRING)
    @Column(name = "cycle_type", nullable = false, length = 20)
    private CycleType cycleType;

    /** 최초 개최연도. 원본이 "미상"인 3건은 null로 정규화한다. */
    @Column(name = "first_held_year")
    private Integer firstHeldYear;

    /** firstHeldYear가 null일 때의 원본 텍스트(예: "미상"). 안내서 5.4의 "null + 원본값 보존" 원칙. */
    @Column(name = "first_held_year_note", length = 20)
    private String firstHeldYearNote;

    // --- 예산 (V~Y열, 백만원 -> 원 단위 변환) ---

    @Column(name = "total_budget_krw")
    private Long totalBudgetKrw;
    @Column(name = "national_budget_krw")
    private Long nationalBudgetKrw;
    @Column(name = "local_budget_krw")
    private Long localBudgetKrw;
    @Column(name = "other_budget_krw")
    private Long otherBudgetKrw;

    @Enumerated(EnumType.STRING)
    @Column(name = "budget_status", nullable = false, length = 20)
    private BudgetStatus budgetStatus;

    // --- 방문객 수 (AA~AD열). 2차 기능용으로 적재만 하고 1차 알고리즘/API에는 사용하지 않는다. ---

    @Column(name = "previous_visitors")
    private Integer previousVisitors;
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_visitors_status", length = 20)
    private VisitorCountStatus previousVisitorsStatus;

    @Column(name = "domestic_visitors")
    private Integer domesticVisitors;
    @Enumerated(EnumType.STRING)
    @Column(name = "domestic_visitors_status", length = 20)
    private VisitorCountStatus domesticVisitorsStatus;

    @Column(name = "foreign_visitors")
    private Integer foreignVisitors;
    @Enumerated(EnumType.STRING)
    @Column(name = "foreign_visitors_status", length = 20)
    private VisitorCountStatus foreignVisitorsStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "visitor_measurement_method", length = 20)
    private VisitorMeasurementMethod visitorMeasurementMethod;

    // --- Import 메타데이터 ---

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_batch_id", nullable = false)
    private DatasetImportBatch importBatch;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}