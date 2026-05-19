package com.pathyana.api.repository;

import com.pathyana.api.model.WeeklyMealPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * v2 — added findAllByUserIdAndActiveTrue and a defensive default for
 * findByUserIdAndActiveTrue.
 *
 * Why: if data ever ends up in a state where multiple plans for one user are
 * marked active=true (failed cleanup, manual DB edit, crash mid-archive), the
 * original Optional-returning method throws NonUniqueResultException and the
 * whole endpoint dies. The new findAll variant lets the orchestrator clean up
 * ALL of them defensively.
 */
public interface WeeklyMealPlanRepository extends JpaRepository<WeeklyMealPlan, Long> {

    /**
     * Returns ALL plans currently marked active for a user.
     * Used during archive-on-new-plan so we clean up any stale "actives" too.
     */
    List<WeeklyMealPlan> findAllByUserIdAndActiveTrueOrderByCreatedAtDesc(Long userId);

    /**
     * Convenience method — first active plan for a user (newest one), or empty.
     * Implemented as a default method so it never throws NonUniqueResultException
     * even if multiple actives exist.
     */
    default Optional<WeeklyMealPlan> findByUserIdAndActiveTrue(Long userId) {
        return findAllByUserIdAndActiveTrueOrderByCreatedAtDesc(userId).stream().findFirst();
    }

    /** All historical plans for a user, newest first. */
    List<WeeklyMealPlan> findByUserIdOrderByCreatedAtDesc(Long userId);
}