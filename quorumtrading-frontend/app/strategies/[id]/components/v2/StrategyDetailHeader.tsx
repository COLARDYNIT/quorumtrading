'use client';

import { useMemo } from 'react';
import styled from 'styled-components';
import { Tag } from '@/app/components/shared/v2';
import { tagBase, tagIdentifier } from '@/app/styles/labels';
import type { StrategyResponse } from '@/app/lib/types/api';
import { parseStrategyYaml } from '../../lib/parseStrategyYaml';

interface Props {
  strategy: StrategyResponse;
  liveCount: number;
  completedCount: number;
}

type Status = 'live' | 'backtest' | 'unused';

function shortDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleDateString('en-US', {
      day: '2-digit', month: 'short', year: 'numeric',
    });
  } catch { return iso; }
}

/**
 * Strategy Detail page-head — v2. Drops the wide 6-cell KPI strip and surfaces
 * the description paragraph instead; aggregate stats move to the right-rail
 * `AtAGlancePanel`. Mirrors the pane-head pattern the design canvas locked in.
 */
export function StrategyDetailHeader({ strategy, liveCount, completedCount }: Props) {
  const parsed = useMemo(() => parseStrategyYaml(strategy.markdownContent), [strategy.markdownContent]);
  const status: Status = liveCount > 0 ? 'live' : completedCount > 0 ? 'backtest' : 'unused';

  const tickers = parsed.tickers.length > 0 ? parsed.tickers : [];
  const cadence = parsed.evaluationTimes.length > 0
    ? parsed.evaluationTimes.join(' · ') + (parsed.timezone ? ` ${parsed.timezone}` : '')
    : null;

  return (
    <Head>
      <TitleRow>
        <Left>
          <H1>{strategy.name}</H1>
          {tickers.length > 0 && (
            <Tickers>
              {tickers.slice(0, 3).map(t => <Ticker key={t}>{t}</Ticker>)}
              {tickers.length > 3 && <Ticker $rest>+{tickers.length - 3}</Ticker>}
            </Tickers>
          )}
          <VersionPill>v{strategy.version}</VersionPill>
          {parsed.consensusMethod && (
            <ConsensusPill>{parsed.consensusMethod}</ConsensusPill>
          )}
          <StatusInline $status={status}>
            <StatusDot $status={status} />
            {status === 'live' ? 'Live' : status === 'backtest' ? 'Backtest only' : 'Unused'}
          </StatusInline>
          {strategy.publicVisible && (
            <Tag hue="cyan">Published</Tag>
          )}
        </Left>
      </TitleRow>
      {strategy.description && <Desc>{strategy.description}</Desc>}
      <MetaRow>
        <span><Dim>id</Dim> <Mono>#{strategy.id.slice(0, 8).toUpperCase()}</Mono></span>
        <Pipe>|</Pipe>
        <span><Dim>created</Dim> <Mono>{shortDate(strategy.createdAt)}</Mono></span>
        {cadence && (
          <>
            <Pipe>|</Pipe>
            <span><Dim>cadence</Dim> <Mono>{cadence}</Mono></span>
          </>
        )}
        {tickers.length > 0 && (
          <>
            <Pipe>|</Pipe>
            <span><Dim>tickers</Dim> <Mono>{tickers.join(', ')}</Mono></span>
          </>
        )}
      </MetaRow>
    </Head>
  );
}

const Head = styled.div`
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-bottom: 12px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
`;

const TitleRow = styled.div`
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
`;

const Left = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  min-width: 0;
`;

const H1 = styled.h1`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 24px;
  letter-spacing: -0.012em;
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  margin: 0;
`;

const Tickers = styled.span`
  display: inline-flex;
  gap: 4px;
`;

const Ticker = styled.span<{ $rest?: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  letter-spacing: 0.04em;
  color: ${p => p.$rest ? p.theme.colors.fgMuted : p.theme.colors.foreground};
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 2px;
  padding: 2px 7px;
`;

/** A version string is an identifier — rendered verbatim, never case-transformed. */
const VersionPill = styled.span`
  ${tagBase}
  ${tagIdentifier}
  color: ${p => p.theme.colors.fgMuted};
`;

const ConsensusPill = styled.span`
  ${tagBase}
  color: ${p => p.theme.colors.info};
`;

const StatusDot = styled.span<{ $status: Status }>`
  width: 7px;
  height: 7px;
  border-radius: 50%;
  display: inline-block;
  margin-right: 6px;
  background: ${p =>
    p.$status === 'live' ? p.theme.colors.brand :
    p.$status === 'backtest' ? p.theme.colors.fgMuted :
    'transparent'};
  box-shadow: ${p =>
    p.$status === 'live' ? '0 0 8px rgba(0, 255, 65, 0.4)' :
    p.$status === 'unused' ? `inset 0 0 0 1px ${p.theme.colors.fgSubtle}` :
    'none'};
`;

const StatusInline = styled.span<{ $status: Status }>`
  display: inline-flex;
  align-items: center;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 12.5px;
  color: ${p =>
    p.$status === 'live' ? p.theme.colors.brand :
    p.$status === 'backtest' ? p.theme.colors.fgMuted :
    p.theme.colors.fgDimmed};
  font-weight: ${p => p.$status === 'live'
    ? p.theme.typography.fontWeight.medium
    : p.theme.typography.fontWeight.normal};
`;

const Desc = styled.p`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 14.5px;
  line-height: 1.55;
  color: ${p => p.theme.colors.fgMuted};
  margin: 0;
  max-width: 78ch;
`;

const MetaRow = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
  margin-top: 2px;
`;

const Pipe = styled.span`
  color: ${p => p.theme.colors.border};
`;

const Dim = styled.span`
  color: ${p => p.theme.colors.fgDimmed};
`;

const Mono = styled.b`
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
`;
