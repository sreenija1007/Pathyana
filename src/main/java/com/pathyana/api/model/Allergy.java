package com.pathyana.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

/**
 * A food/ingredient allergy or intolerance.
 *
 * `severity` distinguishes mild intolerance ("makes me gassy") from anaphylactic risk —
 * the orchestrator should treat SEVERE allergies as HARD BLOCKS, never to be substituted
 * into "small amounts" recipes.
 */
@Entity
@Table(name = "allergies", indexes = @Index(columnList = "user_id"))
@Data
public class Allergy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    /** What they're allergic to — peanuts, dairy, shellfish, etc. */
    @Column(nullable = false)
    private String allergy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity = Severity.MODERATE;

    public enum Severity {
        MILD,       // intolerance, discomfort
        MODERATE,   // significant discomfort, avoid
        SEVERE      // anaphylaxis risk — never include, never trace
    }
}
