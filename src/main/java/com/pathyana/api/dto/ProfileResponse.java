package com.pathyana.api.dto;

import java.time.LocalTime;
import java.util.List;

/**
 * Response for GET /api/profile.
 *
 * Shape matches ProfileSeedRequest so the frontend can use the same form state
 * for both reading and writing. Symmetry between read and write DTOs is one of
 * the easiest wins for a small team — fewer mappings to maintain.
 */
public record ProfileResponse(
    Long userId,
    String name,
    String email,
    Integer age,
    String city,
    String dietaryPreference,
    String healthGoal,
    String cuisinePreference,

    LocalTime eatingWindowStart,
    LocalTime eatingWindowEnd,
    String windowProtocol,
    Integer mealToBedBufferMinutes,
    LocalTime typicalBedtime,

    List<String> healthConditions,
    List<String> allergies,
    List<String> foodPreferences,
    List<String> foodsToAvoid,
    List<MedicationItem> medications,

    Boolean profileCompleted
) {
    public record MedicationItem(String displayName, String genericName, String dosage) {}
}
