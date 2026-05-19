package com.pathyana.api.service.orchestration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pathyana.api.llm.GroqClient;
import com.pathyana.api.model.*;
import com.pathyana.api.repository.MealPlanRepository;
import com.pathyana.api.repository.UserRepository;
import com.pathyana.api.repository.WeeklyMealPlanRepository;
import com.pathyana.api.service.safety.ConditionFoodRuleEngine;
import com.pathyana.api.service.safety.DrugNutrientRuleEngine;
import com.pathyana.api.service.safety.IngredientSwapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Generates a 7-day meal plan asynchronously.
 *
 * IMPORTANT — the @Async dispatch lives in WeeklyPlanAsyncRunner, not here.
 * Spring's @Async cannot intercept self-calls within the same class. If you
 * add @Async directly to a method here and call it from another method here,
 * it will run synchronously and the controller will hang for ~6 minutes.
 *
 * The flow is:
 *   Controller → createAndStart() → asyncRunner.run() → [@Async fires] → generate()
 */
@Service
public class WeeklyPlanOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyPlanOrchestratorService.class);
    private static final int MAX_REGENERATIONS_PER_MEAL = 2;
    private static final int CONTEXT_WINDOW_MEALS = 3;

    /**
     * Preemptive pause between meals to stay under Groq free-tier TPM (~12,000).
     * 2500ms × 21 meals ≈ 52 seconds of throttle.
     * GroqClient also retries 429s automatically as a backstop.
     */
    private static final long INTER_MEAL_THROTTLE_MS = 2500;

    private final GroqClient groqClient;
    private final DrugNutrientRuleEngine drugEngine;
    private final ConditionFoodRuleEngine conditionEngine;
    private final IngredientSwapper swapper;
    private final WeeklyMealPlanRepository weeklyRepo;
    private final MealPlanRepository mealRepo;
    private final UserRepository userRepo;
    private final WeeklyGroceryAggregator groceryAggregator;
    private final WeeklyPlanAsyncRunner asyncRunner;
    private final ObjectMapper mapper = new ObjectMapper();

    public WeeklyPlanOrchestratorService(GroqClient groqClient,
                                         DrugNutrientRuleEngine drugEngine,
                                         ConditionFoodRuleEngine conditionEngine,
                                         IngredientSwapper swapper,
                                         WeeklyMealPlanRepository weeklyRepo,
                                         MealPlanRepository mealRepo,
                                         UserRepository userRepo,
                                         WeeklyGroceryAggregator groceryAggregator,
                                         WeeklyPlanAsyncRunner asyncRunner) {
        this.groqClient = groqClient;
        this.drugEngine = drugEngine;
        this.conditionEngine = conditionEngine;
        this.swapper = swapper;
        this.weeklyRepo = weeklyRepo;
        this.mealRepo = mealRepo;
        this.userRepo = userRepo;
        this.groceryAggregator = groceryAggregator;
        this.asyncRunner = asyncRunner;
    }

    /**
     * Create the plan record synchronously, then dispatch generation to the
     * background via the async runner. Returns immediately so the HTTP request
     * doesn't block.
     */
    @Transactional
    public WeeklyMealPlan createAndStart(User user, LocalDate startDate, String userRequest) {
        // Archive any existing active plan
        weeklyRepo.findByUserIdAndActiveTrue(user.getId()).ifPresent(old -> {
            old.setActive(false);
            old.setLastUpdated(LocalDateTime.now());
            weeklyRepo.save(old);
            log.info("Archived previous active weekly plan {} for user {}", old.getId(), user.getId());
        });

        WeeklyMealPlan plan = new WeeklyMealPlan();
        plan.setUserId(user.getId());
        plan.setStartDate(startDate);
        plan.setStatus(WeeklyMealPlan.Status.PENDING);
        plan.setActive(true);
        plan.setUserRequest(userRequest);
        plan.setProgressTotal(7 * plan.getMealSlots().split(",").length);
        plan.setProgressCurrent(0);
        plan.setProgressMessage("Queued");
        plan = weeklyRepo.save(plan);

        // Cross-bean call — Spring's @Async proxy on WeeklyPlanAsyncRunner intercepts this.
        asyncRunner.run(plan.getId(), user.getId());

        return plan;
    }

    /**
     * The actual generation logic. Called by WeeklyPlanAsyncRunner on a
     * background thread (NOT directly from createAndStart — that would defeat @Async).
     */
    public void generate(Long weeklyPlanId, Long userId) {
        log.info("Starting generation for weekly plan {} on thread {}",
            weeklyPlanId, Thread.currentThread().getName());
        try {
            User user = userRepo.findById(userId).orElseThrow();
            loadProfileEagerly(user);

            WeeklyMealPlan plan = weeklyRepo.findById(weeklyPlanId).orElseThrow();
            plan.setStatus(WeeklyMealPlan.Status.GENERATING);
            plan.setProgressMessage("Starting…");
            plan.setLastUpdated(LocalDateTime.now());
            weeklyRepo.save(plan);

            String[] slots = plan.getMealSlots().split(",");
            String profileContext = buildProfileContext(user);

            List<String> recentMealSummaries = new ArrayList<>();
            int successful = 0;
            int total = plan.getProgressTotal();

            outer:
            for (int day = 1; day <= 7; day++) {
                LocalDate plannedDate = plan.getStartDate().plusDays(day - 1);

                for (String slot : slots) {
                    String slotTrim = slot.trim();

                    String msg = String.format("Day %d of 7 — generating %s", day, slotTrim);
                    updateProgressMessage(weeklyPlanId, msg);

                    try {
                        MealPlan mealPlan = generateOneMeal(
                            user, profileContext, plan.getUserRequest(),
                            slotTrim, day, plannedDate, weeklyPlanId,
                            recentMealSummaries);

                        if (mealPlan.getStatus() == MealPlan.Status.APPROVED) {
                            successful++;
                            String name = extractMealName(mealPlan.getRecipeJson());
                            if (name != null) {
                                recentMealSummaries.add(slotTrim + " (Day " + day + "): " + name);
                                while (recentMealSummaries.size() > CONTEXT_WINDOW_MEALS) {
                                    recentMealSummaries.remove(0);
                                }
                            }
                        } else {
                            log.warn("Meal generation produced non-APPROVED status: {}", mealPlan.getStatus());
                        }
                    } catch (Exception e) {
                        log.error("Failed to generate Day {} {}: {}", day, slotTrim, e.getMessage());
                    }

                    incrementProgress(weeklyPlanId);

                    // Self-pace under Groq's TPM limit. Skip on the last meal.
                    if (!(day == 7 && slotTrim.equals(slots[slots.length - 1].trim()))) {
                        try {
                            Thread.sleep(INTER_MEAL_THROTTLE_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            log.warn("Generation interrupted at day {} {}", day, slotTrim);
                            break outer;
                        }
                    }
                }
            }

            // Finalize plan status
            WeeklyMealPlan finalPlan = weeklyRepo.findById(weeklyPlanId).orElseThrow();

            // Build the aggregated grocery list from all approved meals
            List<MealPlan> generatedMeals = mealRepo
                .findByWeeklyPlanIdOrderByDayNumberAscIdAsc(weeklyPlanId);
            try {
                String groceryJson = groceryAggregator.aggregate(generatedMeals, user);
                finalPlan.setGroceryListJson(groceryJson);
                log.info("Aggregated grocery list for plan {} ({} meals)",
                    weeklyPlanId, generatedMeals.size());
            } catch (Exception e) {
                log.error("Failed to aggregate grocery list for plan {}", weeklyPlanId, e);
            }

            if (successful == total) {
                finalPlan.setStatus(WeeklyMealPlan.Status.COMPLETED);
                finalPlan.setProgressMessage("All meals generated successfully");
            } else if (successful > 0) {
                finalPlan.setStatus(WeeklyMealPlan.Status.PARTIAL);
                finalPlan.setProgressMessage(String.format(
                    "%d of %d meals generated. %d skipped due to safety/system issues.",
                    successful, total, total - successful));
            } else {
                finalPlan.setStatus(WeeklyMealPlan.Status.FAILED);
                finalPlan.setProgressMessage("No meals could be generated");
                finalPlan.setErrorMessage("All meal generations failed. Check logs for details.");
            }
            finalPlan.setCompletedAt(LocalDateTime.now());
            finalPlan.setLastUpdated(LocalDateTime.now());
            weeklyRepo.save(finalPlan);

            log.info("Weekly plan {} finished: status={}, successful={}/{}",
                weeklyPlanId, finalPlan.getStatus(), successful, total);

        } catch (Exception e) {
            log.error("Catastrophic failure during weekly plan {} generation", weeklyPlanId, e);
            try {
                WeeklyMealPlan plan = weeklyRepo.findById(weeklyPlanId).orElseThrow();
                plan.setStatus(WeeklyMealPlan.Status.FAILED);
                plan.setErrorMessage("Unexpected error: " + e.getMessage());
                plan.setLastUpdated(LocalDateTime.now());
                weeklyRepo.save(plan);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Generate ONE meal (one slot of one day).
     */
    @Transactional
    public MealPlan generateOneMeal(User user, String profileContext, String userRequest,
                                    String mealSlot, int dayNumber, LocalDate plannedDate,
                                    Long weeklyPlanId, List<String> recentMealSummaries) {

        List<String> medGenericNames = extractMedicationGenericNames(user);
        List<String> conditions = extractConditions(user);

        JsonNode lastRecipe = null;
        ObjectNode lastReview = null;
        List<DrugNutrientRuleEngine.Finding> drugFindings = List.of();
        List<ConditionFoodRuleEngine.Finding> conditionFindings = List.of();
        String regenerationFeedback = "";
        int attempts = 0;
        boolean approved = false;

        while (attempts <= MAX_REGENERATIONS_PER_MEAL) {
            attempts++;

            JsonNode recipe = callChef(profileContext, userRequest, mealSlot, dayNumber,
                recentMealSummaries, regenerationFeedback);
            lastRecipe = recipe;
            List<String> ingredientNames = extractIngredientNames(recipe);

            drugFindings = drugEngine.check(medGenericNames, ingredientNames);
            conditionFindings = conditionEngine.check(conditions, ingredientNames);

            if (drugEngine.hasBlocker(drugFindings) || conditionEngine.hasBlocker(conditionFindings)) {
                regenerationFeedback = buildFeedback(drugFindings, conditionFindings);
                continue;
            }

            ObjectNode review = callReviewer(profileContext, recipe);
            lastReview = review;
            if ("PASS".equalsIgnoreCase(review.path("verdict").asText("FAIL"))) {
                approved = true;
                break;
            }
            regenerationFeedback = review.path("regenerationGuidance").asText("");
        }

        MealPlan plan = new MealPlan();
        plan.setUserId(user.getId());
        plan.setWeeklyPlanId(weeklyPlanId);
        plan.setDayNumber(dayNumber);
        plan.setMealSlot(mealSlot);
        plan.setPlannedDate(plannedDate);
        plan.setRegenerationCount(attempts - 1);
        plan.setRecipeJson(lastRecipe == null ? null : lastRecipe.toString());
        plan.setReviewJson(buildReviewPayload(lastReview, drugFindings, conditionFindings).toString());
        plan.setStatus(approved ? MealPlan.Status.APPROVED : MealPlan.Status.FAILED_SAFETY);
        plan.setGroceryListJson(null);
        plan.setLastUpdated(LocalDateTime.now());
        return mealRepo.save(plan);
    }

    // ---------- LLM calls ----------

    private JsonNode callChef(String profileContext, String userRequest, String mealSlot,
                              int dayNumber, List<String> recentMealSummaries,
                              String regenerationFeedback) {
        StringBuilder msg = new StringBuilder();
        msg.append("USER PROFILE:\n").append(profileContext).append("\n\n");
        msg.append("THIS IS DAY ").append(dayNumber).append(" OF A 7-DAY WEEKLY PLAN.\n");
        msg.append("MEAL SLOT: ").append(mealSlot).append("\n");
        if (recentMealSummaries != null && !recentMealSummaries.isEmpty()) {
            msg.append("\nRECENT MEALS IN THIS PLAN (avoid repeating these):\n");
            for (String s : recentMealSummaries) msg.append("- ").append(s).append("\n");
        }
        if (userRequest != null && !userRequest.isBlank()) {
            msg.append("\nUSER'S WEEKLY REQUEST: ").append(userRequest).append("\n");
        }
        if (regenerationFeedback != null && !regenerationFeedback.isBlank()) {
            msg.append("\nREGENERATION FEEDBACK (must address):\n").append(regenerationFeedback).append("\n");
        }

        String raw = groqClient.complete(AgentPrompts.CHEF_SYSTEM, msg.toString(), true);
        return parseJsonOrThrow(raw, "chef");
    }

    private ObjectNode callReviewer(String profileContext, JsonNode recipe) {
        String msg = "USER PROFILE:\n" + profileContext + "\n\nRECIPE TO REVIEW:\n" + recipe.toString();
        String raw = groqClient.complete(AgentPrompts.REVIEWER_SYSTEM, msg, true);
        JsonNode node = parseJsonOrThrow(raw, "reviewer");
        return node.isObject() ? (ObjectNode) node : mapper.createObjectNode().put("verdict", "FAIL");
    }

    // ---------- progress helpers (each its own transaction so polling sees updates immediately) ----------

    @Transactional
    public void updateProgressMessage(Long planId, String message) {
        weeklyRepo.findById(planId).ifPresent(plan -> {
            plan.setProgressMessage(message);
            plan.setLastUpdated(LocalDateTime.now());
            weeklyRepo.save(plan);
        });
    }

    @Transactional
    public void incrementProgress(Long planId) {
        weeklyRepo.findById(planId).ifPresent(plan -> {
            plan.setProgressCurrent(plan.getProgressCurrent() + 1);
            plan.setLastUpdated(LocalDateTime.now());
            weeklyRepo.save(plan);
        });
    }

    // ---------- helpers ----------

    private void loadProfileEagerly(User user) {
        if (user.getHealthConditions() != null) user.getHealthConditions().size();
        if (user.getAllergies() != null) user.getAllergies().size();
        if (user.getFoodPreferences() != null) user.getFoodPreferences().size();
        if (user.getFoodsToAvoid() != null) user.getFoodsToAvoid().size();
        if (user.getMedications() != null) user.getMedications().size();
    }

    private String extractMealName(String recipeJson) {
        try {
            JsonNode n = mapper.readTree(recipeJson);
            return n.path("mealName").asText(null);
        } catch (Exception e) { return null; }
    }

    private List<String> extractIngredientNames(JsonNode recipe) {
        List<String> out = new ArrayList<>();
        JsonNode arr = recipe.path("ingredients");
        if (arr.isArray()) for (JsonNode n : arr) out.add(n.path("name").asText(""));
        return out;
    }

    private List<String> extractMedicationGenericNames(User user) {
        if (user.getMedications() == null) return List.of();
        List<String> out = new ArrayList<>();
        for (Medication m : user.getMedications()) {
            if (Boolean.TRUE.equals(m.getActive()) && m.getGenericName() != null) {
                out.add(m.getGenericName().toLowerCase().trim());
            }
        }
        return out;
    }

    private List<String> extractConditions(User user) {
        if (user.getHealthConditions() == null) return List.of();
        List<String> out = new ArrayList<>();
        for (HealthCondition c : user.getHealthConditions()) {
            if (c.getCondition() != null) out.add(c.getCondition());
        }
        return out;
    }

    private String buildProfileContext(User user) {
        ObjectNode profile = mapper.createObjectNode();
        profile.put("age", user.getAge() == null ? 0 : user.getAge());
        profile.put("dietaryPreference", nonNull(user.getDietaryPreference()));
        profile.put("healthGoal", nonNull(user.getHealthGoal()));
        profile.put("cuisinePreference", nonNull(user.getCuisinePreference()));

        addStringArray(profile, "healthConditions", user.getHealthConditions(),
            o -> ((HealthCondition) o).getCondition());
        addStringArray(profile, "allergies", user.getAllergies(),
            o -> ((Allergy) o).getAllergy());
        addStringArray(profile, "foodPreferences", user.getFoodPreferences(),
            o -> ((FoodPreference) o).getFood());
        addStringArray(profile, "foodsToAvoid", user.getFoodsToAvoid(),
            o -> ((FoodToAvoid) o).getFood());

        ArrayNode meds = mapper.createArrayNode();
        if (user.getMedications() != null) {
            for (Medication m : user.getMedications()) {
                if (Boolean.TRUE.equals(m.getActive())) meds.add(nonNull(m.getGenericName()));
            }
        }
        profile.set("medications", meds);

        return profile.toString();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void addStringArray(ObjectNode parent, String fieldName, List list,
                                java.util.function.Function<Object, String> extract) {
        ArrayNode arr = mapper.createArrayNode();
        if (list != null) for (Object o : list) {
            String v = extract.apply(o);
            if (v != null) arr.add(v);
        }
        parent.set(fieldName, arr);
    }

    private ObjectNode buildReviewPayload(ObjectNode llmReview,
                                          List<DrugNutrientRuleEngine.Finding> drugFindings,
                                          List<ConditionFoodRuleEngine.Finding> conditionFindings) {
        ObjectNode out = llmReview == null ? mapper.createObjectNode() : llmReview.deepCopy();
        ArrayNode drugArr = mapper.createArrayNode();
        for (DrugNutrientRuleEngine.Finding f : drugFindings) {
            ObjectNode n = mapper.createObjectNode();
            n.put("medication", f.medication());
            n.put("ingredient", f.ingredient());
            n.put("severity", f.severity().name());
            n.put("reason", f.reason());
            n.put("source", f.source());
            drugArr.add(n);
        }
        out.set("deterministicFindings", drugArr);
        ArrayNode condArr = mapper.createArrayNode();
        for (ConditionFoodRuleEngine.Finding f : conditionFindings) {
            ObjectNode n = mapper.createObjectNode();
            n.put("condition", f.condition());
            n.put("ingredient", f.ingredient());
            n.put("severity", f.severity().name());
            n.put("reason", f.reason());
            n.put("source", f.source());
            condArr.add(n);
        }
        out.set("conditionFindings", condArr);
        return out;
    }

    private String buildFeedback(List<DrugNutrientRuleEngine.Finding> drugFindings,
                                 List<ConditionFoodRuleEngine.Finding> conditionFindings) {
        StringBuilder sb = new StringBuilder("Remove or replace ingredients that failed safety checks:\n");
        for (DrugNutrientRuleEngine.Finding f : drugFindings) {
            if (f.severity() == DrugNutrientRuleEngine.Severity.BLOCK) {
                sb.append("- '").append(f.ingredient()).append("' interacts with ")
                  .append(f.medication()).append(": ").append(f.reason()).append("\n");
            }
        }
        for (ConditionFoodRuleEngine.Finding f : conditionFindings) {
            if (f.severity() == ConditionFoodRuleEngine.Severity.BLOCK) {
                sb.append("- '").append(f.ingredient()).append("' contraindicated for ")
                  .append(f.condition()).append(": ").append(f.reason()).append("\n");
            }
        }
        return sb.toString();
    }

    private String nonNull(String s) { return s == null ? "" : s; }

    private JsonNode parseJsonOrThrow(String raw, String agent) {
        try { return mapper.readTree(cleanJsonResponse(raw)); }
        catch (Exception e) {
            log.error("Failed to parse {} response: {}", agent, raw, e);
            throw new RuntimeException("Could not parse " + agent + " response as JSON", e);
        }
    }

    private String cleanJsonResponse(String raw) {
        if (raw == null) return "{}";
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        return (start != -1 && end != -1 && start < end) ? raw.substring(start, end + 1) : "{}";
    }
}
