/**
 * Session expiry notification utility.
 * Shows a modal dialog when session expiry is detected.
 *
 * Detection methods:
 * 1. Direct 401 from API (explicit session expiry)
 * 2. Background /auth/me probe on API errors (implicit session expiry) —
 *    most endpoints use optional auth and return 404 instead of 401
 *    when the session is gone, so we probe /auth/me to confirm.
 */
import { triggerSessionExpired } from '../../components/SessionExpiredModal';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8085/api/v1';

// Track if we've already triggered the session expiry modal
let hasTriggeredExpiry = false;
let debounceTimer: ReturnType<typeof setTimeout> | null = null;

// Track ongoing session validity check to avoid duplicate probes
let sessionCheckInFlight = false;

// Reset debounce after 5 seconds of no errors
const DEBOUNCE_RESET_MS = 5000;

/**
 * Trigger session expiry modal and save return URL.
 * Debounced to prevent multiple triggers from rapid errors.
 * Called by apiFetch when a 401 is detected.
 */
export function notifySessionExpired(): void {
  // Only run in browser
  if (typeof window === 'undefined') return;

  // Skip if on auth pages (prevents notification during auth check)
  const currentPath = window.location.pathname;
  const isAuthPage = currentPath.startsWith('/auth/') || currentPath === '/';
  if (isAuthPage) return;

  // Debounce multiple triggers
  if (hasTriggeredExpiry) {
    // Reset the timer on each new trigger
    if (debounceTimer) {
      clearTimeout(debounceTimer);
    }
    debounceTimer = setTimeout(() => {
      hasTriggeredExpiry = false;
      debounceTimer = null;
    }, DEBOUNCE_RESET_MS);
    return;
  }

  hasTriggeredExpiry = true;

  // Trigger the modal (this also saves the return URL)
  triggerSessionExpired();

  // Set up debounce reset timer
  debounceTimer = setTimeout(() => {
    hasTriggeredExpiry = false;
    debounceTimer = null;
  }, DEBOUNCE_RESET_MS);
}

/**
 * Check if the session is still valid by probing /auth/me.
 * Most API endpoints use optional auth and return 404 (not 401) when
 * the session expires. This probe detects that case and triggers the modal.
 *
 * Called by apiFetch on 403/404 errors as a background side-effect.
 * Debounced to avoid hammering /auth/me on cascading errors.
 */
export function checkSessionValidity(): void {
  if (typeof window === 'undefined') return;
  if (hasTriggeredExpiry || sessionCheckInFlight) return;

  const currentPath = window.location.pathname;
  const isAuthPage = currentPath.startsWith('/auth/') || currentPath === '/';
  if (isAuthPage) return;

  sessionCheckInFlight = true;

  fetch(`${API_BASE_URL}/auth/me`, {
    credentials: 'include',
    headers: { 'Accept': 'application/json' },
  })
    .then((res) => {
      if (res.status === 401) {
        notifySessionExpired();
      }
    })
    .catch(() => {
      // Network error — don't trigger session expiry, could be connectivity
    })
    .finally(() => {
      sessionCheckInFlight = false;
    });
}

/**
 * Reset the session expiry state.
 * Call this when user successfully re-authenticates.
 */
export function resetSessionExpiryState(): void {
  hasTriggeredExpiry = false;
  sessionCheckInFlight = false;
  if (debounceTimer) {
    clearTimeout(debounceTimer);
    debounceTimer = null;
  }
}
