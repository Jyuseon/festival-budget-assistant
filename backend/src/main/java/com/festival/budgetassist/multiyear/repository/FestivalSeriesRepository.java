package com.festival.budgetassist.multiyear.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.festival.budgetassist.multiyear.domain.FestivalSeries;

public interface FestivalSeriesRepository extends JpaRepository<FestivalSeries, Long> {
}