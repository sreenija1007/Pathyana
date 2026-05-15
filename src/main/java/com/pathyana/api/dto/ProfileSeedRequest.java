package com.pathyana.api.dto;

import java.time.LocalTime;
import java.util.List;

/**
 * For dev/testing — populate the authenticated user's profile in one POST.
 *
 * All fields optional. Whatever you send replaces the existing collections.
 * Skip fields to leave them as-is.
 */
public record ProfileSeedRequest(
    Integer age,
    String city,
    String dietaryPreference,
    String healthGoal,
    String cuisinePreference,

    // Eating window
    LocalTime eatingWindowStart,    // "09:00" in JSON
    LocalTime eatingWindowEnd,      // "19:00"
    String windowProtocol,           // "NONE" | "TWELVE_TWELVE" | "FOURTEEN_TEN" | "SIXTEEN_EIGHT" | "CUSTOM"
    Integer mealToBedBufferMinutes,
    LocalTime typicalBedtime,

    List<String> healthConditions,   // ["Type 2 Diabetes", "Hypertension"]
    List<String> allergies,          // ["peanut", "shellfish"]
    List<String> foodPreferences,    // ["chicken", "spinach"]
    List<String> foodsToAvoid,       // ["beef"]
    List<MedicationSeed> medications
) {
    public record MedicationSeed(String displayName, String genericName, String dosage) {}
}
