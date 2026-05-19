package com.pathyana.api.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One generated meal. Used both for one-off generations (weeklyPlanId = null)
 * and for individual meals inside a weekly plan (weeklyPlanId set, slot + day populated).
 *
 * Why we extended this rather than create a parallel entity:
 *   - Same shape of data (recipe, review, grocery)
 *   - Single source of truth for "all meals this user has had generated"
 *   - Lets one-off and weekly meals show up in a unified history
 */
@Entity
@Table(name = "meal_plans",
       indexes = {
           @Index(columnList = "user_id,created_at"),
           @Index(columnList = "weekly_plan_id,day_number")
       })
@Data
public class MealPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;

    public enum Status { DRAFT, APPROVED, FAILED_SAFETY, FAILED_SYSTEM }

    @Column(columnDefinition = "TEXT")
    private String recipeJson;

    @Column(columnDefinition = "TEXT")
    private String reviewJson;

    @Column(columnDefinition = "TEXT")
    private String groceryListJson;

    @Column(name = "regeneration_count")
    private Integer regenerationCount = 0;

    // ---------- weekly plan linkage (nullable for one-off meals) ----------

    /** FK to weekly_meal_plans.id. Null for one-off meals. */
    @Column(name = "weekly_plan_id")
    private Long weeklyPlanId;

    /** Day of the week within the plan (1-7). Null for one-off meals. */
    @Column(name = "day_number")
    private Integer dayNumber;

    /** "brunch", "dinner", "snack". Null for one-off meals. */
    @Column(name = "meal_slot")
    private String mealSlot;

    /** Actual calendar date this meal is planned for. Null for one-off. */
    @Column(name = "planned_date")
    private LocalDate plannedDate;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated = LocalDateTime.now();
}
