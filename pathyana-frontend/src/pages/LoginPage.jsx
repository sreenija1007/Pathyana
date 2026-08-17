import { useState, useEffect } from 'react';
import { Link, useNavigate, useLocation, useSearchParams } from 'react-router-dom';
import Button from '../components/Button';
import { useAuth } from '../context/AuthContext';
import { AuthShell } from './SignupPage';

/**
 * /login — exchange credentials for a JWT.
 *
 * Edge cases handled:
 *   - ?expired=1 in URL → show "session expired, please sign in again"
 *   - Backend returns 401 → "Invalid email or password"
 *   - Backend returns 403 → "Please verify your email first"
 *   - Coming from a protected route → bounce back there after login
 */
export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const { login } = useAuth();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  // Where to send the user after successful login
  const from = location.state?.from?.pathname || '/meals';

  useEffect(() => {
    if (searchParams.get('expired') === '1') {
      setNotice('Your session expired. Please sign in again.');
    }
  }, [searchParams]);

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    setNotice('');
    setSubmitting(true);

    try {
      await login(email.trim().toLowerCase(), password);
      navigate(from, { replace: true });
    } catch (err) {
      const status = err.response?.status;
      if (status === 401) {
        setError('Invalid email or password.');
      } else if (status === 403) {
        setError('Please verify your email before signing in. Check your inbox for the verification link.');
      } else {
        setError(err.response?.data?.message || err.message || 'Login failed.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthShell>
      <h1 className="text-3xl font-serif font-semibold text-ink mb-2">Welcome back</h1>
      <p className="text-ink-soft mb-8">Sign in to continue.</p>

      {notice && (
        <div className="bg-saffron-50 border border-saffron-200 text-saffron-800 rounded-lg px-4 py-3 text-sm mb-4">
          {notice}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="label" htmlFor="email">Email</label>
          <input
            id="email"
            type="email"
            className="input"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="email"
            required
            autoFocus
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
            autoComplete="current-password"
            required
          />
        </div>

        {error && (
          <div className="bg-terracotta-50 border border-terracotta-200 text-terracotta-800 rounded-lg px-4 py-3 text-sm">
            {error}
          </div>
        )}

        <Button type="submit" className="w-full mt-2" disabled={submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </Button>
      </form>

      <p className="text-sm text-ink-soft text-center mt-6">
        New here?{' '}
        <Link to="/signup" className="text-terracotta-600 font-medium hover:underline">
          Create an account
        </Link>
      </p>
    </AuthShell>
  );
}
