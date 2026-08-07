package com.festival.budgetassist.multiyear.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.festival.budgetassist.multiyear.domain.FestivalSeriesMatchCandidate;

public interface FestivalSeriesMatchCandidateRepository extends JpaRepository<FestivalSeriesMatchCandidate, Long> {
}