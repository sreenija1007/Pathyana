package com.pathyana.api.controller;

import com.pathyana.api.service.safety.ConditionFoodRuleEngine;
import com.pathyana.api.service.safety.DrugNutrientRuleEngine;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Transparency endpoints. Surfaces what the deterministic engines do and don't cover.
 *
 *   GET /api/safety/coverage   — overall summary
 *   GET /api/safety/check-drug?name=... — quick lookup for a single drug
 *
 * Used by the frontend to render "checked" / "not in safety database" indicators
 * next to user medications and conditions. Honesty is a feature: we'd rather a user
 * know our rule book is finite than believe Pathyana checks everything.
 */
@RestController
@RequestMapping("/api/safety")
public class SafetyCoverageController {

    private final DrugNutrientRuleEngine drugEngine;
    private final ConditionFoodRuleEngine conditionEngine;

    public SafetyCoverageController(DrugNutrientRuleEngine drugEngine,
                                    ConditionFoodRuleEngine conditionEngine) {
        this.drugEngine = drugEngine;
        this.conditionEngine = conditionEngine;
    }

    @GetMapping("/coverage")
    public Map<String, Object> coverage() {
        return Map.of(
            "drugCount", drugEngine.coveredDrugs().size(),
            "drugs", drugEngine.coveredDrugs(),
            "conditions", conditionEngine.supportedConditionLabels()
        );
    }

    @GetMapping("/check-drug")
    public Map<String, Object> checkDrug(@RequestParam String name) {
        boolean covered = drugEngine.hasCoverage(name);
        return Map.of(
            "name", name,
            "covered", covered,
            "note", covered
                ? "Pathyana has deterministic rules for this medication."
                : "Pathyana does not have specific food-interaction rules for this medication. The Clinical Reviewer (LLM) will still flag obvious issues, but treat its output as advisory."
        );
    }
}
