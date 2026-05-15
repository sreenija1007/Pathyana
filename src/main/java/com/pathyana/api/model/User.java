package com.pathyana.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Core User entity.
 *
 * Holds:
 *  - Identity:        id, name, email, password (hashed), createdAt
 *  - Profile:         age, city, dateOfBirth, dietaryPreference, healthGoal, cuisinePreference
 *  - Eating window:   start/end times, protocol enum, bed-meal buffer, typical bedtime
 *  - Account flags:   emailVerified, profileCompleted, verificationToken
 *  - Relationships:   healthConditions, allergies, foodPreferences, medications, mealPlans
 *
 * Why password is @JsonIgnore: prevents it from ever leaving the API by accident, even if a
 * developer returns a raw User in a controller.
 *
 * Why relationships are LAZY: we don't want every user lookup to drag in 5 child tables.
 * The orchestrator explicitly fetches what it needs.
 */
@Entity
@Table(name = "users")
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ---------- Identity ----------
    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ---------- Profile ----------
    private Integer age;
    private String city;

    @Column(name = "date_of_birth")
    private String dateOfBirth;

    @Column(name = "dietary_preference")
    private String dietaryPreference;  // e.g., "vegetarian", "vegan", "non-veg", "gluten-free"

    @Column(name = "health_goal")
    private String healthGoal;  // e.g., "weight loss", "muscle gain", "diabetes management"

    @Column(name = "cooking_style")
    private String cookingStyle;  // e.g., "quick", "elaborate", "meal-prep"

    @Column(name = "cuisine_preference")
    private String cuisinePreference;  // e.g., "Indian", "Mediterranean", "mixed"

    // ---------- Eating window (Phase 2 — clinical chrono-nutrition) ----------
    /**
     * Time of day the user starts eating. Null = no window restriction.
     * Example: 09:00 means breakfast at or after 9 AM.
     */
    @Column(name = "eating_window_start")
    private LocalTime eatingWindowStart;

    /**
     * Time of day the user stops eating. Null = no window restriction.
     * Example: 19:00 means last meal by 7 PM.
     */
    @Column(name = "eating_window_end")
    private LocalTime eatingWindowEnd;

    /**
     * Which protocol the user picked. Determines the UI label and any preset behavior.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "window_protocol")
    private WindowProtocol windowProtocol = WindowProtocol.NONE;

    public enum WindowProtocol {
        NONE,        // no restriction
        TWELVE_TWELVE,  // 12:12 — beginner-friendly
        FOURTEEN_TEN,   // 14:10 — moderate
        SIXTEEN_EIGHT,  // 16:8 — stricter
        CUSTOM       // user-defined start/end
    }

    /**
     * Minimum gap (minutes) between the last meal and bedtime. Used for reflux/GERD logic.
     * Default 180 (3h) — standard clinical advice for acid reflux patients.
     */
    @Column(name = "meal_to_bed_buffer_minutes")
    private Integer mealToBedBufferMinutes;

    /**
     * User's typical bedtime. Used together with mealToBedBufferMinutes to validate dinner timing.
     */
    @Column(name = "typical_bedtime")
    private LocalTime typicalBedtime;

    // ---------- Account flags ----------
    @Column(name = "email_verified")
    private Boolean emailVerified = false;

    @Column(name = "profile_completed")
    private Boolean profileCompleted = false;

    @Column(name = "verification_token")
    private String verificationToken;

    // ---------- Relationships ----------
    // LAZY: don't auto-load on every User fetch. The orchestrator forces fetch when needed.
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<HealthCondition> healthConditions = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<Allergy> allergies = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<FoodPreference> foodPreferences = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<FoodToAvoid> foodsToAvoid = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<Medication> medications = new ArrayList<>();
}
