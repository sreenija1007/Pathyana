import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../lib/api';
import AppLayout from '../components/AppLayout';
import Button from '../components/Button';
import FormSection from '../components/profile/FormSection';
import ChipPicker from '../components/profile/ChipPicker';
import MedicationPicker from '../components/profile/MedicationPicker';
import EatingWindowPicker from '../components/profile/EatingWindowPicker';
import {
  DIETARY_PREFERENCES,
  HEALTH_GOALS,
  CUISINES,
  COMMON_CONDITIONS,
  COMMON_ALLERGIES,
} from '../lib/profileOptions';
    import WhatWeCheckHelper from '../components/profile/WhatWeCheckHelper';

/**
 * /profile — full clinical profile form.
 *
 * On mount, GET /api/profile to load existing data. New users will get back
 * mostly-null fields (only name and email are guaranteed); we initialize the
 * form with safe defaults for those.
 *
 * On submit, POST /api/profile/seed to save.
 *
 * State stays in sync with the backend after save (we re-fetch). This is a
 * minor cost (one extra request) for the benefit of always showing the truth.
 */

const EMPTY_PROFILE = {
  age: '',
  city: '',
  dietaryPreference: '',
  healthGoal: '',
  cuisinePreference: '',
  healthConditions: [],
  allergies: [],
  foodPreferences: [],
  foodsToAvoid: [],
  medications: [],
  window: {
    protocol: 'NONE',
    start: null,
    end: null,
    bedtime: null,
    mealToBedBufferMinutes: null,
  },
};

/** Map the GET /api/profile response shape to our form-state shape. */
function responseToFormState(data) {
  return {
    age: data.age != null ? String(data.age) : '',
    city: data.city || '',
    dietaryPreference: data.dietaryPreference || '',
    healthGoal: data.healthGoal || '',
    cuisinePreference: data.cuisinePreference || '',
    healthConditions: data.healthConditions || [],
    allergies: data.allergies || [],
    foodPreferences: data.foodPreferences || [],
    foodsToAvoid: data.foodsToAvoid || [],
    medications: (data.medications || []).map((m) => ({
      displayName: m.displayName,
      genericName: m.genericName,
      dosage: m.dosage || '',
    })),
    window: {
      protocol: data.windowProtocol || 'NONE',
      start: data.eatingWindowStart || null,
      end: data.eatingWindowEnd || null,
      bedtime: data.typicalBedtime || null,
      mealToBedBufferMinutes: data.mealToBedBufferMinutes ?? null,
    },
  };
}

export default function ProfilePage() {
  const navigate = useNavigate();

  const [profile, setProfile] = useState(EMPTY_PROFILE);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  const [isExistingProfile, setIsExistingProfile] = useState(false);

  // Load existing profile on mount
  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const response = await api.get('/api/profile');
        if (cancelled) return;
        const data = response.data;
        setProfile(responseToFormState(data));
        setIsExistingProfile(Boolean(data.profileCompleted));
      } catch (err) {
        // 401 will be handled by axios interceptor (redirect to /login).
        // Anything else, log it and leave the form empty so the user can still fill it.
        if (err.response?.status !== 401) {
          console.error('Failed to load profile:', err);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    load();
    return () => { cancelled = true; };
  }, []);

  function updateField(field, value) {
    setProfile((p) => ({ ...p, [field]: value }));
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    setSubmitting(true);

    try {
      const payload = {
        age: profile.age ? parseInt(profile.age, 10) : null,
        city: profile.city || null,
        dietaryPreference: profile.dietaryPreference || null,
        healthGoal: profile.healthGoal || null,
        cuisinePreference: profile.cuisinePreference || null,

        eatingWindowStart: profile.window.protocol === 'NONE' ? null : profile.window.start,
        eatingWindowEnd:   profile.window.protocol === 'NONE' ? null : profile.window.end,
        windowProtocol:    profile.window.protocol,
        typicalBedtime:    profile.window.protocol === 'NONE' ? null : profile.window.bedtime,
        mealToBedBufferMinutes: profile.window.protocol === 'NONE' ? null : profile.window.mealToBedBufferMinutes,

        healthConditions: profile.healthConditions,
        allergies: profile.allergies,
        foodPreferences: profile.foodPreferences,
        foodsToAvoid: profile.foodsToAvoid,
        medications: profile.medications.map((m) => ({
          displayName: m.displayName,
          genericName: m.genericName,
          dosage: m.dosage || null,
        })),
      };

      await api.post('/api/profile/seed', payload);
      setSuccess(true);
      setIsExistingProfile(true);

      // If it's the user's first save, send them to meals. If they're updating,
      // stay on the page so they can see "Profile saved" without losing context.
      if (!isExistingProfile) {
        setTimeout(() => navigate('/meals'), 800);
      } else {
        setTimeout(() => setSuccess(false), 2500);
      }
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to save profile');
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return (
      <AppLayout>
        <div className="container-pad py-8">
          <div className="card flex items-center justify-center py-16">
            <div className="text-ink-soft">Loading your profile…</div>
          </div>
        </div>
      </AppLayout>
    );
  }

  return (
    <AppLayout>
      <div className="container-pad py-8 pb-20">
        <header className="mb-8">
          <h1 className="text-3xl sm:text-4xl font-serif font-semibold text-ink">
            {isExistingProfile ? 'Update your profile' : 'Your clinical profile'}
          </h1>
          <p className="text-ink-soft mt-2 max-w-2xl">
            {isExistingProfile
              ? 'Adjust your conditions, medications, or preferences here. Changes apply to your next meal generation.'
              : "Every meal Pathyana suggests respects what's here. Take a few minutes to be thorough — the more we know, the safer and more useful your meals will be."}
          </p>
        </header>

        <form onSubmit={handleSubmit} className="card">
        <div className="pb-4 mb-2 border-b border-cream-200">
            <WhatWeCheckHelper />
          </div>

          {/* Basics */}
          <FormSection
            title="About you"
            helper="Basic context for portion sizing and locale-appropriate recipes."
          >
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="label">Age</label>
                <input
                  type="number"
                  className="input py-2"
                  value={profile.age}
                  onChange={(e) => updateField('age', e.target.value)}
                  min="0"
                  max="120"
                />
              </div>
              <div>
                <label className="label">City</label>
                <input
                  type="text"
                  className="input py-2"
                  value={profile.city}
                  onChange={(e) => updateField('city', e.target.value)}
                  placeholder="e.g. Mumbai"
                />
              </div>
            </div>
          </FormSection>

          {/* Diet */}
          <FormSection
            title="Diet & goals"
            helper="What kind of food fits your lifestyle and what you're trying to achieve."
          >
            <div>
              <label className="label">Dietary preference</label>
              <div className="flex flex-wrap gap-2">
                {DIETARY_PREFERENCES.map((d) => (
                  <button
                    key={d.value}
                    type="button"
                    onClick={() => updateField('dietaryPreference', d.value)}
                    className={`px-3 py-1.5 text-sm rounded-full border transition ${
                      profile.dietaryPreference === d.value
                        ? 'bg-terracotta-500 text-white border-terracotta-500'
                        : 'bg-white border-cream-300 hover:border-terracotta-400'
                    }`}
                  >
                    {d.label}
                  </button>
                ))}
              </div>
            </div>

            <div>
              <label className="label">Primary health goal</label>
              <select
                className="input py-2"
                value={profile.healthGoal}
                onChange={(e) => updateField('healthGoal', e.target.value)}
              >
                <option value="">Select a goal…</option>
                {HEALTH_GOALS.map((g) => (
                  <option key={g.value} value={g.value}>{g.label}</option>
                ))}
              </select>
            </div>

            <div>
              <label className="label">Cuisine preference</label>
              <select
                className="input py-2"
                value={profile.cuisinePreference}
                onChange={(e) => updateField('cuisinePreference', e.target.value)}
              >
                <option value="">No preference</option>
                {CUISINES.map((c) => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </select>
            </div>
          </FormSection>

          {/* Conditions */}
          <FormSection
            title="Health conditions"
            helper="These shape both the recipe choices and the safety review. Add anything relevant."
          >
            <ChipPicker
              options={COMMON_CONDITIONS}
              value={profile.healthConditions}
              onChange={(v) => updateField('healthConditions', v)}
              placeholder="Add another condition…"
            />
          </FormSection>

          {/* Allergies */}
          <FormSection
            title="Allergies"
            helper="Hard exclusions. Pathyana will never include these, even in trace amounts."
          >
            <ChipPicker
              options={COMMON_ALLERGIES}
              value={profile.allergies}
              onChange={(v) => updateField('allergies', v)}
              placeholder="Add another allergy…"
            />
          </FormSection>

          {/* Likes / Dislikes */}
          <FormSection
            title="Food preferences"
            helper="Foods you enjoy, and foods you'd rather avoid (for any reason)."
          >
            <div>
              <label className="label">Foods you like</label>
              <ChipPicker
                options={[]}
                value={profile.foodPreferences}
                onChange={(v) => updateField('foodPreferences', v)}
                placeholder="e.g. chicken, lentils, spinach"
              />
            </div>
            <div>
              <label className="label">Foods to avoid</label>
              <ChipPicker
                options={[]}
                value={profile.foodsToAvoid}
                onChange={(v) => updateField('foodsToAvoid', v)}
                placeholder="e.g. beef, mushrooms"
              />
            </div>
          </FormSection>

          {/* Medications */}
          <FormSection
            title="Medications"
            helper="The drug–food safety check is built from this list. Be thorough — include supplements if they're clinically active."
          >
            <MedicationPicker
              value={profile.medications}
              onChange={(v) => updateField('medications', v)}
            />
          </FormSection>

          {/* Eating window */}
          <FormSection
            title="Eating window"
            helper="If you'd like Pathyana to time-shift meals (e.g., for diabetes, reflux, fasting), set your eating hours."
          >
            <EatingWindowPicker
              value={profile.window}
              onChange={(v) => updateField('window', v)}
            />
          </FormSection>

          {/* Submit */}
          <div className="pt-6 flex flex-col sm:flex-row items-center sm:justify-end gap-3">
            {error && (
              <div className="flex-1 bg-terracotta-50 border border-terracotta-200 text-terracotta-800 rounded-lg px-4 py-2 text-sm">
                {error}
              </div>
            )}
            {success && (
              <div className="flex-1 bg-forest-50 border border-forest-200 text-forest-700 rounded-lg px-4 py-2 text-sm">
                {isExistingProfile ? '✓ Profile updated.' : '✓ Profile saved. Taking you to your meals…'}
              </div>
            )}
            <Button type="submit" size="lg" disabled={submitting}>
              {submitting ? 'Saving…' : (isExistingProfile ? 'Update profile' : 'Save profile')}
            </Button>
          </div>
        </form>
      </div>
    </AppLayout>
  );
}
