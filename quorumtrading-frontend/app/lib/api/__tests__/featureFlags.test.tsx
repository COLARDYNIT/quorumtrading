/**
 * Feature availability, and which way it fails.
 *
 * The fallback here points the opposite way to `useInstanceInfo`'s, and that is deliberate. Hiding
 * navigation is a courtesy: the REST gate and the MCP guards are what actually enforce a disabled
 * feature, so guessing "on" when the endpoint is unreachable costs a menu entry that answers 403 —
 * whereas guessing "off" would hide working features from every user for the duration of one
 * failed request. The enforcement layer means we can afford to be generous here; on the instance
 * descriptor, where nothing else enforces the answer, we cannot.
 */
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import { useFeatureEnabled, useFeatures, useMarketResearch } from '../hooks';
import { apiClient } from '../client';

jest.mock('../client', () => ({
  apiClient: {
    features: { get: jest.fn() },
    marketResearch: { list: jest.fn() },
  },
}));

function wrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

describe('useFeatures', () => {
  beforeEach(() => jest.clearAllMocks());

  it('reports what the API says', async () => {
    (apiClient.features.get as jest.Mock).mockResolvedValue({
      features: { challenges: false, 'market-research': true },
    });

    const { result } = renderHook(() => useFeatures(), { wrapper });

    await waitFor(() => expect(result.current.data).toBeDefined());
    expect(result.current.data?.features).toEqual({
      challenges: false,
      'market-research': true,
    });
  });

  it('keeps features visible when availability cannot be fetched', async () => {
    (apiClient.features.get as jest.Mock).mockRejectedValue(new Error('network down'));

    const { result } = renderHook(() => useFeatures(), { wrapper });

    await waitFor(() => expect(result.current.data).toBeDefined());
    expect(result.current.data?.features).toMatchObject({
      challenges: true,
      'market-research': true,
    });
  });
});

describe('useFeatureEnabled', () => {
  beforeEach(() => jest.clearAllMocks());

  it('returns false only once the API has actually said so', async () => {
    (apiClient.features.get as jest.Mock).mockResolvedValue({
      features: { challenges: false, 'market-research': true },
    });

    const { result } = renderHook(() => useFeatureEnabled('challenges'), { wrapper });

    // Optimistic while loading: navigation must not flicker out and back in on every page load.
    expect(result.current).toBe(true);

    await waitFor(() => expect(result.current).toBe(false));
  });

  it('treats a feature the backend never mentioned as available', async () => {
    (apiClient.features.get as jest.Mock).mockResolvedValue({ features: {} });

    const { result } = renderHook(() => useFeatureEnabled('market-research'), { wrapper });

    await waitFor(() => expect(result.current).toBe(true));
  });
});

describe('useMarketResearch', () => {
  beforeEach(() => jest.clearAllMocks());

  it('does not poll the API while the feature is switched off', async () => {
    // The dashboard polls this every 30 seconds. Left unguarded on an instance with the flag off,
    // it would 403 forever in the background — noise that buries real failures in the network tab.
    (apiClient.features.get as jest.Mock).mockResolvedValue({
      features: { 'market-research': false },
    });

    const { result } = renderHook(() => useMarketResearch(), { wrapper });

    await waitFor(() => expect(result.current.fetchStatus).toBe('idle'));
    expect(apiClient.marketResearch.list).not.toHaveBeenCalled();
  });

  it('fetches once the feature is on', async () => {
    (apiClient.features.get as jest.Mock).mockResolvedValue({
      features: { 'market-research': true },
    });
    (apiClient.marketResearch.list as jest.Mock).mockResolvedValue([]);

    const { result } = renderHook(() => useMarketResearch(), { wrapper });

    await waitFor(() => expect(result.current.data).toBeDefined());
    expect(apiClient.marketResearch.list).toHaveBeenCalled();
  });
});
