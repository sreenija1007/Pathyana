package com.pathyana.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

/**
 * A medication a user is currently taking.
 *
 * Kept as its own entity (rather than a comma-joined String column on User) for three reasons:
 *   1. PHI minimization — we can encrypt this table independently and audit access to it later.
 *   2. Structured data for the DrugNutrientRuleEngine — needs a clean key to match against.
 *   3. Future-proofing — dosage, timing, prescriber notes can be added without schema churn.
 *
 * `genericName` is the matching key (lowercased) so brand variants ("Lipitor", "Atorvastatin")
 * collapse to the same rule. The user enters "Lipitor" in the UI; the service layer normalizes
 * it to "atorvastatin" before saving.
 *
 * `displayName` preserves whatever the user typed, so the UI shows their familiar term.
 */
@Entity
@Table(name = "medications", indexes = @Index(columnList = "user_id,generic_name"))
@Data
public class Medication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    /** What the user typed. Shown in the UI. */
    @Column(name = "display_name", nullable = false)
    private String displayName;

    /** Lowercased canonical name used for rule matching. */
    @Column(name = "generic_name", nullable = false)
    private String genericName;

    /** Free text — "10mg daily", "morning with food", etc. We don't parse this. */
    private String dosage;

    /** Whether the user is currently taking this. Soft-delete on stop, don't hard-delete history. */
    @Column(nullable = false)
    private Boolean active = true;
}
