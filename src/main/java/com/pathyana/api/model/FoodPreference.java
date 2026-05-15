package com.pathyana.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

/**
 * A food the user actively likes. Surfaces to the Chef agent so generated meals lean toward
 * familiar/enjoyed foods, which improves adherence (people stick to plans they like).
 *
 * Distinct from FoodToAvoid — which is for foods to deliberately exclude.
 */
@Entity
@Table(name = "food_preferences", indexes = @Index(columnList = "user_id"))
@Data
public class FoodPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @Column(nullable = false)
    private String food;
}
