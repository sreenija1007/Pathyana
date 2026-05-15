package com.pathyana.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

/**
 * A single health condition for a user (e.g., "Type 2 Diabetes", "Hypertension", "CKD Stage 3").
 *
 * Why separate table instead of a comma-string on User:
 *  - Lets us query "all users with diabetes" easily (for analytics, cohort features).
 *  - Lets us add per-condition metadata later (severity, diagnosis date, controlled/uncontrolled)
 *    without re-migrating User.
 *  - Cleaner for the LLM Reviewer agent — we pass an explicit list, not a parsed string.
 *
 * `condition` is stored as free text for now. Once we have a stable list, we can swap to an enum
 * or a lookup table. Free text gives the Questionnaire UI flexibility during the build phase.
 */
@Entity
@Table(name = "health_conditions", indexes = @Index(columnList = "user_id"))
@Data
public class HealthCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore  // avoid circular User <-> HealthCondition serialization
    private User user;

    @Column(nullable = false)
    private String condition;

    /** Optional notes — "diagnosed 2019", "on metformin", etc. */
    private String notes;
}
