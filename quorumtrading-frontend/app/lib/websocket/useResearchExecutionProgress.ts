'use client';

import { useEffect, useState, useCallback, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { getWebSocketManager } from './manager';
import { useStreamMessage } from './hooks';
import { queryKeys } from '../api/hooks';
import type { ResearchExecutionProgressMessage } from '../types/websocket';

/**
 * Completed agent information.
 */
export interface CompletedAgent {
  type: string;
  durationMs: number;
}

/**
 * Research execution progress state.
 */
export interface ResearchExecutionProgress {
  /** Whether execution is currently in progress */
  isExecuting: boolean;
  /** Current execution ID (UUID) */
  executionId: string | null;
  /** Current execution stage */
  stage: string | null;
  /** Currently executing agent type */
  currentAgent: string | null;
  /** List of completed agents with their durations */
  completedAgents: CompletedAgent[];
  /** Elapsed time in milliseconds since execution started */
  elapsedTime: number;
  /** Execution outcome (STRATEGY_CREATED, NO_OPPORTUNITY, FAILED) */
  outcome: string | null;
  /** Created strategy ID if outcome is STRATEGY_CREATED */
  strategyId: string | null;
  /** Error message if execution failed */
  error: string | null;
  /** Whether WebSocket subscription is ready to receive updates */
  isSubscribed: boolean;
}

const initialState: ResearchExecutionProgress = {
  isExecuting: false,
  executionId: null,
  stage: null,
  currentAgent: null,
  completedAgents: [],
  elapsedTime: 0,
  outcome: null,
  strategyId: null,
  error: null,
  isSubscribed: false,
};

/**
 * Hook to subscribe to research execution progress updates via WebSocket.
 * Tracks the execution lifecycle of a research item including agent progress.
 *
 * Features:
 * - Real-time progress updates via WebSocket
 * - Tracks agent execution order and durations
 * - Calculates elapsed time since execution started
 * - Automatically cleans up subscription on unmount
 *
 * @param researchId Research ID to track (null to disable tracking)
 * @returns Research execution progress state
 *
 * @example
 * ```tsx
 * const {
 *   isExecuting,
 *   currentAgent,
 *   completedAgents,
 *   elapsedTime,
 *   outcome,
 *   error,
 * } = useResearchExecutionProgress(researchId);
 *
 * if (isExecuting) {
 *   return <div>Running {currentAgent}... ({elapsedTime}ms elapsed)</div>;
 * }
 * ```
 */
export function useResearchExecutionProgress(
  researchId: string | null
): ResearchExecutionProgress {
  const queryClient = useQueryClient();
  const wsManager = getWebSocketManager();
  const [progress, setProgress] = useState<ResearchExecutionProgress>(initialState);
  const startTimeRef = useRef<number | null>(null);
  const elapsedIntervalRef = useRef<NodeJS.Timeout | null>(null);

  // Subscribe to research updates when researchId is provided
  useEffect(() => {
    if (!researchId) {
      // Reset state when researchId is cleared
      setProgress(initialState);
      startTimeRef.current = null;
      return;
    }

    let isMounted = true;

    // Ensure WebSocket is connected before subscribing
    wsManager.connect().then(() => {
      wsManager.subscribeToResearch(researchId);

      // Give backend time to register the subscription before marking as ready
      // This prevents race conditions where execute is called before subscription is registered
      setTimeout(() => {
        if (isMounted) {
          setProgress(prev => ({ ...prev, isSubscribed: true }));
        }
      }, 100);
    }).catch(error => {
      console.error('Failed to connect WebSocket for research progress:', error);
    });

    return () => {
      isMounted = false;
      setProgress(prev => ({ ...prev, isSubscribed: false }));
      wsManager.unsubscribeFromResearch(researchId);
    };
  }, [researchId, wsManager]);

  // Clean up elapsed time interval
  useEffect(() => {
    return () => {
      if (elapsedIntervalRef.current) {
        clearInterval(elapsedIntervalRef.current);
        elapsedIntervalRef.current = null;
      }
    };
  }, []);

  /**
   * Handle incoming research execution progress messages.
   */
  const handleMessage = useCallback(
    (message: ResearchExecutionProgressMessage) => {
      console.log('[ResearchProgress] Received message:', message.stage, 'for research:', message.researchId);

      // Only process messages for the current research
      if (!researchId || message.researchId !== researchId) {
        console.log('[ResearchProgress] Ignoring message - researchId mismatch', {
          expected: researchId,
          received: message.researchId,
        });
        return;
      }

      console.log('[ResearchProgress] Processing message:', message.stage);

      const { stage, executionId, agentType, durationMs, outcome, strategyId, errorMessage } =
        message;

      setProgress((prev) => {
        // Handle STARTED stage
        if (stage === 'STARTED') {
          startTimeRef.current = Date.now();

          // Start elapsed time interval
          if (elapsedIntervalRef.current) {
            clearInterval(elapsedIntervalRef.current);
          }
          elapsedIntervalRef.current = setInterval(() => {
            if (startTimeRef.current) {
              setProgress((p) => ({
                ...p,
                elapsedTime: Date.now() - startTimeRef.current!,
              }));
            }
          }, 100);

          return {
            ...initialState,
            isExecuting: true,
            executionId,
            stage,
            elapsedTime: 0,
          };
        }

        // Handle AGENT_STARTED stage
        if (stage === 'AGENT_STARTED') {
          return {
            ...prev,
            stage,
            currentAgent: agentType || null,
          };
        }

        // Handle AGENT_COMPLETED stage
        if (stage === 'AGENT_COMPLETED' && agentType && durationMs !== undefined) {
          const completedAgent: CompletedAgent = {
            type: agentType,
            durationMs,
          };

          return {
            ...prev,
            stage,
            currentAgent: null,
            completedAgents: [...prev.completedAgents, completedAgent],
          };
        }

        // Handle COMPLETED stage
        if (stage === 'COMPLETED') {
          // Stop elapsed time interval
          if (elapsedIntervalRef.current) {
            clearInterval(elapsedIntervalRef.current);
            elapsedIntervalRef.current = null;
          }

          const finalElapsedTime = startTimeRef.current
            ? Date.now() - startTimeRef.current
            : prev.elapsedTime;

          // Invalidate market research queries to refresh data
          queryClient.invalidateQueries({ queryKey: queryKeys.marketResearch });
          if (researchId) {
            queryClient.invalidateQueries({ queryKey: queryKeys.marketResearchDetails(researchId) });
          }

          return {
            ...prev,
            isExecuting: false,
            stage,
            currentAgent: null,
            elapsedTime: finalElapsedTime,
            outcome: outcome || null,
            strategyId: strategyId || null,
          };
        }

        // Handle FAILED stage
        if (stage === 'FAILED') {
          // Stop elapsed time interval
          if (elapsedIntervalRef.current) {
            clearInterval(elapsedIntervalRef.current);
            elapsedIntervalRef.current = null;
          }

          const finalElapsedTime = startTimeRef.current
            ? Date.now() - startTimeRef.current
            : prev.elapsedTime;

          // Invalidate market research queries to refresh data
          queryClient.invalidateQueries({ queryKey: queryKeys.marketResearch });
          if (researchId) {
            queryClient.invalidateQueries({ queryKey: queryKeys.marketResearchDetails(researchId) });
          }

          return {
            ...prev,
            isExecuting: false,
            stage,
            currentAgent: null,
            elapsedTime: finalElapsedTime,
            outcome: 'FAILED',
            error: errorMessage || 'Unknown error',
          };
        }

        // Unknown stage, just update the stage
        return {
          ...prev,
          stage,
        };
      });
    },
    [researchId, queryClient]
  );

  // Subscribe to RESEARCH_EXECUTION_PROGRESS messages via WebSocket
  useStreamMessage('RESEARCH_EXECUTION_PROGRESS', handleMessage, [handleMessage]);

  return progress;
}
