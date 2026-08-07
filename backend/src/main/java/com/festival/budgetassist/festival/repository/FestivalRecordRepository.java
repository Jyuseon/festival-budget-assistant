package com.festival.budgetassist.festival.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.festival.budgetassist.festival.domain.BudgetStatus;
import com.festival.budgetassist.festival.domain.FestivalRecord;

public interface FestivalRecordRepository extends JpaRepository<FestivalRecord, Long> {

    long countByDatasetYear(Integer datasetYear);

    @Modifying
    @Query("delete from FestivalRecord f where f.datasetYear = :datasetYear")
    int deleteByDatasetYear(@Param("datasetYear") Integer datasetYear);

    /** 관리자 조회용 - 특정 Import 배치에 연결된 전체 레코드. 배치당 최대 수천 건 규모라 통째로 읽어 메모리에서 집계한다. */
    List<FestivalRecord> findByImportBatchId(Long batchId);

    /** 메타데이터(지역별 시군구 목록 등) 조회용 - 예산 상태와 무관하게 해당 연도 전체. */
    List<FestivalRecord> findByDatasetYear(Integer datasetYear);

    /** 예산 추정 알고리즘의 기본 후보 모집단 - 예산이 확정(0보다 큼)인 표본만. */
    List<FestivalRecord> findByDatasetYearAndBudgetStatus(Integer datasetYear, BudgetStatus budgetStatus);

    @Query("select max(f.datasetYear) from FestivalRecord f")
    Optional<Integer> findMaxDatasetYear();
}