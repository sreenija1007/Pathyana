package com.pathyana.api.service.safety;

import com.pathyana.api.model.Allergy;
import com.pathyana.api.model.HealthCondition;
import com.pathyana.api.model.User;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Deterministic ingredient swapper. Runs AFTER the LLM Optimizer produces the grocery list.
 *
 * Why deterministic (not LLM):
 *   - 100% predictable. "Diabetic + gluten-free" must ALWAYS produce chickpea pasta.
 *   - These are short Map lookups — an LLM call here costs latency and money for zero benefit.
 *
 * Rule precedence (highest wins):
 *   1. Allergies (medical safety — never substituted away)
 *   2. Medical conditions (clinical optimization)
 *   3. Dietary preference (lifestyle)
 *
 * Extension point: load rules from YAML/DB when the list grows.
 */
@Service
public class IngredientSwapper {

    public record Swap(String original, String replacement, String reason) {}

    private record SwapRule(String keyword, String replacement, String triggerLabel, String reason) {}

    private static final List<SwapRule> DIABETIC_RULES = List.of(
        new SwapRule("pasta", "chickpea pasta", "diabetic",
            "Chickpea pasta has roughly half the glycemic load of wheat pasta and 2x the protein."),
        new SwapRule("white rice", "cauliflower rice or quinoa", "diabetic",
            "Lower glycemic impact and higher fiber."),
        new SwapRule("white bread", "sprouted whole-grain bread", "diabetic",
            "Lower glycemic response and more fiber."),
        new SwapRule("sugar", "monk fruit sweetener", "diabetic",
            "Zero glycemic impact.")
    );

    private static final List<SwapRule> GLUTEN_FREE_RULES = List.of(
        new SwapRule("pasta", "chickpea pasta", "gluten-free",
            "Naturally gluten-free."),
        new SwapRule("soy sauce", "tamari", "gluten-free",
            "Traditional soy sauce contains wheat; tamari typically doesn't."),
        new SwapRule("bread", "gluten-free sourdough", "gluten-free",
            "Avoids gluten without sacrificing structure."),
        new SwapRule("flour", "almond flour", "gluten-free",
            "Naturally gluten-free, lower-carb.")
    );

    private static final List<SwapRule> DAIRY_FREE_RULES = List.of(
        new SwapRule("milk", "unsweetened oat milk", "dairy allergy",
            "Avoids dairy protein."),
        new SwapRule("butter", "olive oil", "dairy allergy",
            "Plant-based fat."),
        new SwapRule("cheese", "nutritional yeast or cashew cheese", "dairy allergy",
            "Dairy-free with similar umami.")
    );

    /**
     * Apply all relevant swaps to a list of grocery items.
     *
     * @param items raw grocery items from the Optimizer (mutated in place)
     * @param user  used to determine which rule sets apply
     * @return list of swaps actually performed (for showing in the UI)
     */
    public List<Swap> applySwaps(List<String> items, User user) {
        Set<String> conditions = lowerStringSet(user.getHealthConditions(),
            o -> ((HealthCondition) o).getCondition());
        Set<String> allergies = lowerStringSet(user.getAllergies(),
            o -> ((Allergy) o).getAllergy());
        String diet = user.getDietaryPreference() == null
            ? "" : user.getDietaryPreference().toLowerCase();

        boolean diabetic = matchesAny(conditions, "diabet", "type 2", "type 1", "t2d", "t1d");
        boolean glutenFree = diet.contains("gluten") || matchesAny(conditions, "celiac");
        boolean dairyFree = matchesAny(allergies, "milk", "dairy", "lactose")
            || diet.contains("dairy-free");

        List<Swap> applied = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            String original = items.get(i);
            if (original == null) continue;
            String lower = original.toLowerCase();

            SwapRule rule = null;
            if (dairyFree)    rule = firstMatch(DAIRY_FREE_RULES, lower);
            if (rule == null && glutenFree) rule = firstMatch(GLUTEN_FREE_RULES, lower);
            if (rule == null && diabetic)   rule = firstMatch(DIABETIC_RULES, lower);

            if (rule != null) {
                items.set(i, rule.replacement());
                applied.add(new Swap(original, rule.replacement(),
                    rule.triggerLabel() + ": " + rule.reason()));
            }
        }
        return applied;
    }

    private SwapRule firstMatch(List<SwapRule> rules, String lowerItem) {
        for (SwapRule r : rules) if (lowerItem.contains(r.keyword())) return r;
        return null;
    }

    private boolean matchesAny(Set<String> haystack, String... needles) {
        for (String h : haystack) for (String n : needles) if (h.contains(n)) return true;
        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Set<String> lowerStringSet(List list, java.util.function.Function<Object, String> extract) {
        if (list == null) return Set.of();
        Set<String> out = new HashSet<>();
        for (Object o : list) {
            String v = extract.apply(o);
            if (v != null) out.add(v.toLowerCase());
        }
        return out;
    }
}
