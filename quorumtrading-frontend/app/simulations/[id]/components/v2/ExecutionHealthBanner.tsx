'use client';

import styled from 'styled-components';
import { useSimulationHealth } from '@/app/lib/api/hooks';
import type { SimulationHealth, SimulationStatus } from '@/app/lib/types/api';

/**
 * Amber banner that explains a run that is not progressing, fed by the backend's
 * execution-health read model (GET /simulations/{id}/status).
 *
 * Before this, a stalled simulation showed four empty panels each saying "nothing yet",
 * indistinguishable from a healthy run waiting for its first round — while the reasons sat
 * unread in persisted SKIP rows. The banner renders only when the verdict warrants it
 * (STALLED or a system pause); healthy, idle and user-paused runs stay banner-free.
 */

const Banner = styled.div`
  display: flex;
  align-items: flex-start;
  gap: ${p => p.theme.spacing.md};
  padding: ${p => p.theme.spacing.md} ${p => p.theme.spacing.lg};
  margin-bottom: ${p => p.theme.spacing.lg};
  background: ${p => p.theme.colors.terminalAmber}15;
  border: 1px solid ${p => p.theme.colors.terminalAmber}44;
  border-left: 4px solid ${p => p.theme.colors.terminalAmber};
  font-family: ${p => p.theme.typography.fontFamily.mono};
`;

const Icon = styled.span`
  font-size: 1.4rem;
  flex-shrink: 0;
`;

const Content = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
`;

const Title = styled.span`
  color: ${p => p.theme.colors.terminalAmber};
  font-size: ${p => p.theme.typography.fontSize.sm};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  text-transform: uppercase;
  letter-spacing: ${p => p.theme.typography.letterSpacing.wide};
`;

const Message = styled.span`
  color: ${p => p.theme.colors.textMuted};
  font-size: ${p => p.theme.typography.fontSize.sm};
`;

const Reason = styled.span`
  color: ${p => p.theme.colors.textMuted};
  font-size: ${p => p.theme.typography.fontSize.xs};
  overflow-wrap: anywhere;
`;

const ACTIVE_STATUSES: SimulationStatus[] = ['PENDING', 'WAITING_FOR_DATA', 'SIMULATING', 'LIVE'];

interface BannerCopy {
  icon: string;
  title: string;
  message: string;
}

function copyFor(health: SimulationHealth): BannerCopy | null {
  if (health.verdict === 'STALLED') {
    return {
      icon: '⏳',
      title: 'Stalled — rounds run, but every one is skipped',
      message: `${health.consecutiveSkippedRows} consecutive skipped round(s). Most often the LLM engine `
        + 'is slow or unreachable — check the engine in Settings → Config. Parallel simulations share '
        + 'one local engine; pausing one frees it for the other.',
    };
  }
  if (health.verdict === 'PAUSED_EXTERNAL') {
    switch (health.externalPauseReason) {
      case 'BUDGET_DEPLETED':
        return {
          icon: '⚡',
          title: 'Paused — daily AI budget exceeded',
          message: 'This simulation will resume automatically when your daily budget resets or you top up your balance.',
        };
      case 'DATA_GAP_DETECTED':
        return {
          icon: '📉',
          title: 'Paused — repeated no-market-data skips',
          message: 'The circuit breaker paused the run and requested a re-backfill. It resumes when the missing price data arrives.',
        };
      case 'LLM_FAILURE_DETECTED':
        return {
          icon: '🤖',
          title: 'Paused — the LLM engine stopped answering',
          message: 'Repeated rounds produced no decision because the engine failed or timed out. Fix or switch the engine in Settings → Config, then resume the simulation.',
        };
      default:
        return {
          icon: '⏸',
          title: 'Paused by the system',
          message: health.detail,
        };
    }
  }
  return null;
}

export function ExecutionHealthBanner({ simulationId, status }: {
  simulationId: string;
  status: SimulationStatus | undefined;
}) {
  const enabled = !!status && ACTIVE_STATUSES.includes(status);
  const { data: health } = useSimulationHealth(simulationId, enabled);

  if (!enabled || !health) return null;
  const copy = copyFor(health);
  if (!copy) return null;

  const topReason = health.topSkipReasons?.[0];

  return (
    <Banner data-testid="execution-health-banner" data-verdict={health.verdict}>
      <Icon>{copy.icon}</Icon>
      <Content>
        <Title>{copy.title}</Title>
        <Message>{copy.message}</Message>
        {health.verdict === 'STALLED' && topReason && (
          <Reason>
            Last skip reason ({topReason.count}×): {topReason.reason}
          </Reason>
        )}
      </Content>
    </Banner>
  );
}
