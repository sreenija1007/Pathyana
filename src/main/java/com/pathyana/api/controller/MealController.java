package com.pathyana.api.controller;

import com.pathyana.api.dto.MealGenerateRequest;
import com.pathyana.api.model.MealPlan;
import com.pathyana.api.model.User;
import com.pathyana.api.repository.UserRepository;
import com.pathyana.api.service.orchestration.MealOrchestratorService;
import com.pathyana.api.service.profile.ProfileService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Meal generation endpoints. Profile management lives in ProfileController.
 *
 *   POST /api/meals/generate  — run the orchestrator
 *
 * Future endpoints (Phase 6+):
 *   GET  /api/meals           — list user's meal history
 *   GET  /api/meals/{id}      — retrieve a specific plan
 *   POST /api/meals/weekly    — generate 7-day plan
 */
@RestController
@RequestMapping("/api/meals")
public class MealController {

    private final MealOrchestratorService orchestrator;
    private final ProfileService profileService;
    private final UserRepository userRepository;

    public MealController(MealOrchestratorService orchestrator,
                          ProfileService profileService,
                          UserRepository userRepository) {
        this.orchestrator = orchestrator;
        this.profileService = profileService;
        this.userRepository = userRepository;
    }

    @PostMapping(value = "/generate", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<String> generate(@RequestBody MealGenerateRequest req) {
        User user = currentUser();
        forceLoadProfile(user);

        // Optional inline profile override — useful for one-off "try this profile" requests
        if (req.profileOverride() != null) {
            profileService.seed(user, req.profileOverride());
        }

        MealPlan plan = orchestrator.generate(user, req.userRequest());
        String json = orchestrator.buildFrontendPayload(plan);
        return ResponseEntity.ok(json);
    }

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
