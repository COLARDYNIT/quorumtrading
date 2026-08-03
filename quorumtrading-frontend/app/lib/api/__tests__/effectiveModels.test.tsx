import React from 'react';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { apiClient } from '../client';
import { useEffectiveModels, useFallbackConfig } from '../hooks';
import {
  TRADING_CONTEXTS,
  effectiveModelFor,
  splitModelId,
  describeFallbackKind,
} from '@/app/lib/types/config';

/**
 * The settings page used to render `TRADING_CONTEXTS[].defaultModel` — a hardcoded frontend
 * constant fetched from nowhere. Being an unowned duplicate of the backend's defaults, it drifted:
 * it went on advertising `google/gemini-2.0-flash-001` as the paper-trading default long after the
 * backend replaced it and marked it sunset.
 *
 * These tests pin the replacement: the constant carries no model at all, and the model comes off
 * the wire from the endpoint that computes what the ACTIVE engine will actually run.
 */

global.fetch = jest.fn();

const mockResponse = (data: unknown) => ({
  ok: true,
  status: 200,
  headers: { get: (name: string) => (name.toLowerCase() === 'content-type' ? 'application/json' : null) },
  json: async () => data,
});

function wrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

describe('effective models', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('TRADING_CONTEXTS carries no defaultModel — it cannot drift if it does not exist', () => {
    expect(TRADING_CONTEXTS.length).toBeGreaterThan(0);
    for (const context of TRADING_CONTEXTS) {
      expect(context).not.toHaveProperty('defaultModel');
      // What remains is display metadata only: nothing here can disagree with the backend.
      expect(context).toHaveProperty('key');
      expect(context).toHaveProperty('label');
      expect(context).toHaveProperty('fieldName');
    }
  });

  it('fetches the effective model per context from the backend', async () => {
    const payload = {
      models: [
        {
          context: 'BACKTESTING',
          requested: 'mistralai/mistral-small-3.2-24b-instruct',
          effective: 'llama3.2',
          source: 'system_default',
          decidedByTier: 'SYSTEM_GLOBAL',
          engine: 'LOCAL_HTTP',
          substitutionReason: 'the active engine does not advertise the system default',
          effort: null,
        },
      ],
    };
    (global.fetch as jest.Mock).mockResolvedValueOnce(mockResponse(payload));

    const result = await apiClient.config.getEffectiveModels();

    expect(global.fetch).toHaveBeenCalledWith(
      'http://localhost:8085/api/v1/config/effective-models',
      expect.anything()
    );
    // The page renders `effective`, not `requested` — reporting the configured model when a
    // different one runs is the defect this endpoint exists to remove.
    expect(result.models[0].effective).toBe('llama3.2');
    expect(result.models[0].requested).toBe('mistralai/mistral-small-3.2-24b-instruct');
    expect(result.models[0].engine).toBe('LOCAL_HTTP');
  });

  it('exposes the effective models through a hook the page can render', async () => {
    (global.fetch as jest.Mock).mockResolvedValue(
      mockResponse({
        models: [
          {
            context: 'RESEARCH',
            requested: 'llama3.2',
            effective: 'llama3.2',
            source: 'user_override',
            decidedByTier: 'USER_GLOBAL',
            engine: 'LOCAL_HTTP',
            substitutionReason: null,
            effort: 'HIGH',
          },
        ],
      })
    );

    const { result } = renderHook(() => useEffectiveModels(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.models[0].effective).toBe('llama3.2');
    expect(result.current.data?.models[0].effort).toBe('HIGH');
  });

  it('an unservable model comes back with a null effective — a diagnosis, not an omission', async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce(
      mockResponse({
        models: [
          {
            context: 'LIVE_TRADING',
            requested: 'anthropic/claude-sonnet-4.6',
            effective: null,
            source: 'user_override',
            decidedByTier: 'USER_GLOBAL',
            engine: 'LOCAL_HTTP',
            substitutionReason: 'the active engine does not advertise this model',
            effort: null,
          },
        ],
      })
    );

    const result = await apiClient.config.getEffectiveModels();

    expect(result.models[0].effective).toBeNull();
    expect(result.models[0].substitutionReason).toContain('does not advertise');
  });

  describe('effectiveModelFor', () => {
    const response = {
      models: [
        {
          context: 'BACKTESTING' as const,
          requested: 'mistralai/mistral-small-3.2-24b-instruct',
          effective: 'llama3.2',
          source: 'system_default',
          decidedByTier: 'SYSTEM_GLOBAL',
          engine: 'LOCAL_HTTP',
          substitutionReason: 'substituted',
          effort: null,
        },
        {
          context: 'LIVE_TRADING' as const,
          requested: 'anthropic/claude-sonnet-4.6',
          effective: null,
          source: 'user_override',
          decidedByTier: 'USER_GLOBAL',
          engine: 'LOCAL_HTTP',
          substitutionReason: 'not advertised',
          effort: null,
        },
      ],
    };

    it('returns what will RUN, never what was configured', () => {
      expect(effectiveModelFor(response, 'BACKTESTING')).toBe('llama3.2');
    });

    it('returns null when the engine will not serve the configured model', () => {
      expect(effectiveModelFor(response, 'LIVE_TRADING')).toBeNull();
    });

    it('returns null for a context the response does not mention', () => {
      expect(effectiveModelFor(response, 'CHAT')).toBeNull();
    });

    it('returns null before the fetch lands, so a page renders an em dash rather than a guess', () => {
      expect(effectiveModelFor(undefined, 'BACKTESTING')).toBeNull();
    });
  });

  describe('display helpers', () => {
    it('a bare local model name has no vendor — that is normal, not missing data', () => {
      expect(splitModelId('llama3.2')).toEqual({ vendor: null, name: 'llama3.2' });
      expect(splitModelId('—')).toEqual({ vendor: null, name: '—' });
    });

    it('a hosted id splits into vendor and name', () => {
      expect(splitModelId('anthropic/claude-sonnet-4.6'))
        .toEqual({ vendor: 'anthropic', name: 'claude-sonnet-4.6' });
      expect(splitModelId('openrouter/anthropic/claude'))
        .toEqual({ vendor: 'openrouter', name: 'anthropic/claude' });
    });

    it('each engine gets its own word for what the fallback list is', () => {
      expect(describeFallbackKind('MODEL_CHAIN')).toBe('model chain');
      expect(describeFallbackKind('TOOL_CHAIN')).toBe('installed tools');
      expect(describeFallbackKind('SERVER_DEFAULT')).toBe('no chain');
      expect(describeFallbackKind('NONE')).toBe('no chain');
    });
  });

  it('the fallback config describes the ACTIVE engine, chain kind included', async () => {
    (global.fetch as jest.Mock).mockResolvedValue(
      mockResponse({
        engine: 'LOCAL_HTTP',
        kind: 'SERVER_DEFAULT',
        fallbackChain: [],
        description: 'There is no fallback chain.',
        crossEngineFailover: false,
      })
    );

    const { result } = renderHook(() => useFallbackConfig(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    // An empty chain is the correct answer for a local server, and the page must show the
    // explanation rather than another engine's model list.
    expect(result.current.data?.kind).toBe('SERVER_DEFAULT');
    expect(result.current.data?.fallbackChain).toEqual([]);
    expect(result.current.data?.crossEngineFailover).toBe(false);
  });
});
