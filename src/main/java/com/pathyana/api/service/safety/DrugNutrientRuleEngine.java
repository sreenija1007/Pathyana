package com.pathyana.api.service.safety;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Deterministic drug-nutrient interaction engine.
 *
 * v2 — expanded to ~35 drugs across major therapeutic classes. Every rule has
 * a citation. Public sources only (FDA labels, NIH ODS, AHA, ADA).
 */
@Service
public class DrugNutrientRuleEngine {

    public enum Severity { BLOCK, WARN }

    public record Rule(
        String drugGeneric,
        Set<String> forbiddenKeywords,
        Severity severity,
        String reason,
        String source
    ) {}

    public record Finding(
        String medication,
        String ingredient,
        Severity severity,
        String reason,
        String source
    ) {}

    private static final List<Rule> RULES = List.of(

        // ============== ANTICOAGULANTS ==============
        new Rule("warfarin",
            Set.of("kale", "spinach", "collard", "swiss chard", "natto",
                   "brussels sprouts", "turnip greens", "mustard greens"),
            Severity.WARN,
            "High vitamin K intake reduces warfarin's anticoagulant effect. Consistency matters more than avoidance — sudden changes destabilize INR.",
            "NIH Office of Dietary Supplements — Vitamin K Fact Sheet"),
        new Rule("warfarin",
            Set.of("cranberry juice", "cranberry"),
            Severity.WARN,
            "May increase warfarin's anticoagulant effect and bleeding risk.",
            "FDA Drug Safety Communication"),
        new Rule("warfarin",
            Set.of("grapefruit"),
            Severity.WARN,
            "Grapefruit may modestly increase warfarin levels. Avoid sudden changes.",
            "FDA"),
        new Rule("apixaban",
            Set.of("grapefruit", "st. john's wort"),
            Severity.WARN,
            "Grapefruit and St. John's wort affect CYP3A4 and P-gp, altering apixaban levels.",
            "Eliquis prescribing information"),
        new Rule("rivaroxaban",
            Set.of("grapefruit", "st. john's wort"),
            Severity.WARN,
            "CYP3A4 and P-gp interactions alter rivaroxaban exposure.",
            "Xarelto prescribing information"),

        // ============== STATINS (CYP3A4) ==============
        new Rule("atorvastatin", Set.of("grapefruit"), Severity.BLOCK,
            "Grapefruit inhibits CYP3A4 metabolism of atorvastatin, raising blood levels and the risk of myopathy and rhabdomyolysis.",
            "FDA — Grapefruit Juice and Some Drugs Don't Mix"),
        new Rule("simvastatin", Set.of("grapefruit"), Severity.BLOCK,
            "Same CYP3A4 mechanism as atorvastatin; simvastatin levels can rise dramatically with grapefruit.",
            "FDA"),
        new Rule("lovastatin", Set.of("grapefruit"), Severity.BLOCK,
            "Significant CYP3A4 interaction with grapefruit.",
            "FDA"),

        // ============== CARDIOVASCULAR ==============
        new Rule("amlodipine", Set.of("grapefruit"), Severity.WARN,
            "Grapefruit can modestly raise amlodipine levels, increasing risk of hypotension and edema.",
            "FDA"),
        new Rule("nifedipine", Set.of("grapefruit"), Severity.BLOCK,
            "Substantial CYP3A4 interaction; grapefruit can raise nifedipine levels significantly.",
            "FDA"),
        new Rule("lisinopril",
            Set.of("banana", "salt substitute", "potassium supplement", "low-sodium salt"),
            Severity.WARN,
            "ACE inhibitors reduce potassium excretion. High-potassium foods or salt substitutes (which often contain potassium chloride) can cause hyperkalemia.",
            "American Heart Association"),
        new Rule("losartan",
            Set.of("salt substitute", "potassium supplement", "low-sodium salt"),
            Severity.WARN,
            "ARBs increase potassium retention. Avoid potassium-based salt substitutes.",
            "AHA"),
        new Rule("spironolactone",
            Set.of("banana", "salt substitute", "potassium supplement", "low-sodium salt",
                   "orange juice", "potato", "tomato sauce"),
            Severity.WARN,
            "Potassium-sparing diuretic — high-potassium foods can cause hyperkalemia.",
            "FDA prescribing information"),
        new Rule("furosemide",
            Set.of("licorice"),
            Severity.WARN,
            "Licorice can deplete potassium further when combined with loop diuretics, risking arrhythmias.",
            "NIH"),
        new Rule("metoprolol",
            Set.of("alcohol", "wine", "beer", "vodka", "whiskey", "rum"),
            Severity.WARN,
            "Alcohol increases hypotensive effect and can worsen dizziness on beta blockers.",
            "FDA prescribing information"),
        new Rule("digoxin",
            Set.of("licorice", "high-fiber"),
            Severity.WARN,
            "Licorice-induced hypokalemia raises digoxin toxicity risk. Very high-fiber meals can also reduce absorption.",
            "FDA"),

        // ============== DIABETES ==============
        new Rule("metformin",
            Set.of("wine", "beer", "vodka", "whiskey", "rum", "alcohol", "cocktail"),
            Severity.WARN,
            "Alcohol increases risk of lactic acidosis with metformin. Avoid heavy or binge drinking.",
            "FDA prescribing information"),
        new Rule("glipizide",
            Set.of("alcohol", "wine", "beer", "vodka", "whiskey", "rum"),
            Severity.WARN,
            "Sulfonylureas with alcohol can cause hypoglycemia or a disulfiram-like reaction.",
            "FDA"),
        new Rule("glyburide",
            Set.of("alcohol", "wine", "beer", "vodka", "whiskey", "rum"),
            Severity.WARN,
            "Sulfonylureas with alcohol can cause hypoglycemia.",
            "FDA"),

        // ============== PSYCHIATRIC ==============
        new Rule("phenelzine",
            Set.of("aged cheese", "cured meat", "salami", "soy sauce", "fermented",
                   "tap beer", "sauerkraut", "kimchi", "miso", "fava beans"),
            Severity.BLOCK,
            "Tyramine-rich foods with MAOIs can trigger a hypertensive crisis.",
            "FDA prescribing information"),
        new Rule("tranylcypromine",
            Set.of("aged cheese", "cured meat", "salami", "soy sauce", "fermented",
                   "tap beer", "sauerkraut", "kimchi", "miso", "fava beans"),
            Severity.BLOCK,
            "Tyramine-rich foods with MAOIs can trigger a hypertensive crisis.",
            "FDA prescribing information"),
        new Rule("selegiline",
            Set.of("aged cheese", "cured meat", "tap beer"),
            Severity.WARN,
            "MAO-B inhibitor; large tyramine doses can still cause hypertensive response, especially at higher doses.",
            "FDA"),
        new Rule("sertraline",
            Set.of("st. john's wort", "tryptophan supplement"),
            Severity.WARN,
            "Combined serotonergic agents can increase serotonin syndrome risk.",
            "FDA prescribing information"),
        new Rule("fluoxetine",
            Set.of("st. john's wort", "tryptophan supplement"),
            Severity.WARN,
            "Combined serotonergic agents can increase serotonin syndrome risk.",
            "FDA"),
        new Rule("escitalopram",
            Set.of("st. john's wort"),
            Severity.WARN,
            "Both SSRIs and St. John's wort raise serotonin; combination risks serotonin syndrome.",
            "FDA"),
        new Rule("bupropion",
            Set.of("alcohol", "wine", "beer", "vodka", "whiskey", "rum"),
            Severity.WARN,
            "Alcohol can lower seizure threshold; bupropion already does. Combination increases seizure risk.",
            "FDA prescribing information"),
        new Rule("lithium",
            Set.of("low-sodium", "salt substitute"),
            Severity.WARN,
            "Sudden sodium changes affect lithium clearance. Keep sodium intake consistent.",
            "Lithium prescribing information"),
        new Rule("lithium", Set.of("caffeine"), Severity.WARN,
            "Caffeine can affect lithium levels; consistency in intake matters.",
            "Lithium prescribing information"),

        // ============== THYROID ==============
        new Rule("levothyroxine",
            Set.of("calcium supplement", "iron supplement", "fortified soy",
                   "soy milk", "high-fiber cereal", "calcium-fortified"),
            Severity.WARN,
            "Calcium, iron, and high-fiber foods impair levothyroxine absorption. Separate by at least 4 hours; take on empty stomach.",
            "American Thyroid Association"),
        new Rule("levothyroxine", Set.of("walnut"), Severity.WARN,
            "Walnuts may bind levothyroxine and reduce absorption. Separate timing.",
            "American Thyroid Association"),

        // ============== ANTIBIOTICS ==============
        new Rule("ciprofloxacin",
            Set.of("milk", "yogurt", "cheese", "calcium supplement",
                   "iron supplement", "antacid"),
            Severity.WARN,
            "Dairy and calcium chelate ciprofloxacin, reducing absorption by up to 50%. Separate by 2+ hours.",
            "FDA prescribing information"),
        new Rule("levofloxacin",
            Set.of("milk", "yogurt", "calcium supplement", "iron supplement"),
            Severity.WARN,
            "Same chelation issue as ciprofloxacin. Separate dairy/calcium by 2+ hours.",
            "FDA"),
        new Rule("tetracycline",
            Set.of("milk", "yogurt", "cheese", "calcium supplement"),
            Severity.WARN,
            "Dairy reduces tetracycline absorption substantially.",
            "FDA"),
        new Rule("doxycycline",
            Set.of("milk", "yogurt", "calcium supplement", "iron supplement"),
            Severity.WARN,
            "Calcium and iron reduce doxycycline absorption. Separate by 2+ hours.",
            "FDA"),
        new Rule("metronidazole",
            Set.of("alcohol", "wine", "beer", "vodka", "whiskey", "rum"),
            Severity.BLOCK,
            "Metronidazole + alcohol can cause a disulfiram-like reaction: flushing, nausea, vomiting, headache.",
            "FDA prescribing information"),

        // ============== PROTON PUMP INHIBITORS ==============
        new Rule("omeprazole",
            Set.of("st. john's wort"),
            Severity.WARN,
            "St. John's wort induces CYP enzymes and can reduce omeprazole efficacy.",
            "FDA"),

        // ============== IMMUNOSUPPRESSANTS ==============
        new Rule("cyclosporine", Set.of("grapefruit"), Severity.BLOCK,
            "Grapefruit dramatically raises cyclosporine levels, risking nephrotoxicity.",
            "FDA"),
        new Rule("tacrolimus", Set.of("grapefruit"), Severity.BLOCK,
            "Strong CYP3A4 interaction; grapefruit can cause toxic tacrolimus levels.",
            "FDA"),

        // ============== STEROIDS ==============
        new Rule("prednisone",
            Set.of("licorice"),
            Severity.WARN,
            "Licorice can prolong and intensify prednisone effects.",
            "NIH"),

        // ============== GOUT ==============
        new Rule("allopurinol",
            Set.of("alcohol", "wine", "beer", "purine-rich"),
            Severity.WARN,
            "Alcohol and high-purine foods can worsen hyperuricemia and undermine allopurinol's effect.",
            "American College of Rheumatology"),
        new Rule("colchicine", Set.of("grapefruit"), Severity.BLOCK,
            "Grapefruit increases colchicine toxicity, which can be life-threatening.",
            "FDA")
    );

    private static final Set<String> COVERED_DRUGS = RULES.stream()
        .map(Rule::drugGeneric)
        .collect(Collectors.toUnmodifiableSet());

    public boolean hasCoverage(String genericName) {
        if (genericName == null) return false;
        return COVERED_DRUGS.contains(genericName.toLowerCase().trim());
    }

    public Set<String> coveredDrugs() {
        return COVERED_DRUGS;
    }

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
}
