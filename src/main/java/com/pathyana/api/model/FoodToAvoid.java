package com.pathyana.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

/**
 * Foods the user wants to avoid for non-medical reasons (religious, ethical, taste).
 *
 * Allergies (with anaphylaxis risk) go in Allergy. FoodToAvoid is for preferences like
 * "no beef" (religious), "no mushrooms" (taste), "no honey" (vegan).
 *
 * The Chef treats this as a hard exclude. The Reviewer doesn't flag it as a clinical risk —
 * it's a preference, not a medical issue.
 */
@Entity
@Table(name = "foods_to_avoid", indexes = @Index(columnList = "user_id"))
@Data
public class FoodToAvoid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @Column(nullable = false)
    private String food;

    /** Optional reason — useful for the UI to show why this is being excluded. */
    private String reason;
}
