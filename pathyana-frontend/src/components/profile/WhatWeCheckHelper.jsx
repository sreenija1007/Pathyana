import { useState } from 'react';
import { useSafetyCoverage } from '../../lib/safetyCoverage';

/**
 * "What Pathyana checks for" helper. A collapsible panel shown on the profile
 * page so users see, in plain language, exactly which conditions and how many
 * medications trigger deterministic safety rules.
 *
 * This is the *honesty as a feature* moment: we don't pretend to check
 * everything, we tell users exactly what we do check.
 */
export default function WhatWeCheckHelper() {
  const [open, setOpen] = useState(false);
  const coverage = useSafetyCoverage();

  return (
    <div className="bg-forest-50 border border-forest-100 rounded-lg overflow-hidden">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="w-full flex items-center justify-between px-4 py-3 text-left hover:bg-forest-100/50 transition"
      >
        <div className="flex items-center gap-2">
          <svg className="w-4 h-4 text-forest-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
              d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <span className="text-sm font-medium text-forest-800">
            What Pathyana checks for
          </span>
        </div>
        <svg
          className={`w-4 h-4 text-forest-600 transition-transform ${open ? 'rotate-180' : ''}`}
          fill="none" stroke="currentColor" viewBox="0 0 24 24"
        >
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
        </svg>
      </button>

      {open && (
        <div className="px-4 pb-4 pt-1 text-sm text-forest-900 space-y-3 border-t border-forest-100">
          <p>
            Pathyana uses two deterministic engines plus an advisory LLM Reviewer
            to keep meals safe. Here's what's covered today:
          </p>

          <div>
            <h4 className="font-semibold text-forest-800 mb-1">
              Drug-food interactions
            </h4>
            <p className="text-forest-700">
              {coverage
                ? `${coverage.drugCount} medications have deterministic rules drawn from FDA labels, NIH ODS, and AHA/ACR guidelines.`
                : 'Loading coverage info…'}
              {' '}
              If a medication you list isn't covered, you'll see an "advisory only" badge — the LLM still reviews it, but there's no deterministic safety gate.
            </p>
          </div>

          <div>
            <h4 className="font-semibold text-forest-800 mb-1">
              Condition-based food rules
            </h4>
            {coverage && coverage.conditionLabels?.length > 0 ? (
              <>
                <p className="text-forest-700 mb-1.5">
                  We have deterministic food-safety rules for these conditions:
                </p>
                <div className="flex flex-wrap gap-1.5">
                  {coverage.conditionLabels.map((c) => (
                    <span
                      key={c}
                      className="inline-block bg-white border border-forest-200 text-forest-700 text-xs px-2 py-0.5 rounded-full"
                    >
                      {c}
                    </span>
                  ))}
                </div>
                <p className="text-forest-700 mt-2">
                  Other conditions on your profile are still passed to the LLM Reviewer for advisory consideration.
                </p>
              </>
            ) : (
              <p className="text-forest-700">Loading condition list…</p>
            )}
          </div>

          <div className="pt-2 border-t border-forest-100 text-xs text-forest-700">
            <strong>Limits:</strong> our rule set is hand-curated from public sources.
            It's not comprehensive. Always verify medication interactions with your
            pharmacist or doctor — especially for medications outside our list.
          </div>
        </div>
      )}
    </div>
  );
}
