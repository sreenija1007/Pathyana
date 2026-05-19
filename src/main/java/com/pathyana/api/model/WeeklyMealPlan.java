package com.pathyana.api.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The header record for a weekly meal plan. Each individual meal generated for
 * this plan is a row in `meal_plans` with `weekly_plan_id` pointing back here.
 *
 * Lifecycle states:
 *   PENDING        — created, queued for generation
 *   GENERATING     — async worker is currently producing meals
 *   COMPLETED      — all 21 meals generated successfully
 *   PARTIAL        — some meals generated, generation stopped (error or limit hit)
 *   FAILED         — generation could not produce a usable plan
 *
 * Only ONE plan per user is active at a time. Generating a new plan archives
 * the old one (active=false) so we keep history without cluttering UI.
 *
 * progressCurrent / progressTotal: used by the frontend's polling UI to show
 * "Day 3 of 7 — generating dinner…" — keeps the user informed during the 3-5
 * minute generation window.
 */
@Entity
@Table(name = "weekly_meal_plans",
       indexes = {
           @Index(columnList = "user_id,active"),
           @Index(columnList = "user_id,created_at")
       })
@Data
public class WeeklyMealPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    public enum Status { PENDING, GENERATING, COMPLETED, PARTIAL, FAILED }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    /** First day of the plan. Day 1 is this date, Day 7 is start + 6. */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Only one plan per user is active. Generating a new one archives the old. */
    @Column(nullable = false)
    private Boolean active = true;

    /**
     * The 3 meal slots Pathyana plans for. Stored as a comma-joined string for
     * forward compatibility — if we later add "midnight snack" or "pre-workout"
     * we don't need a new column.
     */
    @Column(name = "meal_slots", nullable = false)
    private String mealSlots = "brunch,dinner,snack";

    /** Aggregated grocery list JSON (built by WeeklyGroceryAggregator after generation). */
    @Column(name = "grocery_list_json", columnDefinition = "TEXT")
    private String groceryListJson;

    /** Optional free-text request the user typed when starting the plan. */
    @Column(name = "user_request", columnDefinition = "TEXT")
    private String userRequest;

    /** How many meals have been generated so far. Updated as each meal finishes. */
    @Column(name = "progress_current", nullable = false)
    private Integer progressCurrent = 0;

    /** Total meals to generate. Computed at plan creation = 7 × number of slots. */
    @Column(name = "progress_total", nullable = false)
    private Integer progressTotal = 21;

    /** Last status message shown to the user (e.g., "Generating Day 3 dinner"). */
    @Column(name = "progress_message")
    private String progressMessage;

    /** Error message if status is FAILED. */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
