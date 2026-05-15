package com.pathyana.api.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Output of one MealOrchestratorService run.
 *
 * The orchestrator's pipeline produces three distinct artifacts that we want to query
 * and display independently:
 *
 *   recipeJson       — the Chef's approved output (recipe, ingredients, steps, nutrition)
 *   reviewJson       — the Reviewer's verdict + deterministic safety findings
 *   groceryListJson  — the Optimizer's output AFTER the deterministic Swapper has run
 *
 * status tracks the pipeline outcome:
 *   DRAFT          — generation in progress (rare, for async support later)
 *   APPROVED       — Reviewer passed, ready to use
 *   FAILED_SAFETY  — couldn't generate a safe recipe after retries
 *   FAILED_SYSTEM  — LLM/parsing/network error
 *
 * regenerationCount tells us how many Chef retries were needed before the Reviewer passed.
 * Useful for monitoring — if this is consistently 2+, our prompts need tuning.
 */
@Entity
@Table(name = "meal_plans", indexes = @Index(columnList = "user_id,created_at"))
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

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated = LocalDateTime.now();
}
