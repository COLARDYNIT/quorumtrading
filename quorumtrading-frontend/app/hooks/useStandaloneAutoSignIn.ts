'use client';

import { useAuth } from './useAuth';
import { useInstanceInfo } from '../lib/api/hooks';

export interface StandaloneAutoSignIn {
  /**
   * Both probes have answered. Until they have, `enabled` is a default rather than a
   * verdict, and a caller that acts on it is guessing.
   */
  resolved: boolean;
  /** This instance signs its own user in; nobody is going to be asked anything. */
  enabled: boolean;
}

/**
 * Whether this instance enters on its own instead of asking who is signing in.
 *
 * Standalone is single-user: the only meaningful identity is the local owner, so a sign-in
 * *choice* is noise. The dev-login endpoint is already unauthenticated on that edition, so
 * entering directly adds convenience, not exposure. OIDC providers, if an operator configured
 * any, take precedence over the bypass — someone who set up real identity wants to use it.
 *
 * One definition with two readers: `/auth/signin` uses it to enter directly, and the
 * session-expiry modal uses it to know that its ten-second warning is about to be undone by an
 * invisible re-login. Two copies of this predicate would drift, and the drift would surface as a
 * padlock and a countdown in front of a sign-in that never needed announcing.
 */
export function useStandaloneAutoSignIn(): StandaloneAutoSignIn {
  const { providers, providersLoading, devLoginEnabled } = useAuth();
  const { data: instance, isLoading: instanceLoading } = useInstanceInfo();

  const resolved = !instanceLoading && !providersLoading;

  return {
    resolved,
    enabled:
      resolved &&
      instance?.edition === 'standalone' &&
      devLoginEnabled &&
      providers.length === 0,
  };
}
