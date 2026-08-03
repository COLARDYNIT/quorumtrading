import { User } from '../../types/user';

// Same base as the rest of the API client (lib/api/client.ts) — deliberately NOT a relative URL.
//
// This used to fall back to the relative '/api/v1' whenever the browser hostname was localhost, on
// the assumption that the Next.js dev proxy would forward it. That rewrite only exists when
// NODE_ENV === 'development' (see next.config.ts), and the self-hosted Docker image serves a
// PRODUCTION build — so every auth call went to localhost:3000/api/v1/... and 404'd. Sign-in
// appeared to fail with "we couldn't verify the OIDC handshake" even though the backend had
// happily created the session, and the header never showed the user.
//
// An absolute URL is cross-origin but not cross-site: localhost:3000 → localhost:8085 shares a
// registrable domain, so SameSite=Lax session cookies are still sent, and CORS already allows the
// frontend origin with credentials.
const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8085/api/v1';

// Dev-session endpoints talk to the same backend; kept as a separate name for readability.
const BACKEND_URL = API_BASE;

// Timeout for auth API calls (3 seconds) - prevents hanging when backend is down
const AUTH_TIMEOUT_MS = 3000;

/**
 * Timeout promise that rejects after specified milliseconds
 */
function timeoutPromise<T>(ms: number): Promise<T> {
  return new Promise((_, reject) => {
    setTimeout(() => reject(new Error('Request timed out')), ms);
  });
}

/**
 * Fetch with timeout using Promise.race (more reliable than AbortController through Next.js proxy)
 */
async function fetchWithTimeout(url: string, options?: RequestInit, timeoutMs = AUTH_TIMEOUT_MS): Promise<Response> {
  const controller = new AbortController();

  try {
    const response = await Promise.race([
      fetch(url, { ...options, signal: controller.signal }),
      timeoutPromise<Response>(timeoutMs),
    ]);
    return response;
  } catch (error) {
    // Abort the fetch if it's still pending
    controller.abort();
    throw error;
  }
}

export interface AuthProvider {
  id: string;
  name: string;
  icon: string;
}

export interface AuthProvidersResponse {
  providers: AuthProvider[];
  devLoginEnabled: boolean;
}

export interface AuthProvidersResult {
  providers: AuthProvider[];
  devLoginEnabled: boolean;
}

export async function fetchAuthProviders(): Promise<AuthProvidersResult> {
  try {
    const res = await fetchWithTimeout(`${API_BASE}/auth/providers`);
    if (res.ok) {
      const data: AuthProvidersResponse = await res.json();
      return {
        providers: data.providers,
        devLoginEnabled: data.devLoginEnabled,
      };
    }
    return { providers: [], devLoginEnabled: false };
  } catch {
    // Timeout, network error, or backend unavailable - return empty providers
    return { providers: [], devLoginEnabled: false };
  }
}

/**
 * One-shot sessionStorage flag: makes the next visit to /auth/signin show the manual
 * provider picker instead of standalone auto-login. Set by logout() so signing out of a
 * standalone instance doesn't bounce straight back in; cleared by the signin page on read.
 */
export const SIGNIN_PICKER_FLAG = 'qt_signin_show_picker';

export function getDevLoginUrl(): string {
  return `${BACKEND_URL}/auth/dev-login`;
}

export function getFutLoginUrl(): string {
  return `${BACKEND_URL}/auth/fut-login`;
}

/**
 * Fetch current user from the unified /me endpoint.
 * Works with both OIDC sessions (production) and dev sessions (dev/E2E).
 */
async function tryFetchUser(): Promise<User | null> {
  try {
    const res = await fetch(`${API_BASE}/auth/me`, {
      credentials: 'include',
    });
    if (res.ok) {
      return await res.json();
    }
  } catch {
    // Endpoint failed or backend unavailable
  }

  return null;
}

export async function fetchCurrentUser(): Promise<User | null> {
  try {
    // Use Promise.race with overall timeout to ensure we don't hang
    const result = await Promise.race([
      tryFetchUser(),
      timeoutPromise<User | null>(AUTH_TIMEOUT_MS),
    ]);
    return result;
  } catch {
    // Timeout or error - user not authenticated
    return null;
  }
}

export async function logout(): Promise<void> {
  await fetch(`${API_BASE}/auth/logout`, {
    method: 'POST',
    credentials: 'include',
  });
}

export function getLoginUrl(provider: string): string {
  return `${API_BASE}/auth/login/${provider}`;
}
