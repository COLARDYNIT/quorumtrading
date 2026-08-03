'use client';

import Link from 'next/link';
import styled from 'styled-components';
import { useStrategyPerformance } from '@/app/lib/api/hooks';
import { labelCompact, tagBase } from '@/app/styles/labels';
import type { StrategyResponse } from '@/app/lib/api/client';
import { parseStrategyFrontmatter, allTickers } from './parseStrategyFrontmatter';

interface MagCardProps {
  strategy: StrategyResponse;
  isOwn?: boolean;
  onFork: (id: string) => void;
  canFork: boolean;
}

const Card = styled.article`
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 4px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: border-color 120ms ease, transform 120ms ease;

  &:hover { border-color: ${p => p.theme.colors.border}; }
`;

const Head = styled.div`
  padding: 18px 20px 14px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
`;

const TopRow = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const TickerRow = styled.span`
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
`;

const Ticker = styled.span`
  display: inline-flex;
  align-items: center;
  padding: 1px 6px;
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 2px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  color: ${p => p.theme.colors.foreground};
  background: ${p => p.theme.colors.background};
`;

const TitleLink = styled(Link)`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 17px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  letter-spacing: -0.005em;
  color: ${p => p.theme.colors.fgStrong};
  text-decoration: none;
  display: block;
  margin-bottom: 6px;
  line-height: 1.25;

  &:hover { color: ${p => p.theme.colors.brand}; }
`;

const Sub = styled.p`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13.5px;
  color: ${p => p.theme.colors.fgMuted};
  line-height: 1.45;
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
`;

const SparkStrip = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 20px;
  background: ${p => p.theme.colors.background};
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};

  & > .label {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11px;
    color: ${p => p.theme.colors.fgDimmed};
    text-transform: uppercase;
    letter-spacing: 0.06em;
    white-space: nowrap;
  }
  & > .ret-label { margin-left: auto; }
  & > .ret-label.pos { color: ${p => p.theme.colors.positive}; }
  & > .ret-label.neg { color: ${p => p.theme.colors.negative}; }
  & > svg { flex: 1; height: 30px; }
`;

const Stats = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};

  & > .s {
    padding: 10px 14px;
    border-right: 1px solid ${p => p.theme.colors.borderSubtle};
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  & > .s:last-child { border-right: none; }
  & > .s > .l {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 9px;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.fgDimmed};
  }
  & > .s > .v {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-variant-numeric: tabular-nums;
    font-size: 15px;
    color: ${p => p.theme.colors.fgStrong};
    font-weight: ${p => p.theme.typography.fontWeight.medium};
  }
  & > .s > .v.pos { color: ${p => p.theme.colors.positive}; }
  & > .s > .v.neg { color: ${p => p.theme.colors.negative}; }
  & > .s > .v.dim { color: ${p => p.theme.colors.fgDimmed}; }
`;

const Foot = styled.div`
  padding: 12px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;

  & > .left {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
  }
  & > .left .name {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 12.5px;
    color: ${p => p.theme.colors.foreground};
    font-weight: ${p => p.theme.typography.fontWeight.medium};
  }
  & > .left .meta {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11.5px;
    color: ${p => p.theme.colors.fgDimmed};
  }
`;

const Avatar = styled.span`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: ${p => p.theme.colors.brandTint};
  color: ${p => p.theme.colors.brand};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 9.5px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  text-transform: uppercase;
`;

const ForkBtn = styled.button`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 12.5px;
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  padding: 5px 12px;
  border-radius: 2px;
  border: 1px solid ${p => p.theme.colors.border};
  background: transparent;
  color: ${p => p.theme.colors.foreground};
  cursor: pointer;
  transition: border-color 120ms ease, color 120ms ease;

  &:hover { border-color: ${p => p.theme.colors.brand}; color: ${p => p.theme.colors.brand}; }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
`;

const OwnPill = styled.span`
  ${tagBase}
  ${labelCompact}
  color: ${p => p.theme.colors.brand};
`;

/**
 * Color the per-run-return sparkline by slope direction, not by best return.
 * A series of falling positives (e.g. [+111%, +102%]) should read amber, not
 * brand-green — even though every point is profitable, the trend is degrading.
 */
function miniSparkColor(data: number[]): string {
  if (data.length < 2) return 'var(--qt-fg-muted, #8a98a4)';
  if (data.some(v => v < 0)) return 'var(--qt-negative)';
  const first = data[0];
  const last = data[data.length - 1];
  if (last > first) return 'var(--qt-brand)';
  if (last < first) return 'var(--qt-amber)';
  return 'var(--qt-fg-muted, #8a98a4)';
}

function MiniSpark({ data }: { data: number[] }) {
  if (data.length < 2) {
    return <svg viewBox="0 0 100 30" preserveAspectRatio="none" />;
  }
  const W = 220, H = 30;
  const min = Math.min(...data), max = Math.max(...data);
  const range = max - min || 1;
  const pts = data
    .map((v, i) => {
      const x = (i / (data.length - 1)) * W;
      const y = H - 3 - ((v - min) / range) * (H - 6);
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .join(' ');
  return (
    <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none">
      <polyline points={pts} fill="none" stroke={miniSparkColor(data)} strokeWidth={1.4} />
    </svg>
  );
}

function shortAge(createdAt: string): string {
  try {
    const ms = Date.now() - new Date(createdAt).getTime();
    const days = Math.floor(ms / 86_400_000);
    if (days < 1) return 'today';
    if (days < 14) return `${days}d`;
    const weeks = Math.floor(days / 7);
    if (weeks < 8) return `${weeks}w`;
    const months = Math.floor(days / 30);
    if (months < 12) return `${months}mo`;
    const years = Math.floor(days / 365);
    return `${years}y`;
  } catch {
    return '—';
  }
}

export function MagCard({ strategy, isOwn = false, onFork, canFork }: MagCardProps) {
  const fm = parseStrategyFrontmatter(strategy.markdownContent);
  const tickers = allTickers(fm);
  const { data: perf } = useStrategyPerformance(strategy.id);
  const bestReturn = perf?.bestReturn ?? null;
  const sharpe = perf?.avgSharpeRatio ?? null;
  const worstReturn = perf?.worstReturn ?? null;
  const trend = perf?.recentTrend ?? [];
  // Owner display: we only have ownerId. Show short hash + "you" tag if own.
  const ownerLabel = isOwn ? 'you' : strategy.ownerId ? strategy.ownerId.slice(0, 8) : 'anonymous';
  const initials = (isOwn ? 'me' : ownerLabel.slice(0, 2)).toUpperCase();

  return (
    <Card>
      <Head>
        <TopRow>
          <TickerRow>
            {tickers.length > 0
              ? tickers.slice(0, 3).map(t => <Ticker key={t}>{t}</Ticker>)
              : <span style={{ color: 'var(--qt-fg-subtle)' }}>no tickers</span>}
            {tickers.length > 3 && <Ticker style={{ color: 'var(--qt-fg-muted)' }}>+{tickers.length - 3}</Ticker>}
          </TickerRow>
          <span>v{strategy.version}</span>
        </TopRow>
        <TitleLink href={`/strategies/public/${strategy.id}`}>{strategy.name}</TitleLink>
        {strategy.description && <Sub>{strategy.description}</Sub>}
      </Head>

      <SparkStrip>
        <span className="label">return / run</span>
        {trend.length >= 2
          ? <MiniSpark data={trend} />
          : <span style={{ flex: 1, color: 'var(--qt-fg-subtle)', fontFamily: 'var(--qt-font-mono)', fontSize: 10 }}>not enough sims yet</span>}
        <span className={`label ret-label ${bestReturn == null ? '' : bestReturn >= 0 ? 'pos' : 'neg'}`}>
          {bestReturn == null ? '—' : `${bestReturn >= 0 ? '+' : '−'}${Math.abs(bestReturn).toFixed(1)}%`}
        </span>
      </SparkStrip>

      <Stats>
        <div className="s">
          <div className="l">Sharpe</div>
          <div className={`v ${sharpe == null ? 'dim' : ''}`}>{sharpe == null ? '—' : sharpe.toFixed(2)}</div>
        </div>
        <div className="s">
          <div className="l">Worst run</div>
          <div className={`v ${worstReturn == null ? 'dim' : worstReturn < 0 ? 'neg' : 'pos'}`}>
            {worstReturn == null ? '—' : `${worstReturn >= 0 ? '+' : '−'}${Math.abs(worstReturn).toFixed(1)}%`}
          </div>
        </div>
        <div className="s">
          <div className="l">Sims</div>
          <div className={`v ${perf == null ? 'dim' : ''}`}>{perf?.totalSimulations ?? '—'}</div>
        </div>
      </Stats>

      <Foot>
        <div className="left">
          <Avatar>{initials}</Avatar>
          <span className="name">{ownerLabel}</span>
          <span className="meta">· {shortAge(strategy.createdAt)}</span>
        </div>
        {isOwn ? (
          <OwnPill>your strategy</OwnPill>
        ) : (
          <ForkBtn type="button" onClick={() => onFork(strategy.id)} disabled={!canFork}>
            {canFork ? 'Fork →' : 'Sign up to fork →'}
          </ForkBtn>
        )}
      </Foot>
    </Card>
  );
}
