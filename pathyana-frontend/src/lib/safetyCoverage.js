import { useEffect, useState } from 'react';
import api from './api';

/**
 * Loads the safety coverage data from /api/safety/coverage and caches it for
 * the lifetime of the session.
 *
 * This is what powers the "✓ checked" vs "⚠ not in safety database" badges
 * throughout the UI. We fetch it once on first use and reuse across components.
 *
 * Module-level cache (not React state) so multiple components requesting it
 * concurrently don't fire 4 simultaneous requests.
 */

let cache = null;
let inflight = null;

async function fetchCoverage() {
  if (cache) return cache;
  if (inflight) return inflight;
  inflight = api.get('/api/safety/coverage')
    .then((r) => {
      cache = {
        // The backend sends Sets; axios deserializes them as arrays
        drugs: new Set((r.data.drugs || []).map((s) => s.toLowerCase())),
        conditions: new Set((r.data.conditions || []).map((s) => s.toLowerCase())),
        drugCount: r.data.drugCount || 0,
        conditionLabels: r.data.conditions || [],
      };
      inflight = null;
      return cache;
    })
    .catch((err) => {
      inflight = null;
      // Network failure shouldn't crash the UI. Treat as "nothing covered".
      console.error('Failed to load safety coverage:', err);
      cache = { drugs: new Set(), conditions: new Set(), drugCount: 0, conditionLabels: [] };
      return cache;
    });
  return inflight;
}

/**
 * React hook for components that need coverage info.
 * Returns null while loading, then the coverage object.
 */
export function useSafetyCoverage() {
  const [coverage, setCoverage] = useState(cache);

  useEffect(() => {
    if (!cache) {
      fetchCoverage().then(setCoverage);
    }
  }, []);

  return coverage;
}

/**
 * Imperative helpers — useful inside event handlers where hooks don't fit.
 */
export function isDrugCovered(coverage, genericName) {
  if (!coverage || !genericName) return false;
  return coverage.drugs.has(genericName.toLowerCase().trim());
}

export function isConditionCovered(coverage, condition) {
  if (!coverage || !condition) return false;
  const lower = condition.toLowerCase();
  // A condition counts as covered if any covered label substring-matches it.
  // (e.g., user types "Type 2 Diabetes Mellitus" — we match against "type 2 diabetes" or "diabetic")
  for (const supported of coverage.conditions) {
    if (lower.includes(supported.toLowerCase()) || supported.toLowerCase().includes(lower)) {
      return true;
    }
  }
  // Also try keyword-based heuristic — the backend rules match keywords, not exact labels
  const keywords = ['diabetes', 'diabetic', 'hypertension', 'high blood pressure',
    'ckd', 'kidney', 'dialysis', 'pcos', 'polycystic', 'gerd', 'reflux',
    'gout', 'celiac', 'coeliac', 'crohn', 'colitis', 'ibd'];
  return keywords.some((k) => lower.includes(k));
}
