import { useState } from 'react';
import { COMMON_MEDICATIONS } from '../../lib/profileOptions';
import { useSafetyCoverage, isDrugCovered } from '../../lib/safetyCoverage';
import CoverageBadge from '../CoverageBadge';

/**
 * Medication input with coverage transparency.
 *
 * v2: each selected medication shows a CoverageBadge indicating whether it has
 * deterministic safety rules. "Advisory only" badges are intentionally visible
 * so users know when they're outside the rule engine's coverage.
 */
export default function MedicationPicker({ value = [], onChange }) {
  const [draft, setDraft] = useState('');
  const coverage = useSafetyCoverage();

  function addPreset(med) {
    if (value.some((m) => m.genericName === med.genericName)) return;
    onChange([...value, { ...med, dosage: '' }]);
  }

  function addCustom() {
    const trimmed = draft.trim();
    if (!trimmed) return;
    const lower = trimmed.toLowerCase();
    if (value.some((m) => m.genericName === lower)) {
      setDraft('');
      return;
    }
    // For custom entries we don't know the generic name yet — backend will resolve via RxNorm
    onChange([...value, { displayName: trimmed, genericName: '', dosage: '' }]);
    setDraft('');
  }

  function remove(idx) {
    onChange(value.filter((_, i) => i !== idx));
  }

  function updateDosage(idx, dosage) {
    const next = value.slice();
    next[idx] = { ...next[idx], dosage };
    onChange(next);
  }

  function handleKeyDown(e) {
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault();
      addCustom();
    }
  }

  function coverageStatus(med) {
    if (!coverage) return 'loading';
    // If we don't have a genericName yet (custom entry, not yet resolved), be advisory.
    if (!med.genericName) return 'advisory';
    return isDrugCovered(coverage, med.genericName) ? 'checked' : 'advisory';
  }

  return (
    <div className="space-y-4">
      {/* Common medication chips */}
      <div className="flex flex-wrap gap-2">
        {COMMON_MEDICATIONS.map((med) => {
          const selected = value.some((m) => m.genericName === med.genericName);
          const isCovered = coverage ? isDrugCovered(coverage, med.genericName) : false;
          return (
            <button
              key={med.displayName}
              type="button"
              onClick={() => addPreset(med)}
              disabled={selected}
              className={`px-3 py-1.5 text-sm rounded-full border transition flex items-center gap-1.5 ${
                selected
                  ? 'bg-terracotta-100 text-terracotta-700 border-terracotta-200 cursor-default'
                  : 'bg-white border-cream-300 hover:border-terracotta-400 hover:text-terracotta-600'
              }`}
              title={isCovered ? 'Deterministic rules available' : 'Advisory only'}
            >
              {med.displayName}
              {coverage && isCovered && <ShieldDot />}
            </button>
          );
        })}
      </div>

      {/* Custom medication input */}
      <div className="flex gap-2">
        <input
          type="text"
          className="input flex-1 py-2 text-sm"
          placeholder="Type a medication name and press Enter"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={handleKeyDown}
        />
        <button
          type="button"
          onClick={addCustom}
          disabled={!draft.trim()}
          className="px-4 py-2 text-sm font-medium text-terracotta-600 border border-terracotta-300 rounded-lg hover:bg-terracotta-50 disabled:opacity-50 disabled:cursor-not-allowed transition"
        >
          Add
        </button>
      </div>

      {/* Selected medications list with badges + dosage */}
      {value.length > 0 && (
        <div className="space-y-2 pt-2">
          {value.map((med, idx) => {
            const status = coverageStatus(med);
            return (
              <div key={idx} className="flex items-center gap-2 bg-cream-100 rounded-lg p-3">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="font-medium text-ink text-sm truncate">{med.displayName}</span>
                    <CoverageBadge status={status} />
                  </div>
                  <div className="text-xs text-ink-muted mt-0.5">
                    {med.genericName ? `generic: ${med.genericName}` : 'generic name will be looked up on save'}
                  </div>
                </div>
                <input
                  type="text"
                  className="input py-1.5 px-2 text-sm w-32 sm:w-40"
                  placeholder="Dosage (optional)"
                  value={med.dosage || ''}
                  onChange={(e) => updateDosage(idx, e.target.value)}
                />
                <button
                  type="button"
                  onClick={() => remove(idx)}
                  className="text-ink-muted hover:text-terracotta-600 text-lg leading-none px-2"
                  aria-label="Remove medication"
                >
                  ×
                </button>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

function ShieldDot() {
  return (
    <svg className="w-3 h-3 text-forest-600" fill="currentColor" viewBox="0 0 20 20">
      <path
        fillRule="evenodd"
        d="M10 1.944A11.954 11.954 0 012.166 5C2.056 5.649 2 6.319 2 7c0 5.225 3.34 9.67 8 11.317C14.66 16.67 18 12.225 18 7c0-.682-.057-1.35-.166-2.001A11.954 11.954 0 0110 1.944zm3.707 6.05a1 1 0 00-1.414-1.414L9 9.586 7.707 8.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z"
        clipRule="evenodd"
      />
    </svg>
  );
}
