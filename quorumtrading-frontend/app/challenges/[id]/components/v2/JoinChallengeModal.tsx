'use client';

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { createPortal } from 'react-dom';
import styled, { keyframes } from 'styled-components';
import {
  useEligibleStrategies,
  useJoinChallenge,
  useStrategies,
  useCostEstimate,
} from '@/app/lib/api/hooks';
import { parseStrategyFrontmatter, allTickers } from '@/app/strategies/public/components/v2/parseStrategyFrontmatter';
import type { ChallengeResponse, EligibleStrategy } from '@/app/lib/types/challenge';
import { buildStrategyLink } from '@/app/lib/types/challenge';
import { countdownTo, fmtMoney, fmtRange } from './helpers';

interface Props {
  ch: ChallengeResponse;
  /** Open state owned by the parent. */
  open: boolean;
  /** Pre-select this strategy on open (used by ?joinWith=…). */
  initialStrategyId?: string | null;
  /** True when the user already has an entry; copy & confirmation differ. */
  hasExistingEntry: boolean;
  onClose: () => void;
}

export function JoinChallengeModal({
  ch, open, initialStrategyId, hasExistingEntry, onClose,
}: Props) {
  const { data: eligibleData, isLoading: eligibleLoading } = useEligibleStrategies(ch.id);
  const { data: strategiesData } = useStrategies();
  const joinMutation = useJoinChallenge();

  // Index strategies for cross-reference (version + tickers come from there, not from the eligible response).
  const strategyMeta = useMemo(() => {
    const m = new Map<string, { version: number; tickers: string[] }>();
    strategiesData?.forEach((s) => {
      m.set(s.id, {
        version: s.version,
        tickers: allTickers(parseStrategyFrontmatter(s.markdownContent)),
      });
    });
    return m;
  }, [strategiesData]);

  const eligible = useMemo(() => eligibleData?.filter((s) => s.compliant) ?? [], [eligibleData]);
  const ineligible = useMemo(() => eligibleData?.filter((s) => !s.compliant) ?? [], [eligibleData]);

  // Default selection: deep-linked id (if eligible) → first compliant.
  const defaultSelectedId = useMemo(() => {
    if (initialStrategyId && eligible.some((s) => s.id === initialStrategyId)) {
      return initialStrategyId;
    }
    return eligible[0]?.id ?? null;
  }, [initialStrategyId, eligible]);

  // User override of the default. Persists across open/close — re-opening
  // the modal keeps the user's last selection if it's still in the list.
  const [override, setOverride] = useState<string | null>(null);
  const selectedId = override ?? defaultSelectedId;
  const setSelectedId = (id: string) => setOverride(id);

  // ESC to close (but not while a submission is in flight).
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !joinMutation.isPending) onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose, joinMutation.isPending]);

  const selected = useMemo(
    () => eligibleData?.find((s) => s.id === selectedId) ?? null,
    [eligibleData, selectedId],
  );

  // Cost estimate fires when a compliant strategy is selected.
  const costRequest = selected && selected.compliant
    ? { strategyIds: [selected.id], startDate: ch.simulationStartDate, endDate: ch.simulationEndDate }
    : null;
  const { data: cost, isLoading: costLoading } = useCostEstimate(costRequest);

  if (!open) return null;
  if (typeof document === 'undefined') return null;

  const submitting = joinMutation.isPending;
  const noStrategiesAtAll = !eligibleLoading && (eligibleData?.length ?? 0) === 0;
  const noEligible = !eligibleLoading && (eligibleData?.length ?? 0) > 0 && eligible.length === 0;
  const canSubmit = !!selected && selected.compliant && !submitting;

  const handleJoin = () => {
    if (!selected || !selected.compliant) return;
    joinMutation.mutate(
      { challengeId: ch.id, strategyId: selected.id },
      { onSuccess: () => { onClose(); } },
    );
  };

  return createPortal(
    <Overlay onClick={submitting ? undefined : onClose}>
      <Modal onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true" aria-labelledby="join-ch-title">
        <ModalHead>
          <div className="left">
            <Eye>{hasExistingEntry ? 'Replace entry' : 'Join challenge'}</Eye>
            <H2 id="join-ch-title">{ch.name}</H2>
            <Sub>
              <TickerInline $crypto={ch.tickerType === 'CRYPTO'}>{ch.ticker}</TickerInline>
              <span className="sep">·</span>
              <span>{fmtMoney(ch.initialCash)} stake</span>
              <span className="sep">·</span>
              <span>{fmtRange(ch)}</span>
              <span className="sep">·</span>
              <CloseCountdown ch={ch} />
            </Sub>
          </div>
          <Close type="button" onClick={onClose} disabled={submitting} aria-label="Close">×</Close>
        </ModalHead>

        <ModalBody>
          {hasExistingEntry && (
            <ReplaceWarn>
              <span className="g">‖</span>
              <span>
                <strong>Heads up — joining again kills your current sim.</strong> The simulation
                re-runs from scratch; LLM votes can vary and your previous rank is lost. Fine for
                re-rolling a flaky run; less fine if you&apos;re sitting comfortably.
              </span>
            </ReplaceWarn>
          )}

          {eligibleLoading ? (
            <LoadingMsg>Loading your strategies…</LoadingMsg>
          ) : noStrategiesAtAll ? (
            <NoStrategies ch={ch} onClose={onClose} />
          ) : noEligible ? (
            <NoEligible ch={ch} ineligible={ineligible} strategyMeta={strategyMeta} />
          ) : (
            <>
              <SectEye>
                <span>Pick a strategy</span>
                <span className="counts">
                  <BrandNum>{eligible.length}</BrandNum> eligible · {ineligible.length} ineligible
                </span>
              </SectEye>
              <StratList>
                {eligible.map((s) => (
                  <StrategyRow
                    key={s.id}
                    strat={s}
                    meta={strategyMeta.get(s.id)}
                    challengeTicker={ch.ticker}
                    selected={selectedId === s.id}
                    onPick={() => setSelectedId(s.id)}
                  />
                ))}
                {ineligible.map((s) => (
                  <StrategyRow
                    key={s.id}
                    strat={s}
                    meta={strategyMeta.get(s.id)}
                    challengeTicker={ch.ticker}
                  />
                ))}
              </StratList>
              <StratFoot>
                <span>
                  Compliance check: strategy must trade <BrandMono>{ch.ticker}</BrandMono>.
                </span>
                <Link
                  href={buildStrategyLink(ch.ticker, ch.tickerType, ch.id)}
                  onClick={onClose}
                >
                  + new strategy for {ch.ticker} →
                </Link>
              </StratFoot>

              {selected && selected.compliant && (
                costLoading ? (
                  <CostLoading>Estimating cost…</CostLoading>
                ) : cost ? (
                  <CostCard cost={cost} ch={ch} strategy={selected} />
                ) : null
              )}
            </>
          )}
        </ModalBody>

        <ModalFoot>
          <FootNote>
            {noStrategiesAtAll || noEligible ? (
              <>
                <strong>No eligible entry to submit</strong>
                <span>
                  Create a strategy that trades {ch.ticker} and come back — your spot is held by
                  the participation window, not the queue.
                </span>
              </>
            ) : !selected ? (
              <>
                <strong>Pick a strategy above</strong>
                <span>Cost appears once selection is compliant.</span>
              </>
            ) : !selected.compliant ? (
              <>
                <AmberStrong>Selection is ineligible</AmberStrong>
                <span>Pick a strategy that trades {ch.ticker} to enable Join.</span>
              </>
            ) : cost ? (
              <>
                <strong>Total: ${cost.estimatedCost.toFixed(2)}</strong>
                <span>charged as the council runs · {fmtRange(ch)}</span>
              </>
            ) : (
              <>
                <strong>Strategy selected</strong>
                <span>Computing cost estimate…</span>
              </>
            )}
          </FootNote>
          <FootActs>
            <GhostBtn type="button" onClick={onClose} disabled={submitting}>Cancel</GhostBtn>
            <SolidBtn
              type="button"
              onClick={handleJoin}
              disabled={!canSubmit}
            >
              {submitting
                ? 'Creating simulation…'
                : hasExistingEntry
                  ? 'Replace entry · spin up sim'
                  : 'Join · spin up sim'}
            </SolidBtn>
          </FootActs>
        </ModalFoot>
      </Modal>
    </Overlay>,
    document.body,
  );
}

// ─── Sub-components ─────────────────────────────────────────────────────────

function CloseCountdown({ ch }: { ch: ChallengeResponse }) {
  const cd = countdownTo(ch.participationClose);
  return (
    <CdCount $urgent={cd.urgent} $soon={cd.soon}>
      closes in {cd.label}
    </CdCount>
  );
}

function StrategyRow({
  strat, meta, challengeTicker, selected, onPick,
}: {
  strat: EligibleStrategy;
  meta: { version: number; tickers: string[] } | undefined;
  challengeTicker: string;
  selected?: boolean;
  onPick?: () => void;
}) {
  const ineligible = !strat.compliant;
  return (
    <StratRow
      type="button"
      onClick={ineligible ? undefined : onPick}
      disabled={ineligible}
      $selected={!!selected}
      $ineligible={ineligible}
      aria-pressed={!!selected}
    >
      <RadioDot $selected={!!selected} $ineligible={ineligible} />
      <StratInfo>
        <span className="nm">
          {strat.name}
          {meta && <span className="ver">v{meta.version}</span>}
          {!ineligible && <span className="ok" title="Compliant">✓</span>}
        </span>
        {meta && meta.tickers.length > 0 && (
          <span className="meta">
            <span>trades </span>
            {meta.tickers.slice(0, 6).map((t, i) => (
              <span key={t}>
                {i > 0 && ', '}
                <TickerInline $crypto={false} $highlight={t === challengeTicker}>
                  {t}
                </TickerInline>
              </span>
            ))}
            {meta.tickers.length > 6 && <span> +{meta.tickers.length - 6}</span>}
          </span>
        )}
        {ineligible && strat.violations.length > 0 && (
          <ViolationList>
            {strat.violations.map((v, i) => (
              <li key={i}>{v}</li>
            ))}
          </ViolationList>
        )}
      </StratInfo>
    </StratRow>
  );
}

function CostCard({
  cost, ch, strategy,
}: {
  cost: {
    estimatedCost: number;
    lowerBound: number;
    upperBound: number;
    totalLLMCalls: number;
    tradingDays: number;
    modelName: string;
  };
  ch: ChallengeResponse;
  strategy: EligibleStrategy;
}) {
  return (
    <CostRoot>
      <CostHead>
        <span className="eye"><CostDot />ESTIMATED AI COST</span>
        <span className="src">model · {cost.modelName}</span>
      </CostHead>
      <CostMain>
        <div className="val">
          <span className="big">${cost.estimatedCost.toFixed(2)}</span>
          <span className="range">
            range ${cost.lowerBound.toFixed(2)} – ${cost.upperBound.toFixed(2)} · 90% confidence
          </span>
        </div>
        <CostMeta>
          <li><strong>{cost.totalLLMCalls.toLocaleString('en-US')}</strong> council ticks</li>
          <li><strong>{cost.tradingDays}</strong> trading days · {ch.isBacktest ? 'backtest' : 'live'}</li>
          <li title={strategy.name}>
            <strong>{strategy.name.length > 28 ? strategy.name.slice(0, 26) + '…' : strategy.name}</strong>
          </li>
        </CostMeta>
      </CostMain>
      <CostNote>
        Charged to your wallet as the council runs · paused if your daily AI budget depletes ·
        no refund on a halted sim.
      </CostNote>
    </CostRoot>
  );
}

function NoEligible({
  ch, ineligible, strategyMeta,
}: {
  ch: ChallengeResponse;
  ineligible: EligibleStrategy[];
  strategyMeta: Map<string, { version: number; tickers: string[] }>;
}) {
  return (
    <>
      <NoEligibleHero>
        <h3>None of your strategies trade {ch.ticker}</h3>
        <p>
          Compliance check: a challenge entry must trade the challenge ticker. You have{' '}
          <strong>{ineligible.length} strateg{ineligible.length === 1 ? 'y' : 'ies'}</strong>{' '}
          on the books — none currently match. Fork an existing one for{' '}
          <BrandMono>{ch.ticker}</BrandMono>, or start fresh.
        </p>
        <div className="acts">
          <PrimaryLink href={buildStrategyLink(ch.ticker, ch.tickerType, ch.id)}>
            + New strategy for {ch.ticker}
          </PrimaryLink>
          <GhostLink href="/strategies/public">Fork from public library</GhostLink>
        </div>
      </NoEligibleHero>
      <SectEye>
        <span>What you have today</span>
        <span className="counts">{ineligible.length} strategies · 0 eligible</span>
      </SectEye>
      <StratList>
        {ineligible.map((s) => (
          <StrategyRow
            key={s.id}
            strat={s}
            meta={strategyMeta.get(s.id)}
            challengeTicker={ch.ticker}
          />
        ))}
      </StratList>
    </>
  );
}

function NoStrategies({ ch, onClose }: { ch: ChallengeResponse; onClose: () => void }) {
  return (
    <NoEligibleHero>
      <h3>You haven&apos;t authored a strategy yet</h3>
      <p>
        A challenge entry is one of your strategies running against the challenge ticker. Start
        with a {ch.ticker} strategy — the council reads it on every tick.
      </p>
      <div className="acts">
        <PrimaryLink
          href={buildStrategyLink(ch.ticker, ch.tickerType, ch.id)}
          onClick={onClose}
        >
          + New strategy for {ch.ticker}
        </PrimaryLink>
        <GhostLink href="/strategies/public" onClick={onClose}>
          Browse public library
        </GhostLink>
      </div>
    </NoEligibleHero>
  );
}

// ─── Styles ─────────────────────────────────────────────────────────────────

const Overlay = styled.div`
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 32px 16px;
`;

const fadeIn = keyframes`
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
`;

const Modal = styled.div`
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 4px;
  width: 640px;
  max-width: 100%;
  max-height: calc(100vh - 64px);
  display: flex;
  flex-direction: column;
  animation: ${fadeIn} 180ms ease-out;
  box-shadow: 0 24px 48px rgba(0, 0, 0, 0.4);
`;

const ModalHead = styled.div`
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 18px 14px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};

  & .left {
    display: flex;
    flex-direction: column;
    gap: 4px;
    min-width: 0;
  }
`;

const Eye = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.terminalAmber};
`;

const H2 = styled.h2`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 19px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.theme.colors.fgStrong};
  letter-spacing: -0.008em;
  margin: 0;
`;

const Sub = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgMuted};

  & .sep { color: ${p => p.theme.colors.border}; }
`;

const CdCount = styled.span<{ $urgent: boolean; $soon: boolean }>`
  color: ${p =>
    p.$urgent ? p.theme.colors.brand :
    p.$soon ? p.theme.colors.terminalAmber :
    p.theme.colors.foreground};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
`;

const Close = styled.button`
  background: transparent;
  border: none;
  color: ${p => p.theme.colors.fgDimmed};
  font-size: 24px;
  line-height: 1;
  cursor: pointer;
  padding: 2px 8px;
  border-radius: 2px;

  &:hover:not(:disabled) { color: ${p => p.theme.colors.fgStrong}; background: rgba(255, 255, 255, 0.04); }
  &:disabled { opacity: 0.4; cursor: not-allowed; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
`;

const ModalBody = styled.div`
  flex: 1;
  overflow-y: auto;
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
`;

const LoadingMsg = styled.div`
  padding: 28px 0;
  text-align: center;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const ReplaceWarn = styled.div`
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 10px 12px;
  border: 1px solid rgba(255, 176, 0, 0.4);
  background: rgba(255, 176, 0, 0.06);
  border-radius: 2px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13px;
  color: ${p => p.theme.colors.fgMuted};
  line-height: 1.55;

  & .g {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    color: ${p => p.theme.colors.terminalAmber};
    font-weight: ${p => p.theme.typography.fontWeight.bold};
    flex-shrink: 0;
  }
  & strong { color: ${p => p.theme.colors.terminalAmber}; font-weight: ${p => p.theme.typography.fontWeight.semibold}; }
`;

const SectEye = styled.div`
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};

  & .counts { letter-spacing: 0.04em; text-transform: none; }
`;

const BrandNum = styled.b`
  color: ${p => p.theme.colors.brand};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
`;

const StratList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;
`;

const StratRow = styled.button<{ $selected: boolean; $ineligible: boolean }>`
  display: flex;
  align-items: flex-start;
  gap: 12px;
  width: 100%;
  text-align: left;
  padding: 10px 12px;
  background: ${p => p.$selected ? 'rgba(0, 255, 65, 0.04)' : 'transparent'};
  border: 1px solid ${p => p.$selected ? 'rgba(0, 255, 65, 0.45)' : p.theme.colors.border};
  border-radius: 2px;
  cursor: ${p => p.$ineligible ? 'not-allowed' : 'pointer'};
  opacity: ${p => p.$ineligible ? 0.62 : 1};
  font-family: inherit;
  color: inherit;
  transition: border-color 120ms ease, background 120ms ease;

  &:hover:not(:disabled) {
    border-color: ${p => p.theme.colors.focusRing};
    background: ${p => p.$selected ? 'rgba(0, 255, 65, 0.06)' : 'rgba(255, 255, 255, 0.02)'};
  }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
`;

const RadioDot = styled.span<{ $selected: boolean; $ineligible: boolean }>`
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 1.5px solid ${p =>
    p.$ineligible ? p.theme.colors.fgDimmed :
    p.$selected ? p.theme.colors.brand :
    p.theme.colors.border};
  background: ${p => p.$selected ? p.theme.colors.brand : 'transparent'};
  flex-shrink: 0;
  margin-top: 2px;
  box-shadow: ${p => p.$selected ? 'inset 0 0 0 2.5px ' + p.theme.colors.surface : 'none'};
`;

const StratInfo = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  flex: 1;

  & .nm {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 14px;
    font-weight: ${p => p.theme.typography.fontWeight.medium};
    color: ${p => p.theme.colors.fgStrong};
  }
  & .nm .ver {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11px;
    color: ${p => p.theme.colors.fgDimmed};
    font-weight: ${p => p.theme.typography.fontWeight.normal};
  }
  & .nm .ok {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    color: ${p => p.theme.colors.brand};
    font-size: 13px;
  }
  & .meta {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    flex-wrap: wrap;
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11.5px;
    color: ${p => p.theme.colors.fgDimmed};
  }
`;

const ViolationList = styled.ul`
  margin: 4px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 2px;

  & li {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11.5px;
    color: ${p => p.theme.colors.negative};
    line-height: 1.4;
  }
  & li::before {
    content: '× ';
    font-weight: ${p => p.theme.typography.fontWeight.bold};
  }
`;

const TickerInline = styled.span<{ $crypto: boolean; $highlight?: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  letter-spacing: 0.04em;
  padding: 1px 5px;
  border-radius: 2px;
  border: 1px solid ${p =>
    p.$highlight ? 'rgba(0, 255, 65, 0.5)' :
    p.$crypto ? 'rgba(0, 217, 255, 0.4)' :
    p.theme.colors.borderSubtle};
  color: ${p =>
    p.$highlight ? p.theme.colors.brand :
    p.$crypto ? p.theme.colors.info :
    p.theme.colors.foreground};
  background: ${p =>
    p.$highlight ? 'rgba(0, 255, 65, 0.06)' :
    p.$crypto ? 'rgba(0, 217, 255, 0.04)' :
    p.theme.colors.background};
`;

const StratFoot = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 12.5px;
  color: ${p => p.theme.colors.fgDimmed};
  line-height: 1.5;

  & a {
    color: ${p => p.theme.colors.info};
    text-decoration: none;
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 12px;
  }
  & a:hover { color: ${p => p.theme.colors.fgStrong}; text-decoration: underline; }
`;

const BrandMono = styled.b`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.brand};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
`;

// ─── Cost card ──────────────────────────────────────────────────────────────

const CostLoading = styled.div`
  padding: 14px;
  border: 1px dashed ${p => p.theme.colors.border};
  background: rgba(0, 217, 255, 0.03);
  border-radius: 2px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
  text-align: center;
`;

const CostRoot = styled.div`
  border: 1px solid rgba(0, 217, 255, 0.4);
  background:
    linear-gradient(135deg, rgba(0, 217, 255, 0.04), transparent 60%),
    ${p => p.theme.colors.surface};
  border-radius: 4px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
`;

const CostHead = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 9.5px;
  letter-spacing: 0.12em;
  text-transform: uppercase;

  & .eye {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    color: ${p => p.theme.colors.info};
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
  }
  & .src {
    color: ${p => p.theme.colors.fgDimmed};
    letter-spacing: 0.04em;
    text-transform: none;
  }
`;

const CostDot = styled.span`
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: ${p => p.theme.colors.info};
  box-shadow: 0 0 4px rgba(0, 217, 255, 0.5);
`;

const CostMain = styled.div`
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;

  & .val {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  & .val .big {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 30px;
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
    color: ${p => p.theme.colors.fgStrong};
    line-height: 1.1;
    letter-spacing: -0.01em;
    font-variant-numeric: tabular-nums;
  }
  & .val .range {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11.5px;
    color: ${p => p.theme.colors.fgDimmed};
  }
`;

const CostMeta = styled.ul`
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 3px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  color: ${p => p.theme.colors.fgDimmed};

  & strong {
    color: ${p => p.theme.colors.foreground};
    font-weight: ${p => p.theme.typography.fontWeight.medium};
  }
`;

const CostNote = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  color: ${p => p.theme.colors.fgDimmed};
  line-height: 1.55;
  padding-top: 6px;
  border-top: 1px solid ${p => p.theme.colors.borderSubtle};
`;

// ─── No-eligible hero ───────────────────────────────────────────────────────

const NoEligibleHero = styled.div`
  border: 1px dashed ${p => p.theme.colors.border};
  background: ${p => p.theme.colors.background};
  border-radius: 2px;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 8px;

  & h3 {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 15px;
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
    color: ${p => p.theme.colors.fgStrong};
    margin: 0;
  }
  & p {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 13.5px;
    line-height: 1.55;
    color: ${p => p.theme.colors.fgMuted};
    margin: 0;
  }
  & p strong { color: ${p => p.theme.colors.fgStrong}; }
  & .acts {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
    margin-top: 4px;
  }
`;

const PrimaryLink = styled(Link)`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 13px;
  border-radius: 2px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.theme.colors.fgInverse};
  background: ${p => p.theme.colors.brand};
  border: 1px solid ${p => p.theme.colors.brand};
  text-decoration: none;

  &:hover { background: ${p => p.theme.colors.brandHover}; border-color: ${p => p.theme.colors.brandHover}; }
`;

const GhostLink = styled(Link)`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 13px;
  border-radius: 2px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13px;
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  color: ${p => p.theme.colors.foreground};
  background: transparent;
  border: 1px solid ${p => p.theme.colors.border};
  text-decoration: none;

  &:hover { background: rgba(255, 255, 255, 0.04); border-color: ${p => p.theme.colors.fgMuted}; }
`;

// ─── Foot ──────────────────────────────────────────────────────────────────

const ModalFoot = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 18px;
  border-top: 1px solid ${p => p.theme.colors.borderSubtle};
  background: ${p => p.theme.colors.background};
`;

const FootNote = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;

  & strong {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 13px;
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
    color: ${p => p.theme.colors.fgStrong};
  }
  & span {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11.5px;
    color: ${p => p.theme.colors.fgDimmed};
  }
`;

const AmberStrong = styled.strong`
  color: ${p => p.theme.colors.terminalAmber} !important;
`;

const FootActs = styled.div`
  display: flex;
  gap: 8px;
`;

const SolidBtn = styled.button`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  padding: 8px 14px;
  border-radius: 2px;
  background: ${p => p.theme.colors.brand};
  border: 1px solid ${p => p.theme.colors.brand};
  color: ${p => p.theme.colors.fgInverse};
  cursor: pointer;

  &:hover:not(:disabled) { background: ${p => p.theme.colors.brandHover}; border-color: ${p => p.theme.colors.brandHover}; }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
`;

const GhostBtn = styled.button`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13px;
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  padding: 8px 14px;
  border-radius: 2px;
  background: transparent;
  border: 1px solid ${p => p.theme.colors.border};
  color: ${p => p.theme.colors.foreground};
  cursor: pointer;

  &:hover:not(:disabled) { background: rgba(255, 255, 255, 0.04); border-color: ${p => p.theme.colors.fgMuted}; }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
`;
