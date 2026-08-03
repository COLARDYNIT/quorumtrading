'use client';

import { useMemo } from 'react';
import { ThemeProvider } from 'styled-components';
import { Toaster } from 'react-hot-toast';
import { buildTheme } from './styles/theme';
import { GlobalStyles } from './styles/GlobalStyles';
import { QueryProvider } from './providers/QueryProvider';
import { AuthProvider } from './contexts/AuthContext';
import { ThemeModeProvider } from './contexts/ThemeModeContext';
import { useThemeMode } from './hooks/useThemeMode';
import { SessionExpiredProvider } from './components/SessionExpiredModal';
import { CookieConsent } from './components/shared/CookieConsent';
import { RateLimitProvider } from './components/RateLimitModal';
import { LlmProviderSetupGate } from './components/LlmProviderSetupGate';
import { registerA2UICatalog } from './lib/a2ui/registerCatalog';

// Register A2UI catalog components once on client
registerA2UICatalog();

function ThemedApp({ children }: { children: React.ReactNode }) {
  const { mode } = useThemeMode();
  const theme = useMemo(() => buildTheme(mode), [mode]);

  return (
    <ThemeProvider theme={theme}>
      <GlobalStyles />
      <RateLimitProvider>
      <SessionExpiredProvider>
        <Toaster
          position="top-right"
          toastOptions={{
            duration: 4000,
            style: {
              background: theme.colors.background,
              color: theme.colors.foreground,
              border: `1px solid ${theme.colors.foreground}`,
              fontFamily: theme.typography.fontFamily.mono,
              fontSize: '14px',
              boxShadow: `0 0 10px ${theme.colors.foreground}40`,
            },
            success: {
              iconTheme: {
                primary: theme.colors.success,
                secondary: theme.colors.background,
              },
            },
            error: {
              iconTheme: {
                primary: theme.colors.error,
                secondary: theme.colors.background,
              },
            },
          }}
        />
        {children}
        {/* Self-host first-run: full-screen LLM engine picker; no-op on SaaS (endpoint 404s). */}
        <LlmProviderSetupGate />
        <CookieConsent />
      </SessionExpiredProvider>
      </RateLimitProvider>
    </ThemeProvider>
  );
}

export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <QueryProvider>
      <AuthProvider>
        <ThemeModeProvider>
          <ThemedApp>{children}</ThemedApp>
        </ThemeModeProvider>
      </AuthProvider>
    </QueryProvider>
  );
}
