package com.festival.budgetassist.festival.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.festival.budgetassist.festival.domain.DatasetImportBatch;
import com.festival.budgetassist.festival.domain.ImportStatus;

public interface DatasetImportBatchRepository extends JpaRepository<DatasetImportBatch, Long> {

    Optional<DatasetImportBatch> findFirstByFileHashAndStatusOrderByImportedAtDesc(String fileHash, ImportStatus status);

    /** 성공/실패를 가리지 않은 가장 최근 시도. 관리자 화면의 "최근 Import 상태" 표시용. */
    Optional<DatasetImportBatch> findFirstByOrderByImportedAtDesc();

    /** 가장 최근 성공 배치 - 지금 서비스되고 있는 데이터의 출처. */
    Optional<DatasetImportBatch> findFirstByStatusOrderByImportedAtDesc(ImportStatus status);
}