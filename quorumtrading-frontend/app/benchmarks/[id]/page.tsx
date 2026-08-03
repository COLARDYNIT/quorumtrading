'use client';

import { use, useMemo, useState, useCallback, useEffect } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import styled, { useTheme } from 'styled-components';
import { DashboardLayout } from '@/app/components/layouts/DashboardLayout';
import { TerminalButton, TerminalBadge } from '@/app/components/terminal';
import { useBenchmark, useSimulations, useDeleteBenchmark, useBenchmarkOverlays, useBenchmarkChartData } from '@/app/lib/api/hooks';
import { formatBenchmarkDateRange, getBenchmarkMode } from '@/app/lib/types/benchmark';
import { BenchmarkChart } from '../components/BenchmarkChart';
import { BenchmarkSidebar } from '../components/BenchmarkSidebar';
import { calculateLeaders } from '@/app/lib/utils/leaders';
import { transformToChartData, mergeOverlaysIntoChartData, transformCompactChartData } from '@/app/lib/utils/chartData';
import { OverlaySelector } from '../components/OverlaySelector';
import { DividendMismatchWarning } from '../components/DividendMismatchWarning';
import { useStreamMessage } from '@/app/lib/websocket/hooks';
import { getWebSocketManager } from '@/app/lib/websocket/manager';
import type { BenchmarkSimulation, BenchmarkData } from '@/app/lib/types/benchmark';
import type { PortfolioUpdateMessage } from '@/app/lib/types/websocket';
import { fadeInUp, shadows, textShadows } from '@/app/styles/animations';

const Container = styled.div`
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
  gap: ${p => p.theme.spacing.md};
  animation: ${fadeInUp} 0.5s ease-out;

  @media (max-width: 480px) {
    gap: ${p => p.theme.spacing.md};
    height: auto;
  }
`;

const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
  padding: 4px 0 0;
  flex-wrap: wrap;

  @media (max-width: 768px) {
    flex-direction: column;
    gap: 10px;
  }
`;

const HeaderContent = styled.div`
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
`;

const Title = styled.h1`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 24px;
  letter-spacing: -0.012em;
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  margin: 0;

  @media (max-width: 768px) {
    font-size: 19px;
  }
`;

const Metadata = styled.div`
  display: flex;
  gap: 14px;
  align-items: center;
  flex-wrap: wrap;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const MetadataItem = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13px;
`;

const MetadataLabel = styled.span`
  color: ${p => p.theme.colors.fgDimmed};
  letter-spacing: 0.06em;
  text-transform: uppercase;
  font-size: 11.5px;
`;

const MetadataValue = styled.span`
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  font-variant-numeric: tabular-nums;
`;

const ContentPane = styled.div`
  display: flex;
  flex: 1;
  gap: ${p => p.theme.spacing.lg};
  min-height: 0;

  @media (max-width: 480px) {
    flex-direction: column;
    gap: ${p => p.theme.spacing.md};
  }
`;

const ChartPane = styled.div`
  flex: 1;
  height: 100%;
  overflow: hidden;
  animation: ${fadeInUp} 0.5s ease-out 0.1s both;

  @media (max-width: 480px) {
    flex: none;
    height: 300px;
  }
`;

const SidebarPane = styled.div`
  flex: 0 0 280px;
  height: 100%;
  overflow: hidden;
  animation: ${fadeInUp} 0.5s ease-out 0.2s both;

  @media (max-width: 480px) {
    flex: none;
    height: auto;
  }
`;

const LoadingMessage = styled.div`
  color: ${p => p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.base};
  text-align: center;
  padding: ${p => p.theme.spacing.xl};
`;

const ErrorMessage = styled.div`
  color: ${p => p.theme.colors.error};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.base};
  padding: ${p => p.theme.spacing.lg};
  border: 1px solid ${p => p.theme.colors.error};
  background: ${p => p.theme.colors.error}10;
`;

const EmptyState = styled.div`
  color: ${p => p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.base};
  text-align: center;
  padding: ${p => p.theme.spacing.xl};
  background: linear-gradient(135deg, ${p => p.theme.colors.surface}60, ${p => p.theme.colors.background});
  border: 1px dashed ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.md};
  animation: ${fadeInUp} 0.5s ease-out 0.1s both;
`;

const PreparingOverlay = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: ${p => p.theme.spacing.md};
  color: ${p => p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.base};
  border: 1px solid ${p => p.theme.colors.border};
  background: ${p => p.theme.colors.surface};
`;

const PreparingTitle = styled.div`
  color: ${p => p.theme.colors.primary};
  font-size: ${p => p.theme.typography.fontSize.lg};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  text-transform: uppercase;
  letter-spacing: 0.1em;
  text-shadow: ${textShadows.primary};
`;

const PreparingSpinner = styled.div`
  @keyframes blink {
    0%, 100% { opacity: 0.3; }
    50% { opacity: 1; }
  }
  display: flex;
  gap: 6px;
  & span {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: ${p => p.theme.colors.primary};
    animation: blink 1.4s infinite;
  }
  & span:nth-child(2) { animation-delay: 0.2s; }
  & span:nth-child(3) { animation-delay: 0.4s; }
`;

const HeaderActions = styled.div`
  display: flex;
  gap: 12px;
  align-items: center;
  flex-shrink: 0;

  @media (max-width: 768px) {
    flex-wrap: wrap;
    width: 100%;
  }
`;

const RedButtonWrapper = styled.div`
  button {
    /* Clean styling for danger button */
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.3) !important;
  }
`;

const ConfirmationModal = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
`;

const ConfirmationDialog = styled.div`
  background: ${p => p.theme.colors.surface};
  border: 2px solid ${p => p.theme.colors.error};
  padding: ${p => p.theme.spacing.xl};
  max-width: 500px;
  width: 90%;
  box-shadow: ${shadows.cardPremium};
  animation: ${fadeInUp} 0.3s ease-out;
`;

const ConfirmationTitle = styled.h2`
  font-size: ${p => p.theme.typography.fontSize.xl};
  color: ${p => p.theme.colors.error};
  margin: 0 0 ${p => p.theme.spacing.md} 0;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  text-transform: uppercase;
`;

const ConfirmationMessage = styled.p`
  color: ${p => p.theme.colors.foreground};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.base};
  margin: 0 0 ${p => p.theme.spacing.lg} 0;
  line-height: 1.6;
`;

const ConfirmationActions = styled.div`
  display: flex;
  gap: ${p => p.theme.spacing.md};
  justify-content: flex-end;
`;

const OverlayBar = styled.div`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.md};
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.lg};
  box-shadow: ${shadows.card};
  animation: ${fadeInUp} 0.5s ease-out 0.05s both;
`;

const OverlayBarLabel = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.theme.colors.primary};
  text-transform: uppercase;
  letter-spacing: 0.05em;
  white-space: nowrap;
`;

type BenchmarkStatus = 'LIVE' | 'SIMULATING' | 'COMPLETED' | 'FAILED' | 'PENDING';

function deriveBenchmarkStatus(statuses: string[]): BenchmarkStatus {
  if (statuses.length === 0) return 'PENDING';
  if (statuses.some(s => s === 'LIVE')) return 'LIVE';
  if (statuses.some(s => s === 'SIMULATING')) return 'SIMULATING';
  if (statuses.some(s => s === 'FAILED')) return 'FAILED';
  if (statuses.every(s => s === 'COMPLETED')) return 'COMPLETED';
  return 'PENDING';
}

function statusBadgeVariant(status: BenchmarkStatus): 'success' | 'info' | 'error' | 'neutral' {
  switch (status) {
    case 'LIVE': return 'success';
    case 'SIMULATING': return 'info';
    case 'FAILED': return 'error';
    default: return 'neutral';
  }
}

interface BenchmarkDetailPageProps {
  params: Promise<{
    id: string;
  }>;
}

export default function BenchmarkDetailPage({ params }: BenchmarkDetailPageProps) {
  const { id } = use(params);
  const router = useRouter();
  const theme = useTheme();
  const colors = theme.colors;
  const deleteBenchmark = useDeleteBenchmark();
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [activeOverlays, setActiveOverlays] = useState<string[]>([]);

  const { data: benchmark, isLoading: benchmarkLoading, error: benchmarkError } = useBenchmark(id);
  const { data: allSimulations } = useSimulations();

  // Filter simulations that belong to this benchmark (memoized for stable reference)
  const benchmarkSimulations = useMemo(
    () => allSimulations?.filter(sim => sim.benchmarkId === id) || [],
    [allSimulations, id]
  );

  const benchmarkStatus = deriveBenchmarkStatus(benchmarkSimulations.map(s => s.status));
  const isBenchmarkCompleted = benchmarkSimulations.length > 0 && benchmarkSimulations.every(s => s.status === 'COMPLETED');

  // Fetch compact chart data from single endpoint (replaces N+1 snapshot/strategy fetches)
  // No need to wait for useSimulations() — the backend resolves simulations from the benchmark ID
  const { data: compactData, isLoading: dataLoading } = useBenchmarkChartData(id);

  // Transform compact columnar data into BenchmarkData for chart components
  const benchmarkData = useMemo((): BenchmarkData | null => {
    if (!compactData) return null;

    const { simulations: sims } = transformCompactChartData(compactData);

    // Enrich with status from simulation API data when available
    const enrichedSims = sims.map(sim => {
      const apiSim = benchmarkSimulations.find(s => s.id === sim.id);
      if (apiSim) {
        return {
          ...sim,
          strategyId: apiSim.strategyId,
          status: apiSim.status as BenchmarkSimulation['status'],
          startDate: new Date(apiSim.startDate),
          endDate: new Date(apiSim.endDate),
        };
      }
      return sim;
    });

    const allStartDates = enrichedSims.map(s => s.startDate);
    const allEndDates = enrichedSims.map(s => s.endDate);

    return {
      simulations: enrichedSims,
      timelineBounds: {
        start: new Date(Math.min(...allStartDates.map(d => d.getTime()))),
        end: new Date(Math.max(...allEndDates.map(d => d.getTime()))),
      },
    };
  }, [compactData, benchmarkSimulations]);

  // Overlay data
  const { data: overlayData, isLoading: overlaysLoading } = useBenchmarkOverlays(
    id, activeOverlays, undefined, isBenchmarkCompleted
  );

  const handleOverlayToggle = useCallback((symbol: string) => {
    setActiveOverlays(prev =>
      prev.includes(symbol)
        ? prev.filter(s => s !== symbol)
        : [...prev, symbol]
    );
  }, []);

  const [localData, setLocalData] = useState<BenchmarkData | null>(benchmarkData || null);

  // Update local state when data loads
  useEffect(() => {
    if (benchmarkData) {
      setLocalData(benchmarkData);
    }
  }, [benchmarkData]);

  // Handle WebSocket portfolio updates
  const handlePortfolioUpdate = useCallback((message: PortfolioUpdateMessage) => {
    setLocalData(prev => {
      if (!prev) return prev;

      // Find the simulation that was updated
      const simIndex = prev.simulations.findIndex(s => s.id === message.simulationId);
      if (simIndex === -1) return prev;

      const updatedSimulations = [...prev.simulations];
      const sim = updatedSimulations[simIndex];

      // Add new data point
      const newDataPoint = {
        timestamp: new Date(message.timestamp),
        portfolioValue: message.totalValue,
      };

      updatedSimulations[simIndex] = {
        ...sim,
        dataPoints: [...sim.dataPoints, newDataPoint],
        currentValue: message.totalValue,
        // startValue is the invested basis: benchmark members are never DCA-funded and
        // the PORTFOLIO_UPDATE stream carries no contribution data, so seed == invested
        // here. Contribution-aware returns for funded sims are computed server-side.
        returnPercent: ((message.totalValue - sim.startValue) / sim.startValue) * 100,
      };

      return {
        ...prev,
        simulations: updatedSimulations,
      };
    });
  }, []);

  // Subscribe to PORTFOLIO_UPDATE messages
  useStreamMessage<PortfolioUpdateMessage>('PORTFOLIO_UPDATE', handlePortfolioUpdate, [handlePortfolioUpdate]);

  // Subscribe to WebSocket updates for all simulations in this benchmark
  useEffect(() => {
    if (!localData || localData.simulations.length === 0) return;

    const wsManager = getWebSocketManager();

    // Subscribe to each simulation
    localData.simulations.forEach((sim) => {
      wsManager.subscribe(sim.id);
    });

    // Cleanup: unsubscribe when component unmounts or simulations change
    return () => {
      localData.simulations.forEach((sim) => {
        wsManager.unsubscribe(sim.id);
      });
    };
  }, [localData]);

  // Handle delete confirmation
  const handleDeleteConfirm = async () => {
    try {
      await deleteBenchmark.mutateAsync(id);
      // Navigate to benchmarks overview after successful deletion
      router.push('/benchmarks');
    } catch (error) {
      // Error is already handled by the mutation hook (toast notification)
      console.error('Failed to delete benchmark:', error);
      setShowDeleteConfirm(false);
    }
  };

  const chartData = useMemo(() => {
    if (!localData || localData.simulations.length === 0) return [];
    const baseData = transformToChartData(localData.simulations);
    if (overlayData && overlayData.length > 0) {
      return mergeOverlaysIntoChartData(baseData, overlayData);
    }
    return baseData;
  }, [localData, overlayData]);

  const leaders = useMemo(() => {
    if (!localData || localData.simulations.length === 0) return [];
    return calculateLeaders(localData.simulations);
  }, [localData]);

  const isLoading = benchmarkLoading || dataLoading;

  if (isLoading) {
    return (
      <DashboardLayout>
        <Container>
          <LoadingMessage>Loading benchmark...</LoadingMessage>
        </Container>
      </DashboardLayout>
    );
  }

  if (benchmarkError) {
    return (
      <DashboardLayout>
        <Container>
          <ErrorMessage>
            Failed to load benchmark: {benchmarkError instanceof Error ? benchmarkError.message : 'Unknown error'}
          </ErrorMessage>
          <Link href="/">
            <TerminalButton variant="secondary">Back to Dashboard</TerminalButton>
          </Link>
        </Container>
      </DashboardLayout>
    );
  }

  if (!benchmark) {
    return (
      <DashboardLayout>
        <Container>
          <ErrorMessage>Benchmark not found</ErrorMessage>
          <Link href="/">
            <TerminalButton variant="secondary">Back to Dashboard</TerminalButton>
          </Link>
        </Container>
      </DashboardLayout>
    );
  }

  const mode = getBenchmarkMode(benchmark);
  const dateRange = formatBenchmarkDateRange(benchmark);

  return (
    <DashboardLayout>
      <Container>
        <Header>
          <HeaderContent>
            <Title>{benchmark.name}</Title>
            <Metadata>
              <MetadataItem>
                <MetadataLabel>Status:</MetadataLabel>
                <MetadataValue>
                  <TerminalBadge variant={statusBadgeVariant(benchmarkStatus)}>{benchmarkStatus}</TerminalBadge>
                </MetadataValue>
              </MetadataItem>
              <MetadataItem>
                <MetadataLabel>Mode:</MetadataLabel>
                <MetadataValue>
                  <TerminalBadge>{mode.toUpperCase()}</TerminalBadge>
                </MetadataValue>
              </MetadataItem>
              <MetadataItem>
                <MetadataLabel>Period:</MetadataLabel>
                <MetadataValue>{dateRange}</MetadataValue>
              </MetadataItem>
              <MetadataItem>
                <MetadataLabel>Simulations:</MetadataLabel>
                <MetadataValue>{compactData?.simulations.length ?? benchmark.simulationIds.length}</MetadataValue>
              </MetadataItem>
            </Metadata>
          </HeaderContent>
          <HeaderActions>
            <TerminalButton
              variant="danger"
              onClick={() => setShowDeleteConfirm(true)}
            >
              Delete
            </TerminalButton>
            <Link href="/">
              <TerminalButton variant="secondary">Back to Dashboard</TerminalButton>
            </Link>
          </HeaderActions>
        </Header>

        <OverlayBar>
          <OverlayBarLabel>Compare vs Market</OverlayBarLabel>
          <OverlaySelector
            activeOverlays={activeOverlays}
            onToggle={handleOverlayToggle}
            loadingSymbols={overlaysLoading ? activeOverlays : []}
          />
        </OverlayBar>

        <DividendMismatchWarning
          simulations={benchmarkSimulations}
          overlaySymbols={activeOverlays}
        />

        {compactData && compactData.simulations.length === 0 ? (
          <EmptyState>
            No simulations found for this benchmark.
            {mode === 'live' && ' Simulations may still be starting...'}
          </EmptyState>
        ) : !localData ? (
          <LoadingMessage>Loading chart data...</LoadingMessage>
        ) : (
          <ContentPane>
            <ChartPane>
              {chartData.length === 0 ? (
                <PreparingOverlay>
                  <PreparingSpinner>
                    <span /><span /><span />
                  </PreparingSpinner>
                  <PreparingTitle>Preparing benchmark</PreparingTitle>
                  <div>{localData.simulations.length} simulation{localData.simulations.length !== 1 ? 's' : ''} starting up...</div>
                </PreparingOverlay>
              ) : (
                <BenchmarkChart
                  chartData={chartData}
                  simulations={localData.simulations}
                  timelineBounds={localData.timelineBounds}
                  overlays={overlayData}
                />
              )}
            </ChartPane>
            <SidebarPane>
              <BenchmarkSidebar leaders={leaders} overlays={overlayData} />
            </SidebarPane>
          </ContentPane>
        )}
      </Container>

      {/* Delete Confirmation Modal */}
      {showDeleteConfirm && (
        <ConfirmationModal onClick={() => setShowDeleteConfirm(false)}>
          <ConfirmationDialog onClick={(e) => e.stopPropagation()}>
            <ConfirmationTitle>Delete Benchmark?</ConfirmationTitle>
            <ConfirmationMessage>
              Are you sure you want to delete &quot;{benchmark?.name}&quot;?
              <br />
              <br />
              This will permanently delete the benchmark and all {benchmark?.simulationIds.length ?? 0} associated simulation{(benchmark?.simulationIds.length ?? 0) !== 1 ? 's' : ''}.
              <br />
              <br />
              This action cannot be undone.
            </ConfirmationMessage>
            <ConfirmationActions>
              <TerminalButton
                variant="secondary"
                onClick={() => setShowDeleteConfirm(false)}
                disabled={deleteBenchmark.isPending}
              >
                Cancel
              </TerminalButton>
              <RedButtonWrapper>
                <TerminalButton
                  variant="secondary"
                  onClick={handleDeleteConfirm}
                  disabled={deleteBenchmark.isPending}
                  style={{
                    borderColor: colors.danger,
                    color: colors.danger,
                  }}
                >
                  {deleteBenchmark.isPending ? 'Deleting...' : 'Delete'}
                </TerminalButton>
              </RedButtonWrapper>
            </ConfirmationActions>
          </ConfirmationDialog>
        </ConfirmationModal>
      )}
    </DashboardLayout>
  );
}
