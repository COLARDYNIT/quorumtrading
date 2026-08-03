'use client';

import { useAuthContext } from '../contexts/AuthContext';

export function useAuth() {
  return useAuthContext();
}

export function useUser() {
  const { user, isLoading } = useAuthContext();
  return { user, isLoading };
}

export function useIsAuthenticated() {
  const { isAuthenticated, isLoading } = useAuthContext();
  return { isAuthenticated, isLoading };
}
