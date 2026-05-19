package com.pathyana.api.service.orchestration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pathyana.api.llm.GroqClient;
import com.pathyana.api.model.*;
import com.pathyana.api.repository.MealPlanRepository;
import com.pathyana.api.service.safety.ConditionFoodRuleEngine;
import com.pathyana.api.service.safety.DrugNutrientRuleEngine;
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
 * The 3-agent pipeline with TWO deterministic rule engines (drug + condition).
 *
 * v2 changes:
 *   - Adds ConditionFoodRuleEngine alongside DrugNutrientRuleEngine.
 *   - Surfaces uncovered medications and conditions in the response so the UI can
 *     flag them as "not in our safety database".
 *   - Either engine can trigger regeneration.
 */
@Service
public class MealOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(MealOrchestratorService.class);
    private static final int MAX_REGENERATIONS = 2;
    private static final String DISCLAIMER =
        "Pathyana provides educational information only. Not a substitute for advice from a qualified healthcare professional.";

    private final GroqClient groqClient;
    private final DrugNutrientRuleEngine drugEngine;
    private final ConditionFoodRuleEngine conditionEngine;
    private final IngredientSwapper swapper;
    private final MealPlanRepository mealPlanRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public MealOrchestratorService(GroqClient groqClient,
                                   DrugNutrientRuleEngine drugEngine,
                                   ConditionFoodRuleEngine conditionEngine,
                                   IngredientSwapper swapper,
                                   MealPlanRepository mealPlanRepository) {
        this.groqClient = groqClient;
        this.drugEngine = drugEngine;
        this.conditionEngine = conditionEngine;
        this.swapper = swapper;
        this.mealPlanRepository = mealPlanRepository;
    }

    public MealPlan generate(User user, String userRequest) {
        Objects.requireNonNull(user, "user");
        String profileJson = buildProfileContext(user);
        List<String> medGenericNames = extractMedicationGenericNames(user);
        List<String> conditions = extractConditions(user);

        List<String> uncoveredMeds = extractUncoveredMedications(user);
        List<String> uncoveredConditions = extractUncoveredConditions(user);

        JsonNode lastRecipe = null;
        ObjectNode lastReview = null;
        List<DrugNutrientRuleEngine.Finding> drugFindings = List.of();
        List<ConditionFoodRuleEngine.Finding> conditionFindings = List.of();
        String regenerationFeedback = "";
        int attempts = 0;
        boolean approved = false;

        while (attempts <= MAX_REGENERATIONS) {
            attempts++;
            log.info("Pipeline attempt {} for user {}", attempts, user.getId());

            JsonNode recipe = callChef(profileJson, userRequest, regenerationFeedback);
            lastRecipe = recipe;
            List<String> ingredientNames = extractIngredientNames(recipe);

            drugFindings = drugEngine.check(medGenericNames, ingredientNames);
            conditionFindings = conditionEngine.check(conditions, ingredientNames);

            boolean drugBlocked = drugEngine.hasBlocker(drugFindings);
            boolean conditionBlocked = conditionEngine.hasBlocker(conditionFindings);

            if (drugBlocked || conditionBlocked) {
                regenerationFeedback = buildFeedback(drugFindings, conditionFindings);
                log.info("Deterministic engines blocked attempt {}:\n{}", attempts, regenerationFeedback);
                continue;
            }

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
        plan.setReviewJson(buildReviewPayload(lastReview, drugFindings, conditionFindings,
            uncoveredMeds, uncoveredConditions).toString());

        if (approved) {
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

    // ---------- LLM calls ----------

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

    // ---------- response building ----------

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

    private ObjectNode buildReviewPayload(ObjectNode llmReview,
                                          List<DrugNutrientRuleEngine.Finding> drugFindings,
                                          List<ConditionFoodRuleEngine.Finding> conditionFindings,
                                          List<String> uncoveredMeds,
                                          List<String> uncoveredConditions) {
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

        ObjectNode coverage = mapper.createObjectNode();
        ArrayNode medsArr = mapper.createArrayNode();
        for (String m : uncoveredMeds) medsArr.add(m);
        coverage.set("uncoveredMedications", medsArr);
        ArrayNode condsArr = mapper.createArrayNode();
        for (String c : uncoveredConditions) condsArr.add(c);
        coverage.set("uncoveredConditions", condsArr);
        out.set("coverage", coverage);

        return out;
    }

    private String buildFeedback(List<DrugNutrientRuleEngine.Finding> drugFindings,
                                 List<ConditionFoodRuleEngine.Finding> conditionFindings) {
        StringBuilder sb = new StringBuilder("The previous recipe failed deterministic safety checks. Remove or replace these:\n");
        for (DrugNutrientRuleEngine.Finding f : drugFindings) {
            if (f.severity() == Severity.BLOCK) {
                sb.append("- '").append(f.ingredient()).append("' interacts with medication ")
                  .append(f.medication()).append(": ").append(f.reason()).append("\n");
            }
        }
        for (ConditionFoodRuleEngine.Finding f : conditionFindings) {
            if (f.severity() == ConditionFoodRuleEngine.Severity.BLOCK) {
                sb.append("- '").append(f.ingredient()).append("' is contraindicated for ")
                  .append(f.condition()).append(": ").append(f.reason()).append("\n");
            }
        }
        return sb.toString();
    }

    // ---------- extraction helpers ----------

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

    private List<String> extractUncoveredMedications(User user) {
        if (user.getMedications() == null) return List.of();
        List<String> out = new ArrayList<>();
        for (Medication m : user.getMedications()) {
            if (!Boolean.TRUE.equals(m.getActive())) continue;
            if (!drugEngine.hasCoverage(m.getGenericName())) {
                out.add(m.getDisplayName());
            }
        }
        return out;
    }

    private List<String> extractUncoveredConditions(User user) {
        if (user.getHealthConditions() == null) return List.of();
        List<String> out = new ArrayList<>();
        for (HealthCondition c : user.getHealthConditions()) {
            if (!conditionEngine.hasCoverage(c.getCondition())) {
                out.add(c.getCondition());
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
        try { return mapper.readTree(cleanJsonResponse(raw)); }
        catch (Exception e) {
            log.error("Failed to parse {} response: {}", agent, raw, e);
            throw new RuntimeException("Could not parse " + agent + " response as JSON", e);
        }
    }

    private JsonNode readTreeSafe(String s) {
        try { return mapper.readTree(s); } catch (Exception e) { return mapper.createObjectNode(); }
    }

    private String cleanJsonResponse(String raw) {
        if (raw == null) return "{}";
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        return (start != -1 && end != -1 && start < end) ? raw.substring(start, end + 1) : "{}";
    }
}
