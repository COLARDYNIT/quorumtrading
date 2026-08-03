'use client';

import { useState, useEffect, useCallback } from 'react';

const STORAGE_KEY = 'quorumtrading:dashboard:layout';

interface DashboardLayout {
  statsVisible: boolean;
  sidebarVisible: boolean;
  chatMaximized: boolean;
}

const DEFAULTS: DashboardLayout = {
  statsVisible: true,
  sidebarVisible: true,
  chatMaximized: false,
};

function loadLayout(): DashboardLayout {
  if (typeof window === 'undefined') return DEFAULTS;
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return DEFAULTS;
    return { ...DEFAULTS, ...JSON.parse(raw) };
  } catch {
    return DEFAULTS;
  }
}

function saveLayout(layout: DashboardLayout) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(layout));
  } catch { /* quota exceeded — ignore */ }
}

export function useDashboardLayout() {
  const [layout, setLayout] = useState<DashboardLayout>(DEFAULTS);
  const [hydrated, setHydrated] = useState(false);

  // Hydrate from localStorage after mount (SSR-safe)
  useEffect(() => {
    setLayout(loadLayout());
    setHydrated(true);
  }, []);

  const persist = useCallback((next: DashboardLayout) => {
    setLayout(next);
    saveLayout(next);
  }, []);

  const toggleMaximize = useCallback(() => {
    setLayout(prev => {
      const next = prev.chatMaximized
        ? { statsVisible: true, sidebarVisible: true, chatMaximized: false }
        : { statsVisible: false, sidebarVisible: false, chatMaximized: true };
      saveLayout(next);
      return next;
    });
  }, []);

  const toggleStats = useCallback(() => {
    setLayout(prev => {
      const next = { ...prev, statsVisible: !prev.statsVisible };
      saveLayout(next);
      return next;
    });
  }, []);

  const resetLayout = useCallback(() => {
    persist(DEFAULTS);
    try { localStorage.removeItem(STORAGE_KEY); } catch { /* ignore */ }
  }, [persist]);

  return {
    statsVisible: hydrated ? layout.statsVisible : DEFAULTS.statsVisible,
    sidebarVisible: hydrated ? layout.sidebarVisible : DEFAULTS.sidebarVisible,
    chatMaximized: hydrated ? layout.chatMaximized : DEFAULTS.chatMaximized,
    toggleMaximize,
    toggleStats,
    resetLayout,
  };
}
