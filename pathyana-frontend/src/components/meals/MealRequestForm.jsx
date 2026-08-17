import { useState } from 'react';
import Button from '../Button';

/**
 * The main input — user types what they want; we send to /api/meals/generate.
 *
 * Includes quick "suggested prompt" pills so first-time users have a starting point.
 * After submission the parent shows the loading state, then the result.
 */
const SUGGESTIONS = [
  'Light dinner under 400 calories',
  'Quick breakfast under 15 minutes',
  'High-protein lunch',
  'Comforting evening meal',
  'Something with lentils',
];

export default function MealRequestForm({ onSubmit, disabled }) {
  const [request, setRequest] = useState('');

  function submit(e) {
    e.preventDefault();
    if (!request.trim() || disabled) return;
    onSubmit(request.trim());
  }

  function useSuggestion(s) {
    setRequest(s);
  }

  return (
    <div className="card">
      <form onSubmit={submit} className="space-y-3">
        <label className="label" htmlFor="mealRequest">
          What are you in the mood for?
        </label>
        <textarea
          id="mealRequest"
          className="input min-h-[88px] resize-none"
          placeholder="A light dinner with chicken and vegetables, ready in 30 minutes…"
          value={request}
          onChange={(e) => setRequest(e.target.value)}
          disabled={disabled}
        />

        <div className="flex flex-wrap gap-2">
          {SUGGESTIONS.map((s) => (
            <button
              key={s}
              type="button"
              onClick={() => useSuggestion(s)}
              disabled={disabled}
              className="text-xs text-ink-soft border border-cream-300 hover:border-terracotta-400 hover:text-terracotta-600 rounded-full px-3 py-1 transition disabled:opacity-50"
            >
              {s}
            </button>
          ))}
        </div>

        <div className="flex justify-end pt-1">
          <Button
            type="submit"
            disabled={disabled || !request.trim()}
            size="md"
          >
            Generate meal
          </Button>
        </div>
      </form>
    </div>
  );
}
