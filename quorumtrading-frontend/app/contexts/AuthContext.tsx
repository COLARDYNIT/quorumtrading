'use client';

import { createContext, useContext, useEffect, useRef, useState, useCallback, ReactNode } from 'react';
import { User } from '../types/user';
import {
  fetchCurrentUser,
  logout as apiLogout,
  getLoginUrl,
  getDevLoginUrl,
  getFutLoginUrl,
  fetchAuthProviders,
  SIGNIN_PICKER_FLAG,
  AuthProvider as AuthProviderType
} from '../lib/api/auth';
import { resetSessionExpiryState, notifySessionExpired } from '../lib/api/sessionExpiry';

interface AuthContextType {
  user: User | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  providers: AuthProviderType[];
  providersLoading: boolean;
  devLoginEnabled: boolean;
  login: (provider: string) => void;
  devLogin: () => void;
  futLogin: () => void;
  logout: () => Promise<void>;
  refreshUser: () => Promise<User | null>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [providers, setProviders] = useState<AuthProviderType[]>([]);
  const [providersLoading, setProvidersLoading] = useState(true);
  const [devLoginEnabled, setDevLoginEnabled] = useState(false);

  const refreshUser = useCallback(async (): Promise<User | null> => {
    setIsLoading(true);
    const userData = await fetchCurrentUser();
    setUser(userData);
    // Reset session expiry notification if user is authenticated
    if (userData) {
      resetSessionExpiryState();
    }
    setIsLoading(false);
    return userData;
  }, []);

  useEffect(() => {
    const initAuth = async () => {
      // Fetch providers and user in parallel
      const [, result] = await Promise.all([
        refreshUser(),
        fetchAuthProviders()
      ]);
      setProviders(result.providers);
      setDevLoginEnabled(result.devLoginEnabled);
      setProvidersLoading(false);
    };
    initAuth();
  }, []);

  // Heartbeat: periodically check session validity so idle users get notified
  const prevUserRef = useRef<User | null>(null);
  useEffect(() => {
    // Keep ref in sync with user state
    prevUserRef.current = user;
  }, [user]);

  useEffect(() => {
    const interval = setInterval(async () => {
      const prev = prevUserRef.current;
      // Only poll if we believe the user is logged in
      if (!prev) return;

      const current = await fetchCurrentUser();
      if (current) {
        // Session still valid — update user silently (e.g. profile changes)
        setUser(current);
      } else {
        // Session expired while idle
        setUser(null);
        notifySessionExpired();
      }
    }, 60_000);

    return () => clearInterval(interval);
  }, []);

  const login = useCallback((provider: string) => {
    window.location.href = getLoginUrl(provider);
  }, []);

  const devLogin = useCallback(() => {
    window.location.href = getDevLoginUrl();
  }, []);

  const futLogin = useCallback(() => {
    window.location.href = getFutLoginUrl();
  }, []);

  const logout = useCallback(async () => {
    await apiLogout();
    // A standalone instance auto-enters on the signin page; without this one-shot flag
    // an explicit logout would immediately sign the user back in.
    if (typeof window !== 'undefined') {
      sessionStorage.setItem(SIGNIN_PICKER_FLAG, '1');
    }
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider value={{
      user,
      isLoading,
      isAuthenticated: !!user,
      providers,
      providersLoading,
      devLoginEnabled,
      login,
      devLogin,
      futLogin,
      logout,
      refreshUser,
    }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuthContext() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuthContext must be used within AuthProvider');
  }
  return context;
}
