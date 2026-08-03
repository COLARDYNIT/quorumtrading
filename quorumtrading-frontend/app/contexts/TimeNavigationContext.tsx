'use client';

import { createContext, useContext, useState, useCallback, ReactNode } from 'react';

interface TimeNavigationState {
  mode: 'live' | 'historical';
  selectedTimestamp: string | null;
  activeConsensusId: string | null;
}

interface TimeNavigationContextValue {
  state: TimeNavigationState;
  jumpToTimestamp: (timestamp: string) => void;
  returnToLive: () => void;
  setActiveConsensus: (consensusId: string | null) => void;
}

const TimeNavigationContext = createContext<TimeNavigationContextValue | undefined>(undefined);

export function TimeNavigationProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<TimeNavigationState>({
    mode: 'live',
    selectedTimestamp: null,
    activeConsensusId: null,
  });

  const jumpToTimestamp = useCallback((timestamp: string) => {
    setState({
      mode: 'historical',
      selectedTimestamp: timestamp,
      activeConsensusId: null,
    });
  }, []);

  const returnToLive = useCallback(() => {
    setState({
      mode: 'live',
      selectedTimestamp: null,
      activeConsensusId: null,
    });
  }, []);

  const setActiveConsensus = useCallback((consensusId: string | null) => {
    setState(prev => ({
      ...prev,
      activeConsensusId: consensusId,
    }));
  }, []);

  return (
    <TimeNavigationContext.Provider
      value={{
        state,
        jumpToTimestamp,
        returnToLive,
        setActiveConsensus,
      }}
    >
      {children}
    </TimeNavigationContext.Provider>
  );
}

export function useTimeNavigation() {
  const context = useContext(TimeNavigationContext);
  if (!context) {
    throw new Error('useTimeNavigation must be used within TimeNavigationProvider');
  }
  return context;
}
