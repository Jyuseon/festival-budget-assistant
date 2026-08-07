package com.festival.budgetassist.multiyear.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.festival.budgetassist.multiyear.domain.FestivalSeriesMembership;

public interface FestivalSeriesMembershipRepository extends JpaRepository<FestivalSeriesMembership, Long> {

    List<FestivalSeriesMembership> findByFestivalSeriesId(Long festivalSeriesId);
}