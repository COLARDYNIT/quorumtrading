'use client';

import { useState, useEffect, useMemo, useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import styled, { keyframes } from 'styled-components';
import { DashboardLayout } from '../components/layouts/DashboardLayout';
import { WelcomeModal } from '../components/dashboard/WelcomeModal';
import { WelcomeBackModal } from '../components/dashboard/WelcomeBackModal';
import { useAuth } from '@/app/hooks/useAuth';
import {
  useStrategies,
  useMarketResearch,
  useSimulations,
  useBrokerAccounts,
  useUnreadNotificationCount,
  queryKeys,
} from '@/app/lib/api/hooks';
import { useStreamMessage } from '@/app/lib/websocket/hooks';
import type { SimulationCompletedMessage, PortfolioUpdateMessage } from '@/app/lib/types/websocket';
import type { BrokerType } from '@/app/lib/types/api';
import { fadeIn } from '@/app/styles/animations';
import { featureFlags } from '@/app/lib/featureFlags';
import {
  AccountScopeRow,
  ControlKpis,
  CtaBanner,
  LiveStrategiesPanel,
  RecentDecisionsPanel,
  AgentHeatmapPanel,
  UpcomingPanel,
  StrategyRosterPanel,
  NotificationsPanel,
  ResearchPanel,
  ChallengePanel,
  OnboardingHero,
  type ScopeId,
} from './components/v2';

// ===== Layout chrome =====

const Dash = styled.div`
  padding: 16px 24px 28px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  animation: ${fadeIn} 0.3s ease-out;

  @media (max-width: 640px) {
    padding: 12px 14px 24px;
  }
`;

const Greet = styled.div`
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
`;

const H1 = styled.h1`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 22px;
  letter-spacing: -0.005em;
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  margin: 0;

  .sub {
    font-size: 13px;
    color: ${p => p.theme.colors.fgMuted};
    font-weight: ${p => p.theme.typography.fontWeight.normal};
    margin-left: 8px;
    font-family: ${p => p.theme.typography.fontFamily.mono};
  }
`;

const Since = styled.div`
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  display: flex;
  gap: 14px;
  flex-wrap: wrap;

  b { color: ${p => p.theme.colors.foreground}; font-weight: ${p => p.theme.typography.fontWeight.medium}; }
  .pulse { color: ${p => p.theme.colors.brand}; }
`;

const Grid = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;

  @media (max-width: 980px) {
    grid-template-columns: 1fr;
  }
`;

// ===== Loading / error states (pattern shared with app/simulations/page.tsx) =====

function DashSkeleton() {
  return (
    <>
      <SkelKpis aria-hidden="true">
        {[0, 1, 2, 3].map(i => (
          <div className="cell" key={i}>
            <SkelLine $w={90} $h={10} />
            <SkelLine $w={60} $h={22} style={{ marginTop: 8 }} />
          </div>
        ))}
      </SkelKpis>
      <Grid aria-hidden="true">
        {[0, 1, 2, 3].map(i => (
          <SkelPanel key={i}>
            <SkelLine $w={160} $h={12} />
            <SkelLine $w={260} $h={10} style={{ marginTop: 14 }} />
            <SkelLine $w={220} $h={10} style={{ marginTop: 8 }} />
            <SkelLine $w={240} $h={10} style={{ marginTop: 8 }} />
          </SkelPanel>
        ))}
      </Grid>
    </>
  );
}

const shimmer = keyframes`
  0% { background-position: -200px 0; }
  100% { background-position: 200px 0; }
`;

const SkelLine = styled.span<{ $w: number; $h: number }>`
  display: inline-block;
  width: ${p => p.$w}px;
  height: ${p => p.$h}px;
  background: linear-gradient(
    90deg,
    ${p => p.theme.colors.borderSubtle} 0%,
    rgba(255, 255, 255, 0.04) 50%,
    ${p => p.theme.colors.borderSubtle} 100%
  );
  background-size: 200px 100%;
  border-radius: 1px;
  animation: ${shimmer} 1.6s linear infinite;
`;

const SkelKpis = styled.div`
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1px;
  background: ${p => p.theme.colors.borderSubtle};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 4px;
  overflow: hidden;

  & > .cell {
    background: ${p => p.theme.colors.surface};
    padding: 14px 16px;
    display: flex;
    flex-direction: column;
  }

  @media (max-width: 640px) {
    grid-template-columns: repeat(2, 1fr);
  }
`;

const SkelPanel = styled.div`
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 4px;
  padding: 16px 18px;
  min-height: 160px;
  display: flex;
  flex-direction: column;
`;

const ErrorBox = styled.div`
  border: 1px solid rgba(255, 51, 102, 0.4);
  background: rgba(255, 51, 102, 0.06);
  color: ${p => p.theme.colors.negative};
  padding: 14px 16px;
  border-radius: 4px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13.5px;
`;

// ===== Page =====

export default function DashboardPage() {
  const queryClient = useQueryClient();
  const { user } = useAuth();

  const {
    data: strategies,
    isLoading: strategiesLoading,
    isError: strategiesIsError,
    error: strategiesError,
  } = useStrategies({ refetchInterval: 30_000 });
  const {
    data: simulations,
    isLoading: simulationsLoading,
    isError: simulationsIsError,
    error: simulationsError,
  } = useSimulations({ refetchInterval: 10_000 });
  const { data: marketResearch, isLoading: researchLoading } = useMarketResearch({ refetchInterval: 30_000 });
  const { data: brokerAccounts, isLoading: brokersLoading } = useBrokerAccounts();
  const { data: unreadCount } = useUnreadNotificationCount({ refetchInterval: 30_000 });

  const [scope, setScope] = useState<ScopeId>('all');

  // ===== Welcome modals (kept from previous dashboard) =====
  const [showWelcomeModal, setShowWelcomeModal] = useState(false);
  const [showWelcomeBackModal, setShowWelcomeBackModal] = useState(false);

  useEffect(() => {
    if (user?.welcomeBudgetCredited && typeof window !== 'undefined') {
      if (localStorage.getItem('quorumtrading:welcome:dismissed') !== 'true') setShowWelcomeModal(true);
    }
  }, [user]);

  useEffect(() => {
    if (user?.returningUser && !user?.welcomeBudgetCredited && typeof window !== 'undefined') {
      if (localStorage.getItem('quorumtrading:welcome-back:dismissed') !== 'true') setShowWelcomeBackModal(true);
    }
  }, [user]);

  // ===== WebSocket invalidations (kept) =====
  const handleSimulationCompleted = useCallback(
    (_: SimulationCompletedMessage) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.simulations });
      queryClient.invalidateQueries({
        queryKey: ['strategies'],
        predicate: q => q.queryKey.length === 3 && q.queryKey[0] === 'strategies' && q.queryKey[2] === 'performance',
      });
    },
    [queryClient],
  );
  const handlePortfolioUpdate = useCallback(
    (message: PortfolioUpdateMessage) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.portfolioLatest(message.simulationId) });
    },
    [queryClient],
  );
  useStreamMessage<SimulationCompletedMessage>('SIMULATION_COMPLETED', handleSimulationCompleted);
  useStreamMessage<PortfolioUpdateMessage>('PORTFOLIO_UPDATE', handlePortfolioUpdate);

  const handleDismissWelcome = () => {
    setShowWelcomeModal(false);
    if (typeof window !== 'undefined') {
      localStorage.setItem('quorumtrading:welcome:dismissed', 'true');
      localStorage.removeItem('quorumtrading:onboarding:dismissed');
    }
  };
  const handleDismissWelcomeBack = () => {
    setShowWelcomeBackModal(false);
    if (typeof window !== 'undefined') localStorage.setItem('quorumtrading:welcome-back:dismissed', 'true');
  };

  // ===== Derived state =====
  const allSims = simulations ?? [];
  const accounts = brokerAccounts ?? [];
  const liveSimsAll = useMemo(
    () => allSims.filter(s => s.status === 'LIVE' || s.status === 'SIMULATING'),
    [allSims],
  );

  const liveSimsByBrokerType = useMemo<Record<BrokerType, number>>(() => {
    const acc: Record<BrokerType, number> = { MOCK: 0, ALPACA_PAPER: 0, ALPACA_LIVE: 0, SAXO_SIM: 0, SAXO_LIVE: 0 };
    liveSimsAll.forEach(s => {
      if (s.brokerType) acc[s.brokerType] = (acc[s.brokerType] ?? 0) + 1;
    });
    return acc;
  }, [liveSimsAll]);

  /**
   * Filter sims to the active scope cell. Because SimulationResponse only carries
   * `brokerType` (not brokerAccountId), the scope cells filter by brokerType:
   * the cell for an Alpaca-paper account matches every sim with brokerType=ALPACA_PAPER.
   */
  const scopedLiveSims = useMemo(() => {
    if (scope === 'all') return liveSimsAll;
    const account = accounts.find(a => a.id === scope);
    if (!account) return liveSimsAll;
    return liveSimsAll.filter(s => s.brokerType === account.brokerType);
  }, [scope, liveSimsAll, accounts]);

  // Loading covers every dataset the layout branches on, so the control-room
  // grid (or OnboardingHero) never flashes with half-loaded data.
  const isDashLoading = strategiesLoading || simulationsLoading || researchLoading || brokersLoading;
  const isDashError = strategiesIsError || simulationsIsError;
  const dashErrorMessage = (() => {
    const err = strategiesIsError ? strategiesError : simulationsError;
    return err instanceof Error ? err.message : 'unknown error';
  })();

  const hasStrategies = (strategies?.length ?? 0) > 0;
  const hasSimulations = (simulations?.length ?? 0) > 0;
  const hasResearch = (marketResearch?.length ?? 0) > 0;
  const hasBrokerAccount = accounts.length > 0;
  const isNewUser =
    strategies != null &&
    marketResearch != null &&
    simulations != null &&
    !hasStrategies &&
    !hasResearch &&
    !hasSimulations;

  // Display values
  const totalSimCount = allSims.length;
  const stratCount = strategies?.length ?? 0;
  const subline = isDashLoading
    ? 'syncing…'
    : isNewUser
      ? 'no live simulations'
      : `${scopedLiveSims.length} active sim${scopedLiveSims.length === 1 ? '' : 's'} · ${stratCount} strateg${stratCount === 1 ? 'y' : 'ies'}${totalSimCount > 0 ? ` · ${totalSimCount} total run${totalSimCount === 1 ? '' : 's'}` : ''}`;

  // Date + last-seen are computed client-side only so SSR HTML and client HTML stay aligned.
  const [nowLabel, setNowLabel] = useState<string | null>(null);
  const [lastSeenLabel, setLastSeenLabel] = useState<string | null>(null);
  useEffect(() => {
    setNowLabel(new Date().toLocaleString('en-US', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false }));
    try {
      const v = localStorage.getItem('quorumtrading:dashboard:last-seen');
      localStorage.setItem('quorumtrading:dashboard:last-seen', new Date().toISOString());
      if (v) {
        const d = new Date(v);
        setLastSeenLabel(d.toLocaleString('en-US', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', hour12: false }));
      }
    } catch { /* ignore */ }
  }, []);

  return (
    <DashboardLayout>
      <WelcomeModal isOpen={showWelcomeModal} onClose={handleDismissWelcome} userName={user?.displayName ?? undefined} />
      <WelcomeBackModal isOpen={showWelcomeBackModal} onClose={handleDismissWelcomeBack} userName={user?.displayName ?? undefined} />

      <Dash>
        <Greet>
          <H1>
            {isNewUser ? <>Welcome{user?.displayName ? `, ${user.displayName}` : ''}</> : 'Control room'}
            <span className="sub">{subline}</span>
          </H1>
          <Since>
            <span><span className="pulse">●</span> <b>{nowLabel ?? '—'}</b></span>
            {lastSeenLabel && !isNewUser && <span>· last seen <b>{lastSeenLabel}</b></span>}
          </Since>
        </Greet>

        {isDashError ? (
          <ErrorBox>
            Failed to load dashboard data — {dashErrorMessage}.
          </ErrorBox>
        ) : isDashLoading ? (
          <DashSkeleton />
        ) : isNewUser ? (
          <OnboardingHero
            hasResearch={hasResearch}
            hasStrategies={hasStrategies}
            hasSimulations={hasSimulations}
            hasBrokerAccount={hasBrokerAccount}
          />
        ) : (
          <>
            {accounts.length > 0 && (
              <AccountScopeRow
                accounts={accounts}
                liveSimsByBrokerType={liveSimsByBrokerType}
                activeId={scope}
                onChange={setScope}
              />
            )}

            <ControlKpis
              liveSims={scopedLiveSims}
              accounts={scope === 'all' ? accounts : accounts.filter(a => a.id === scope)}
              unreadNotificationCount={unreadCount?.unread ?? 0}
              strategyCount={stratCount}
            />

            <CtaBanner />

            <Grid>
              <LiveStrategiesPanel liveSims={scopedLiveSims} strategies={strategies ?? []} />
              <RecentDecisionsPanel liveSims={scopedLiveSims} strategies={strategies ?? []} />

              <AgentHeatmapPanel liveSims={scopedLiveSims} />
              {featureFlags.investmentPlans && <UpcomingPanel />}

              <StrategyRosterPanel strategies={strategies ?? []} simulations={allSims} />
              <NotificationsPanel />

              <ResearchPanel />
              <ChallengePanel />
            </Grid>
          </>
        )}
      </Dash>
    </DashboardLayout>
  );
}
