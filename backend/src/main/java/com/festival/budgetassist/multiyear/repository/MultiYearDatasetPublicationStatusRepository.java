package com.festival.budgetassist.multiyear.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.festival.budgetassist.multiyear.domain.MultiYearDatasetPublicationStatus;

public interface MultiYearDatasetPublicationStatusRepository extends JpaRepository<MultiYearDatasetPublicationStatus, Long> {

    Optional<MultiYearDatasetPublicationStatus> findByDatasetYear(Integer datasetYear);
}