'use client';

import { useEffect, useState, useCallback, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { queryKeys } from '../api/hooks';
import { useStreamMessage } from './hooks';
import type { BackfillProgressMessage, BackfillStatus, GapProgressDTO } from '../types/dataAvailability';

// Minimum interval between progress-triggered invalidations (ms)
const PROGRESS_INVALIDATION_THROTTLE_MS = 1000;

/**
 * Backfill progress state for a specific job.
 */
export interface BackfillProgress {
  /** Job ID (UUID) */
  jobId: string;
  /** Current job status */
  status: BackfillStatus;
  /** Progress percentage (0-100) */
  progressPercentage: number | null;
  /** Human-readable message */
  message: string;
  /** Total records to backfill */
  totalRecords: number | null;
  /** Records processed so far */
  processedRecords: number | null;
  /** Last update timestamp */
  updatedAt: string;
  /** The gap that was just processed (if available) */
  currentGap: GapProgressDTO | null;
  /** All gaps processed so far with status */
  gapDetails: GapProgressDTO[];
}

/**
 * Hook to subscribe to backfill progress updates via WebSocket.
 * Listens for BACKFILL_PROGRESS messages and updates state accordingly.
 *
 * Features:
 * - Real-time progress updates via WebSocket
 * - Tracks progress for a specific job or all jobs
 * - Automatically invalidates data coverage queries on completion
 * - Provides helper to check if job is active
 *
 * @param jobId Optional job ID to track specific job progress
 * @returns Progress state and helper functions
 *
 * @example
 * ```tsx
 * // Track specific job
 * const { progress, isJobActive } = useBackfillProgress(jobId);
 *
 * // Track all jobs
 * const { allJobs } = useBackfillProgress();
 * ```
 */
export function useBackfillProgress(jobId?: string) {
  const queryClient = useQueryClient();
  const [progress, setProgress] = useState<BackfillProgress | null>(null);
  const [allJobs, setAllJobs] = useState<Map<string, BackfillProgress>>(new Map());
  // Track last invalidation time per job to throttle progress-based invalidations
  const lastInvalidationRef = useRef<Map<string, number>>(new Map());

  /**
   * Handle incoming backfill progress messages.
   * Updates state and invalidates queries on completion.
   */
  const handleMessage = useCallback((message: any) => {
    console.log('[useBackfillProgress] Received message:', message);

    // Type guard to ensure message is BackfillProgressMessage
    if (message.type !== 'BACKFILL_PROGRESS') {
      console.log('[useBackfillProgress] Ignoring non-BACKFILL_PROGRESS message');
      return;
    }

    console.log('[useBackfillProgress] Processing BACKFILL_PROGRESS message');
    const backfillMessage = message as BackfillProgressMessage;

    const update: BackfillProgress = {
      jobId: backfillMessage.jobId,
      status: backfillMessage.status,
      progressPercentage: backfillMessage.progressPercentage,
      message: backfillMessage.message,
      totalRecords: backfillMessage.totalRecords,
      processedRecords: backfillMessage.processedRecords,
      updatedAt: backfillMessage.timestamp,
      currentGap: backfillMessage.currentGap ?? null,
      gapDetails: backfillMessage.gapDetails ?? [],
    };

    // Update all jobs map
    setAllJobs(prev => {
      const next = new Map(prev);
      next.set(backfillMessage.jobId, update);
      console.log('[useBackfillProgress] Updated allJobs map, size:', next.size);
      return next;
    });

    // Update specific job if watching
    if (jobId && backfillMessage.jobId === jobId) {
      setProgress(update);
    }

    // Determine whether to invalidate queries
    // Always invalidate on COMPLETED or FAILED
    // Throttle IN_PROGRESS invalidations to avoid too many API calls
    const shouldInvalidate = (() => {
      if (backfillMessage.status === 'COMPLETED' || backfillMessage.status === 'FAILED') {
        return true;
      }

      if (backfillMessage.status === 'IN_PROGRESS') {
        const now = Date.now();
        const lastInvalidation = lastInvalidationRef.current.get(backfillMessage.jobId) || 0;
        const timeSinceLastInvalidation = now - lastInvalidation;

        // Throttle progress invalidations to every 5 seconds
        if (timeSinceLastInvalidation >= PROGRESS_INVALIDATION_THROTTLE_MS) {
          lastInvalidationRef.current.set(backfillMessage.jobId, now);
          return true;
        }
      }

      return false;
    })();

    if (shouldInvalidate) {
      console.log('[useBackfillProgress] Invalidating data coverage queries for status:', backfillMessage.status);
      // Invalidate all coverage queries since backfill affects data availability
      queryClient.invalidateQueries({
        queryKey: queryKeys.dataAvailability,
      });

      // Invalidate specific job status query
      queryClient.invalidateQueries({
        queryKey: queryKeys.backfillStatus(backfillMessage.jobId),
      });
    }
  }, [jobId, queryClient]);

  // Subscribe to BACKFILL_PROGRESS messages via WebSocket
  useStreamMessage('BACKFILL_PROGRESS' as any, handleMessage, [handleMessage]);

  /**
   * Check if a specific job is currently active (PENDING or IN_PROGRESS).
   */
  const isJobActive = useCallback((id: string): boolean => {
    const job = allJobs.get(id);
    return job !== undefined && (
      job.status === 'PENDING' ||
      job.status === 'IN_PROGRESS'
    );
  }, [allJobs]);

  return {
    /** Progress for the watched job (if jobId provided) */
    progress: jobId ? progress : null,
    /** All tracked jobs across all subscriptions */
    allJobs: Array.from(allJobs.values()),
    /** Check if a job is active (PENDING or IN_PROGRESS) */
    isJobActive,
  };
}
