package com.pathyana.api.controller;

import com.pathyana.api.dto.ProfileResponse;
import com.pathyana.api.dto.ProfileResponse.MedicationItem;
import com.pathyana.api.dto.ProfileSeedRequest;
import com.pathyana.api.model.*;
import com.pathyana.api.repository.UserRepository;
import com.pathyana.api.service.profile.ProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Profile endpoints, split out from MealController for clarity.
 *
 *   GET  /api/profile         — read current user's profile (for pre-filling the form)
 *   POST /api/profile/seed    — replace profile data
 *
 * The seed endpoint moves here from MealController. It's still the same logic;
 * just better organized.
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final UserRepository userRepository;
    private final ProfileService profileService;

    public ProfileController(UserRepository userRepository, ProfileService profileService) {
        this.userRepository = userRepository;
        this.profileService = profileService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ProfileResponse> getProfile() {
        User user = currentUser();
        forceLoadProfile(user);

        ProfileResponse response = new ProfileResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getAge(),
            user.getCity(),
            user.getDietaryPreference(),
            user.getHealthGoal(),
            user.getCuisinePreference(),

            user.getEatingWindowStart(),
            user.getEatingWindowEnd(),
            user.getWindowProtocol() == null ? "NONE" : user.getWindowProtocol().name(),
            user.getMealToBedBufferMinutes(),
            user.getTypicalBedtime(),

            user.getHealthConditions().stream().map(HealthCondition::getCondition).toList(),
            user.getAllergies().stream().map(Allergy::getAllergy).toList(),
            user.getFoodPreferences().stream().map(FoodPreference::getFood).toList(),
            user.getFoodsToAvoid().stream().map(FoodToAvoid::getFood).toList(),
            user.getMedications().stream()
                .map(m -> new MedicationItem(m.getDisplayName(), m.getGenericName(), m.getDosage()))
                .toList(),

            user.getProfileCompleted()
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/seed")
    @Transactional
    public ResponseEntity<?> seedProfile(@RequestBody ProfileSeedRequest req) {
        User user = currentUser();
        forceLoadProfile(user);
        User saved = profileService.seed(user, req);
        return ResponseEntity.ok(Map.of(
            "message", "Profile updated",
            "userId", saved.getId()
        ));
    }

    // ---------- helpers ----------

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = Long.parseLong(auth.getPrincipal().toString());
        return userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Authenticated user not found: " + userId));
    }

    private void forceLoadProfile(User user) {
        if (user.getHealthConditions() != null) user.getHealthConditions().size();
        if (user.getAllergies() != null) user.getAllergies().size();
        if (user.getFoodPreferences() != null) user.getFoodPreferences().size();
        if (user.getFoodsToAvoid() != null) user.getFoodsToAvoid().size();
        if (user.getMedications() != null) user.getMedications().size();
    }
}
