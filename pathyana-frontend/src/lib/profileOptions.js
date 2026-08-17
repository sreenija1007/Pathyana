/**
 * Predefined options used in the profile form.
 *
 * Kept in one file so we can later swap to a backend-driven taxonomy without
 * touching component code. For now, lists are curated to cover the most common
 * cases for the target user base (people managing chronic conditions).
 *
 * Each "chip" group has a stable canonical value; the displayLabel is what the
 * user sees. The canonical value is what we send to the backend so the rule
 * engine can match against it deterministically.
 */

export const DIETARY_PREFERENCES = [
  { value: 'non-veg',     label: 'Non-vegetarian' },
  { value: 'vegetarian',  label: 'Vegetarian' },
  { value: 'vegan',       label: 'Vegan' },
  { value: 'pescatarian', label: 'Pescatarian' },
  { value: 'gluten-free', label: 'Gluten-free' },
  { value: 'dairy-free',  label: 'Dairy-free' },
  { value: 'keto',        label: 'Keto' },
];

export const HEALTH_GOALS = [
  { value: 'diabetes management', label: 'Diabetes management' },
  { value: 'blood pressure control', label: 'Blood pressure control' },
  { value: 'weight loss', label: 'Weight loss' },
  { value: 'weight gain', label: 'Weight gain' },
  { value: 'muscle gain', label: 'Muscle gain' },
  { value: 'heart health', label: 'Heart health' },
  { value: 'kidney health', label: 'Kidney health' },
  { value: 'general wellness', label: 'General wellness' },
];

export const CUISINES = [
  'Indian', 'Mediterranean', 'East Asian', 'Mexican', 'Middle Eastern',
  'American', 'Italian', 'Thai', 'Continental', 'Mixed',
];

export const COMMON_CONDITIONS = [
  'Type 2 Diabetes', 'Type 1 Diabetes', 'Hypertension', 'High Cholesterol',
  'Heart Disease', 'CKD Stage 3', 'CKD Stage 4', 'Hypothyroidism',
  'Hyperthyroidism', 'PCOS', 'Celiac Disease', 'IBS', 'GERD',
  'Fatty Liver', 'Gout',
];

export const COMMON_ALLERGIES = [
  'Peanut', 'Tree nut', 'Dairy', 'Egg', 'Soy', 'Wheat', 'Gluten',
  'Shellfish', 'Fish', 'Sesame',
];

/**
 * Common medications mapped to their generic (lowercased) form.
 * The genericName matches what DrugNutrientRuleEngine checks against.
 *
 * If the user takes something not in this list, they can type a custom entry
 * and we save the displayName as-is and the genericName as the lowercased text.
 */
export const COMMON_MEDICATIONS = [
  { displayName: 'Lipitor',      genericName: 'atorvastatin' },
  { displayName: 'Crestor',      genericName: 'rosuvastatin' },
  { displayName: 'Zocor',        genericName: 'simvastatin' },
  { displayName: 'Mevacor',      genericName: 'lovastatin' },
  { displayName: 'Coumadin (Warfarin)', genericName: 'warfarin' },
  { displayName: 'Synthroid',    genericName: 'levothyroxine' },
  { displayName: 'Metformin',    genericName: 'metformin' },
  { displayName: 'Glucophage',   genericName: 'metformin' },
  { displayName: 'Lithium',      genericName: 'lithium' },
  { displayName: 'Zoloft',       genericName: 'sertraline' },
  { displayName: 'Nardil',       genericName: 'phenelzine' },
  { displayName: 'Parnate',      genericName: 'tranylcypromine' },
];

/**
 * Eating window protocols. Each preset auto-fills start + end times.
 */
export const WINDOW_PROTOCOLS = [
  { value: 'NONE',           label: 'No restriction',  start: null,    end: null    },
  { value: 'TWELVE_TWELVE',  label: '12:12 (gentle)',  start: '08:00', end: '20:00' },
  { value: 'FOURTEEN_TEN',   label: '14:10 (moderate)', start: '09:00', end: '19:00' },
  { value: 'SIXTEEN_EIGHT',  label: '16:8 (strict)',   start: '11:00', end: '19:00' },
  { value: 'CUSTOM',         label: 'Custom',          start: null,    end: null    },
];
