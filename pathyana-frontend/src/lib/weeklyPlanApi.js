import api from './api';

/**
 * Helper module for talking to the weekly meal plan endpoints.
 *
 * Endpoints used:
 *   POST /api/meals/weekly/generate         — kicks off generation, returns plan stub
 *   GET  /api/meals/weekly/{id}/status      — lightweight progress polling
 *   GET  /api/meals/weekly/active           — current active plan, full detail
 *   GET  /api/meals/weekly/{id}             — specific plan, full detail
 *   GET  /api/meals/weekly/{id}/grocery     — grocery list only
 *
 * Why a separate module:
 *   - The weekly plan response shapes are unique (plan + days + meals + grocery).
 *   - The status polling pattern is best encapsulated.
 *   - Keeps the page components clean.
 */

/** Start a new weekly plan. Returns {id, status, progressCurrent, progressTotal, ...}. */
export async function startWeeklyPlan({ startDate, userRequest } = {}) {
  const body = {};
  if (startDate) body.startDate = startDate;
  if (userRequest) body.userRequest = userRequest;
  const response = await api.post('/api/meals/weekly/generate', body);
  return response.data;
}

/** Lightweight status — call this every few seconds during generation. */
export async function getWeeklyPlanStatus(id) {
  const response = await api.get(`/api/meals/weekly/${id}/status`);
  return response.data;
}

/**
 * Full active plan — returns the big object with plan metadata, days array,
 * and grocery list. Throws on 404 if no active plan exists.
 */
export async function getActiveWeeklyPlan() {
  // Backend returns the body as a JSON string (because it builds it manually).
  // axios may auto-parse depending on content-type. Handle both.
  try {
    const response = await api.get('/api/meals/weekly/active');
    return typeof response.data === 'string' ? JSON.parse(response.data) : response.data;
  } catch (err) {
    if (err.response?.status === 404 || err.response?.status === 403) return null;
    throw err;
  }
}

/** Specific plan by ID (active or archived). */
export async function getWeeklyPlan(id) {
  const response = await api.get(`/api/meals/weekly/${id}`);
  return typeof response.data === 'string' ? JSON.parse(response.data) : response.data;
}

/**
 * Status strings the backend may return. Helpful to import for switch statements.
 */
export const PLAN_STATUS = {
  PENDING:    'PENDING',
  GENERATING: 'GENERATING',
  COMPLETED:  'COMPLETED',
  PARTIAL:    'PARTIAL',
  FAILED:     'FAILED',
};

export function isPlanInProgress(status) {
  return status === PLAN_STATUS.PENDING || status === PLAN_STATUS.GENERATING;
}

export function isPlanDone(status) {
  return status === PLAN_STATUS.COMPLETED || status === PLAN_STATUS.PARTIAL || status === PLAN_STATUS.FAILED;
}
