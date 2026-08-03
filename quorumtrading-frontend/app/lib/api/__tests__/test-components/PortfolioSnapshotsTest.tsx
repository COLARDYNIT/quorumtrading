import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/app/lib/api/client';

interface PortfolioSnapshotsTestProps {
  simulationId: string;
  from: string;
  to: string;
}

export function PortfolioSnapshotsTest({ simulationId, from, to }: PortfolioSnapshotsTestProps) {
  const { data, isLoading, error } = useQuery({
    queryKey: ['portfolio-snapshots', simulationId, from, to],
    queryFn: () => apiClient.portfolio.snapshots(simulationId, from, to),
  });

  if (isLoading) return <div data-testid="loading">Loading...</div>;
  if (error) return <div data-testid="error">Error: {(error as Error).message}</div>;

  return (
    <div data-testid="portfolio-snapshots">
      <div data-testid="snapshots-count">{data?.snapshots?.length || 0}</div>
      {data?.snapshots?.map((snapshot, index) => (
        <div key={index} data-testid={`snapshot-${index}`}>
          <div data-testid={`snapshot-${index}-value`}>{snapshot.totalValue}</div>
        </div>
      ))}
    </div>
  );
}
