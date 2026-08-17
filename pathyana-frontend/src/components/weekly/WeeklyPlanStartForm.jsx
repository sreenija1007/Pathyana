import { useState } from 'react';
import Button from '../Button';

/**
 * Initial form shown when there's no active plan. User picks a start date and
 * optionally adds a free-text request, then clicks "Plan my week".
 */
export default function WeeklyPlanStartForm({ onStart, disabled }) {
  const [startDate, setStartDate] = useState(() => {
    // Default to today
    return new Date().toISOString().slice(0, 10);
  });
  const [userRequest, setUserRequest] = useState('');

  function submit(e) {
    e.preventDefault();
    if (disabled) return;
    onStart({ startDate, userRequest: userRequest.trim() || undefined });
  }

  return (
    <div className="card">
      <h2 className="text-2xl font-serif font-semibold text-ink mb-2">Plan your week</h2>
      <p className="text-ink-soft mb-6">
        Pathyana will generate 21 meals (brunch, dinner, snack) over 7 days, plus a
        consolidated grocery list. Each meal is checked against your medications
        and conditions.
      </p>

      <form onSubmit={submit} className="space-y-4">
        <div>
          <label className="label" htmlFor="startDate">Start date</label>
          <input
            id="startDate"
            type="date"
            className="input py-2"
            value={startDate}
            onChange={(e) => setStartDate(e.target.value)}
            required
          />
        </div>

        <div>
          <label className="label" htmlFor="userRequest">
            Any preferences for this week? <span className="text-ink-muted">(optional)</span>
          </label>
          <textarea
            id="userRequest"
            className="input min-h-[80px] resize-none"
            placeholder="e.g. variety of cuisines, lots of vegetables, lighter dinners"
            value={userRequest}
            onChange={(e) => setUserRequest(e.target.value)}
          />
        </div>

        <div className="bg-saffron-50 border border-saffron-200 rounded-lg p-3 text-sm text-saffron-900">
          <strong>Heads up:</strong> generation takes about 5–6 minutes. The page
          shows live progress and you can leave it and come back — the work
          continues in the background.
        </div>

        <div className="flex justify-end">
          <Button type="submit" size="lg" disabled={disabled}>
            {disabled ? 'Starting…' : 'Plan my week'}
          </Button>
        </div>
      </form>
    </div>
  );
}
