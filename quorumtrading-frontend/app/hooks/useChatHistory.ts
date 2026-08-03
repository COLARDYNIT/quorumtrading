'use client';

import { useState, useCallback, useEffect, useRef } from 'react';
import { listChatSessions, ChatSessionSummary } from '@/app/lib/api/chat';

export function useChatHistory() {
  const [sessions, setSessions] = useState<ChatSessionSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const mountedRef = useRef(true);

  const fetchSessions = useCallback(async () => {
    try {
      const data = await listChatSessions();
      if (mountedRef.current) {
        setSessions(data);
      }
    } catch {
      // Silently fail — sidebar just shows empty
    } finally {
      if (mountedRef.current) {
        setIsLoading(false);
      }
    }
  }, []);

  // Fetch on mount
  useEffect(() => {
    mountedRef.current = true;
    fetchSessions();
    return () => { mountedRef.current = false; };
  }, [fetchSessions]);

  return {
    sessions,
    isLoading,
    refreshSessions: fetchSessions,
  };
}
