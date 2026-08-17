import { useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../lib/api';
import AppLayout from '../components/AppLayout';
import MealRequestForm from '../components/meals/MealRequestForm';
import MealLoadingState from '../components/meals/MealLoadingState';
import RecipeCard from '../components/meals/RecipeCard';
import SafetyReviewPanel from '../components/meals/SafetyReviewPanel';
import GroceryList from '../components/meals/GroceryList';
import Button from '../components/Button';

/**
 * /meals — single-meal generator (unchanged from before), with a new banner
 * inviting the user to try the weekly plan feature.
 */
export default function MealsPage() {
  const [status, setStatus] = useState('idle');
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');

  async function generate(userRequest) {
    setStatus('loading');
    setError('');
    setResult(null);
    try {
      const response = await api.post('/api/meals/generate', { userRequest });
      const data = typeof response.data === 'string' ? JSON.parse(response.data) : response.data;
      setResult(data);
      setStatus('success');
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to generate meal');
      setStatus('error');
    }
  }

  function reset() {
    setStatus('idle');
    setResult(null);
    setError('');
  }

  return (
    <AppLayout>
      <div className="container-pad py-8 space-y-6">
        {status !== 'success' && (
          <header>
            <h1 className="text-3xl sm:text-4xl font-serif font-semibold text-ink">
              What should we cook?
            </h1>
            <p className="text-ink-soft mt-2 max-w-2xl">
              Tell Pathyana what you'd like. Your profile drives every safety check.
            </p>
          </header>
        )}

        {/* NEW: invite users to try weekly planning */}
        {status === 'idle' && (
          <div className="card bg-gradient-to-r from-saffron-50 to-cream-100 border-saffron-200">
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
              <div>
                <h2 className="text-lg font-serif font-semibold text-ink mb-1">
                  Plan a whole week at once
                </h2>
                <p className="text-sm text-ink-soft">
                  21 meals over 7 days with one consolidated grocery list.
                </p>
              </div>
              <Link to="/meals/weekly">
                <Button>Plan my week →</Button>
              </Link>
            </div>
          </div>
        )}

        {status !== 'loading' && (
          <MealRequestForm onSubmit={generate} disabled={status === 'loading'} />
        )}

        {status === 'loading' && <MealLoadingState />}

        {status === 'error' && error && (
          <div className="card bg-terracotta-50 border-terracotta-200">
            <h3 className="font-semibold text-terracotta-800 mb-1">Something went wrong</h3>
            <p className="text-sm text-terracotta-700 mb-3">{error}</p>
            <Button variant="secondary" size="sm" onClick={reset}>Try again</Button>
          </div>
        )}

        {status === 'success' && result && (
          <ResultDisplay result={result} onNewMeal={reset} />
        )}
      </div>
    </AppLayout>
  );
}

function ResultDisplay({ result, onNewMeal }) {
  const failed = result.status === 'FAILED_SAFETY' || result.status === 'FAILED_SYSTEM';

  return (
    <div className="space-y-6">
      {failed ? (
        <div className="card bg-terracotta-50 border-terracotta-200">
          <h2 className="text-xl font-serif font-semibold text-terracotta-800 mb-2">
            Couldn't generate a safe meal
          </h2>
          <p className="text-sm text-terracotta-700 mb-3">
            After {result.regenerationCount + 1} attempt{result.regenerationCount === 0 ? '' : 's'},
            Pathyana couldn't produce a recipe that's safe for your profile.
          </p>
          <Button variant="secondary" size="sm" onClick={onNewMeal}>Try a new request</Button>
        </div>
      ) : (
        <div className="flex flex-wrap items-center justify-between gap-3 bg-forest-50 border border-forest-100 rounded-lg px-4 py-3">
          <div className="flex items-center gap-3 text-sm text-forest-700">
            <span className="text-lg">✓</span>
            <span>
              Approved
              {result.regenerationCount > 0 && (
                <span className="text-ink-muted"> · {result.regenerationCount} regeneration{result.regenerationCount === 1 ? '' : 's'}</span>
              )}
            </span>
          </div>
          <Button variant="secondary" size="sm" onClick={onNewMeal}>
            New meal
          </Button>
        </div>
      )}

      <div className="grid lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 space-y-6">
          <RecipeCard recipe={result.recipe} />
          <GroceryList grocery={result.grocery} />
        </div>
        <div className="lg:col-span-1 space-y-6">
          <SafetyReviewPanel review={result.review} />
        </div>
      </div>

      {result.disclaimer && (
        <div className="text-center text-xs text-ink-muted px-4 py-2">
          {result.disclaimer}
        </div>
      )}
    </div>
  );
}
