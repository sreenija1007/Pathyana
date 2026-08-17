import MealCard from './MealCard';

/**
 * The 7-day x 3-meal grid view.
 *
 * Layout choices:
 *   - On mobile: stack day by day, each day is a horizontal row of meal cards.
 *   - On larger screens: still day-by-day rows (more readable than a 7x3 grid).
 *   - Today's date is highlighted with a terracotta accent.
 */
const DAY_LABELS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

export default function WeeklyDayGrid({ days, onMealClick }) {
  if (!days || days.length === 0) {
    return <div className="text-ink-muted text-sm">No meals yet.</div>;
  }

  const todayIso = new Date().toISOString().slice(0, 10);

  return (
    <div className="space-y-3">
      {days.map((day) => {
        const isToday = day.date === todayIso;
        const dateObj = day.date ? new Date(day.date + 'T00:00') : null;
        const weekdayLabel = dateObj
          ? DAY_LABELS[(dateObj.getDay() + 6) % 7]  // Monday = 0
          : `D${day.day}`;
        const datePretty = dateObj
          ? dateObj.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
          : '';

        return (
          <div
            key={day.day}
            className={`card !p-4 ${isToday ? 'ring-2 ring-terracotta-400' : ''}`}
          >
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-3">
                <div className="font-serif text-lg font-semibold text-ink">
                  Day {day.day}
                  {isToday && (
                    <span className="ml-2 text-xs uppercase tracking-wider text-terracotta-600 font-sans font-medium">
                      Today
                    </span>
                  )}
                </div>
                <div className="text-sm text-ink-muted">
                  {weekdayLabel}, {datePretty}
                </div>
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              {day.meals && day.meals.length > 0 ? (
                day.meals.map((meal, idx) => (
                  <MealCard
                    key={idx}
                    meal={meal}
                    onClick={() => onMealClick?.(meal, day)}
                  />
                ))
              ) : (
                <div className="col-span-3 text-sm text-ink-muted text-center py-4">
                  No meals for this day yet.
                </div>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}
