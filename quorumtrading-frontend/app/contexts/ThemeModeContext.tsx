'use client';

import { createContext, useCallback, useEffect, useState, type ReactNode } from 'react';

export type ThemeMode = 'dark' | 'light';
export type ThemeChoice = ThemeMode | 'system';

interface ThemeModeContextValue {
  mode: ThemeMode;
  choice: ThemeChoice;
  toggle: () => void;
  setChoice: (choice: ThemeChoice) => void;
}

const STORAGE_KEY = 'quorum-theme-mode';

export const ThemeModeContext = createContext<ThemeModeContextValue>({
  mode: 'dark',
  choice: 'dark',
  toggle: () => {},
  setChoice: () => {},
});

function getOsMode(): ThemeMode {
  return window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
}

function getStoredChoice(): ThemeChoice {
  const stored = localStorage.getItem(STORAGE_KEY);
  if (stored === 'dark' || stored === 'light' || stored === 'system') return stored;
  return 'dark';
}

function resolveMode(choice: ThemeChoice): ThemeMode {
  return choice === 'system' ? getOsMode() : choice;
}

export function ThemeModeProvider({ children }: { children: ReactNode }) {
  const [choice, setChoiceState] = useState<ThemeChoice>('dark');
  const [mode, setModeState] = useState<ThemeMode>('dark');

  // After hydration, read the actual user preference
  useEffect(() => {
    const stored = getStoredChoice();
    setChoiceState(stored);
    setModeState(resolveMode(stored));
  }, []);

  // Sync to localStorage and <html> data attribute
  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, choice);
    document.documentElement.setAttribute('data-theme', mode);
  }, [choice, mode]);

  // Listen for OS preference changes when choice is 'system'
  useEffect(() => {
    if (choice !== 'system') return;
    const mql = window.matchMedia('(prefers-color-scheme: light)');
    const handler = (e: MediaQueryListEvent) => {
      setModeState(e.matches ? 'light' : 'dark');
    };
    mql.addEventListener('change', handler);
    return () => mql.removeEventListener('change', handler);
  }, [choice]);

  const toggle = useCallback(() => {
    setChoiceState(prev => {
      const next: ThemeChoice = prev === 'dark' ? 'light' : prev === 'light' ? 'system' : 'dark';
      setModeState(resolveMode(next));
      return next;
    });
  }, []);

  const setChoice = useCallback((c: ThemeChoice) => {
    setChoiceState(c);
    setModeState(resolveMode(c));
  }, []);

  return (
    <ThemeModeContext.Provider value={{ mode, choice, toggle, setChoice }}>
      {children}
    </ThemeModeContext.Provider>
  );
}
