import { useEffect, useState } from 'react';

/**
 * Animated loader while the orchestrator runs.
 *
 * The pipeline takes 5-10 seconds (3 sequential Groq calls + possible regeneration).
 * Rather than show a blank spinner, we cycle through stage labels to communicate
 * what's happening behind the scenes. This is purely cosmetic — there's no real
 * server-sent progress events yet — but it makes the wait feel intentional.
 *
 * The stages roughly mirror the orchestrator's actual phases:
 *   1. Chef
 *   2. Safety check
 *   3. Reviewer
 *   4. Grocery
 *
 * Each stage holds for ~2 seconds. If the request finishes first, the loader
 * is unmounted and the user never sees later stages.
 */
const STAGES = [
  { label: 'Cooking up ideas',     hint: 'The Chef is drafting a recipe…' },
  { label: 'Checking safety',      hint: 'Cross-referencing your medications…' },
  { label: 'Clinical review',      hint: 'The Reviewer is checking against your conditions…' },
  { label: 'Building grocery list', hint: 'Translating ingredients into a shopping list…' },
];

export default function MealLoadingState() {
  const [stage, setStage] = useState(0);

  useEffect(() => {
    const id = setInterval(() => {
      setStage((s) => Math.min(s + 1, STAGES.length - 1));
    }, 2000);
    return () => clearInterval(id);
  }, []);

  const current = STAGES[stage];

  return (
    <div className="card flex flex-col items-center justify-center py-16 text-center">
      {/* Pulsing dot ring */}
      <div className="relative w-16 h-16 mb-6">
        <div className="absolute inset-0 rounded-full bg-terracotta-500 animate-ping opacity-30" />
        <div className="absolute inset-2 rounded-full bg-terracotta-500 animate-pulse" />
      </div>

      <h2 className="text-2xl font-serif font-semibold text-ink mb-2">
        {current.label}…
      </h2>
      <p className="text-ink-soft mb-8">{current.hint}</p>

      {/* Stage indicators */}
      <div className="flex gap-2">
        {STAGES.map((_, idx) => (
          <div
            key={idx}
            className={`h-1.5 w-12 rounded-full transition ${
              idx <= stage ? 'bg-terracotta-500' : 'bg-cream-200'
            }`}
          />
        ))}
      </div>

      <p className="text-xs text-ink-muted mt-8 max-w-sm">
        This usually takes 5–10 seconds. Three AI calls plus a deterministic safety scan
        are running in sequence.
      </p>
    </div>
  );
}
