package com.pathyana.api.service.safety;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Deterministic drug-nutrient interaction engine.
 *
 * THIS is the authoritative safety check, not the Reviewer LLM. If a rule here fires,
 * the meal is blocked regardless of what the LLM said. The Reviewer adds nuanced
 * clinical commentary but cannot override these rules.
 *
 * Why deterministic and not LLM:
 *   - Predictability: warfarin + spinach is ALWAYS flagged. No "the AI was creative today."
 *   - Auditability: a clinician can read this file and verify what's blocked.
 *   - Speed/cost: milliseconds, no API call.
 *
 * Design notes:
 *   - Rules are in-memory for now. Production would back this with a vetted database
 *     (Lexicomp, RxNorm + NIH ODS) or a clinical decision support API. The interface stays
 *     the same — only the loader changes.
 *   - Matching is substring-based on lowercased text. Cheap and predictable. False positives
 *     in cooking contexts are vanishingly rare and the cost of a false negative is much higher.
 *
 * IMPORTANT: this is illustrative seed data drawn from public FDA/NIH sources. Before
 * shipping to real users, have a registered dietitian or pharmacist review and expand the
 * rule list, and add a prominent in-app disclaimer.
 */
@Service
public class DrugNutrientRuleEngine {

    public enum Severity { BLOCK, WARN }

    public record Rule(
        String drugGeneric,             // matches Medication.genericName (lowercased)
        Set<String> forbiddenKeywords,  // lowercased substrings to look for in ingredient names
        Severity severity,
        String reason,
        String source                   // citation for explainability
    ) {}

    public record Finding(
        String medication,
        String ingredient,
        Severity severity,
        String reason,
        String source
    ) {}

    // Seed rules. Citations are to real sources; verify current guidance before relying on these.
    private static final List<Rule> RULES = List.of(
        // Warfarin (anticoagulant) — Vitamin K consistency
        new Rule("warfarin",
            Set.of("kale", "spinach", "collard", "swiss chard", "natto", "brussels sprouts"),
            Severity.WARN,
            "High vitamin K intake reduces warfarin's anticoagulant effect. Consistency matters more than avoidance — sudden changes destabilize INR.",
            "NIH Office of Dietary Supplements — Vitamin K Fact Sheet"),
        new Rule("warfarin",
            Set.of("cranberry juice", "cranberry"),
            Severity.WARN,
            "May increase warfarin's anticoagulant effect and bleeding risk.",
            "FDA Drug Safety Communication"),

        // Statins — CYP3A4 inhibition by grapefruit furanocoumarins
        new Rule("atorvastatin", Set.of("grapefruit"), Severity.BLOCK,
            "Grapefruit inhibits CYP3A4 metabolism of atorvastatin, raising blood levels and the risk of myopathy/rhabdomyolysis.",
            "FDA — Grapefruit Juice and Some Drugs Don't Mix"),
        new Rule("simvastatin", Set.of("grapefruit"), Severity.BLOCK,
            "Same CYP3A4 mechanism as atorvastatin; simvastatin blood levels can rise dramatically.",
            "FDA"),
        new Rule("lovastatin", Set.of("grapefruit"), Severity.BLOCK,
            "Same CYP3A4 mechanism; significant interaction.",
            "FDA"),

        // MAOIs — tyramine hypertensive crisis risk
        new Rule("phenelzine",
            Set.of("aged cheese", "cured meat", "salami", "soy sauce", "fermented", "tap beer"),
            Severity.BLOCK,
            "Tyramine-rich foods with MAOIs can trigger a hypertensive crisis.",
            "FDA prescribing information"),
        new Rule("tranylcypromine",
            Set.of("aged cheese", "cured meat", "salami", "soy sauce", "fermented", "tap beer"),
            Severity.BLOCK,
            "Tyramine-rich foods with MAOIs can trigger a hypertensive crisis.",
            "FDA prescribing information"),

        // Levothyroxine — absorption interference
        new Rule("levothyroxine",
            Set.of("calcium supplement", "iron supplement", "fortified soy"),
            Severity.WARN,
            "Calcium and iron impair levothyroxine absorption. Separate doses by at least 4 hours.",
            "American Thyroid Association"),

        // Lithium — sodium balance
        new Rule("lithium",
            Set.of("low-sodium", "salt substitute"),
            Severity.WARN,
            "Sudden sodium changes affect lithium clearance. Keep sodium intake consistent.",
            "Lithium prescribing information"),

        // Metformin (Type 2 diabetes) — alcohol increases lactic acidosis risk
        new Rule("metformin",
            Set.of("wine", "beer", "vodka", "whiskey", "rum", "alcohol"),
            Severity.WARN,
            "Alcohol increases risk of lactic acidosis with metformin. Avoid heavy drinking.",
            "FDA prescribing information"),

        // SSRIs — tryptophan-rich + serotonin syndrome (very mild rule, advisory)
        new Rule("sertraline",
            Set.of("st. john's wort", "tryptophan supplement"),
            Severity.WARN,
            "Combined serotonergic agents can increase serotonin syndrome risk.",
            "FDA prescribing information")
    );

    /**
     * Check a list of ingredient names against the user's medications.
     *
     * @param userMedications canonical lowercase generic names
     * @param ingredients     ingredient strings from the recipe (any case)
     * @return findings — empty list if no interactions detected
     */
    public List<Finding> check(List<String> userMedications, List<String> ingredients) {
        if (userMedications == null || userMedications.isEmpty()) return List.of();
        if (ingredients == null || ingredients.isEmpty()) return List.of();

        Set<String> activeMeds = userMedications.stream()
            .filter(Objects::nonNull)
            .map(s -> s.toLowerCase().trim())
            .collect(Collectors.toSet());

        List<Finding> findings = new ArrayList<>();
        for (String ingredient : ingredients) {
            if (ingredient == null) continue;
            String lower = ingredient.toLowerCase();
            for (Rule rule : RULES) {
                if (!activeMeds.contains(rule.drugGeneric())) continue;
                for (String kw : rule.forbiddenKeywords()) {
                    if (lower.contains(kw)) {
                        findings.add(new Finding(
                            rule.drugGeneric(), ingredient,
                            rule.severity(), rule.reason(), rule.source()));
                        break;  // one finding per ingredient-rule pair is enough
                    }
                }
            }
        }
        return findings;
    }

    public boolean hasBlocker(List<Finding> findings) {
        return findings.stream().anyMatch(f -> f.severity() == Severity.BLOCK);
    }
}
