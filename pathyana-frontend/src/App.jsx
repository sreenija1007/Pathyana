import { Routes, Route, Navigate } from 'react-router-dom';
import LandingPage from './pages/LandingPage';
import SignupPage from './pages/SignupPage';
import LoginPage from './pages/LoginPage';
import ProfilePage from './pages/ProfilePage';
import MealsPage from './pages/MealsPage';
import WeeklyPlanPage from './pages/WeeklyPlanPage';
import ProtectedRoute from './components/ProtectedRoute';

/**
 * Top-level routes.
 *
 * Phase 6 added:
 *   /meals/weekly — 7-day meal plan with consolidated grocery list
 */
function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/signup" element={<SignupPage />} />
      <Route path="/login" element={<LoginPage />} />

      <Route
        path="/profile"
        element={
          <ProtectedRoute>
            <ProfilePage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/meals"
        element={
          <ProtectedRoute>
            <MealsPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/meals/weekly"
        element={
          <ProtectedRoute>
            <WeeklyPlanPage />
          </ProtectedRoute>
        }
      />

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default App;
