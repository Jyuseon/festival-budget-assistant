package com.festival.budgetassist.multiyear.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.festival.budgetassist.multiyear.domain.BudgetQualityFlag;
import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;

public interface MultiYearFestivalRecordRepository extends JpaRepository<MultiYearFestivalRecord, Long> {

    long countByDatasetYear(Integer datasetYear);

    long countByDatasetYearAndBudgetQualityFlag(Integer datasetYear, BudgetQualityFlag budgetQualityFlag);

    @Modifying
    @Query("delete from MultiYearFestivalRecord r where r.datasetYear = :datasetYear")
    int deleteByDatasetYear(@Param("datasetYear") Integer datasetYear);

    List<MultiYearFestivalRecord> findByDatasetYear(Integer datasetYear);

    /** 향후 알고리즘 후보 모집단 조회용 - UNIT_SCALE_SUSPECT로 플래그된 행은 제외. */
    List<MultiYearFestivalRecord> findByDatasetYearAndBudgetQualityFlagNot(Integer datasetYear, BudgetQualityFlag budgetQualityFlag);

    List<MultiYearFestivalRecord> findByImportBatchId(Long batchId);

    /**
     * leakage-safe 다년도 실험 API용 - {@code datasetYear < year}인 record만 DB 쿼리 단계에서
     * 걸러 온다. {@code year} 자체(예: 2026)나 그 이후 연도는 절대 포함되지 않으므로, 매 요청마다
     * 전체 10,198행을 애플리케이션 메모리로 가져온 뒤 걸러내는 것보다 효율적이다.
     */
    List<MultiYearFestivalRecord> findByDatasetYearLessThan(Integer year);

    /**
     * planningYear 일반화({@code estimateForPlanning})용 - {@code datasetYear <= year}인 record만
     * 걸러 온다. {@code ReferenceDataPolicy.INCLUDE_PUBLISHED_SAME_YEAR}가 planningYear 자체의
     * 데이터도 참고할 수 있어야 하므로 {@code findByDatasetYearLessThan}보다 한 해 더 넓게 가져온다
     * - 실제 어느 정책을 적용할지는 {@code MultiYearBacktestService.estimateForPlanning} 내부의
     * {@code MultiYearReferenceYearFilter}가 다시 한 번 정확하게 결정한다(이 쿼리는 성능 목적의
     * 상한선일 뿐, leakage 방지의 최종 방어선이 아니다).
     */
    List<MultiYearFestivalRecord> findByDatasetYearLessThanEqual(Integer year);

    /**
     * 현재 DB에 존재하는 가장 최신 datasetYear - "선택 가능한 planningYear 목록"을 연도 상수를
     * 하드코딩하지 않고 계산하기 위한 유일한 근거다({@code MultiYearExperimentalEstimateService}
     * 참고: {@code [maxDatasetYear, maxDatasetYear + 1]}). 데이터가 아예 없으면 null.
     */
    @Query("select max(r.datasetYear) from MultiYearFestivalRecord r")
    Integer findMaxDatasetYear();

    /** 관리자 publication status 화면용 - 실제 데이터가 존재하는 연도만 오름차순으로. */
    @Query("select distinct r.datasetYear from MultiYearFestivalRecord r order by r.datasetYear")
    List<Integer> findDistinctDatasetYears();
}