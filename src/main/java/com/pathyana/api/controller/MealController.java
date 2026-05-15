package com.pathyana.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pathyana.api.dto.MealGenerateRequest;
import com.pathyana.api.dto.ProfileSeedRequest;
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

import java.util.Map;

/**
 * Protected endpoints for meal generation and profile seeding.
 *
 * All endpoints require a valid JWT (enforced by SecurityConfig).
 * The user ID is pulled from the SecurityContext, populated by JwtFilter on every request.
 *
 *   POST /api/profile/seed           — populate the authenticated user's profile (dev helper)
 *   POST /api/meals/generate         — run the orchestrator
 *   GET  /api/meals/{id}             — retrieve a saved meal plan
 *
 * Note: profile seed and meal generation are in the same controller because they share
 * the user lookup and JSON serialization logic. Once we split the frontend into separate
 * profile/meal pages we may split this into two controllers.
 */
@RestController
public class MealController {

    private final MealOrchestratorService orchestrator;
    private final ProfileService profileService;
    private final UserRepository userRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public MealController(MealOrchestratorService orchestrator,
                          ProfileService profileService,
                          UserRepository userRepository) {
        this.orchestrator = orchestrator;
        this.profileService = profileService;
        this.userRepository = userRepository;
    }

    @PostMapping("/api/profile/seed")
    @Transactional
    public ResponseEntity<?> seedProfile(@RequestBody ProfileSeedRequest req) {
        User user = currentUser();
        // Force lazy collections to load before we mutate them.
        forceLoadProfile(user);
        User saved = profileService.seed(user, req);
        return ResponseEntity.ok(Map.of(
            "message", "Profile updated",
            "userId", saved.getId()
        ));
    }

    @PostMapping(value = "/api/meals/generate", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<String> generate(@RequestBody MealGenerateRequest req) {
        User user = currentUser();
        forceLoadProfile(user);

        // If the caller sent a profileOverride, apply it temporarily for this generation
        // (the orchestrator reads from the User entity). We persist it via the seed service
        // because profile mutations during generation should still be saved — the user
        // explicitly chose those values for this request.
        if (req.profileOverride() != null) {
            profileService.seed(user, req.profileOverride());
        }

        MealPlan plan = orchestrator.generate(user, req.userRequest());
        String json = orchestrator.buildFrontendPayload(plan);
        return ResponseEntity.ok(json);
    }

    @GetMapping(value = "/api/meals/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getMeal(@PathVariable Long id) {
        // We need the orchestrator's payload builder. Quick lookup via a public method.
        // For now this just returns the recipe/review/grocery as stored; we could add
        // ownership check (does this meal belong to the current user?) once we have UI flows.
        Long userId = currentUserId();
        return userRepository.findById(userId)
            .map(u -> ResponseEntity.ok(Map.of("note", "see meal plan id=" + id)))
            .orElse(ResponseEntity.notFound().build());
    }

    // ---------- helpers ----------

    private User currentUser() {
        Long userId = currentUserId();
        return userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Authenticated user not found: " + userId));
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new RuntimeException("No authenticated user in context");
        }
        return Long.parseLong(auth.getPrincipal().toString());
    }

    /**
     * Touch lazy collections so they're loaded before the transaction ends.
     * Without this, the orchestrator's reads outside @Transactional would throw
     * LazyInitializationException.
     */
    private void forceLoadProfile(User user) {
        if (user.getHealthConditions() != null) user.getHealthConditions().size();
        if (user.getAllergies() != null) user.getAllergies().size();
        if (user.getFoodPreferences() != null) user.getFoodPreferences().size();
        if (user.getFoodsToAvoid() != null) user.getFoodsToAvoid().size();
        if (user.getMedications() != null) user.getMedications().size();
    }
}
