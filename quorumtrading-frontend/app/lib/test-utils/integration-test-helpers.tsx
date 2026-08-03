import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

/**
 * Creates a fresh QueryClient for integration tests
 * with conservative retry/stale settings for predictable test behavior
 */
export function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false, // Don't retry in tests
        staleTime: 0, // Always refetch
        gcTime: 0, // Clear cache immediately
      },
      mutations: {
        retry: false,
      },
    },
  });
}

/**
 * Wrapper component for integration tests that need TanStack Query
 *
 * Usage:
 *   const component = await mount(
 *     <QueryWrapper>
 *       <YourComponent />
 *     </QueryWrapper>
 *   );
 */
export function QueryWrapper({ children }: { children: React.ReactNode }) {
  const queryClient = createTestQueryClient();

  return (
    <QueryClientProvider client={queryClient}>
      {children}
    </QueryClientProvider>
  );
}
