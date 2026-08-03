import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/app/lib/api/client';

interface MarketDataQueryTestProps {
  from: string;
  to: string;
  symbol?: string;
}

export function MarketDataQueryTest({ from, to, symbol }: MarketDataQueryTestProps) {
  const { data, isLoading, error } = useQuery({
    queryKey: ['market-data', from, to, symbol],
    queryFn: () => apiClient.marketData.query({ from, to, symbol }),
  });

  if (isLoading) return <div data-testid="loading">Loading...</div>;
  if (error) return <div data-testid="error">Error: {(error as Error).message}</div>;

  return (
    <div data-testid="market-data">
      <div data-testid="events-count">{data?.events?.length || 0}</div>
      <div data-testid="total-count">{data?.total || 0}</div>
    </div>
  );
}
