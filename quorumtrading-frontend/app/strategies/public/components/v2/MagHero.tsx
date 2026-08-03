'use client';

import Link from 'next/link';
import styled from 'styled-components';
import { useStrategyPerformance } from '@/app/lib/api/hooks';
import { labelCompact, tagBase } from '@/app/styles/labels';
import type { StrategyResponse } from '@/app/lib/api/client';
import { parseStrategyFrontmatter, allTickers } from './parseStrategyFrontmatter';

interface MagHeroProps {
  strategy: StrategyResponse;
  isOwn: boolean;
  onFork: (id: string) => void;
  canFork: boolean;
}

const Hero = styled.section`
  display: grid;
  grid-template-columns: 1fr 1fr;
  margin-bottom: 28px;
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 4px;
  background: ${p => p.theme.colors.surface};
  overflow: hidden;

  @media (max-width: 980px) {
    grid-template-columns: 1fr;
  }
`;

const Editorial = styled.div`
  padding: 32px 36px;
  border-right: 1px solid ${p => p.theme.colors.borderSubtle};

  @media (max-width: 980px) {
    border-right: none;
    border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
    padding: 24px;
  }
`;

/**
 * Editorial eyebrow above the hero headline — deliberately left borderless.
 * It carries the label voice (uppercase mono, `fontSize.label`) but it is a heading
 * kicker, not a metadata chip, so it does not take the `tagBase` box.
 */
const Tag = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.label};
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.brand};
  margin-bottom: 16px;

  & > .gly {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: currentColor;
    box-shadow: 0 0 6px currentColor;
  }
`;

const H2 = styled.h2`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 30px;
  line-height: 1.15;
  letter-spacing: -0.018em;
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  margin: 0 0 12px;

  @media (max-width: 768px) {
    font-size: 24px;
  }
`;

const Sub = styled.p`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 14.5px;
  line-height: 1.55;
  color: ${p => p.theme.colors.fgMuted};
  margin: 0 0 14px;
`;

const MetaRow = styled.div`
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
  margin-bottom: 18px;

  & > .pipe { color: ${p => p.theme.colors.fgSubtle}; }
`;

const Actions = styled.div`
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
`;

const CtaSolid = styled.button`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 14px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.theme.colors.fgInverse};
  background: ${p => p.theme.colors.brand};
  border: 1px solid ${p => p.theme.colors.brand};
  border-radius: 2px;
  cursor: pointer;

  &:hover { background: ${p => p.theme.colors.brandHover}; border-color: ${p => p.theme.colors.brandHover}; }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
`;

const CtaGhost = styled(Link)`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 14px;
  color: ${p => p.theme.colors.foreground};
  border: 1px solid ${p => p.theme.colors.border};
  background: transparent;
  border-radius: 2px;
  text-decoration: none;

  &:hover { border-color: ${p => p.theme.colors.fgMuted}; }
`;

const Data = styled.div`
  padding: 32px 36px;
  display: flex;
  flex-direction: column;
  gap: 18px;

  @media (max-width: 980px) {
    padding: 24px;
  }
`;

const Kpis = styled.div`
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  background: ${p => p.theme.colors.borderSubtle};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 3px;
  overflow: hidden;
`;

const Kpi = styled.div`
  background: ${p => p.theme.colors.surface};
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 3px;

  & > .lbl {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 9.5px;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.fgDimmed};
  }
  & > .val {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-variant-numeric: tabular-nums;
    font-size: 19px;
    color: ${p => p.theme.colors.fgStrong};
    font-weight: ${p => p.theme.typography.fontWeight.medium};
  }
  & > .val.pos { color: ${p => p.theme.colors.positive}; }
  & > .val.neg { color: ${p => p.theme.colors.negative}; }
  & > .val.dim { color: ${p => p.theme.colors.fgDimmed}; }
  & > .delta {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11.5px;
    color: ${p => p.theme.colors.fgDimmed};
  }
`;

const SparkWrap = styled.div`
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 3px;
  padding: 16px;
  background: ${p => p.theme.colors.background};
  position: relative;

  & > .legend {
    display: flex;
    gap: 14px;
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11.5px;
    color: ${p => p.theme.colors.fgMuted};
    margin-bottom: 10px;
  }
  & > .legend > span > .sw {
    display: inline-block;
    width: 10px;
    height: 2px;
    margin-right: 6px;
    vertical-align: middle;
  }
  & > svg {
    width: 100%;
    height: 110px;
    display: block;
  }
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
  margin-right: 4px;
`;

const OwnPill = styled.span`
  ${tagBase}
  ${labelCompact}
  color: ${p => p.theme.colors.brand};
`;

/**
 * Slope color reflects what the eye actually reads:
 *  - any run negative           → red    (real loss)
 *  - all positive AND rising    → brand  (improving)
 *  - all positive but declining → amber  (still profitable, but trend down)
 *  - flat                       → muted
 *
 * Sparkline color was previously tied to `bestReturn >= 0` which made a series
 * of falling positive returns (e.g. [+111.64%, +102.71%]) render as brand-green
 * even though the line sloped down — confusing.
 */
type TrendDirection = 'positive' | 'negative' | 'declining' | 'flat';

function trendDirection(trend: number[]): TrendDirection {
  if (trend.length < 2) return 'flat';
  const first = trend[0];
  const last = trend[trend.length - 1];
  const anyNegative = trend.some(v => v < 0);
  if (anyNegative) return 'negative';
  if (last > first) return 'positive';
  if (last < first) return 'declining';
  return 'flat';
}

function trendColor(dir: TrendDirection): string {
  switch (dir) {
    case 'positive': return 'var(--qt-brand)';
    case 'declining': return 'var(--qt-amber)';
    case 'negative': return 'var(--qt-negative)';
    case 'flat': return 'var(--qt-fg-muted, #8a98a4)';
  }
}

function HeroChart({ trend }: { trend: number[] }) {
  if (trend.length < 2) {
    return (
      <svg viewBox="0 0 400 110" preserveAspectRatio="none">
        <text x={200} y={60} textAnchor="middle" fontFamily="var(--qt-font-mono)" fontSize={11} fill="var(--qt-fg-dimmed)">
          not enough sims yet
        </text>
      </svg>
    );
  }
  const W = 400, H = 110;
  const min = Math.min(...trend), max = Math.max(...trend);
  const range = max - min || 1;
  const pts = trend
    .map((v, i) => {
      const x = (i / (trend.length - 1)) * W;
      const y = H - 8 - ((v - min) / range) * (H - 16);
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .join(' ');
  const dir = trendDirection(trend);
  const stroke = trendColor(dir);
  return (
    <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none">
      {[0.25, 0.5, 0.75].map(p => (
        <line key={p} x1={0} x2={W} y1={H * p} y2={H * p} stroke="var(--qt-border-subtle)" strokeWidth={0.5} />
      ))}
      <polyline points={pts} fill="none" stroke={stroke} strokeWidth={1.8} />
    </svg>
  );
}

export function MagHero({ strategy, isOwn, onFork, canFork }: MagHeroProps) {
  const fm = parseStrategyFrontmatter(strategy.markdownContent);
  const tickers = allTickers(fm);
  const { data: perf } = useStrategyPerformance(strategy.id);
  const bestReturn = perf?.bestReturn ?? null;
  const worstReturn = perf?.worstReturn ?? null;
  const sharpe = perf?.avgSharpeRatio ?? null;
  const trend = perf?.recentTrend ?? [];
  const owner = isOwn ? 'you' : strategy.ownerId ? strategy.ownerId.slice(0, 8) : 'anonymous';

  return (
    <Hero>
      <Editorial>
        <Tag><span className="gly" />Top performer · ranked by best run</Tag>
        <H2>{strategy.name}</H2>
        {strategy.description && <Sub>{strategy.description}</Sub>}
        <MetaRow>
          <span>by <b style={{ color: 'var(--qt-foreground, #e6e6e6)' }}>{owner}</b></span>
          <span className="pipe">·</span>
          <span>v{strategy.version}</span>
          {tickers.length > 0 && (
            <>
              <span className="pipe">·</span>
              <span>{tickers.slice(0, 5).map(t => <Ticker key={t}>{t}</Ticker>)}</span>
            </>
          )}
          {fm.consensusMethod && (
            <>
              <span className="pipe">·</span>
              <span>{fm.consensusMethod}{fm.consensusThreshold != null ? ` · ${fm.consensusThreshold}` : ''}</span>
            </>
          )}
        </MetaRow>
        <Actions>
          {isOwn ? (
            <OwnPill>your strategy</OwnPill>
          ) : (
            <CtaSolid type="button" onClick={() => onFork(strategy.id)} disabled={!canFork}>
              {canFork ? '→ Fork into my account' : 'Sign up to fork →'}
            </CtaSolid>
          )}
          <CtaGhost href={`/strategies/public/${strategy.id}`}>Open strategy.md →</CtaGhost>
        </Actions>
      </Editorial>

      <Data>
        <Kpis>
          <Kpi>
            <div className="lbl">Best run</div>
            <div className={`val ${bestReturn == null ? 'dim' : bestReturn >= 0 ? 'pos' : 'neg'}`}>
              {bestReturn == null ? '—' : `${bestReturn >= 0 ? '+' : '−'}${Math.abs(bestReturn).toFixed(2)}%`}
            </div>
            <div className="delta">across {perf?.completedSimulations ?? 0} sim{(perf?.completedSimulations ?? 0) === 1 ? '' : 's'}</div>
          </Kpi>
          <Kpi>
            <div className="lbl">Sharpe</div>
            <div className={`val ${sharpe == null ? 'dim' : ''}`}>{sharpe == null ? '—' : sharpe.toFixed(2)}</div>
            <div className="delta">avg across runs</div>
          </Kpi>
          <Kpi>
            <div className="lbl">Worst run</div>
            <div className={`val ${worstReturn == null ? 'dim' : worstReturn < 0 ? 'neg' : 'pos'}`}>
              {worstReturn == null ? '—' : `${worstReturn >= 0 ? '+' : '−'}${Math.abs(worstReturn).toFixed(2)}%`}
            </div>
            <div className="delta">downside floor</div>
          </Kpi>
        </Kpis>
        <SparkWrap>
          <div className="legend">
            <span>
              <span className="sw" style={{ background: trendColor(trendDirection(trend)) }} />
              return per run · older → newer
            </span>
            {trend.length >= 2 && (
              <span style={{ marginLeft: 'auto', color: 'var(--qt-fg-dimmed)' }}>
                {(() => {
                  const dir = trendDirection(trend);
                  if (dir === 'positive') return 'trend up';
                  if (dir === 'declining') return 'trend down · still profitable';
                  if (dir === 'negative') return 'loss in series';
                  return 'flat';
                })()}
              </span>
            )}
          </div>
          <HeroChart trend={trend} />
        </SparkWrap>
      </Data>
    </Hero>
  );
}
