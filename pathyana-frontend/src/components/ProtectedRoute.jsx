import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

/**
 * Wraps a route that requires authentication.
 *
 * Behavior:
 *   - While auth state is loading (first render) → render nothing (avoid flicker)
 *   - If not authenticated → redirect to /login, remembering where they were headed
 *     so we can bounce them back after login
 *   - If authenticated → render the children
 *
 * Usage in App.jsx:
 *   <Route path="/profile" element={<ProtectedRoute><ProfilePage /></ProtectedRoute>} />
 */
export default function ProtectedRoute({ children }) {
  const { isAuthenticated, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) {
    // Brief render while we read from localStorage. Could show a spinner if you want.
    return null;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return children;
}
