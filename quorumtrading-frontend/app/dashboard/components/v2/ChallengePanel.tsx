'use client';

import Link from 'next/link';
import styled from 'styled-components';
import { Panel } from '@/app/components/shared/v2';
import { useChallenges } from '@/app/lib/api/hooks';
import { formatChallengeTimeRemaining } from '@/app/lib/types/challenge';
import type { ChallengeResponse } from '@/app/lib/types/challenge';

const Card = styled(Link)`
  display: flex; flex-direction: column; gap: 8px;
  padding: 14px;
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 3px;
  font-size: 12.5px;
  text-decoration: none;
  color: inherit;
  transition: border-color 120ms ease;

  &:hover { border-color: ${p => p.theme.colors.brand}55; }

  .name { color: ${p => p.theme.colors.foreground}; font-size: 13.5px; font-weight: ${p => p.theme.typography.fontWeight.medium}; }
  .meta { color: ${p => p.theme.colors.fgDimmed}; font-family: ${p => p.theme.typography.fontFamily.mono}; font-size: 11.5px; }
  .bar { height: 4px; background: ${p => p.theme.colors.borderSubtle}; border-radius: 1px; position: relative; margin: 4px 0; }
  .bar > i { position: absolute; left: 0; top: 0; bottom: 0; background: ${p => p.theme.colors.info}; border-radius: 1px; display: block; }
  .row { display: flex; justify-content: space-between; align-items: center; font-family: ${p => p.theme.typography.fontFamily.mono}; font-size: 11.5px; color: ${p => p.theme.colors.fgMuted}; }
  .row .pos { color: ${p => p.theme.colors.positive}; }
`;

const Empty = styled.div`
  padding: 28px 16px; text-align: center;
  color: ${p => p.theme.colors.fgMuted}; font-size: 13px;

  a { color: ${p => p.theme.colors.brand}; text-decoration: none; }
  a:hover { text-decoration: underline; }
`;

function fmtCash(n: number): string {
  return n.toLocaleString('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 });
}

function progress(c: ChallengeResponse): number {
  const start = new Date(c.simulationStartDate).getTime();
  const end = new Date(c.simulationEndDate).getTime();
  const now = Date.now();
  if (end <= start) return 0;
  return Math.max(0, Math.min(1, (now - start) / (end - start)));
}

export function ChallengePanel() {
  const { data: challenges, isLoading } = useChallenges();
  const active = (challenges ?? []).filter(c => c.status === 'ACTIVE' || c.status === 'UPCOMING');
  const total = challenges?.length ?? 0;
  const featured = active[0];

  return (
    <Panel
      title="Challenges"
      count={`${active.length} active · ${total} total`}
      headerActions={<Link href="/challenges" style={{ fontSize: 10, color: 'var(--qt-fg-muted, #8a98a4)', textDecoration: 'none' }}>Browse →</Link>}
    >
      {isLoading ? (
        <Empty>Loading…</Empty>
      ) : !featured ? (
        <Empty>
          No active challenges right now. <Link href="/challenges">Browse past →</Link>
        </Empty>
      ) : (
        <Card href={`/challenges/${featured.slug}`}>
          <span className="name">{featured.name}</span>
          <span className="meta">
            {featured.ticker} · {fmtCash(featured.initialCash)} · {featured.participantCount} participant{featured.participantCount === 1 ? '' : 's'}
          </span>
          <span className="bar"><i style={{ width: `${(progress(featured) * 100).toFixed(0)}%` }} /></span>
          <span className="row">
            <span>{featured.status === 'ACTIVE' ? formatChallengeTimeRemaining(featured.participationClose) : `Opens ${new Date(featured.participationOpen).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}`}</span>
            <span className={featured.acceptingEntries ? 'pos' : undefined}>
              {featured.acceptingEntries ? '→ Join' : 'View →'}
            </span>
          </span>
        </Card>
      )}
    </Panel>
  );
}
