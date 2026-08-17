/**
 * Grocery list display. Items are grouped by store section for in-store shopping.
 * Swaps performed by IngredientSwapper are highlighted in a separate callout box
 * so the user knows what was changed and why.
 */

const SECTION_ORDER = ['produce', 'protein', 'dairy', 'pantry', 'frozen', 'other'];
const SECTION_LABELS = {
  produce: 'Produce',
  protein: 'Protein',
  dairy:   'Dairy',
  pantry:  'Pantry',
  frozen:  'Frozen',
  other:   'Other',
};

export default function GroceryList({ grocery }) {
  if (!grocery) return null;
  const items = grocery.items || [];
  const swaps = grocery.appliedSwaps || [];
  const total = grocery.estimatedTotalUsd;

  // Group items by section
  const grouped = items.reduce((acc, item) => {
    const sec = (item.section || 'other').toLowerCase();
    (acc[sec] = acc[sec] || []).push(item);
    return acc;
  }, {});

  return (
    <section className="card">
      <header className="flex items-start justify-between mb-5">
        <div>
          <h2 className="text-xl font-serif font-semibold text-ink">Grocery list</h2>
          <p className="text-sm text-ink-soft mt-1">
            Organized by store section. {items.length} item{items.length === 1 ? '' : 's'}.
          </p>
        </div>
        {total != null && total > 0 && (
          <div className="text-right">
            <div className="text-xs text-ink-muted uppercase tracking-wide">Est. total</div>
            <div className="font-serif font-semibold text-xl text-terracotta-600">
              ${total.toFixed(2)}
            </div>
          </div>
        )}
      </header>

      {/* Applied swaps callout */}
      {swaps.length > 0 && (
        <div className="bg-saffron-50 border border-saffron-200 rounded-lg p-4 mb-5">
          <div className="flex items-center gap-2 mb-2">
            <SwapIcon />
            <h3 className="font-semibold text-saffron-800 text-sm">
              {swaps.length} smart swap{swaps.length === 1 ? '' : 's'} applied
            </h3>
          </div>
          <p className="text-xs text-saffron-700 mb-3">
            Pathyana automatically substituted these based on your profile.
          </p>
          <div className="space-y-2">
            {swaps.map((swap, idx) => (
              <div key={idx} className="text-sm bg-white/60 rounded-md p-2.5 border border-saffron-200/50">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="line-through text-ink-muted">{swap.original}</span>
                  <span className="text-saffron-600">→</span>
                  <span className="font-semibold text-ink">{swap.replacement}</span>
                </div>
                {swap.reason && (
                  <p className="text-xs text-ink-soft mt-1">{swap.reason}</p>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Sections */}
      <div className="space-y-5">
        {SECTION_ORDER.map((sec) => {
          const list = grouped[sec];
          if (!list?.length) return null;
          return (
            <div key={sec}>
              <h3 className="text-xs font-semibold uppercase tracking-wider text-ink-muted mb-2">
                {SECTION_LABELS[sec]}
              </h3>
              <ul className="space-y-1">
                {list.map((item, idx) => (
                  <li
                    key={idx}
                    className="flex items-center justify-between py-2 px-3 rounded-md hover:bg-cream-50 transition"
                  >
                    <span className="text-ink">{item.name}</span>
                    {item.quantity && (
                      <span className="text-sm text-ink-muted">{item.quantity}</span>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          );
        })}
      </div>
    </section>
  );
}

function SwapIcon() {
  return (
    <svg className="w-4 h-4 text-saffron-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
    </svg>
  );
}
