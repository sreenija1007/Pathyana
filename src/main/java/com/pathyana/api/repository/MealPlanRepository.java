package com.pathyana.api.repository;

import com.pathyana.api.model.MealPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MealPlanRepository extends JpaRepository<MealPlan, Long> {

    /** Newest one-off meals for a user (excludes weekly plan meals). */
    List<MealPlan> findByUserIdAndWeeklyPlanIdIsNullOrderByCreatedAtDesc(Long userId);

    /** All meals belonging to a specific weekly plan, in day/slot order. */
    List<MealPlan> findByWeeklyPlanIdOrderByDayNumberAscIdAsc(Long weeklyPlanId);
}
