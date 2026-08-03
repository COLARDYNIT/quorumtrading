'use client';

import { use, useMemo, useState } from 'react';
import Link from 'next/link';
import styled from 'styled-components';
import { DashboardLayout } from '@/app/components/layouts/DashboardLayout';
import {
  useStrategy,
  useStrategyPerformance,
  useSimulations,
  useDividendInfo,
  useToggleStrategyVisibility,
  useDeleteStrategy,
  useChallenges,
} from '@/app/lib/api/hooks';
import { useAuth } from '@/app/hooks/useAuth';
import type { DividendInfoItem } from '@/app/lib/types/api';
import { parseStrategyYaml } from './lib/parseStrategyYaml';
import { DeleteStrategyModal } from '@/app/components/strategies/DeleteStrategyModal';
import {
  StrategyDetailHeader,
  SimsTablePanel,
  RulesPanel,
  AgentCouncilPanel,
  DefinitionPanel,
  AtAGlancePanel,
  PromoteCta,
  ActionsPanel,
} from './components/v2';

/**
 * Strategy Detail — v2 (post-Strategy-Detail-v2.html redesign).
 * Pane-head with description + meta row sits at the top; the body is a 2fr/1fr
 * split: strategy.md + Promote CTA + Simulations on the left, At-a-glance +
 * Agent council + Rules + Actions on the right.
 */
interface PageProps {
  params: Promise<{ id: string }>;
}

export default function StrategyDetailPage({ params }: PageProps) {
  const { id } = use(params);
  const { user } = useAuth();
  const { data: strategy, isLoading: strategyLoading, error: strategyError } = useStrategy(id);
  const { data: performance, isLoading: performanceLoading } = useStrategyPerformance(id);
  const { data: challenges } = useChallenges();
  const { data: allSimulations, isLoading: simulationsLoading } = useSimulations();
  const toggleVisibility = useToggleStrategyVisibility();
  const deleteMutation = useDeleteStrategy();

  const [confirmDelete, setConfirmDelete] = useState(false);

  const canTogglePublish = user?.role === 'ADMIN' && strategy?.ownerId === user?.id;
  const isAdmin = user?.role === 'ADMIN';
  const isLoading = strategyLoading || performanceLoading || simulationsLoading;

  const strategySimulations = useMemo(
    () => (allSimulations ?? []).filter((s) => s.strategyId === id),
    [allSimulations, id],
  );

  const counts = useMemo(() => {
    let live = 0, completed = 0, failed = 0;
    for (const s of strategySimulations) {
      if (
        s.status === 'LIVE' || s.status === 'SIMULATING' ||
        s.status === 'PENDING' || s.status === 'WAITING_FOR_DATA'
      ) {
        live++;
      } else if (s.status === 'COMPLETED') {
        completed++;
      } else if (s.status === 'FAILED') {
        failed++;
      }
    }
    return { live, completed, failed };
  }, [strategySimulations]);

  const parsedConfig = useMemo(
    () => parseStrategyYaml(strategy?.markdownContent),
    [strategy?.markdownContent],
  );

  const matchingChallenges = useMemo(() => {
    if (!challenges || parsedConfig.tickers.length === 0) return [];
    const upper = parsedConfig.tickers.map((t) => t.toUpperCase());
    return challenges.filter((c) => upper.includes(c.ticker.toUpperCase()));
  }, [challenges, parsedConfig.tickers]);

  const acceptingMatchingChallenges = matchingChallenges.filter(
    (c) => c.status === 'ACTIVE' && c.acceptingEntries,
  );

  if (isLoading) {
    return (
      <DashboardLayout>
        <Page>
          <Loading>Loading strategy…</Loading>
        </Page>
      </DashboardLayout>
    );
  }

  if (strategyError) {
    return (
      <DashboardLayout>
        <Page>
          <ErrorBox>
            Failed to load strategy — {strategyError instanceof Error ? strategyError.message : 'unknown error'}.
          </ErrorBox>
          <BackLink href="/strategies">← Back to strategies</BackLink>
        </Page>
      </DashboardLayout>
    );
  }

  if (!strategy) {
    return (
      <DashboardLayout>
        <Page>
          <ErrorBox>Strategy not found.</ErrorBox>
          <BackLink href="/strategies">← Back to strategies</BackLink>
        </Page>
      </DashboardLayout>
    );
  }

  const newSimHref = `/benchmarks/new?strategyId=${id}`;
  const backfillHref = isAdmin ? `/admin/data-availability?strategyId=${id}` : undefined;
  const hasSims = strategySimulations.length > 0;
  const totalSims = strategySimulations.length;
  const hasLiveSims = counts.live > 0;

  return (
    <DashboardLayout>
      <Page>
        <StrategyDetailHeader
          strategy={strategy}
          liveCount={counts.live}
          completedCount={counts.completed}
        />

        {(acceptingMatchingChallenges.length > 0 || parsedConfig.tickers.length > 0) && (
          <Banners>
            {acceptingMatchingChallenges.map((c) => (
              <ChallengeCta key={c.id} href={`/challenges/${c.slug}?joinWith=${id}`}>
                <span className="text">
                  This strategy matches the <strong>{c.name}</strong> challenge ({c.ticker})
                </span>
                <span className="action">Join now →</span>
              </ChallengeCta>
            ))}
            {parsedConfig.tickers.length > 0 && (
              <DividendInfoBanner tickers={parsedConfig.tickers} />
            )}
          </Banners>
        )}

        <Body>
          <LeftCol>
            <DefinitionPanel
              yamlContent={strategy.markdownContent}
              linkedChallenges={matchingChallenges}
              strategyId={id}
            />
            <PromoteCta
              newSimHref={newSimHref}
              backfillHref={backfillHref}
              highlighted={!hasSims}
            />
            <SimsTablePanel
              simulations={strategySimulations}
              newHref={newSimHref}
            />
          </LeftCol>
          <RightCol>
            <AtAGlancePanel
              performance={performance}
              liveCount={counts.live}
              completedCount={counts.completed}
              failedCount={counts.failed}
              publicVisible={!!strategy.publicVisible}
              canTogglePublish={!!canTogglePublish}
              onTogglePublish={() => toggleVisibility.mutate({
                id: strategy.id,
                publicVisible: !strategy.publicVisible,
              })}
              togglePending={toggleVisibility.isPending}
            />
            <AgentCouncilPanel
              consensusMethod={parsedConfig.consensusMethod}
              consensusQuorum={parsedConfig.consensusQuorum}
              agents={parsedConfig.agents}
            />
            <RulesPanel
              config={parsedConfig}
              version={strategy.version}
            />
            <ActionsPanel
              strategyId={id}
              publicVisible={!!strategy.publicVisible}
              canTogglePublish={!!canTogglePublish}
              hasSims={hasSims}
              togglePending={toggleVisibility.isPending}
              deletePending={deleteMutation.isPending}
              onTogglePublish={() => toggleVisibility.mutate({
                id: strategy.id,
                publicVisible: !strategy.publicVisible,
              })}
              onDelete={() => {
                if (hasSims) {
                  setConfirmDelete(true);
                } else {
                  deleteMutation.mutate(strategy.id);
                }
              }}
              isAdmin={isAdmin}
            />
          </RightCol>
        </Body>

        {confirmDelete && (
          <DeleteStrategyModal
            isOpen
            onClose={() => setConfirmDelete(false)}
            strategyId={strategy.id}
            strategyName={strategy.name}
            simulationCount={totalSims}
            hasLiveSims={hasLiveSims}
          />
        )}
      </Page>
    </DashboardLayout>
  );
}

function DividendInfoBanner({ tickers }: { tickers: string[] }) {
  const { data } = useDividendInfo(tickers);
  const dividendPayers = useMemo(() => {
    if (!data?.items) return [] as DividendInfoItem[];
    return data.items.filter((item: DividendInfoItem) => item.paysDividends);
  }, [data]);
  if (dividendPayers.length === 0) return null;
  const summary = dividendPayers.map((item: DividendInfoItem) => {
    const yld = item.annualYieldEstimate != null
      ? ` (~$${item.annualYieldEstimate.toFixed(2)}/share)`
      : '';
    return `${item.ticker}${yld}`;
  }).join(', ');
  return (
    <DividendBanner>
      <div className="ttl">Dividend Notice</div>
      <div className="msg">
        This strategy includes dividend-paying stocks: {summary}.
        Backtest results reflect price returns only — dividend income is not tracked.
      </div>
    </DividendBanner>
  );
}

// ─── styled ─────────────────────────────────────────────────────────────────

const Page = styled.div`
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px 24px 28px;
  min-width: 0;

  @media (max-width: 640px) {
    padding: 12px 16px 24px;
  }
`;

const Body = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
  gap: 16px;
  align-items: start;

  @media (max-width: 980px) {
    grid-template-columns: 1fr;
  }
`;

const LeftCol = styled.div`
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
`;

const RightCol = styled.div`
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
`;

const Banners = styled.div`
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

const DividendBanner = styled.div`
  padding: 14px 18px;
  border: 1px solid rgba(255, 176, 0, 0.4);
  border-left: 3px solid ${p => p.theme.colors.terminalAmber};
  background: rgba(255, 176, 0, 0.06);
  border-radius: 4px;

  & > .ttl {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 11px;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.terminalAmber};
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
    margin-bottom: 4px;
  }
  & > .msg {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 13.5px;
    color: ${p => p.theme.colors.fgMuted};
    line-height: 1.55;
  }
`;

const ChallengeCta = styled(Link)`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 18px;
  background: rgba(6, 255, 165, 0.06);
  border: 1px solid rgba(6, 255, 165, 0.4);
  border-radius: 4px;
  text-decoration: none;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  transition: border-color 120ms ease, transform 120ms ease;

  &:hover { border-color: ${p => p.theme.colors.action.buy}; transform: translateY(-1px); }

  & > .text {
    font-size: 13.5px;
    color: ${p => p.theme.colors.foreground};
  }
  & > .text strong {
    color: ${p => p.theme.colors.action.buy};
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
  }
  & > .action {
    font-size: 13px;
    color: ${p => p.theme.colors.brand};
    white-space: nowrap;
    font-family: ${p => p.theme.typography.fontFamily.mono};
  }
`;

const Loading = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13px;
  color: ${p => p.theme.colors.fgMuted};
  text-align: center;
  padding: 48px 16px;
`;

const ErrorBox = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13.5px;
  color: ${p => p.theme.colors.negative};
  padding: 14px 16px;
  border-radius: 4px;
  border: 1px solid rgba(255, 51, 102, 0.4);
  background: rgba(255, 51, 102, 0.06);
`;

const BackLink = styled(Link)`
  display: inline-block;
  margin-top: 12px;
  padding: 7px 13px;
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 2px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13.5px;
  color: ${p => p.theme.colors.foreground};
  text-decoration: none;
  background: transparent;

  &:hover { background: rgba(255, 255, 255, 0.04); border-color: ${p => p.theme.colors.fgMuted}; }
`;
