'use client';

import { useMemo } from 'react';
import Link from 'next/link';
import styled from 'styled-components';
import { Panel } from '@/app/components/shared/v2';
import { useMarketResearch } from '@/app/lib/api/hooks';
import type { MarketResearchResponse } from '@/app/lib/api/client';
import { assetTicker } from '@/app/lib/utils/targetAssets';

interface ResearchPanelProps {
  /** Max rows to render. Default 4. */
  limit?: number;
}

const List = styled.div`
  display: flex; flex-direction: column;
`;

const Row = styled(Link)`
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
  align-items: center;
  padding: 10px 12px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  font-size: 12.5px;
  text-decoration: none;
  color: inherit;
  transition: background 120ms ease;

  &:last-child { border-bottom: none; }
  &:hover { background: rgba(255, 255, 255, 0.02); }

  .what { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
  .what .name { color: ${p => p.theme.colors.fgStrong}; font-size: 13px; font-weight: ${p => p.theme.typography.fontWeight.medium}; }
  .what .meta { color: ${p => p.theme.colors.fgMuted}; font-size: 11.5px; font-family: ${p => p.theme.typography.fontFamily.mono}; }
  .status { font-family: ${p => p.theme.typography.fontFamily.mono}; font-size: 11px; color: ${p => p.theme.colors.fgDimmed}; text-transform: uppercase; letter-spacing: 0.05em; }
`;

const Empty = styled.div`
  padding: 28px 16px; text-align: center;
  color: ${p => p.theme.colors.fgMuted}; font-size: 13px;

  a { color: ${p => p.theme.colors.brand}; text-decoration: none; }
  a:hover { text-decoration: underline; }
`;

function fmtDate(iso?: string): string {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleDateString('en-US', { day: '2-digit', month: 'short' });
  } catch { return '—'; }
}

export function ResearchPanel({ limit = 4 }: ResearchPanelProps) {
  const { data: research, isLoading } = useMarketResearch();

  const rows = useMemo(() => {
    const all = (research ?? []) as MarketResearchResponse[];
    const ts = (r: MarketResearchResponse) => new Date(r.lastExecutedAt ?? r.createdAt).getTime();
    return [...all]
      .sort((a, b) => ts(b) - ts(a))
      .slice(0, limit);
  }, [research, limit]);

  return (
    <Panel
      title="Research candidates"
      count={`${rows.length} recent`}
      noBodyPadding
      headerActions={<Link href="/research" style={{ fontSize: 10, color: 'var(--qt-fg-muted, #8a98a4)', textDecoration: 'none' }}>Open →</Link>}
    >
      {isLoading ? (
        <Empty>Loading…</Empty>
      ) : rows.length === 0 ? (
        <Empty>
          No market research yet. <Link href="/research/new">Start a scan →</Link>
        </Empty>
      ) : (
        <List>
          {rows.map(r => (
            <Row key={r.id} href={`/research/${r.id}`}>
              <div className="what">
                <span className="name">{r.name}</span>
                <span className="meta">
                  {Array.isArray(r.targetAssets) && r.targetAssets.length > 0 ? r.targetAssets.map(assetTicker).join(' · ') : 'no targets'}
                  {' · '}
                  {fmtDate(r.lastExecutedAt ?? r.createdAt)}
                </span>
              </div>
              <span className="status">{r.status}</span>
            </Row>
          ))}
        </List>
      )}
    </Panel>
  );
}
