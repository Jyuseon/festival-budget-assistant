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
}