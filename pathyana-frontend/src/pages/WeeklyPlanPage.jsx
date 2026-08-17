import { useEffect, useState } from 'react';
import AppLayout from '../components/AppLayout';
import Button from '../components/Button';
import GroceryList from '../components/meals/GroceryList';
import WeeklyPlanStartForm from '../components/weekly/WeeklyPlanStartForm';
import WeeklyPlanProgress from '../components/weekly/WeeklyPlanProgress';
import WeeklyDayGrid from '../components/weekly/WeeklyDayGrid';
import MealDetailModal from '../components/weekly/MealDetailModal';
import {
  startWeeklyPlan,
  getActiveWeeklyPlan,
  isPlanInProgress,
  isPlanDone,
} from '../lib/weeklyPlanApi';

/**
 * /meals/weekly — the weekly plan page.
 *
 * Render modes (the page picks one based on backend state):
 *
 *   1. LOADING     — initial fetch in flight
 *   2. NO_PLAN     — user has no active plan, show start form
 *   3. IN_PROGRESS — plan exists but generation is GENERATING/PENDING, show progress
 *   4. DONE        — plan COMPLETED / PARTIAL / FAILED, show grid + grocery
 *
 * Loads on mount: GET /api/meals/weekly/active. If 404 → NO_PLAN. If in progress → IN_PROGRESS.
 * If done → DONE. Each transition is handled by setting mode state.
 *
 * Why we don't useEffect-poll here directly:
 *   The WeeklyPlanProgress component owns its own polling lifecycle. Cleaner separation.
 */

const MODE = { LOADING: 'loading', NO_PLAN: 'no_plan', IN_PROGRESS: 'in_progress', DONE: 'done' };

export default function WeeklyPlanPage() {
  const [mode, setMode] = useState(MODE.LOADING);
  const [activePlanId, setActivePlanId] = useState(null);
  const [planData, setPlanData] = useState(null);  // full plan JSON when DONE
  const [starting, setStarting] = useState(false);
  const [error, setError] = useState('');
  const [selectedMeal, setSelectedMeal] = useState(null);

  // ---- initial load ----
  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const data = await getActiveWeeklyPlan();
        if (cancelled) return;
        if (!data) {
          setMode(MODE.NO_PLAN);
          return;
        }
        const status = data.plan?.status;
        if (isPlanInProgress(status)) {
          setActivePlanId(data.plan.id);
          setMode(MODE.IN_PROGRESS);
        } else if (isPlanDone(status)) {
          setPlanData(data);
          setMode(MODE.DONE);
        } else {
          setMode(MODE.NO_PLAN);
        }
      } catch (err) {
        setError(err.response?.data?.message || err.message || 'Failed to load weekly plan');
        setMode(MODE.NO_PLAN);
      }
    }
    load();
    return () => { cancelled = true; };
  }, []);

  // ---- start a new plan ----
  async function handleStart({ startDate, userRequest }) {
    setStarting(true);
    setError('');
    try {
      const plan = await startWeeklyPlan({ startDate, userRequest });
      setActivePlanId(plan.id);
      setMode(MODE.IN_PROGRESS);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to start plan');
    } finally {
      setStarting(false);
    }
  }

  // ---- when generation completes, fetch the full plan ----
  async function handleGenerationComplete() {
    try {
      const data = await getActiveWeeklyPlan();
      if (data) {
        setPlanData(data);
        setMode(MODE.DONE);
      } else {
        setMode(MODE.NO_PLAN);
      }
    } catch (err) {
      setError(err.message || 'Failed to load completed plan');
    }
  }

  // ---- "start a fresh plan" — visible when there's a DONE plan ----
  function handleStartFresh() {
    setPlanData(null);
    setActivePlanId(null);
    setMode(MODE.NO_PLAN);
  }

  return (
    <AppLayout>
      <div className="container-pad py-8 space-y-6 pb-20">
        <header>
          <h1 className="text-3xl sm:text-4xl font-serif font-semibold text-ink">
            Weekly meal plan
          </h1>
          <p className="text-ink-soft mt-2 max-w-2xl">
            7 days of brunch, dinner, and snacks — generated together, checked
            against your profile, with a consolidated grocery list.
          </p>
        </header>

        {error && mode !== MODE.IN_PROGRESS && (
          <div className="card bg-terracotta-50 border-terracotta-200">
            <p className="text-sm text-terracotta-700">{error}</p>
          </div>
        )}

        {mode === MODE.LOADING && (
          <div className="card flex items-center justify-center py-16">
            <div className="text-ink-soft">Loading…</div>
          </div>
        )}

        {mode === MODE.NO_PLAN && (
          <WeeklyPlanStartForm onStart={handleStart} disabled={starting} />
        )}

        {mode === MODE.IN_PROGRESS && activePlanId && (
          <WeeklyPlanProgress
            planId={activePlanId}
            onComplete={handleGenerationComplete}
          />
        )}

        {mode === MODE.DONE && planData && (
          <DonePlanView
            planData={planData}
            onMealClick={(meal, day) => setSelectedMeal({ meal, day })}
            onStartFresh={handleStartFresh}
          />
        )}

        {selectedMeal && (
          <MealDetailModal
            meal={selectedMeal.meal}
            dayLabel={`Day ${selectedMeal.day.day}`}
            onClose={() => setSelectedMeal(null)}
          />
        )}
      </div>
    </AppLayout>
  );
}

/**
 * Subview shown when a plan is done. Status banner + day grid + grocery list.
 */
function DonePlanView({ planData, onMealClick, onStartFresh }) {
  const plan = planData.plan || {};
  const status = plan.status;
  const isPartial = status === 'PARTIAL';
  const isFailed  = status === 'FAILED';

  return (
    <div className="space-y-6">
      {/* Status banner */}
      <div className={`flex flex-wrap items-center justify-between gap-3 rounded-lg px-4 py-3 border ${
        isFailed
          ? 'bg-terracotta-50 border-terracotta-200 text-terracotta-800'
          : isPartial
            ? 'bg-saffron-50 border-saffron-200 text-saffron-900'
            : 'bg-forest-50 border-forest-100 text-forest-700'
      }`}>
        <div className="flex items-center gap-3 text-sm">
          <span className="text-lg">{isFailed ? '⚠' : isPartial ? '⚠' : '✓'}</span>
          <span>
            {isFailed && 'No meals were generated.'}
            {isPartial && `Partial: ${plan.progressCurrent}/${plan.progressTotal} meals completed.`}
            {status === 'COMPLETED' && `All ${plan.progressTotal} meals generated successfully.`}
            {plan.progressMessage && (
              <span className="text-ink-muted block sm:inline sm:ml-2">{plan.progressMessage}</span>
            )}
          </span>
        </div>
        <Button variant="secondary" size="sm" onClick={onStartFresh}>
          Plan a new week
        </Button>
      </div>

      {/* Two-column layout on wide screens: days on left, grocery on right */}
      <div className="grid lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2">
          <WeeklyDayGrid days={planData.days || []} onMealClick={onMealClick} />
        </div>
        <div className="lg:col-span-1">
          {planData.grocery ? (
            <GroceryList grocery={planData.grocery} />
          ) : (
            <div className="card">
              <h2 className="text-xl font-serif font-semibold text-ink mb-2">Grocery list</h2>
              <p className="text-sm text-ink-muted">No grocery list yet.</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
