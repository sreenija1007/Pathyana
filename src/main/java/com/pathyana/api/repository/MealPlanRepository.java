package com.pathyana.api.repository;

import com.pathyana.api.model.MealPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MealPlanRepository extends JpaRepository<MealPlan, Long> {
    List<MealPlan> findByUserIdOrderByCreatedAtDesc(Long userId);
}
