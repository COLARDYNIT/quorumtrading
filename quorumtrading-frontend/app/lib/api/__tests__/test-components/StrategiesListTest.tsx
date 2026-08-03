import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/app/lib/api/client';

export function StrategiesListTest() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['strategies'],
    queryFn: () => apiClient.strategies.list(),
  });

  if (isLoading) return <div data-testid="loading">Loading...</div>;
  if (error) return <div data-testid="error">Error: {(error as Error).message}</div>;

  return (
    <div data-testid="strategies-list">
      <div data-testid="count">{data?.length || 0}</div>
      {data?.map((strategy) => (
        <div key={strategy.version} data-testid={`strategy-${strategy.version}`}>
          {strategy.version}
        </div>
      ))}
    </div>
  );
}
