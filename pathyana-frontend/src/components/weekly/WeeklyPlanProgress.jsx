import { useEffect, useState, useRef } from 'react';
import { getWeeklyPlanStatus, isPlanDone, PLAN_STATUS } from '../../lib/weeklyPlanApi';

/**
 * Live progress UI while a weekly plan is generating.
 *
 * Polls /api/meals/weekly/{id}/status every 3 seconds. Calls onComplete when the
 * plan reaches COMPLETED / PARTIAL / FAILED so the parent can switch views.
 *
 * Why 3 seconds:
 *   - With 2.5s throttle between meals, meals land roughly every 5-8s.
 *   - 3-second polling means the UI updates "live enough" without hammering the
 *     backend. We're not building a real-time stock ticker.
 *
 * Cleanup:
 *   - useEffect's cleanup clears the interval. Required to avoid leaks if the
 *     user navigates away mid-generation.
 *   - The isMounted ref prevents the final state update from firing after
 *     unmount (React would warn but tolerates it).
 */
export default function WeeklyPlanProgress({ planId, onComplete }) {
  const [status, setStatus] = useState(null);
  const [error, setError] = useState('');
  const isMounted = useRef(true);

  useEffect(() => {
    isMounted.current = true;

    async function tick() {
      try {
        const data = await getWeeklyPlanStatus(planId);
        if (!isMounted.current) return;
        setStatus(data);
        if (isPlanDone(data.status)) {
          onComplete?.(data);
        }
      } catch (err) {
        if (!isMounted.current) return;
        setError(err.message || 'Failed to fetch plan status');
      }
    }

    tick();                              // first poll immediately
    const interval = setInterval(tick, 3000);

    return () => {
      isMounted.current = false;
      clearInterval(interval);
    };
  }, [planId, onComplete]);

  if (error) {
    return (
      <div className="card bg-terracotta-50 border-terracotta-200">
        <h2 className="text-xl font-serif font-semibold text-terracotta-800 mb-1">
          Couldn't load progress
        </h2>
        <p className="text-sm text-terracotta-700">{error}</p>
      </div>
    );
  }

  if (!status) {
    return (
      <div className="card flex items-center justify-center py-16">
        <div className="text-ink-soft">Starting your weekly plan…</div>
      </div>
    );
  }

  const pct = status.progressTotal > 0
    ? Math.round((status.progressCurrent / status.progressTotal) * 100)
    : 0;

  return (
    <div className="card">
      <div className="flex flex-col items-center text-center py-6">
        {/* Pulse */}
        <div className="relative w-16 h-16 mb-6">
          <div className="absolute inset-0 rounded-full bg-terracotta-500 animate-ping opacity-30" />
          <div className="absolute inset-2 rounded-full bg-terracotta-500 animate-pulse" />
        </div>

        <h2 className="text-2xl font-serif font-semibold text-ink mb-2">
          Building your week
        </h2>
        <p className="text-ink-soft mb-6 max-w-md">
          {status.progressMessage || 'Generating meals…'}
        </p>

        {/* Progress bar */}
        <div className="w-full max-w-md">
          <div className="flex justify-between text-xs text-ink-muted mb-1.5">
            <span>{status.progressCurrent} of {status.progressTotal} meals</span>
            <span>{pct}%</span>
          </div>
          <div className="w-full h-2 bg-cream-200 rounded-full overflow-hidden">
            <div
              className="h-full bg-terracotta-500 transition-all duration-500 ease-out"
              style={{ width: `${pct}%` }}
            />
          </div>
        </div>

        <div className="mt-8 text-xs text-ink-muted max-w-md">
          This usually takes 5–6 minutes. Pathyana runs each meal through the Chef,
          two safety engines, and the Reviewer in sequence. You can leave this page
          and come back — generation continues in the background.
        </div>

        {/* Soft status indicator */}
        <div className="mt-6 flex items-center gap-2 text-xs">
          <span className={`inline-block w-2 h-2 rounded-full ${
            status.status === PLAN_STATUS.GENERATING ? 'bg-terracotta-500 animate-pulse' : 'bg-cream-300'
          }`} />
          <span className="text-ink-muted uppercase tracking-wider">{status.status}</span>
        </div>
      </div>
    </div>
  );
}
