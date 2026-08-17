/**
 * Recipe display. Reads from the orchestrator's response.recipe object.
 *
 * Expected shape (from Chef's JSON):
 *   {
 *     mealName, mealSlot, suggestedTime, description, servings,
 *     prepTimeMinutes, cookTimeMinutes,
 *     ingredients: [{name, quantity}],
 *     instructions: [string],
 *     nutritionEstimate: {calories, protein, carbs, fat, fiber}
 *   }
 *
 * Robust against missing fields — we don't trust the LLM to always include
 * every key, so each section guards before rendering.
 */
export default function RecipeCard({ recipe }) {
  if (!recipe) return null;

  const totalTime = (recipe.prepTimeMinutes || 0) + (recipe.cookTimeMinutes || 0);

  return (
    <article className="card">
      {/* Header */}
      <header className="border-b border-cream-200 pb-5 mb-5">
        {recipe.mealSlot && (
          <span className="inline-block text-xs font-medium tracking-widest uppercase text-saffron-600 mb-2">
            {recipe.mealSlot}
            {recipe.suggestedTime && ` · ${recipe.suggestedTime}`}
          </span>
        )}
        <h2 className="text-3xl font-serif font-semibold text-ink mb-2">
          {recipe.mealName || 'Untitled recipe'}
        </h2>
        {recipe.description && (
          <p className="text-ink-soft">{recipe.description}</p>
        )}

        {/* Stats row */}
        <div className="flex flex-wrap gap-x-6 gap-y-2 mt-4 text-sm">
          {recipe.servings != null && (
            <Stat label="Servings" value={recipe.servings} />
          )}
          {totalTime > 0 && (
            <Stat label="Total time" value={`${totalTime} min`} />
          )}
          {recipe.prepTimeMinutes != null && (
            <Stat label="Prep" value={`${recipe.prepTimeMinutes} min`} />
          )}
          {recipe.cookTimeMinutes != null && (
            <Stat label="Cook" value={`${recipe.cookTimeMinutes} min`} />
          )}
        </div>
      </header>

      {/* Ingredients + Instructions side-by-side on wide screens */}
      <div className="grid md:grid-cols-3 gap-6 mb-6">
        {/* Ingredients */}
        {recipe.ingredients?.length > 0 && (
          <div className="md:col-span-1">
            <h3 className="font-serif font-semibold text-ink mb-3">Ingredients</h3>
            <ul className="space-y-1.5">
              {recipe.ingredients.map((ing, idx) => (
                <li key={idx} className="text-sm text-ink-soft flex gap-2">
                  <span className="text-terracotta-500 select-none mt-1">•</span>
                  <span>
                    <span className="font-medium text-ink">{ing.name}</span>
                    {ing.quantity && <span className="text-ink-muted"> — {ing.quantity}</span>}
                  </span>
                </li>
              ))}
            </ul>
          </div>
        )}

        {/* Instructions */}
        {recipe.instructions?.length > 0 && (
          <div className="md:col-span-2">
            <h3 className="font-serif font-semibold text-ink mb-3">Instructions</h3>
            <ol className="space-y-3">
              {recipe.instructions.map((step, idx) => (
                <li key={idx} className="flex gap-3">
                  <span className="flex-shrink-0 w-6 h-6 rounded-full bg-terracotta-100 text-terracotta-700 text-xs font-semibold flex items-center justify-center">
                    {idx + 1}
                  </span>
                  <span className="text-ink-soft text-sm leading-relaxed pt-0.5">
                    {step}
                  </span>
                </li>
              ))}
            </ol>
          </div>
        )}
      </div>

      {/* Nutrition */}
      {recipe.nutritionEstimate && (
        <div className="bg-forest-50 border border-forest-100 rounded-lg p-4">
          <h3 className="text-sm font-semibold text-forest-700 mb-2">Estimated nutrition (per serving)</h3>
          <div className="grid grid-cols-2 sm:grid-cols-5 gap-3 text-sm">
            <NutritionStat label="Calories" value={recipe.nutritionEstimate.calories} />
            <NutritionStat label="Protein" value={recipe.nutritionEstimate.protein} />
            <NutritionStat label="Carbs" value={recipe.nutritionEstimate.carbs} />
            <NutritionStat label="Fat" value={recipe.nutritionEstimate.fat} />
            <NutritionStat label="Fiber" value={recipe.nutritionEstimate.fiber} />
          </div>
        </div>
      )}
    </article>
  );
}

function Stat({ label, value }) {
  return (
    <div>
      <span className="text-ink-muted">{label}: </span>
      <span className="font-medium text-ink">{value}</span>
    </div>
  );
}

function NutritionStat({ label, value }) {
  if (!value) return null;
  return (
    <div>
      <div className="text-xs text-forest-600 uppercase tracking-wide">{label}</div>
      <div className="font-semibold text-forest-800">{value}</div>
    </div>
  );
}
