'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import { useParams, useRouter } from 'next/navigation';
import styled, { useTheme } from 'styled-components';
import { DashboardLayout } from '@/app/components/layouts/DashboardLayout';
import { useBreadcrumb } from '@/app/contexts/BreadcrumbContext';
import { TerminalButton, TerminalText, TerminalPane } from '@/app/components/terminal';
import { useSimulation, useStrategy, useStartSimulation, usePauseSimulation, useResumeSimulation, useDeleteSimulation, useForceEndSimulation } from '@/app/lib/api/hooks';
import { TimeNavigationProvider, useTimeNavigation } from '@/app/contexts/TimeNavigationContext';
import { SimulationDataProvider, useSimulationData } from './contexts/SimulationDataContext';
import { RestartFromModal } from './components/RestartFromModal';
import { SimulationDetailHeader } from './components/SimulationDetailHeader';
import { ChartPanel, HoldingsPanel, DecisionsPanel, AgentsPanel, DcaPanel, ConsensusInspectModal } from './components/v2';
import { ExecutionHealthBanner } from './components/v2/ExecutionHealthBanner';
import { fadeInUp, shadows } from '@/app/styles/animations';

// Styled Components
const ViewerContainer = styled.div`
  min-height: 100vh;
  padding: ${p => p.theme.spacing.lg};
  background: ${p => p.theme.colors.background};
  overflow-x: hidden;
  animation: ${fadeInUp} 0.5s ease-out;

  @media (max-width: 640px) {
    padding: ${p => p.theme.spacing.md};
  }

  @media (max-width: 480px) {
    padding: ${p => p.theme.spacing.sm};
  }
`;

/**
 * V1 Dense grid layout for the Simulation Detail page.
 * Chart top-left + Holdings top-right; Decisions filling left bottom (2 rows);
 * Agents middle-right; DCA bottom-right.
 */
const V1Grid = styled.div`
  display: grid;
  grid-template-columns: 1.55fr 1fr;
  grid-template-rows: auto auto auto;
  gap: 12px;
  padding: 12px 0;

  /*
   * min-width: 0 is required on every grid child — without it the default
   * min-content sizing lets a wide child (a table with nowrap cells, an SVG
   * with intrinsic width) expand the grid track past the viewport, which on
   * mobile clips the right edge of charts and table headers (Image #19).
   */
  & > * { min-width: 0; }

  & > .gc-chart    { grid-column: 1 / 2; grid-row: 1 / 2; }
  & > .gc-snapshot { grid-column: 2 / 3; grid-row: 1 / 2; }
  & > .gc-decisions{ grid-column: 1 / 2; grid-row: 2 / 4; }
  & > .gc-agents   { grid-column: 2 / 3; grid-row: 2 / 3; }
  & > .gc-dca      { grid-column: 2 / 3; grid-row: 3 / 4; }

  @media (max-width: 1180px) {
    grid-template-columns: 1fr;
    grid-template-rows: auto;
    & > .gc-chart,
    & > .gc-snapshot,
    & > .gc-decisions,
    & > .gc-agents,
    & > .gc-dca {
      grid-column: 1 / -1;
      grid-row: auto;
    }
  }
`;

// Overflow menu for admin actions (used in breadcrumb pageActions)
const OverflowMenuContainer = styled.div`
  position: relative;
`;

const OverflowButton = styled.button`
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  background: transparent;
  border: 1px solid ${p => p.theme.colors.border};
  color: ${p => p.theme.colors.textMuted};
  font-size: 19px;
  cursor: pointer;
  transition: all 0.15s ease;

  &:hover {
    background: ${p => p.theme.colors.surface};
    color: ${p => p.theme.colors.text};
    border-color: ${p => p.theme.colors.text};
  }
`;

const OverflowDropdown = styled.div<{ $open: boolean }>`
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 4px;
  min-width: 160px;
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  z-index: 100;
  display: ${p => p.$open ? 'block' : 'none'};
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
`;

const OverflowMenuItem = styled.button<{ $danger?: boolean }>`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.sm};
  width: 100%;
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  background: transparent;
  border: none;
  color: ${p => p.$danger ? p.theme.colors.error : p.theme.colors.text};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  text-align: left;
  cursor: pointer;
  transition: background 0.15s ease;

  &:hover {
    background: ${p => p.$danger ? `${p.theme.colors.error}22` : p.theme.colors.surface};
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

// Delete Confirmation Modal
const DeleteModalOverlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  backdrop-filter: blur(4px);
`;

const DeleteModalContent = styled.div`
  background: ${p => p.theme.colors.background};
  border: 2px solid ${p => p.theme.colors.error};
  border-radius: ${p => p.theme.borderRadius.md};
  max-width: 600px;
  width: 90%;
  box-shadow: ${shadows.cardPremium};
  position: relative;
  animation: ${fadeInUp} 0.3s ease-out;
`;

const DeleteModalHeader = styled.div`
  padding: ${p => p.theme.spacing.lg};
  border-bottom: 1px solid ${p => p.theme.colors.error}44;
  background: ${p => p.theme.colors.error}11;
`;

const DeleteModalTitle = styled.h2`
  margin: 0;
  font-size: ${p => p.theme.typography.fontSize.lg};
  color: ${p => p.theme.colors.error};
  text-transform: uppercase;
  letter-spacing: ${p => p.theme.typography.letterSpacing.wide};
  font-family: ${p => p.theme.typography.fontFamily.mono};
`;

const DeleteModalBody = styled.div`
  padding: ${p => p.theme.spacing.lg};
`;

const DeleteModalText = styled.p`
  margin: 0 0 ${p => p.theme.spacing.md} 0;
  color: ${p => p.theme.colors.text};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  line-height: 1.6;
`;

const DeleteModalList = styled.ul`
  margin: ${p => p.theme.spacing.md} 0;
  padding-left: ${p => p.theme.spacing.lg};
  color: ${p => p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};

  li {
    margin: ${p => p.theme.spacing.xs} 0;

    &::marker {
      color: ${p => p.theme.colors.error};
    }
  }
`;

const DeleteModalWarning = styled.p`
  margin: ${p => p.theme.spacing.md} 0 0 0;
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  background: ${p => p.theme.colors.error}22;
  border-left: 3px solid ${p => p.theme.colors.error};
  color: ${p => p.theme.colors.error};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  text-transform: uppercase;
  font-size: ${p => p.theme.typography.fontSize.sm};
`;

const DeleteModalFooter = styled.div`
  padding: ${p => p.theme.spacing.lg};
  border-top: 1px solid ${p => p.theme.colors.border};
  display: flex;
  gap: ${p => p.theme.spacing.md};
  justify-content: flex-end;
`;

// End Simulation Modal (amber-themed)
const EndModalContent = styled.div`
  background: ${p => p.theme.colors.background};
  border: 2px solid ${p => p.theme.colors.terminalAmber};
  border-radius: ${p => p.theme.borderRadius.md};
  max-width: 600px;
  width: 90%;
  box-shadow: ${shadows.cardPremium};
  position: relative;
  animation: ${fadeInUp} 0.3s ease-out;
`;

const EndModalHeader = styled.div`
  padding: ${p => p.theme.spacing.lg};
  border-bottom: 1px solid ${p => p.theme.colors.terminalAmber}44;
  background: ${p => p.theme.colors.terminalAmber}11;
`;

const EndModalTitle = styled.h2`
  margin: 0;
  font-size: ${p => p.theme.typography.fontSize.lg};
  color: ${p => p.theme.colors.terminalAmber};
  text-transform: uppercase;
  letter-spacing: ${p => p.theme.typography.letterSpacing.wide};
  font-family: ${p => p.theme.typography.fontFamily.mono};
`;

const EndModalWarning = styled.p`
  margin: ${p => p.theme.spacing.md} 0 0 0;
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  background: ${p => p.theme.colors.terminalAmber}22;
  border-left: 3px solid ${p => p.theme.colors.terminalAmber};
  color: ${p => p.theme.colors.terminalAmber};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
`;

const CheckboxLabel = styled.label`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.sm};
  cursor: pointer;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.text};
  font-size: ${p => p.theme.typography.fontSize.sm};
  margin: ${p => p.theme.spacing.md} 0;

  input[type="checkbox"] {
    width: 16px;
    height: 16px;
    accent-color: ${p => p.theme.colors.terminalAmber};
    cursor: pointer;
  }
`;

// FAILED State Display - Full page replacement
const FailedStateContainer = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  padding: ${p => p.theme.spacing['2xl']};
  animation: ${fadeInUp} 0.5s ease-out;
`;

const FailedStateCard = styled.div`
  max-width: 600px;
  width: 100%;
  background: ${p => p.theme.colors.surface};
  border: 2px solid ${p => p.theme.colors.error};
  border-radius: ${p => p.theme.borderRadius.lg};
  box-shadow: 0 0 30px ${p => p.theme.colors.error}33;
  overflow: hidden;
`;

const FailedStateHeader = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: ${p => p.theme.spacing.md};
  padding: ${p => p.theme.spacing['2xl']};
  background: ${p => p.theme.colors.error}15;
  border-bottom: 1px solid ${p => p.theme.colors.error}44;
`;

const FailedStateIcon = styled.div`
  font-size: 64px;
  line-height: 1;
  filter: drop-shadow(0 0 20px ${p => p.theme.colors.error});
`;

const FailedStateTitle = styled.h1`
  margin: 0;
  font-size: ${p => p.theme.typography.fontSize['2xl']};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.error};
  text-transform: uppercase;
  letter-spacing: ${p => p.theme.typography.letterSpacing.wide};
  text-shadow: 0 0 10px ${p => p.theme.colors.error}66;
`;

const FailedStateBody = styled.div`
  padding: ${p => p.theme.spacing.xl};
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.lg};
`;

const FailedStateMessage = styled.p`
  margin: 0;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.base};
  color: ${p => p.theme.colors.text};
  line-height: 1.6;
  text-align: center;
`;

const FailedStateDetails = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.md};
  padding: ${p => p.theme.spacing.lg};
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
`;

const FailedStateDetailRow = styled.div`
  display: grid;
  grid-template-columns: 120px 1fr;
  gap: ${p => p.theme.spacing.md};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  align-items: baseline;

  span:first-child {
    color: ${p => p.theme.colors.textMuted};
    white-space: nowrap;
  }

  span:last-child {
    color: ${p => p.theme.colors.text};
    text-align: right;
  }
`;

const FailedStateTip = styled.div`
  display: flex;
  align-items: flex-start;
  gap: ${p => p.theme.spacing.sm};
  padding: ${p => p.theme.spacing.md};
  background: ${p => p.theme.colors.terminalAmber}15;
  border: 1px solid ${p => p.theme.colors.terminalAmber}44;
  border-radius: ${p => p.theme.borderRadius.sm};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.terminalAmber};
  line-height: 1.5;
`;

const FailedStateActions = styled.div`
  display: flex;
  gap: ${p => p.theme.spacing.md};
  justify-content: center;
  padding: ${p => p.theme.spacing.lg};
  border-top: 1px solid ${p => p.theme.colors.border};
`;

// ── News & Sentiment section (replaces MarketEvents sidebar) ──

const NewsSection = styled.div`
  margin-top: ${p => p.theme.spacing.lg};
`;

const NewsItem = styled.div`
  display: flex;
  align-items: baseline;
  gap: ${p => p.theme.spacing.xs};
  padding: ${p => p.theme.spacing.xs} 0;
  border-bottom: 1px solid ${p => p.theme.colors.border}20;
  font-size: ${p => p.theme.typography.fontSize.sm};

  &:last-child {
    border-bottom: none;
  }
`;

const NewsTimestamp = styled.span`
  color: ${p => p.theme.colors.textSubtle};
  margin-right: ${p => p.theme.spacing.sm};
  white-space: nowrap;
`;

const NewsSource = styled.span`
  color: ${p => p.theme.colors.crypto};
  margin-right: ${p => p.theme.spacing.sm};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  white-space: nowrap;
`;

function NewsSentiment() {
  const { marketEvents } = useSimulationData();
  const [collapsed, setCollapsed] = useState(true);

  if (marketEvents.news.length === 0) return null;

  const formatTime = (timestamp: string) => {
    const date = new Date(timestamp);
    return date.toLocaleDateString('en-GB', {
      day: 'numeric', month: 'short', timeZone: 'America/New_York',
    }) + ' ' + date.toLocaleTimeString('en-GB', {
      hour: '2-digit', minute: '2-digit', timeZone: 'America/New_York',
    }) + ' ET';
  };

  return (
    <NewsSection>
      <TerminalPane
        title={`News & Sentiment (${marketEvents.news.length})`}
        actions={
          <TerminalButton size="sm" variant="ghost" onClick={() => setCollapsed(c => !c)}>
            {collapsed ? 'Show' : 'Hide'}
          </TerminalButton>
        }
      >
        {!collapsed && marketEvents.news.map((news, i) => (
          <NewsItem key={`${news.timestamp}-${i}`}>
            <TerminalText size="sm">
              <NewsTimestamp>[{formatTime(news.timestamp)}]</NewsTimestamp>
              <NewsSource>[{news.source}]</NewsSource>
              {news.title}
            </TerminalText>
          </NewsItem>
        ))}
        {collapsed && (
          <TerminalText size="xs" variant="muted">
            {marketEvents.news.length} news items available — click Show to expand
          </TerminalText>
        )}
      </TerminalPane>
    </NewsSection>
  );
}

export default function SimulationViewerPage() {
  const params = useParams();
  const simulationId = params.id as string;

  return (
    <DashboardLayout>
      <TimeNavigationProvider>
        <SimulationDataProvider simulationId={simulationId}>
          <SimulationViewerContent simulationId={simulationId} />
        </SimulationDataProvider>
      </TimeNavigationProvider>
    </DashboardLayout>
  );
}

function SimulationViewerContent({ simulationId }: { simulationId: string }) {
  const theme = useTheme();
  const colors = theme.colors;
  const timeNav = useTimeNavigation();
  const { setCustomBreadcrumbs, setPageActions } = useBreadcrumb();

  // State for delete confirmation modal
  const [showDeleteModal, setShowDeleteModal] = useState(false);

  // State for end simulation modal
  const [showEndModal, setShowEndModal] = useState(false);
  const [liquidateOnEnd, setLiquidateOnEnd] = useState(true);

  // State for restart-from-decision modal
  const [restartFromState, setRestartFromState] = useState<{ consensusDecisionId: number; decisionTimestamp: string } | null>(null);

  // State for the consensus inspect modal (chart-dot click + decision-row click).
  const [inspectedConsensusId, setInspectedConsensusId] = useState<string | null>(null);

  // State for overflow menu
  const [showOverflowMenu, setShowOverflowMenu] = useState(false);
  const overflowMenuRef = useRef<HTMLDivElement>(null);

  // Close overflow menu when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (overflowMenuRef.current && !overflowMenuRef.current.contains(event.target as Node)) {
        setShowOverflowMenu(false);
      }
    };

    if (showOverflowMenu) {
      document.addEventListener('mousedown', handleClickOutside);
    }

    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [showOverflowMenu]);

  // Fetch simulation and strategy data
  const { data: simulation, isLoading: simulationLoading, error: simulationError } = useSimulation(simulationId);
  const { data: strategy, isLoading: strategyLoading, error: strategyError } = useStrategy(simulation?.strategyId || '');

  // Set breadcrumbs based on strategy data - include simulation mode (Live/Backtest)
  useEffect(() => {
    if (simulation && strategy) {
      const simulationLabel = simulation.status === 'LIVE' ? 'Live Simulation' : 'Backtest Simulation';
      setCustomBreadcrumbs([
        { label: 'Dashboard', path: '/' },
        { label: 'Strategies', path: '/strategies' },
        { label: strategy.name || `Strategy #${simulation.strategyId.slice(0, 8)}`, path: `/strategies/${simulation.strategyId}` },
        { label: simulationLabel, path: `/simulations/${simulationId}` },
      ]);
    }
    return () => setCustomBreadcrumbs(null);
  }, [simulation, strategy, simulationId, setCustomBreadcrumbs]);

  // Router for navigation after delete
  const router = useRouter();

  // Start/Pause/Resume mutations
  const startMutation = useStartSimulation();
  const pauseMutation = usePauseSimulation();
  const resumeMutation = useResumeSimulation();
  const deleteMutation = useDeleteSimulation();
  const forceEndMutation = useForceEndSimulation();

  // Handle end simulation
  const handleEndSimulation = useCallback(() => {
    if (!simulation) return;
    setShowEndModal(true);
  }, [simulation]);

  const confirmEndSimulation = () => {
    forceEndMutation.mutate(
      { simulationId, liquidatePositions: liquidateOnEnd },
      {
        onSuccess: () => {
          setShowEndModal(false);
        },
      }
    );
  };

  const cancelEndSimulation = () => {
    setShowEndModal(false);
    setLiquidateOnEnd(true);
  };

  // Handle start/pause/resume toggle
  const handleTogglePause = useCallback(() => {
    if (!simulation) return;

    // If simulation is PENDING, start it
    if (simulation.status === 'PENDING') {
      startMutation.mutate(simulationId);
      return;
    }

    // Only pause/resume if simulation is in SIMULATING or LIVE state
    if (simulation.status === 'SIMULATING' || simulation.status === 'LIVE') {
      if (simulation.paused) {
        resumeMutation.mutate(simulationId);
      } else {
        pauseMutation.mutate(simulationId);
      }
    }
  }, [simulation, simulationId, startMutation, pauseMutation, resumeMutation]);

  // Handle simulation deletion
  const handleDelete = useCallback(() => {
    if (!simulation) return;
    setShowDeleteModal(true);
  }, [simulation]);

  // Confirm and execute deletion
  const confirmDelete = () => {
    const strategyId = simulation?.strategyId;
    deleteMutation.mutate(simulationId, {
      onSuccess: () => {
        setShowDeleteModal(false);
        // Navigate to the parent strategy page after successful deletion
        if (strategyId) {
          router.push(`/strategies/${strategyId}`);
        } else {
          router.push('/benchmarks');
        }
      },
    });
  };

  // Cancel deletion
  const cancelDelete = () => {
    setShowDeleteModal(false);
  };

  // Refs for handlers to avoid dependency issues in useEffect
  const handleTogglePauseRef = useRef(handleTogglePause);
  const handleDeleteRef = useRef(handleDelete);
  const handleEndSimulationRef = useRef(handleEndSimulation);
  const returnToLiveRef = useRef(timeNav.returnToLive);
  const setShowOverflowMenuRef = useRef(setShowOverflowMenu);

  // Keep refs updated
  useEffect(() => {
    handleTogglePauseRef.current = handleTogglePause;
    handleDeleteRef.current = handleDelete;
    handleEndSimulationRef.current = handleEndSimulation;
    returnToLiveRef.current = timeNav.returnToLive;
    setShowOverflowMenuRef.current = setShowOverflowMenu;
  });

  // Extract primitive values to avoid object reference changes triggering re-renders
  const simulationStatus = simulation?.status;
  const simulationPaused = simulation?.paused;
  const hasSimulation = !!simulation;

  // Set page actions in breadcrumb row
  useEffect(() => {
    if (!hasSimulation) {
      setPageActions(null);
      return;
    }

    setPageActions(
      <>
        {timeNav.state.mode === 'historical' && (
          <TerminalButton onClick={() => returnToLiveRef.current()} variant="secondary">
            BACK TO LIVE
          </TerminalButton>
        )}
        {simulationStatus !== 'COMPLETED' && (
          <TerminalButton
            onClick={() => handleTogglePauseRef.current()}
            variant={simulationStatus === 'PENDING' ? 'primary' : simulationPaused ? 'primary' : 'ghost'}
            disabled={startMutation.isPending || pauseMutation.isPending || resumeMutation.isPending}
          >
            {startMutation.isPending || pauseMutation.isPending || resumeMutation.isPending
              ? 'UPDATING...'
              : simulationStatus === 'PENDING'
              ? 'START'
              : simulationPaused
              ? 'RESUME'
              : 'PAUSE'}
          </TerminalButton>
        )}
        <OverflowMenuContainer ref={overflowMenuRef}>
          <OverflowButton
            onClick={() => setShowOverflowMenuRef.current(prev => !prev)}
            title="More actions"
          >
            ⋮
          </OverflowButton>
          <OverflowDropdown $open={showOverflowMenu}>
            {simulationStatus === 'LIVE' && (
              <OverflowMenuItem
                onClick={() => {
                  setShowOverflowMenuRef.current(false);
                  handleEndSimulationRef.current();
                }}
                disabled={forceEndMutation.isPending}
              >
                {forceEndMutation.isPending ? 'Ending...' : 'End Simulation'}
              </OverflowMenuItem>
            )}
            <OverflowMenuItem
              $danger
              onClick={() => {
                setShowOverflowMenuRef.current(false);
                handleDeleteRef.current();
              }}
              disabled={deleteMutation.isPending}
            >
              {deleteMutation.isPending ? 'Deleting...' : 'Delete Simulation'}
            </OverflowMenuItem>
          </OverflowDropdown>
        </OverflowMenuContainer>
      </>
    );

    return () => setPageActions(null);
  }, [
    hasSimulation,
    simulationStatus,
    simulationPaused,
    timeNav.state.mode,
    showOverflowMenu,
    startMutation.isPending,
    pauseMutation.isPending,
    resumeMutation.isPending,
    deleteMutation.isPending,
    forceEndMutation.isPending,
    setPageActions,
  ]);

  // Loading state
  if (simulationLoading || strategyLoading) {
    return (
      <ViewerContainer>
        <TerminalText size="lg">Loading simulation...</TerminalText>
      </ViewerContainer>
    );
  }

  // Error state
  if (simulationError || strategyError || !simulation) {
    return (
      <ViewerContainer>
        <TerminalText size="lg" style={{ color: colors.error }}>
          Error loading simulation: {simulationError?.message || strategyError?.message || 'Simulation not found'}
        </TerminalText>
      </ViewerContainer>
    );
  }

  // FAILED state - Full page display
  if (simulation.status === 'FAILED') {
    const formatDateFull = (dateStr: string) => {
      const date = new Date(dateStr);
      return date.toLocaleDateString('en-GB', {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        timeZone: 'America/New_York'
      }) + ' ET';
    };

    return (
      <ViewerContainer>
        <FailedStateContainer>
          <FailedStateCard>
            <FailedStateHeader>
              <FailedStateIcon>⚠</FailedStateIcon>
              <FailedStateTitle>Simulation Failed</FailedStateTitle>
            </FailedStateHeader>
            <FailedStateBody>
              <FailedStateMessage>
                This simulation encountered an error and could not be executed.
                No trading decisions or portfolio data were recorded.
              </FailedStateMessage>

              <FailedStateDetails>
                <FailedStateDetailRow>
                  <span>Strategy</span>
                  <span>{strategy?.name || 'Unknown'}</span>
                </FailedStateDetailRow>
                <FailedStateDetailRow>
                  <span>Planned Period</span>
                  <span>{formatDateFull(simulation.startDate)} — {formatDateFull(simulation.endDate)}</span>
                </FailedStateDetailRow>
                {simulation.startedAt && (
                  <FailedStateDetailRow>
                    <span>Started At</span>
                    <span>{formatDateFull(simulation.startedAt)}</span>
                  </FailedStateDetailRow>
                )}
                <FailedStateDetailRow>
                  <span>Progress</span>
                  <span>{simulation.progress || 0}%</span>
                </FailedStateDetailRow>
              </FailedStateDetails>

              {simulation.failureReason ? (
                <FailedStateTip>
                  <span>💡</span>
                  <span>
                    Recorded reason: {simulation.failureReason}
                  </span>
                </FailedStateTip>
              ) : (
                <FailedStateTip>
                  <span>💡</span>
                  <span>
                    Common causes: missing market data for one or more tickers,
                    invalid date range, or API connectivity issues during startup.
                    Check that all strategy tickers have available data for the selected period.
                  </span>
                </FailedStateTip>
              )}
            </FailedStateBody>
            <FailedStateActions>
              <TerminalButton
                variant="ghost"
                onClick={() => router.push(`/strategies/${simulation.strategyId}`)}
              >
                Back to Strategy
              </TerminalButton>
              <TerminalButton
                variant="danger"
                onClick={() => setShowDeleteModal(true)}
                disabled={deleteMutation.isPending}
              >
                {deleteMutation.isPending ? 'Deleting...' : 'Delete Simulation'}
              </TerminalButton>
            </FailedStateActions>
          </FailedStateCard>
        </FailedStateContainer>

        {/* Delete Confirmation Modal - also available in failed state */}
        {showDeleteModal && (
          <DeleteModalOverlay onClick={cancelDelete}>
            <DeleteModalContent onClick={(e) => e.stopPropagation()}>
              <DeleteModalHeader>
                <DeleteModalTitle>⚠️ Confirm Deletion</DeleteModalTitle>
              </DeleteModalHeader>
              <DeleteModalBody>
                <DeleteModalText>
                  Are you sure you want to delete this failed simulation?
                </DeleteModalText>
                <DeleteModalWarning>
                  ⚠ This action cannot be undone
                </DeleteModalWarning>
              </DeleteModalBody>
              <DeleteModalFooter>
                <TerminalButton
                  variant="ghost"
                  onClick={cancelDelete}
                  disabled={deleteMutation.isPending}
                >
                  Cancel
                </TerminalButton>
                <TerminalButton
                  variant="danger"
                  onClick={confirmDelete}
                  disabled={deleteMutation.isPending}
                >
                  {deleteMutation.isPending ? 'Deleting...' : 'Delete'}
                </TerminalButton>
              </DeleteModalFooter>
            </DeleteModalContent>
          </DeleteModalOverlay>
        )}
      </ViewerContainer>
    );
  }

  return (
    <ViewerContainer>
      <ExecutionHealthBanner simulationId={simulationId} status={simulation.status} />

      <SimulationDetailHeader />

      {(() => {
        const canRestart =
          !!simulation &&
          simulation.brokerType !== 'ALPACA_PAPER' &&
          simulation.brokerType !== 'ALPACA_LIVE' &&
          (simulation.status === 'COMPLETED' ||
            ((simulation.status === 'SIMULATING' || simulation.status === 'LIVE') && simulation.paused === true));

        const openRestart = (consensusDecisionId: string, decisionTimestamp: string) => {
          const numericId = Number(consensusDecisionId);
          if (!Number.isNaN(numericId)) {
            setRestartFromState({ consensusDecisionId: numericId, decisionTimestamp });
          }
        };

        return (
          <>
            <V1Grid>
              <div className="gc-chart"><ChartPanel onInspect={setInspectedConsensusId} /></div>
              <div className="gc-snapshot"><HoldingsPanel /></div>
              <div className="gc-decisions">
                <DecisionsPanel
                  canRestart={canRestart}
                  onRestartFrom={openRestart}
                  onInspect={setInspectedConsensusId}
                />
              </div>
              <div className="gc-agents"><AgentsPanel /></div>
              {/* A simulation with no contributions is the common case and should
                  not carry DCA chrome it has no data for. */}
              {(simulation?.contributions?.length ?? 0) > 0 && (
                <div className="gc-dca"><DcaPanel /></div>
              )}
            </V1Grid>

            {inspectedConsensusId && (
              <ConsensusInspectModal
                consensusId={inspectedConsensusId}
                onClose={() => setInspectedConsensusId(null)}
                canRestart={canRestart}
                onRestartFrom={(consensusId, decisionTimestamp) => {
                  setInspectedConsensusId(null);
                  openRestart(consensusId, decisionTimestamp);
                }}
              />
            )}
          </>
        );
      })()}

      <NewsSentiment />

      {/* Restart From Decision Modal */}
      {restartFromState && (
        <RestartFromModal
          simulationId={simulationId}
          consensusDecisionId={restartFromState.consensusDecisionId}
          decisionTimestamp={restartFromState.decisionTimestamp}
          onClose={() => setRestartFromState(null)}
        />
      )}

      {/* Delete Confirmation Modal */}
      {showDeleteModal && (
        <DeleteModalOverlay onClick={cancelDelete}>
          <DeleteModalContent onClick={(e) => e.stopPropagation()}>
            <DeleteModalHeader>
              <DeleteModalTitle>⚠️ Confirm Deletion</DeleteModalTitle>
            </DeleteModalHeader>
            <DeleteModalBody>
              <DeleteModalText>
                Are you sure you want to delete this simulation?
              </DeleteModalText>
              <DeleteModalText>
                This will permanently delete:
              </DeleteModalText>
              <DeleteModalList>
                <li>The simulation</li>
                <li>All portfolio snapshots</li>
                <li>All LLM decisions</li>
                <li>All consensus decisions</li>
                <li>All performance metrics</li>
              </DeleteModalList>
              <DeleteModalWarning>
                ⚠ This action cannot be undone
              </DeleteModalWarning>
            </DeleteModalBody>
            <DeleteModalFooter>
              <TerminalButton
                variant="ghost"
                onClick={cancelDelete}
                disabled={deleteMutation.isPending}
              >
                Cancel
              </TerminalButton>
              <TerminalButton
                variant="danger"
                onClick={confirmDelete}
                disabled={deleteMutation.isPending}
              >
                {deleteMutation.isPending ? 'Deleting...' : 'Delete Simulation'}
              </TerminalButton>
            </DeleteModalFooter>
          </DeleteModalContent>
        </DeleteModalOverlay>
      )}

      {/* End Simulation Modal */}
      {showEndModal && (
        <DeleteModalOverlay onClick={cancelEndSimulation}>
          <EndModalContent onClick={(e) => e.stopPropagation()}>
            <EndModalHeader>
              <EndModalTitle>End Simulation</EndModalTitle>
            </EndModalHeader>
            <DeleteModalBody>
              <DeleteModalText>
                This will permanently end the simulation and set its status to COMPLETED.
                The simulation cannot be restarted after ending.
              </DeleteModalText>

              <CheckboxLabel>
                <input
                  type="checkbox"
                  checked={liquidateOnEnd}
                  onChange={(e) => setLiquidateOnEnd(e.target.checked)}
                />
                Liquidate all positions before ending
              </CheckboxLabel>

              {!liquidateOnEnd && (simulation?.brokerType === 'ALPACA_PAPER' || simulation?.brokerType === 'ALPACA_LIVE') && (
                <EndModalWarning>
                  Positions will remain open in Alpaca without QuorumTrading monitoring.
                  You will need to manage them manually in your broker account.
                </EndModalWarning>
              )}
            </DeleteModalBody>
            <DeleteModalFooter>
              <TerminalButton
                variant="ghost"
                onClick={cancelEndSimulation}
                disabled={forceEndMutation.isPending}
              >
                Cancel
              </TerminalButton>
              <TerminalButton
                variant="danger"
                onClick={confirmEndSimulation}
                disabled={forceEndMutation.isPending}
              >
                {forceEndMutation.isPending ? 'Ending...' : 'End Simulation'}
              </TerminalButton>
            </DeleteModalFooter>
          </EndModalContent>
        </DeleteModalOverlay>
      )}
    </ViewerContainer>
  );
}
