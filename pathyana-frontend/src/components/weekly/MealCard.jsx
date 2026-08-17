/**
 * Compact meal card shown in the day grid.
 * Click to expand → full RecipeCard + SafetyReviewPanel.
 *
 * Status states it handles:
 *   - APPROVED → show meal name + slot + brief preview
 *   - FAILED_SAFETY → red-tinted "safety blocked" card so the user understands the gap
 *   - missing → empty placeholder
 */
export default function MealCard({ meal, onClick }) {
  if (!meal || meal.status === undefined) {
    return (
      <div className="rounded-lg border border-dashed border-cream-300 bg-cream-50 p-3 text-center">
        <div className="text-xs uppercase tracking-wider text-ink-muted">empty</div>
      </div>
    );
  }

  const slot = meal.slot || 'meal';
  const recipe = meal.recipe || {};
  const isApproved = meal.status === 'APPROVED';
  const name = recipe.mealName || (isApproved ? 'Untitled' : 'Couldn\'t generate');

  if (!isApproved) {
    return (
      <div className="rounded-lg border border-terracotta-200 bg-terracotta-50 p-3">
        <div className="text-xs uppercase tracking-wider text-terracotta-700 mb-1">
          {slot}
        </div>
        <div className="text-sm font-medium text-terracotta-900">
          Safety-blocked
        </div>
        <div className="text-xs text-terracotta-700 mt-1">
          Couldn't generate a safe option. Tap to see why.
        </div>
        {onClick && (
          <button onClick={onClick} className="text-xs text-terracotta-700 underline mt-2">
            View
          </button>
        )}
      </div>
    );
  }

  return (
    <button
      type="button"
      onClick={onClick}
      className="w-full text-left rounded-lg border border-cream-200 bg-white p-3 hover:border-terracotta-400 hover:shadow-soft transition"
    >
      <div className="flex items-center justify-between mb-1">
        <span className="text-xs uppercase tracking-wider text-saffron-700 font-medium">
          {slot}
        </span>
        {recipe.suggestedTime && (
          <span className="text-xs text-ink-muted">{recipe.suggestedTime}</span>
        )}
      </div>
      <div className="font-medium text-ink text-sm leading-tight mb-1">
        {name}
      </div>
      {recipe.nutritionEstimate?.calories && (
        <div className="text-xs text-ink-muted">
          {recipe.nutritionEstimate.calories}
        </div>
      )}
    </button>
  );
}
