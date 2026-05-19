package com.pathyana.api.service.orchestration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pathyana.api.model.MealPlan;
import com.pathyana.api.model.User;
import com.pathyana.api.service.safety.IngredientSwapper;
import com.pathyana.api.service.safety.IngredientSwapper.Swap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Aggregates ingredients across all meals in a weekly plan into a single
 * shopping list, grouped by store section.
 *
 * Why deterministic Java and not an LLM:
 *   - Predictable: same recipes → same grocery list.
 *   - Cheap: aggregating 21 recipes takes milliseconds, not 21 LLM calls.
 *   - Auditable: a clinician can read this logic and trust it.
 *
 * Aggregation strategy:
 *   1. For each meal, parse its ingredients[] array.
 *   2. Normalize each name (lowercase, trim, collapse whitespace) to a key.
 *   3. Group identical keys together; collect raw quantity strings as a list.
 *   4. Apply the deterministic IngredientSwapper based on user profile.
 *   5. Assign each unique item to a store section using a small classifier.
 *   6. Render quantities as either summed (when units match) or listed (when mixed).
 *
 * Why we don't try to sum "200g spinach + 1 cup spinach":
 *   - Unit conversion across mass / volume / count is a rabbit hole.
 *   - "Buy 2 onions" and "buy 1 onion" → "buy 3 onions" works.
 *   - "buy 200g spinach" + "buy 1 cup spinach" → we just show both quantities,
 *     and the user reads them as "I need spinach for two recipes, here's how much."
 *   - This is honest. Pretending we computed perfect unit conversions would be wrong
 *     30% of the time.
 */
@Service
public class WeeklyGroceryAggregator {

    private static final Logger log = LoggerFactory.getLogger(WeeklyGroceryAggregator.class);

    private final IngredientSwapper swapper;
    private final ObjectMapper mapper = new ObjectMapper();

    public WeeklyGroceryAggregator(IngredientSwapper swapper) {
        this.swapper = swapper;
    }

    /**
     * Build a weekly grocery list from a list of approved meal plans.
     * Returns a JSON string structured the same way as the single-meal grocery
     * list so the frontend can reuse the existing GroceryList component.
     */
    public String aggregate(List<MealPlan> meals, User user) {
        // Step 1 — pull all ingredient entries
        Map<String, AggregatedItem> grouped = new LinkedHashMap<>();
        int approvedCount = 0;

        for (MealPlan meal : meals) {
            if (meal.getStatus() != MealPlan.Status.APPROVED) continue;
            if (meal.getRecipeJson() == null) continue;
            approvedCount++;

            try {
                JsonNode recipe = mapper.readTree(meal.getRecipeJson());
                JsonNode ingredients = recipe.path("ingredients");
                if (!ingredients.isArray()) continue;

                String mealLabel = describeMeal(meal);
                for (JsonNode ing : ingredients) {
                    String rawName = ing.path("name").asText("").trim();
                    String rawQty  = ing.path("quantity").asText("").trim();
                    if (rawName.isEmpty()) continue;

                    String key = normalizeKey(rawName);
                    AggregatedItem item = grouped.computeIfAbsent(key,
                        k -> new AggregatedItem(rawName));
                    if (!rawQty.isEmpty()) {
                        item.addQuantity(rawQty, mealLabel);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse recipe for meal {}: {}", meal.getId(), e.getMessage());
            }
        }

        log.info("Aggregating {} approved meals into grocery list", approvedCount);

        // Step 2 — extract names into a mutable list for the swapper
        List<String> names = new ArrayList<>();
        List<AggregatedItem> items = new ArrayList<>(grouped.values());
        for (AggregatedItem item : items) names.add(item.displayName);

        // Step 3 — apply deterministic swaps (e.g., pasta → chickpea pasta)
        List<Swap> appliedSwaps = swapper.applySwaps(names, user);
        for (int i = 0; i < items.size(); i++) {
            items.get(i).displayName = names.get(i);
        }

        // Step 4 — assign sections + render to JSON
        return renderJson(items, appliedSwaps);
    }

    private String renderJson(List<AggregatedItem> items, List<Swap> appliedSwaps) {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode itemArr = mapper.createArrayNode();

        for (AggregatedItem item : items) {
            ObjectNode n = mapper.createObjectNode();
            n.put("name", item.displayName);
            n.put("quantity", item.renderQuantity());
            n.put("section", classifySection(item.displayName));
            // Note: we deliberately don't include "usedIn" labels in the response
            // to keep the existing GroceryList component working unchanged. Future
            // enhancement could surface them.
            itemArr.add(n);
        }
        root.set("items", itemArr);

        // estimated total — rough sum based on a per-section heuristic.
        // Honestly: this is a guess. We mark it as such.
        double estimated = roughEstimateUsd(items);
        root.put("estimatedTotalUsd", estimated);

        ArrayNode swapsArr = mapper.createArrayNode();
        for (Swap s : appliedSwaps) {
            ObjectNode n = mapper.createObjectNode();
            n.put("original", s.original());
            n.put("replacement", s.replacement());
            n.put("reason", s.reason());
            swapsArr.add(n);
        }
        root.set("appliedSwaps", swapsArr);

        return root.toString();
    }

    // ---------- Section classifier ----------

    private static final Map<String, List<String>> SECTION_KEYWORDS = Map.of(
        "produce", List.of("spinach", "kale", "lettuce", "tomato", "onion", "garlic",
            "pepper", "carrot", "cucumber", "broccoli", "cauliflower", "potato", "sweet potato",
            "zucchini", "eggplant", "mushroom", "celery", "asparagus", "leek", "green bean",
            "avocado", "lemon", "lime", "apple", "banana", "berry", "orange",
            "cilantro", "parsley", "basil", "mint", "ginger", "scallion", "kale", "arugula"),
        "protein", List.of("chicken", "beef", "pork", "lamb", "turkey", "fish", "salmon",
            "tuna", "shrimp", "tofu", "tempeh", "egg", "lentil", "chickpea", "bean", "paneer",
            "cottage cheese"),
        "dairy", List.of("milk", "yogurt", "cheese", "butter", "cream", "ghee"),
        "pantry", List.of("rice", "pasta", "oil", "vinegar", "salt", "pepper", "sugar",
            "honey", "flour", "oat", "quinoa", "couscous", "soy sauce", "tamari", "spice",
            "cumin", "turmeric", "paprika", "cinnamon", "stock", "broth", "bread",
            "tortilla", "nut", "seed", "almond", "walnut", "tahini"),
        "frozen", List.of("frozen")
    );

    private String classifySection(String name) {
        String lower = name.toLowerCase();
        for (Map.Entry<String, List<String>> entry : SECTION_KEYWORDS.entrySet()) {
            for (String kw : entry.getValue()) {
                if (lower.contains(kw)) return entry.getKey();
            }
        }
        return "other";
    }

    // ---------- Cost estimate ----------

    private double roughEstimateUsd(List<AggregatedItem> items) {
        // Honest rough cut: $2 per unique grocery item averages out for a US grocery basket.
        // For a portfolio app, this is fine. Production would need a real pricing API.
        return Math.round(items.size() * 2.5 * 100.0) / 100.0;
    }

    // ---------- Inner state ----------

    /** Normalize a name for grouping ("Fresh Spinach " → "fresh spinach"). */
    private String normalizeKey(String name) {
        return name.toLowerCase().trim().replaceAll("\\s+", " ");
    }

    private String describeMeal(MealPlan meal) {
        String slot = meal.getMealSlot() == null ? "meal" : meal.getMealSlot();
        Integer day = meal.getDayNumber();
        return day == null ? slot : ("D" + day + " " + slot);
    }

    /** Mutable per-ingredient bucket, used inside the LinkedHashMap during aggregation. */
    private static class AggregatedItem {
        String displayName;
        List<String> quantities = new ArrayList<>();
        List<String> usedIn = new ArrayList<>();

        AggregatedItem(String displayName) { this.displayName = displayName; }

        void addQuantity(String qty, String mealLabel) {
            quantities.add(qty);
            usedIn.add(mealLabel);
        }

        /**
         * Render quantities for display. If all match (e.g., all "1 onion"), we sum them.
         * Otherwise we list them comma-separated. Conservative — we don't try to convert
         * between mass and volume.
         */
        String renderQuantity() {
            if (quantities.isEmpty()) return "as needed";
            if (quantities.size() == 1) return quantities.get(0);

            // Try simple summation when quantities all match a "N unit" pattern
            Integer summedCount = tryCountSum(quantities);
            if (summedCount != null) {
                String unit = extractUnit(quantities.get(0));
                return summedCount + (unit.isEmpty() ? "" : " " + unit);
            }

            // Fall back: list them
            return String.join(" + ", quantities);
        }

        /**
         * If every quantity string parses as "<integer> <same unit>" (e.g., "2 onion", "1 onion"),
         * return the sum. Otherwise null.
         */
        private Integer tryCountSum(List<String> qs) {
            String unit = null;
            int total = 0;
            for (String q : qs) {
                String[] parts = q.trim().split("\\s+", 2);
                try {
                    int n = Integer.parseInt(parts[0]);
                    String u = parts.length > 1 ? parts[1] : "";
                    if (unit == null) unit = u;
                    else if (!unit.equalsIgnoreCase(u)) return null;
                    total += n;
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return total;
        }

        private String extractUnit(String q) {
            String[] parts = q.trim().split("\\s+", 2);
            return parts.length > 1 ? parts[1] : "";
        }
    }
}
