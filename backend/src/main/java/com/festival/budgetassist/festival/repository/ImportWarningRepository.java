package com.festival.budgetassist.festival.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.festival.budgetassist.festival.domain.ImportWarning;

public interface ImportWarningRepository extends JpaRepository<ImportWarning, Long> {

    List<ImportWarning> findByBatchIdOrderBySourceRowNumberAsc(Long batchId);

    long countByBatchId(Long batchId);
}