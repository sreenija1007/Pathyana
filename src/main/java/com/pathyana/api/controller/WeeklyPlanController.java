package com.pathyana.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pathyana.api.dto.WeeklyPlanRequest;
import com.pathyana.api.dto.WeeklyPlanStatusResponse;
import com.pathyana.api.model.MealPlan;
import com.pathyana.api.model.User;
import com.pathyana.api.model.WeeklyMealPlan;
import com.pathyana.api.repository.MealPlanRepository;
import com.pathyana.api.repository.UserRepository;
import com.pathyana.api.repository.WeeklyMealPlanRepository;
import com.pathyana.api.service.orchestration.WeeklyPlanOrchestratorService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Weekly meal plan endpoints.
 *
 *   POST /api/meals/weekly/generate         — start a new plan (async)
 *   GET  /api/meals/weekly/active           — current active plan (full detail)
 *   GET  /api/meals/weekly/{id}/status      — cheap polling endpoint
 *   GET  /api/meals/weekly/{id}             — full plan with meals + grocery
 *   GET  /api/meals/weekly/{id}/grocery     — just the grocery list
 *
 * "Full detail" responses can be big (21 recipes' JSON inside). The status
 * endpoint is intentionally tiny so the frontend can poll it cheaply.
 */
@RestController
@RequestMapping("/api/meals/weekly")
public class WeeklyPlanController {

    private final WeeklyPlanOrchestratorService orchestrator;
    private final WeeklyMealPlanRepository weeklyRepo;
    private final MealPlanRepository mealRepo;
    private final UserRepository userRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public WeeklyPlanController(WeeklyPlanOrchestratorService orchestrator,
                                WeeklyMealPlanRepository weeklyRepo,
                                MealPlanRepository mealRepo,
                                UserRepository userRepo) {
        this.orchestrator = orchestrator;
        this.weeklyRepo = weeklyRepo;
        this.mealRepo = mealRepo;
        this.userRepo = userRepo;
    }

    /** Kick off generation. Returns the plan record immediately (status=PENDING). */
    @PostMapping("/generate")
    public ResponseEntity<WeeklyPlanStatusResponse> generate(@RequestBody(required = false) WeeklyPlanRequest body) {
        User user = currentUser();
        LocalDate startDate = (body != null && body.startDate() != null)
            ? body.startDate() : LocalDate.now();
        String userRequest = body == null ? null : body.userRequest();

        WeeklyMealPlan plan = orchestrator.createAndStart(user, startDate, userRequest);
        return ResponseEntity.accepted().body(toStatus(plan));
    }

    /** Lightweight status — designed for repeated polling. */
    @GetMapping("/{id}/status")
    public ResponseEntity<WeeklyPlanStatusResponse> status(@PathVariable Long id) {
        return weeklyRepo.findById(id)
            .filter(p -> p.getUserId().equals(currentUserId()))
            .map(p -> ResponseEntity.ok(toStatus(p)))
            .orElse(ResponseEntity.notFound().build());
    }

    /** Currently-active plan, if any. Returns 404 if user has no active plan. */
    @GetMapping(value = "/active", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> active() {
        Long userId = currentUserId();
        Optional<WeeklyMealPlan> opt = weeklyRepo.findByUserIdAndActiveTrue(userId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(buildFullPlanJson(opt.get()));
    }

    /** Full detail of a specific plan (any of the user's, active or archived). */
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> get(@PathVariable Long id) {
        Optional<WeeklyMealPlan> opt = weeklyRepo.findById(id)
            .filter(p -> p.getUserId().equals(currentUserId()));
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(buildFullPlanJson(opt.get()));
    }

    /** Just the grocery list, useful for printing/sharing. */
    @GetMapping(value = "/{id}/grocery", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> grocery(@PathVariable Long id) {
        Optional<WeeklyMealPlan> opt = weeklyRepo.findById(id)
            .filter(p -> p.getUserId().equals(currentUserId()));
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        String json = opt.get().getGroceryListJson();
        if (json == null || json.isBlank()) {
            return ResponseEntity.ok("{\"items\":[],\"appliedSwaps\":[],\"estimatedTotalUsd\":0,\"note\":\"Grocery list not yet generated\"}");
        }
        return ResponseEntity.ok(json);
    }

    // ---------- internal builders ----------

    /**
     * Build the response combining plan metadata + all meals + grocery list.
     *
     * Shape:
     *   {
     *     plan: { id, status, startDate, progressCurrent, progressTotal, ... },
     *     days: [
     *       { day: 1, date: "2026-05-15", meals: [
     *         { slot: "brunch", status: "APPROVED", recipe: {...}, review: {...} },
     *         { slot: "dinner", ... },
     *         { slot: "snack", ... }
     *       ]},
     *       ...
     *     ],
     *     grocery: {...}    // null if not yet aggregated
     *   }
     */
    private String buildFullPlanJson(WeeklyMealPlan plan) {
        ObjectNode root = mapper.createObjectNode();

        // Plan header
        ObjectNode header = mapper.valueToTree(toStatus(plan));
        root.set("plan", header);

        // Group meals by day
        List<MealPlan> meals = mealRepo.findByWeeklyPlanIdOrderByDayNumberAscIdAsc(plan.getId());
        ArrayNode days = mapper.createArrayNode();
        for (int d = 1; d <= 7; d++) {
            final int dayNum = d;
            ObjectNode dayNode = mapper.createObjectNode();
            dayNode.put("day", dayNum);
            dayNode.put("date", plan.getStartDate().plusDays(d - 1).toString());

            ArrayNode mealArr = mapper.createArrayNode();
            for (MealPlan m : meals) {
                if (m.getDayNumber() == null || m.getDayNumber() != dayNum) continue;
                ObjectNode mealNode = mapper.createObjectNode();
                mealNode.put("id", m.getId());
                mealNode.put("slot", m.getMealSlot());
                mealNode.put("status", m.getStatus().name());
                mealNode.put("regenerationCount", m.getRegenerationCount());
                mealNode.set("recipe", parseSafe(m.getRecipeJson()));
                mealNode.set("review", parseSafe(m.getReviewJson()));
                mealArr.add(mealNode);
            }
            dayNode.set("meals", mealArr);
            days.add(dayNode);
        }
        root.set("days", days);

        // Grocery list — null if not yet built
        if (plan.getGroceryListJson() != null && !plan.getGroceryListJson().isBlank()) {
            root.set("grocery", parseSafe(plan.getGroceryListJson()));
        } else {
            root.putNull("grocery");
        }

        return root.toString();
    }

    private JsonNode parseSafe(String json) {
        if (json == null || json.isBlank()) return mapper.nullNode();
        try { return mapper.readTree(json); }
        catch (Exception e) { return mapper.nullNode(); }
    }

    private WeeklyPlanStatusResponse toStatus(WeeklyMealPlan plan) {
        return new WeeklyPlanStatusResponse(
            plan.getId(),
            plan.getStatus().name(),
            plan.getStartDate(),
            plan.getProgressCurrent(),
            plan.getProgressTotal(),
            plan.getProgressMessage(),
            plan.getErrorMessage(),
            plan.getLastUpdated(),
            plan.getCompletedAt()
        );
    }

    // ---------- auth helpers ----------

    private User currentUser() {
        return userRepo.findById(currentUserId())
            .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getPrincipal().toString());
    }
}
