/**
 * The instance descriptor's fail-safe shape.
 *
 * When /api/v1/instance cannot be reached, useInstanceInfo resolves to a hosted-looking default so
 * the index page never shows an error state. That default now has to answer one more question —
 * whether the DCA scheduler runs here — and the safe answer is no.
 *
 * The asymmetry matters: warning about a scheduler that turns out to be enabled costs the user a
 * redundant sentence, while promising monthly execution on an instance where nothing will run it
 * means their money silently does not move. Not knowing must fall on the cautious side.
 */
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import { useInstanceInfo } from '../hooks';
import { apiClient } from '../client';

jest.mock('../client', () => ({
  apiClient: { instance: { get: jest.fn() } },
}));

function wrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

describe('useInstanceInfo', () => {
  beforeEach(() => jest.clearAllMocks());

  it('assumes automatic investing is off when the instance cannot be reached', async () => {
    (apiClient.instance.get as jest.Mock).mockRejectedValue(new Error('network down'));

    const { result } = renderHook(() => useInstanceInfo(), { wrapper });

    await waitFor(() => expect(result.current.data).toBeDefined());
    expect(result.current.data).toMatchObject({
      edition: 'saas',
      automaticInvestmentsEnabled: false,
    });
  });

  it('reports what the instance actually says when it responds', async () => {
    (apiClient.instance.get as jest.Mock).mockResolvedValue({
      edition: 'standalone',
      brokers: ['ALPACA'],
      automaticInvestmentsEnabled: true,
    });

    const { result } = renderHook(() => useInstanceInfo(), { wrapper });

    await waitFor(() => expect(result.current.data).toBeDefined());
    expect(result.current.data).toMatchObject({ automaticInvestmentsEnabled: true });
  });
});
