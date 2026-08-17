import { Link } from 'react-router-dom';
import Logo from '../components/Logo';
import Button from '../components/Button';

/**
 * Public homepage for unauthenticated visitors.
 *
 * Structure:
 *   1. Top nav with logo + Sign in / Sign up buttons
 *   2. Hero — the pitch
 *   3. Three-feature explainer (drug safety, eating window, swap engine)
 *   4. CTA strip
 *   5. Footer with disclaimer
 *
 * Auth links go to /signup and /login, which don't exist yet (Chunk 2).
 * They'll 404 silently — that's fine for now.
 */
export default function LandingPage() {
  return (
    <div className="min-h-screen bg-cream-50">
      {/* ---- Top Nav ---- */}
      <nav className="border-b border-cream-200 bg-white/70 backdrop-blur sticky top-0 z-10">
        <div className="container-pad flex items-center justify-between py-4">
          <Logo size="md" />
          <div className="flex gap-2 sm:gap-3">
            <Link to="/login">
              <Button variant="ghost" size="sm">Sign in</Button>
            </Link>
            <Link to="/signup">
              <Button variant="primary" size="sm">Get started</Button>
            </Link>
          </div>
        </div>
      </nav>

      {/* ---- Hero ---- */}
      <section className="container-pad pt-16 pb-20 sm:pt-24 sm:pb-28">
        <div className="max-w-3xl">
          <span className="inline-block text-xs font-medium tracking-widest uppercase text-terracotta-600 mb-4">
            Clinical nutrition, personalized
          </span>
          <h1 className="text-4xl sm:text-6xl font-serif font-semibold leading-tight text-ink mb-6">
            Meals that{' '}
            <span className="text-terracotta-600">respect your medications</span>,
            conditions, and rhythm.
          </h1>
          <p className="text-lg text-ink-soft mb-8 max-w-2xl">
            Pathyana is a clinical nutrition engine for people managing diabetes,
            hypertension, kidney disease, or complex medication regimens. Every meal
            is checked against your prescriptions before it reaches your plate.
          </p>
          <div className="flex flex-col sm:flex-row gap-3">
            <Link to="/signup">
              <Button size="lg">Create your profile</Button>
            </Link>
            <Link to="/login">
              <Button variant="secondary" size="lg">I already have an account</Button>
            </Link>
          </div>
        </div>
      </section>

      {/* ---- Feature highlights ---- */}
      <section className="bg-white border-y border-cream-200">
        <div className="container-pad py-16 grid md:grid-cols-3 gap-8">
          <FeatureCard
            title="Drug–food safety"
            body="Grapefruit on statins. Spinach on warfarin. Aged cheese on MAOIs. Pathyana blocks dangerous combinations automatically — no AI guessing."
            accent="terracotta"
          />
          <FeatureCard
            title="Eating window aware"
            body="Tell us your eating hours and we'll plan meals to fit — including the bedtime buffer for reflux and morning meds like levothyroxine."
            accent="saffron"
          />
          <FeatureCard
            title="Smart ingredient swaps"
            body="Diabetic? Pasta becomes chickpea pasta. Gluten-free? Soy sauce becomes tamari. Predictable, every time — not random AI substitutions."
            accent="forest"
          />
        </div>
      </section>

      {/* ---- CTA Strip ---- */}
      <section className="container-pad py-20 text-center">
        <h2 className="text-3xl sm:text-4xl font-serif font-semibold text-ink mb-4">
          Built for complex care.
        </h2>
        <p className="text-ink-soft max-w-xl mx-auto mb-8">
          Whether you're newly diagnosed or have been juggling prescriptions for years,
          Pathyana keeps your meals aligned with your body's needs.
        </p>
        <Link to="/signup">
          <Button size="lg">Start your account</Button>
        </Link>
      </section>

      {/* ---- Footer ---- */}
      <footer className="bg-ink text-cream-100 py-10">
        <div className="container-pad flex flex-col sm:flex-row justify-between items-start gap-6">
          <div>
            <div className="font-serif text-2xl font-bold text-cream-50">
              pathyana<span className="text-saffron-400">.</span>
            </div>
            <p className="text-cream-200 text-sm mt-2">The clinical nutrition engine.</p>
          </div>
          <div className="text-xs text-cream-200 max-w-md leading-relaxed">
            Pathyana provides educational information only. Not a substitute for
            advice from a qualified healthcare professional. Always consult your
            doctor before making changes to your diet or medications.
          </div>
        </div>
      </footer>
    </div>
  );
}

/**
 * Small presentational sub-component, kept inline because it's only used here.
 * Three-color accent stripe on top changes per feature.
 */
function FeatureCard({ title, body, accent }) {
  const accentClasses = {
    terracotta: 'bg-terracotta-500',
    saffron:    'bg-saffron-400',
    forest:     'bg-forest-500',
  };
  return (
    <div className="relative pt-6">
      <div className={`absolute top-0 left-0 w-12 h-1 rounded-full ${accentClasses[accent]}`} />
      <h3 className="text-xl font-serif font-semibold text-ink mb-3">{title}</h3>
      <p className="text-ink-soft leading-relaxed">{body}</p>
    </div>
  );
}
