import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import Logo from '../components/Logo';
import Button from '../components/Button';
import { useAuth } from '../context/AuthContext';

/**
 * /signup — new user creates an account.
 *
 * Flow:
 *   1. User submits form
 *   2. POST /api/auth/signup
 *   3. Backend creates user + sends verification email
 *   4. We show a "check your email" success state
 *   5. User clicks email link → backend marks verified → user can /login
 *
 * Validation: minimal client-side. The backend has the authoritative rules.
 * We just check non-empty + password length to save a round-trip on obvious mistakes.
 */
export default function SignupPage() {
  const navigate = useNavigate();
  const { signup } = useAuth();

  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');

    if (password.length < 8) {
      setError('Password must be at least 8 characters');
      return;
    }

    setSubmitting(true);
    try {
      await signup(name.trim(), email.trim().toLowerCase(), password);
      setSuccess(true);
    } catch (err) {
      const detail = err.response?.data?.message
                  || err.response?.data?.detail
                  || err.message;
      // Common case: 409 from backend means email already exists
      if (err.response?.status === 409) {
        setError('An account with this email already exists. Try logging in instead.');
      } else {
        setError(detail || 'Sign up failed. Please try again.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (success) {
    return (
      <AuthShell>
        <div className="text-center">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-forest-100 rounded-full mb-4">
            <svg className="w-8 h-8 text-forest-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
            </svg>
          </div>
          <h1 className="text-2xl font-serif font-semibold mb-3">Check your inbox</h1>
          <p className="text-ink-soft mb-6">
            We sent a verification link to <strong className="text-ink">{email}</strong>.
            Click it to activate your account, then come back here to sign in.
          </p>
          <Link to="/login">
            <Button variant="secondary">Go to login</Button>
          </Link>
        </div>
      </AuthShell>
    );
  }

  return (
    <AuthShell>
      <h1 className="text-3xl font-serif font-semibold text-ink mb-2">Create your account</h1>
      <p className="text-ink-soft mb-8">Your meals start with knowing you.</p>

      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="label" htmlFor="name">Full name</label>
          <input
            id="name"
            type="text"
            className="input"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Priya Sharma"
            autoComplete="name"
            required
          />
        </div>

        <div>
          <label className="label" htmlFor="email">Email</label>
          <input
            id="email"
            type="email"
            className="input"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="you@example.com"
            autoComplete="email"
            required
          />
        </div>

        <div>
          <label className="label" htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            className="input"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="At least 8 characters"
            autoComplete="new-password"
            minLength={8}
            required
          />
        </div>

        {error && (
          <div className="bg-terracotta-50 border border-terracotta-200 text-terracotta-800 rounded-lg px-4 py-3 text-sm">
            {error}
          </div>
        )}

        <Button type="submit" className="w-full mt-2" disabled={submitting}>
          {submitting ? 'Creating account…' : 'Create account'}
        </Button>
      </form>

      <p className="text-sm text-ink-soft text-center mt-6">
        Already have an account?{' '}
        <Link to="/login" className="text-terracotta-600 font-medium hover:underline">
          Sign in
        </Link>
      </p>
    </AuthShell>
  );
}

/**
 * Shared centered shell used by SignupPage and LoginPage. Inlined here because it's
 * only used by these two pages — no need for a separate file yet.
 */
function AuthShell({ children }) {
  return (
    <div className="min-h-screen bg-cream-50 flex flex-col">
      <div className="container-pad py-6">
        <Link to="/"><Logo size="md" /></Link>
      </div>
      <div className="flex-1 flex items-center justify-center px-5 py-8">
        <div className="w-full max-w-md card">{children}</div>
      </div>
    </div>
  );
}

// Export AuthShell so LoginPage can reuse it without duplication
export { AuthShell };
