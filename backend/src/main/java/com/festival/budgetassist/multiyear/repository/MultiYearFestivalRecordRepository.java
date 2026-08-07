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
}