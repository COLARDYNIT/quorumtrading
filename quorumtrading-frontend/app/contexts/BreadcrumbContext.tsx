'use client';

import { createContext, useContext, useState, useCallback, ReactNode } from 'react';

export interface BreadcrumbItem {
  label: string;
  path: string;
}

interface BreadcrumbContextType {
  customBreadcrumbs: BreadcrumbItem[] | null;
  setCustomBreadcrumbs: (breadcrumbs: BreadcrumbItem[] | null) => void;
  pageActions: ReactNode | null;
  setPageActions: (actions: ReactNode | null) => void;
}

const BreadcrumbContext = createContext<BreadcrumbContextType | null>(null);

export function BreadcrumbProvider({ children }: { children: ReactNode }) {
  const [customBreadcrumbs, setCustomBreadcrumbsState] = useState<BreadcrumbItem[] | null>(null);
  const [pageActions, setPageActionsState] = useState<ReactNode | null>(null);

  const setCustomBreadcrumbs = useCallback((breadcrumbs: BreadcrumbItem[] | null) => {
    setCustomBreadcrumbsState(breadcrumbs);
  }, []);

  const setPageActions = useCallback((actions: ReactNode | null) => {
    setPageActionsState(actions);
  }, []);

  return (
    <BreadcrumbContext.Provider value={{ customBreadcrumbs, setCustomBreadcrumbs, pageActions, setPageActions }}>
      {children}
    </BreadcrumbContext.Provider>
  );
}

export function useBreadcrumb() {
  const context = useContext(BreadcrumbContext);
  if (!context) {
    throw new Error('useBreadcrumb must be used within a BreadcrumbProvider');
  }
  return context;
}

export function useBreadcrumbOptional() {
  return useContext(BreadcrumbContext);
}
