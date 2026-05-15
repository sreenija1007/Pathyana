package com.pathyana.api.service.orchestration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pathyana.api.llm.GroqClient;
import com.pathyana.api.model.*;
import com.pathyana.api.repository.MealPlanRepository;
import com.pathyana.api.service.safety.DrugNutrientRuleEngine;
import com.pathyana.api.service.safety.DrugNutrientRuleEngine.Finding;
import com.pathyana.api.service.safety.DrugNutrientRuleEngine.Severity;
import com.pathyana.api.service.safety.IngredientSwapper;
import com.pathyana.api.service.safety.IngredientSwapper.Swap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * The Chef → Reviewer (+ deterministic safety) → Optimizer (+ swapper) pipeline.
 *
 * Control flow:
 *   1. CHEF generates a recipe.
 *   2. DETERMINISTIC RULE ENGINE checks ingredients vs user's medications.
 *      - Any BLOCK finding → regenerate with explicit feedback, WITHOUT calling the Reviewer
 *        (saves a Groq call; the rule engine is authoritative).
 *   3. REVIEWER LLM does the nuanced review.
 *   4. If Reviewer says FAIL → regenerate with its guidance.
 *   5. After MAX_REGENERATIONS attempts, give up and return FAILED_SAFETY with the last attempt.
 *   6. OPTIMIZER converts the approved recipe to a grocery list.
 *   7. SWAPPER deterministically replaces items based on user profile.
 *   8. Persist and return.
 */
@Service
public class MealOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(MealOrchestratorService.class);
    private static final int MAX_REGENERATIONS = 2;
    private static final String DISCLAIMER =
        "Pathyana provides educational information only. Not a substitute for advice from a qualified healthcare professional.";

    private final GroqClient groqClient;
    private final DrugNutrientRuleEngine ruleEngine;
    private final IngredientSwapper swapper;
    private final MealPlanRepository mealPlanRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public MealOrchestratorService(GroqClient groqClient,
                                   DrugNutrientRuleEngine ruleEngine,
                                   IngredientSwapper swapper,
                                   MealPlanRepository mealPlanRepository) {
        this.groqClient = groqClient;
        this.ruleEngine = ruleEngine;
        this.swapper = swapper;
        this.mealPlanRepository = mealPlanRepository;
    }

    public MealPlan generate(User user, String userRequest) {
        Objects.requireNonNull(user, "user");
        String profileJson = buildProfileContext(user);
        List<String> medGenericNames = extractMedications(user);

        JsonNode lastRecipe = null;
        ObjectNode lastReview = null;
        List<Finding> deterministicFindings = List.of();
        String regenerationFeedback = "";
        int attempts = 0;
        boolean approved = false;

        while (attempts <= MAX_REGENERATIONS) {
            attempts++;
            log.info("Pipeline attempt {} for user {}", attempts, user.getId());

            // 1. Chef
            JsonNode recipe = callChef(profileJson, userRequest, regenerationFeedback);
            lastRecipe = recipe;
            List<String> ingredientNames = extractIngredientNames(recipe);

            // 2. Deterministic rule engine — authoritative
            deterministicFindings = ruleEngine.check(medGenericNames, ingredientNames);
            if (ruleEngine.hasBlocker(deterministicFindings)) {
                regenerationFeedback = buildFeedbackFromFindings(deterministicFindings);
                log.info("Deterministic engine blocked attempt {}: {}", attempts, regenerationFeedback);
                continue;  // skip Reviewer, go straight to Chef retry
            }

            // 3. Reviewer LLM
            ObjectNode review = callReviewer(profileJson, recipe);
            lastReview = review;
            String verdict = review.path("verdict").asText("FAIL");

            if ("PASS".equalsIgnoreCase(verdict)) {
                approved = true;
                break;
            }
            regenerationFeedback = review.path("regenerationGuidance").asText("");
            log.info("Reviewer FAILED attempt {}: {}", attempts, regenerationFeedback);
        }

        MealPlan plan = new MealPlan();
        plan.setUserId(user.getId());
        plan.setRegenerationCount(attempts - 1);
        plan.setRecipeJson(lastRecipe == null ? null : lastRecipe.toString());
        plan.setReviewJson(buildReviewPayload(lastReview, deterministicFindings).toString());

        if (approved) {
            // 4 & 5. Optimizer + Swapper
            JsonNode groceryList = callOptimizer(lastRecipe);
            List<Swap> swaps = applySwapsToGrocery(groceryList, user);

            ObjectNode finalGrocery = mapper.createObjectNode();
            finalGrocery.set("items", groceryList.path("items"));
            finalGrocery.put("estimatedTotalUsd", groceryList.path("estimatedTotalUsd").asDouble(0));
            ArrayNode swapsArr = mapper.createArrayNode();
            for (Swap s : swaps) {
                ObjectNode n = mapper.createObjectNode();
                n.put("original", s.original());
                n.put("replacement", s.replacement());
                n.put("reason", s.reason());
                swapsArr.add(n);
            }
            finalGrocery.set("appliedSwaps", swapsArr);
            plan.setGroceryListJson(finalGrocery.toString());
            plan.setStatus(MealPlan.Status.APPROVED);
        } else {
            plan.setStatus(MealPlan.Status.FAILED_SAFETY);
        }

        plan.setLastUpdated(LocalDateTime.now());
        return mealPlanRepository.save(plan);
    }

    // ---------- agent calls ----------

    private JsonNode callChef(String profileJson, String userRequest, String regenerationFeedback) {
        StringBuilder userMsg = new StringBuilder();
        userMsg.append("USER PROFILE:\n").append(profileJson).append("\n\n");
        userMsg.append("USER REQUEST: ").append(userRequest == null ? "Generate a meal." : userRequest).append("\n");
        if (regenerationFeedback != null && !regenerationFeedback.isBlank()) {
            userMsg.append("\nREGENERATION FEEDBACK from prior review (you MUST address this):\n")
                   .append(regenerationFeedback).append("\n");
        }
        String raw = groqClient.complete(AgentPrompts.CHEF_SYSTEM, userMsg.toString(), true);
        return parseJsonOrThrow(raw, "chef");
    }

    private ObjectNode callReviewer(String profileJson, JsonNode recipe) {
        String userMsg = "USER PROFILE:\n" + profileJson + "\n\nRECIPE TO REVIEW:\n" + recipe.toString();
        String raw = groqClient.complete(AgentPrompts.REVIEWER_SYSTEM, userMsg, true);
        JsonNode node = parseJsonOrThrow(raw, "reviewer");
        return node.isObject() ? (ObjectNode) node : mapper.createObjectNode().put("verdict", "FAIL");
    }

    private JsonNode callOptimizer(JsonNode recipe) {
        String raw = groqClient.complete(AgentPrompts.OPTIMIZER_SYSTEM,
            "APPROVED RECIPE:\n" + recipe.toString(), true);
        return parseJsonOrThrow(raw, "optimizer");
    }

    // ---------- helpers ----------

    /** Build the combined response payload the controller returns to the frontend. */
    public String buildFrontendPayload(MealPlan plan) {
        ObjectNode combined = mapper.createObjectNode();
        combined.put("status", plan.getStatus().name());
        combined.put("regenerationCount", plan.getRegenerationCount());
        combined.put("modelId", groqClient.modelId());
        combined.put("mealPlanId", plan.getId());
        if (plan.getRecipeJson() != null) combined.set("recipe", readTreeSafe(plan.getRecipeJson()));
        combined.set("review", readTreeSafe(plan.getReviewJson()));
        if (plan.getGroceryListJson() != null) combined.set("grocery", readTreeSafe(plan.getGroceryListJson()));
        combined.put("disclaimer", DISCLAIMER);
        return combined.toString();
    }

    private List<Swap> applySwapsToGrocery(JsonNode groceryList, User user) {
        JsonNode items = groceryList.path("items");
        if (!items.isArray()) return List.of();
        List<String> names = new ArrayList<>();
        for (JsonNode item : items) names.add(item.path("name").asText(""));
        List<Swap> applied = swapper.applySwaps(names, user);
        for (int i = 0; i < items.size(); i++) {
            ((ObjectNode) items.get(i)).put("name", names.get(i));
        }
        return applied;
    }

    private ObjectNode buildReviewPayload(ObjectNode llmReview, List<Finding> deterministicFindings) {
        ObjectNode out = llmReview == null ? mapper.createObjectNode() : llmReview.deepCopy();
        ArrayNode detArr = mapper.createArrayNode();
        for (Finding f : deterministicFindings) {
            ObjectNode n = mapper.createObjectNode();
            n.put("medication", f.medication());
            n.put("ingredient", f.ingredient());
            n.put("severity", f.severity().name());
            n.put("reason", f.reason());
            n.put("source", f.source());
            detArr.add(n);
        }
        out.set("deterministicFindings", detArr);
        return out;
    }

    private String buildFeedbackFromFindings(List<Finding> findings) {
        StringBuilder sb = new StringBuilder(
            "The previous recipe contained ingredients that interact with the user's medications. Remove or replace these:\n");
        for (Finding f : findings) {
            if (f.severity() == Severity.BLOCK) {
                sb.append("- '").append(f.ingredient()).append("' interacts with ")
                  .append(f.medication()).append(": ").append(f.reason()).append("\n");
            }
        }
        return sb.toString();
    }

    private List<String> extractIngredientNames(JsonNode recipe) {
        List<String> out = new ArrayList<>();
        JsonNode arr = recipe.path("ingredients");
        if (arr.isArray()) for (JsonNode n : arr) out.add(n.path("name").asText(""));
        return out;
    }

    private List<String> extractMedications(User user) {
        if (user.getMedications() == null) return List.of();
        List<String> out = new ArrayList<>();
        for (Medication m : user.getMedications()) {
            if (Boolean.TRUE.equals(m.getActive()) && m.getGenericName() != null) {
                out.add(m.getGenericName().toLowerCase().trim());
            }
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

        // Eating window context
        LocalTime startT = user.getEatingWindowStart();
        LocalTime endT   = user.getEatingWindowEnd();
        if (startT != null && endT != null) {
            ObjectNode window = mapper.createObjectNode();
            window.put("start", startT.toString());
            window.put("end", endT.toString());
            window.put("protocol", user.getWindowProtocol() == null ? "CUSTOM"
                : user.getWindowProtocol().name());
            if (user.getMealToBedBufferMinutes() != null)
                window.put("mealToBedBufferMinutes", user.getMealToBedBufferMinutes());
            if (user.getTypicalBedtime() != null)
                window.put("typicalBedtime", user.getTypicalBedtime().toString());
            profile.set("eatingWindow", window);
        }

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

    private String nonNull(String s) { return s == null ? "" : s; }

    private JsonNode parseJsonOrThrow(String raw, String agent) {
        try {
            return mapper.readTree(cleanJsonResponse(raw));
        } catch (Exception e) {
            log.error("Failed to parse {} response: {}", agent, raw, e);
            throw new RuntimeException("Could not parse " + agent + " response as JSON", e);
        }
    }

    private JsonNode readTreeSafe(String s) {
        try { return mapper.readTree(s); } catch (Exception e) { return mapper.createObjectNode(); }
    }

    /** Strip prose around JSON if the model ignored response_format. */
    private String cleanJsonResponse(String raw) {
        if (raw == null) return "{}";
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        return (start != -1 && end != -1 && start < end) ? raw.substring(start, end + 1) : "{}";
    }
}
