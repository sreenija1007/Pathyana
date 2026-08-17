import axios from 'axios';

/**
 * Centralized axios instance for talking to the Pathyana backend.
 *
 * Two interceptors do most of the work:
 *
 *  1. REQUEST interceptor — automatically attaches the JWT from localStorage
 *     to the Authorization header on every outbound request. Pages never have
 *     to think about it.
 *
 *  2. RESPONSE interceptor — if the backend returns 401 (token expired or
 *     missing), we clear the stored token and redirect to /login. The user
 *     re-authenticates and life continues. Without this, an expired token
 *     would silently fail every API call until the user manually logs out.
 *
 * Why localStorage and not cookies? Pathyana is a single-page app talking to
 * a JSON API. We don't need CSRF protection (which is the main reason to use
 * cookies). localStorage is simpler and matches our stateless JWT model.
 *
 * XSS is the relevant risk for localStorage — but since we control the entire
 * frontend code, the same XSS that could steal a token could also impersonate
 * the user directly. Cookies don't actually help.
 */

const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
const TOKEN_KEY = 'pathyana.token';
const USER_KEY = 'pathyana.user';

const api = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
});

// ---- Token storage helpers (exported so context can use them) ----
export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  },
};

export const userStore = {
  get: () => {
    try {
      const raw = localStorage.getItem(USER_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  },
  set: (user) => localStorage.setItem(USER_KEY, JSON.stringify(user)),
  clear: () => localStorage.removeItem(USER_KEY),
};

// ---- Interceptors ----
api.interceptors.request.use((config) => {
  const token = tokenStore.get();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      tokenStore.clear();
      // Avoid an infinite loop if we're already on /login
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login?expired=1';
      }
    }
    return Promise.reject(error);
  }
);

export default api;
