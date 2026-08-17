import { useEffect } from 'react';
import RecipeCard from '../meals/RecipeCard';
import SafetyReviewPanel from '../meals/SafetyReviewPanel';
import Button from '../Button';

/**
 * Modal showing full meal detail (recipe + safety review).
 * Opened when the user clicks a meal card in the weekly grid.
 *
 * Why a modal vs a separate route:
 *   - Keeps the weekly grid mounted (no flash of empty state on close).
 *   - Matches how iOS/Android apps usually present "expanded" meal views.
 *   - Cheap to implement with the existing RecipeCard + SafetyReviewPanel.
 *
 * Closes on ESC key and on backdrop click. Body scroll is locked while open.
 */
export default function MealDetailModal({ meal, dayLabel, onClose }) {
  useEffect(() => {
    function handleEsc(e) {
      if (e.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', handleEsc);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', handleEsc);
      document.body.style.overflow = '';
    };
  }, [onClose]);

  if (!meal) return null;

  const recipe = meal.recipe || {};
  const review = meal.review || {};

  return (
    <div
      className="fixed inset-0 z-50 bg-ink/40 backdrop-blur-sm flex items-start justify-center p-4 sm:p-8 overflow-y-auto"
      onClick={onClose}
    >
      <div
        className="bg-cream-50 rounded-xl max-w-5xl w-full my-auto shadow-medium"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-cream-200">
          <div>
            <div className="text-xs uppercase tracking-wider text-saffron-700 font-medium">
              {dayLabel} · {meal.slot}
            </div>
            <h2 className="text-xl font-serif font-semibold text-ink">
              {recipe.mealName || 'Meal'}
            </h2>
          </div>
          <button
            onClick={onClose}
            className="text-ink-muted hover:text-ink text-2xl leading-none px-2"
            aria-label="Close"
          >
            ×
          </button>
        </div>

        {/* Body */}
        <div className="p-6 space-y-6">
          {meal.status === 'FAILED_SAFETY' && (
            <div className="card bg-terracotta-50 border-terracotta-200">
              <h3 className="font-semibold text-terracotta-800 mb-1">
                This meal couldn't be safely generated
              </h3>
              <p className="text-sm text-terracotta-700">
                After {(meal.regenerationCount || 0) + 1} attempts, the safety
                engines kept blocking the recipe. The review below explains why.
              </p>
            </div>
          )}

          {meal.status === 'APPROVED' && <RecipeCard recipe={recipe} />}

          <SafetyReviewPanel review={review} />
        </div>

        {/* Footer */}
        <div className="px-6 py-4 border-t border-cream-200 flex justify-end">
          <Button variant="secondary" onClick={onClose}>Close</Button>
        </div>
      </div>
    </div>
  );
}
