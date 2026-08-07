package com.festival.budgetassist.multiyear.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.festival.budgetassist.festival.domain.ImportStatus;
import com.festival.budgetassist.multiyear.domain.MultiYearImportBatch;

public interface MultiYearImportBatchRepository extends JpaRepository<MultiYearImportBatch, Long> {

    Optional<MultiYearImportBatch> findFirstByFileHashAndStatusOrderByImportedAtDesc(String fileHash, ImportStatus status);

    Optional<MultiYearImportBatch> findFirstByOrderByImportedAtDesc();

    Optional<MultiYearImportBatch> findFirstByStatusOrderByImportedAtDesc(ImportStatus status);
}