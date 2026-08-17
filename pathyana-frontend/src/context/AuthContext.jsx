import { createContext, useContext, useEffect, useState } from 'react';
import api, { tokenStore, userStore } from '../lib/api';

/**
 * AuthContext makes the current user + auth actions available anywhere in the
 * component tree without prop drilling.
 *
 * State shape:
 *   user:        { id, email, name } or null
 *   isLoading:   true on first render while we read from localStorage
 *   login():     POST /api/auth/login, store token+user, return on success
 *   logout():    clear storage, push to /login
 *   signup():    POST /api/auth/signup (does NOT log in — user must verify email first)
 *
 * Why isLoading matters: without it, ProtectedRoute would briefly think the user
 * is logged out on initial page load (before localStorage is read) and redirect
 * to /login. Even though localStorage is synchronous in practice, gating on a
 * boolean keeps the contract explicit.
 */

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [isLoading, setIsLoading] = useState(true);

  // On mount, restore session from localStorage
  useEffect(() => {
    const storedUser = userStore.get();
    const storedToken = tokenStore.get();
    if (storedUser && storedToken) {
      setUser(storedUser);
    }
    setIsLoading(false);
  }, []);

  async function login(email, password) {
    const response = await api.post('/api/auth/login', { email, password });
    const { token, userId, email: respEmail, name } = response.data;
    const userObj = { id: userId, email: respEmail, name };
    tokenStore.set(token);
    userStore.set(userObj);
    setUser(userObj);
    return userObj;
  }

  async function signup(name, email, password) {
    // Returns 201 with a message; user must verify email before login works
    return api.post('/api/auth/signup', { name, email, password });
  }

  function logout() {
    tokenStore.clear();
    setUser(null);
    window.location.href = '/login';
  }

  const value = { user, isLoading, login, signup, logout, isAuthenticated: !!user };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be called inside an <AuthProvider>');
  }
  return ctx;
}
