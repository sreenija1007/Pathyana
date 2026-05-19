package com.pathyana.api.service.safety;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Deterministic condition-based food rules.
 *
 * Parallel to DrugNutrientRuleEngine but keyed on the user's health conditions.
 * Like the drug engine, this is AUTHORITATIVE. The LLM Reviewer is advisory.
 *
 * Coverage in v1:
 *   - Diabetes (T1 and T2)
 *   - Hypertension
 *   - CKD (stage 3+)
 *   - PCOS
 *   - GERD / acid reflux
 *   - Gout
 *   - Celiac disease
 *   - IBD (Crohn's, ulcerative colitis)
 */
@Service
public class ConditionFoodRuleEngine {

    public enum Severity { BLOCK, WARN }

    public record ConditionRule(
        Set<String> conditionKeywords,
        Set<String> forbiddenKeywords,
        Severity severity,
        String reason,
        String source
    ) {}

    public record Finding(
        String condition,
        String ingredient,
        Severity severity,
        String reason,
        String source
    ) {}

    private static final List<ConditionRule> RULES = List.of(

        // ============== DIABETES ==============
        new ConditionRule(
            Set.of("diabetes", "diabetic", "t1d", "t2d"),
            Set.of("white bread", "sugary drink", "soda", "sweetened juice",
                   "candy", "honey syrup", "maple syrup", "agave"),
            Severity.WARN,
            "High glycemic load can spike blood glucose. Prefer whole grains, low-sugar alternatives, or smaller portions.",
            "American Diabetes Association — Standards of Care"),

        // ============== HYPERTENSION ==============
        new ConditionRule(
            Set.of("hypertension", "high blood pressure", "stage 2 hypertension"),
            Set.of("table salt added", "soy sauce", "instant noodle", "canned soup",
                   "deli meat", "processed cheese", "bouillon"),
            Severity.WARN,
            "Sodium-dense ingredients drive up blood pressure. Aim for <1500-2300mg sodium/day depending on stage.",
            "American Heart Association DASH guidelines"),

        // ============== CKD STAGE 3+ ==============
        new ConditionRule(
            Set.of("ckd stage 3", "ckd stage 4", "ckd stage 5", "chronic kidney disease",
                   "kidney disease", "renal failure", "dialysis"),
            Set.of("banana", "orange juice", "potato", "tomato sauce", "tomato paste",
                   "avocado", "spinach", "dried fruit", "raisin", "prune",
                   "salt substitute", "low-sodium salt", "coconut water"),
            Severity.WARN,
            "High potassium content. CKD impairs potassium excretion — hyperkalemia is a real risk. Discuss limits with your nephrologist.",
            "National Kidney Foundation KDOQI guidelines"),
        new ConditionRule(
            Set.of("ckd stage 3", "ckd stage 4", "ckd stage 5", "chronic kidney disease",
                   "kidney disease", "dialysis"),
            Set.of("processed cheese", "cola", "dark soda", "phosphate additive",
                   "bran cereal", "organ meat"),
            Severity.WARN,
            "High phosphorus content. CKD elevates serum phosphate, contributing to bone disease and vascular calcification.",
            "National Kidney Foundation"),

        // ============== PCOS ==============
        new ConditionRule(
            Set.of("pcos", "polycystic ovary"),
            Set.of("white bread", "sugary drink", "soda", "candy", "white rice",
                   "white pasta", "pastry", "donut"),
            Severity.WARN,
            "PCOS is associated with insulin resistance. High-glycemic foods worsen insulin and androgen dynamics; prefer whole grains and lower-GI choices.",
            "Endocrine Society PCOS guidelines"),

        // ============== GERD ==============
        new ConditionRule(
            Set.of("gerd", "acid reflux", "reflux", "heartburn"),
            Set.of("tomato sauce", "tomato paste", "citrus", "orange juice",
                   "grapefruit juice", "chocolate", "peppermint", "fried",
                   "spicy", "carbonated", "coffee"),
            Severity.WARN,
            "Common reflux triggers. Avoiding within 3 hours of bedtime is especially important.",
            "American College of Gastroenterology"),

        // ============== GOUT ==============
        new ConditionRule(
            Set.of("gout", "hyperuricemia"),
            Set.of("organ meat", "liver", "kidney", "sardine", "anchovy",
                   "mussel", "scallop", "beer", "alcohol", "high-fructose corn syrup"),
            Severity.WARN,
            "High-purine foods and alcohol drive uric acid production, precipitating gout flares.",
            "American College of Rheumatology"),

        // ============== CELIAC ==============
        new ConditionRule(
            Set.of("celiac", "coeliac"),
            Set.of("wheat", "barley", "rye", "regular pasta", "regular bread",
                   "couscous", "bulgur", "semolina", "soy sauce", "seitan"),
            Severity.BLOCK,
            "Celiac disease requires complete gluten avoidance — even trace amounts damage the small intestine.",
            "Celiac Disease Foundation"),

        // ============== IBD ==============
        new ConditionRule(
            Set.of("crohn", "ulcerative colitis", "ibd", "inflammatory bowel"),
            Set.of("popcorn", "raw kale", "raw cabbage", "sesame seed",
                   "spicy", "alcohol", "carbonated", "high-fiber raw"),
            Severity.WARN,
            "Common IBD trigger foods. Tolerance varies between flare and remission; ask your gastroenterologist what's right for you.",
            "Crohn's & Colitis Foundation")
    );

    private static final Set<String> SUPPORTED_CONDITION_LABELS = Set.of(
        "Type 1 Diabetes", "Type 2 Diabetes", "Hypertension",
        "CKD Stage 3", "CKD Stage 4", "CKD Stage 5", "Dialysis",
        "PCOS", "GERD / Acid Reflux", "Gout",
        "Celiac Disease", "Crohn's Disease", "Ulcerative Colitis"
    );

    public Set<String> supportedConditionLabels() {
        return SUPPORTED_CONDITION_LABELS;
    }

    public boolean hasCoverage(String condition) {
        if (condition == null) return false;
        String lower = condition.toLowerCase();
        for (ConditionRule rule : RULES) {
            for (String kw : rule.conditionKeywords()) {
                if (lower.contains(kw)) return true;
            }
        }
        return false;
    }

    public List<Finding> check(List<String> userConditions, List<String> ingredients) {
        if (userConditions == null || userConditions.isEmpty()) return List.of();
        if (ingredients == null || ingredients.isEmpty()) return List.of();

        Set<String> lowerConditions = userConditions.stream()
            .filter(Objects::nonNull)
            .map(s -> s.toLowerCase().trim())
            .collect(Collectors.toSet());

        List<Finding> findings = new ArrayList<>();
        for (String ingredient : ingredients) {
            if (ingredient == null) continue;
            String lowerIng = ingredient.toLowerCase();
            for (ConditionRule rule : RULES) {
                String matchedCondition = matchedCondition(rule.conditionKeywords(), lowerConditions);
                if (matchedCondition == null) continue;
                for (String kw : rule.forbiddenKeywords()) {
                    if (lowerIng.contains(kw)) {
                        findings.add(new Finding(
                            matchedCondition, ingredient,
                            rule.severity(), rule.reason(), rule.source()));
                        break;
                    }
                }
            }
        }
        return findings;
    }

    public boolean hasBlocker(List<Finding> findings) {
        return findings.stream().anyMatch(f -> f.severity() == Severity.BLOCK);
    }

    private String matchedCondition(Set<String> conditionKeywords, Set<String> userConditions) {
        for (String userCond : userConditions) {
            for (String kw : conditionKeywords) {
                if (userCond.contains(kw)) return userCond;
            }
        }
        return null;
    }
}
