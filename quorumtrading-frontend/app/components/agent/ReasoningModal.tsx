'use client';

import { useEffect, useState } from 'react';
import styled from 'styled-components';
import { createPortal } from 'react-dom';
import { ActionPill, Tag } from '@/app/components/shared/v2';
import type { ActionKind } from '@/app/components/shared/v2';
import { A2UIRenderer } from '@/app/components/a2ui/A2UIRenderer';
import { hueColor, labelCompact, tagBase } from '@/app/styles/labels';
import { useMarketDataContext, useFinancialMetrics } from '@/app/lib/api/hooks';
import type { MarketEventDTO, LLMDecision } from '@/app/lib/types/api';
import { parseActionLabel } from '@/app/lib/utils/decisionAction';

// ELI5 explanations for technical indicators
const INDICATOR_TOOLTIPS: Record<string, string> = {
  RSI: "Measures if a stock is being bought or sold too much. Below 30 = oversold (might go up), above 70 = overbought (might go down).",
  SMA_50: "The average price over the last 50 days. If the current price is above this, the stock is trending up short-term.",
  SMA_200: "The average price over the last 200 days. If the current price is above this, the stock is in a long-term uptrend.",
  MACD: "Shows the relationship between two moving averages. When the line crosses above the signal, it's bullish. Below = bearish.",
  BOLLINGER: "Shows a 'normal' price range. Near the upper band = potentially expensive. Near the lower band = potentially cheap.",
};

interface IndicatorData {
  calculatedAt?: string;
  indicators?: {
    RSI_14?: { value: number; interpretation?: string };
    SMA_50?: { value: number };
    SMA_200?: { value: number };
    MACD?: { line: number; signal: number; histogram: number; interpretation?: string };
    BOLLINGER?: { upper: number; middle: number; lower: number };
  };
  warnings?: string[];
}

interface ReasoningModalProps {
  isOpen: boolean;
  onClose: () => void;
  agentName: string;
  action: string;
  confidence: number;
  reasoning: string;
  model?: string;
  duration?: number;
  timestamp?: string;
  tokens?: number;
  cost?: number;
  executionTimeMs?: number;
  indicatorSnapshot?: string;
  uiPayload?: string;
  simulationId?: string;
  agentRole?: string;
  ticker?: string;
  /** Sibling agents in the same consensus — drives the orchestrator's "Visual analysis" panel. */
  otherAgents?: LLMDecision[];
  /** Optional aux line under the confidence chip ("weight 0.30 · contributes +0.234 to composite"). */
  confidenceAux?: string;
  /** Restart-from-here is reachable from inside the agent modal too, mirroring the parent inspect chrome. */
  canRestart?: boolean;
  onRestartFrom?: () => void;
}

// ---------- helpers ---------------------------------------------------------

function actionToKind(action: string): ActionKind {
  const a = (action || '').toUpperCase();
  if (a === 'BUY') return 'buy';
  if (a === 'SELL') return 'sell';
  return 'hold';
}

function getInterpretationTone(interpretation?: string): 'pos' | 'neg' | 'warn' | '' {
  if (!interpretation) return '';
  const v = interpretation.toLowerCase();
  if (v === 'bullish' || v === 'oversold') return 'pos';
  if (v === 'bearish' || v === 'overbought') return 'neg';
  return 'warn';
}

function getShortModelName(model: string | undefined): string {
  if (!model) return '';
  const parts = model.split('/');
  return parts[parts.length - 1];
}

function formatTokens(value: number | undefined): string {
  if (value == null) return '—';
  return value.toLocaleString('en-US');
}

function formatCost(value: number | undefined): string {
  if (value == null) return '—';
  if (value === 0) return '$0';
  if (value < 0.01) return `$${value.toFixed(4)}`;
  return `$${value.toFixed(2)}`;
}

function formatDuration(seconds: number | undefined): string {
  if (seconds == null) return '—';
  if (seconds < 1) return `${Math.round(seconds * 1000)}ms`;
  return `${seconds.toFixed(1)}s`;
}

function formatTimestamp(ts: string): string {
  try {
    return new Date(ts).toLocaleString('en-GB', {
      day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit',
      timeZone: 'America/New_York',
    }) + ' ET';
  } catch { return ts; }
}

function parseIndicatorSnapshot(snapshot: string | undefined): Record<string, IndicatorData> | null {
  if (!snapshot) return null;
  try { return JSON.parse(snapshot); } catch { return null; }
}

// ---------- main component --------------------------------------------------

export function ReasoningModal({
  isOpen,
  onClose,
  agentName,
  action,
  confidence,
  reasoning,
  model,
  duration,
  timestamp,
  tokens,
  cost,
  executionTimeMs,
  indicatorSnapshot,
  uiPayload,
  simulationId,
  agentRole,
  ticker,
  otherAgents,
  confidenceAux,
  canRestart,
  onRestartFrom,
}: ReasoningModalProps) {
  const isCached = executionTimeMs === 0;
  const roleUpper = (agentRole || '').toUpperCase();
  const isOrchestrator = roleUpper === 'ORCHESTRATOR';
  const isRiskManager = roleUpper === 'RISK_MANAGER' || roleUpper === 'RISK';
  const isRuleEngine = isRiskManager; // Risk Manager is deterministic in this codebase.
  const isNewsAgent = roleUpper === 'SENTIMENT' || roleUpper === 'FUNDAMENTAL';
  const isFundamentalAgent = roleUpper === 'FUNDAMENTAL';

  useEffect(() => {
    if (!isOpen) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        // Inner ESC closes inner only — the parent's handler skips when this modal is open.
        e.stopPropagation();
        onClose();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [isOpen, onClose]);

  useEffect(() => {
    if (!isOpen) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = prev; };
  }, [isOpen]);

  const { data: marketData, isLoading: loadingMarketData } = useMarketDataContext(
    simulationId,
    timestamp,
    !!isOpen && isNewsAgent,
  );
  const { data: financialMetrics, isLoading: loadingFinancialMetrics } = useFinancialMetrics(
    ticker,
    !!isOpen && isFundamentalAgent,
  );

  const [expandedEvents, setExpandedEvents] = useState<Set<number>>(new Set());
  const toggleSummary = (idx: number) => {
    setExpandedEvents(prev => {
      const next = new Set(prev);
      if (next.has(idx)) next.delete(idx); else next.add(idx);
      return next;
    });
  };

  if (!isOpen) return null;
  if (typeof document === 'undefined') return null;

  const indicators = parseIndicatorSnapshot(indicatorSnapshot);
  const actionLabel = (action || '').toUpperCase();
  const actionKind = actionToKind(action);

  const newsEvents: MarketEventDTO[] = [];
  if (marketData && ticker) {
    const tickerData = marketData.tickers[ticker] || marketData.tickers['MARKET'];
    if (tickerData) {
      for (const tf of Object.values(tickerData.timeframes)) {
        for (const event of tf.events) {
          if (event.eventType === 'NEWS' || event.eventType === 'SOCIAL') {
            newsEvents.push(event);
          }
        }
      }
    }
    if (ticker !== 'MARKET' && marketData.tickers['MARKET']) {
      for (const tf of Object.values(marketData.tickers['MARKET'].timeframes)) {
        for (const event of tf.events) {
          if (event.eventType === 'NEWS' || event.eventType === 'SOCIAL') {
            newsEvents.push(event);
          }
        }
      }
    }
    newsEvents.sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());
  }

  const showVisualAnalysis = isOrchestrator && otherAgents && otherAgents.length > 0;

  const overlay = (
    <Overlay onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <Frame onMouseDown={(e) => e.stopPropagation()} role="dialog" aria-modal="true" aria-label={`${agentName} reasoning`}>
        <Head>
          <HeadLeft>
            <ActionPill action={actionKind}>{actionLabel}</ActionPill>
            <AgentTitle>{agentName}</AgentTitle>
            {/* `id` — a symbol's casing is data, so it renders verbatim, never uppercased. */}
            {ticker && <Tag id>{ticker}</Tag>}
            {timestamp && <HeadTs>{formatTimestamp(timestamp)}</HeadTs>}
          </HeadLeft>
          <CloseBtn type="button" onClick={onClose} aria-label="Close">×</CloseBtn>
        </Head>

        <MetaRow>
          <MetaItem>
            <span className="lbl">KIND</span>
            <KindChip $variant={isRuleEngine ? 'rule' : 'llm'}>{isRuleEngine ? 'rule' : 'llm'}</KindChip>
          </MetaItem>
          {isRuleEngine ? (
            <>
              <MetaItem><span className="lbl">DURATION</span><span className="val">{formatDuration(duration)}</span></MetaItem>
              {isCached && <MetaItem><span className="lbl">CACHED</span><span className="val">yes</span></MetaItem>}
            </>
          ) : (
            <>
              {model && <MetaItem><span className="lbl">MODEL</span><span className="val" title={model}>{getShortModelName(model)}</span></MetaItem>}
              {!isCached && tokens != null && <MetaItem><span className="lbl">TOKENS</span><span className="val">{formatTokens(tokens)}</span></MetaItem>}
              {!isCached && cost != null && <MetaItem><span className="lbl">COST</span><span className="val">{formatCost(cost)}</span></MetaItem>}
              {!isCached && duration != null && <MetaItem><span className="lbl">DURATION</span><span className="val">{formatDuration(duration)}</span></MetaItem>}
              {isCached && <MetaItem><span className="lbl">CACHED</span><span className="val">reused HOLD</span></MetaItem>}
            </>
          )}
        </MetaRow>

        <Body>
          {!isOrchestrator && (
            <ConfidenceRow>
              <div className="row">
                <span className="left">
                  <ActionPill action={actionKind}>{actionLabel}</ActionPill>
                  <span className="pct">{Math.round(confidence)}%</span>
                  <span className="lbl">confidence</span>
                </span>
                {confidenceAux && <span className="aux">{confidenceAux}</span>}
              </div>
              <ConfBar><i style={{ width: `${Math.max(0, Math.min(100, confidence))}%` }} /></ConfBar>
            </ConfidenceRow>
          )}

          <SubPanel>
            <Eyebrow>Reasoning</Eyebrow>
            <ReasoningText>{reasoning || '—'}</ReasoningText>
          </SubPanel>

          {showVisualAnalysis && (
            <SubPanel>
              <Eyebrow>Visual analysis · agent votes<span className="count">· {otherAgents!.length} agents</span></Eyebrow>
              <VAList>
                {otherAgents!.map((agent) => {
                  const aLabel = parseActionLabel(agent.suggestedAction) || 'HOLD';
                  const aKind = actionToKind(aLabel);
                  const cls = agentRoleClass(agent.agentType);
                  return (
                    <VARow key={agent.id}>
                      <VAName className={cls}><span className="dot" />{agent.agentType.replace(/_/g, ' ')}</VAName>
                      <VAReason>{agent.reasoning || '—'}</VAReason>
                      <VARight>
                        <ActionPill action={aKind}>{aLabel}</ActionPill>
                        <span className="pct">{Math.round((agent.confidence ?? 0) * 100)}%</span>
                      </VARight>
                    </VARow>
                  );
                })}
              </VAList>
            </SubPanel>
          )}

          {indicators && Object.keys(indicators).length > 0 && (
            <SubPanel>
              <Eyebrow>Technical indicators<span className="count">· {Object.keys(indicators).length} ticker{Object.keys(indicators).length === 1 ? '' : 's'}</span></Eyebrow>
              {Object.entries(indicators).map(([tk, data]) => {
                if (!data) return null;
                return (
                  <IndGroup key={tk}>
                    <IndTickerLabel>{tk}</IndTickerLabel>
                    <A2UIGrid>
                      {data.indicators?.RSI_14 && (
                        <A2UICard>
                          <A2UICardTitle title={INDICATOR_TOOLTIPS.RSI}>RSI-14</A2UICardTitle>
                          <A2UIRow>
                            <span className="k">value</span>
                            <span className="v">{typeof data.indicators.RSI_14.value === 'number' ? data.indicators.RSI_14.value.toFixed(1) : data.indicators.RSI_14.value}</span>
                          </A2UIRow>
                          {data.indicators.RSI_14.interpretation && (
                            <A2UIRow>
                              <span className="k">signal</span>
                              <span className={`v ${getInterpretationTone(data.indicators.RSI_14.interpretation)}`}>{data.indicators.RSI_14.interpretation}</span>
                            </A2UIRow>
                          )}
                        </A2UICard>
                      )}
                      {data.indicators?.SMA_50 && (
                        <A2UICard>
                          <A2UICardTitle title={INDICATOR_TOOLTIPS.SMA_50}>SMA-50</A2UICardTitle>
                          <A2UIRow><span className="k">value</span><span className="v">${typeof data.indicators.SMA_50.value === 'number' ? data.indicators.SMA_50.value.toFixed(2) : data.indicators.SMA_50.value}</span></A2UIRow>
                        </A2UICard>
                      )}
                      {data.indicators?.SMA_200 && (
                        <A2UICard>
                          <A2UICardTitle title={INDICATOR_TOOLTIPS.SMA_200}>SMA-200</A2UICardTitle>
                          <A2UIRow><span className="k">value</span><span className="v">${typeof data.indicators.SMA_200.value === 'number' ? data.indicators.SMA_200.value.toFixed(2) : data.indicators.SMA_200.value}</span></A2UIRow>
                        </A2UICard>
                      )}
                      {data.indicators?.MACD && (
                        <A2UICard>
                          <A2UICardTitle title={INDICATOR_TOOLTIPS.MACD}>MACD</A2UICardTitle>
                          <A2UIRow><span className="k">line</span><span className="v">{data.indicators.MACD.line.toFixed(2)}</span></A2UIRow>
                          <A2UIRow><span className="k">signal</span><span className="v">{data.indicators.MACD.signal.toFixed(2)}</span></A2UIRow>
                          <A2UIRow><span className="k">hist</span><span className="v">{data.indicators.MACD.histogram >= 0 ? '+' : ''}{data.indicators.MACD.histogram.toFixed(2)}</span></A2UIRow>
                          {data.indicators.MACD.interpretation && (
                            <A2UIRow><span className="k">trend</span><span className={`v ${getInterpretationTone(data.indicators.MACD.interpretation)}`}>{data.indicators.MACD.interpretation}</span></A2UIRow>
                          )}
                        </A2UICard>
                      )}
                      {data.indicators?.BOLLINGER && (
                        <A2UICard>
                          <A2UICardTitle title={INDICATOR_TOOLTIPS.BOLLINGER}>Bollinger</A2UICardTitle>
                          <A2UIRow><span className="k">upper</span><span className="v">${data.indicators.BOLLINGER.upper.toFixed(2)}</span></A2UIRow>
                          <A2UIRow><span className="k">middle</span><span className="v">${data.indicators.BOLLINGER.middle.toFixed(2)}</span></A2UIRow>
                          <A2UIRow><span className="k">lower</span><span className="v">${data.indicators.BOLLINGER.lower.toFixed(2)}</span></A2UIRow>
                        </A2UICard>
                      )}
                    </A2UIGrid>
                    {data.warnings && data.warnings.length > 0 && (
                      <WarningStrip>
                        {data.warnings.map((w, i) => (<div key={i} className="row"><span className="glyph">△</span>{w}</div>))}
                      </WarningStrip>
                    )}
                  </IndGroup>
                );
              })}
            </SubPanel>
          )}

          {isFundamentalAgent && (
            <SubPanel>
              <Eyebrow>Financial snapshot</Eyebrow>
              {loadingFinancialMetrics && <SubStatus>Loading financial data…</SubStatus>}
              {!loadingFinancialMetrics && !financialMetrics && (
                <SubStatus>No financial metrics available (crypto/ETF or data unavailable).</SubStatus>
              )}
              {!loadingFinancialMetrics && financialMetrics && (
                <>
                  {financialMetrics.companyName && (
                    <CompanyRow>
                      <span className="name">{financialMetrics.companyName}</span>
                      {financialMetrics.industry && <span className="ind">{financialMetrics.industry}</span>}
                      {financialMetrics.marketCapMillions && (
                        <span className="cap">${(financialMetrics.marketCapMillions / 1000).toFixed(1)}B</span>
                      )}
                    </CompanyRow>
                  )}
                  <A2UIGrid>
                    {financialMetrics.valuation && (
                      <A2UICard>
                        <A2UICardTitle>Valuation</A2UICardTitle>
                        {financialMetrics.valuation.peRatioTTM != null && <A2UIRow><span className="k">P/E (TTM)</span><span className="v">{financialMetrics.valuation.peRatioTTM.toFixed(1)}×</span></A2UIRow>}
                        {financialMetrics.valuation.forwardPE != null && <A2UIRow><span className="k">Forward P/E</span><span className="v">{financialMetrics.valuation.forwardPE.toFixed(1)}×</span></A2UIRow>}
                        {financialMetrics.valuation.priceToBook != null && <A2UIRow><span className="k">P/B</span><span className="v">{financialMetrics.valuation.priceToBook.toFixed(1)}×</span></A2UIRow>}
                        {financialMetrics.valuation.evToEbitda != null && <A2UIRow><span className="k">EV/EBITDA</span><span className="v">{financialMetrics.valuation.evToEbitda.toFixed(1)}×</span></A2UIRow>}
                        {financialMetrics.valuation.pegRatio != null && <A2UIRow><span className="k">PEG</span><span className="v">{financialMetrics.valuation.pegRatio.toFixed(2)}</span></A2UIRow>}
                      </A2UICard>
                    )}
                    {financialMetrics.growth && (
                      <A2UICard>
                        <A2UICardTitle>Growth</A2UICardTitle>
                        {financialMetrics.growth.epsGrowthYoY != null && <A2UIRow><span className="k">EPS (YoY)</span><span className={`v ${financialMetrics.growth.epsGrowthYoY >= 0 ? 'pos' : 'neg'}`}>{financialMetrics.growth.epsGrowthYoY >= 0 ? '+' : ''}{financialMetrics.growth.epsGrowthYoY.toFixed(1)}%</span></A2UIRow>}
                        {financialMetrics.growth.revenueGrowthYoY != null && <A2UIRow><span className="k">Rev (YoY)</span><span className={`v ${financialMetrics.growth.revenueGrowthYoY >= 0 ? 'pos' : 'neg'}`}>{financialMetrics.growth.revenueGrowthYoY >= 0 ? '+' : ''}{financialMetrics.growth.revenueGrowthYoY.toFixed(1)}%</span></A2UIRow>}
                        {financialMetrics.growth.epsGrowth5Y != null && <A2UIRow><span className="k">EPS (5Y)</span><span className={`v ${financialMetrics.growth.epsGrowth5Y >= 0 ? 'pos' : 'neg'}`}>{financialMetrics.growth.epsGrowth5Y >= 0 ? '+' : ''}{financialMetrics.growth.epsGrowth5Y.toFixed(1)}%</span></A2UIRow>}
                        {financialMetrics.growth.revenueGrowth5Y != null && <A2UIRow><span className="k">Rev (5Y)</span><span className={`v ${financialMetrics.growth.revenueGrowth5Y >= 0 ? 'pos' : 'neg'}`}>{financialMetrics.growth.revenueGrowth5Y >= 0 ? '+' : ''}{financialMetrics.growth.revenueGrowth5Y.toFixed(1)}%</span></A2UIRow>}
                      </A2UICard>
                    )}
                    {financialMetrics.profitability && (
                      <A2UICard>
                        <A2UICardTitle>Profitability</A2UICardTitle>
                        {financialMetrics.profitability.grossMargin != null && <A2UIRow><span className="k">Gross margin</span><span className="v">{financialMetrics.profitability.grossMargin.toFixed(1)}%</span></A2UIRow>}
                        {financialMetrics.profitability.operatingMargin != null && <A2UIRow><span className="k">Op margin</span><span className="v">{financialMetrics.profitability.operatingMargin.toFixed(1)}%</span></A2UIRow>}
                        {financialMetrics.profitability.netProfitMargin != null && <A2UIRow><span className="k">Net margin</span><span className="v">{financialMetrics.profitability.netProfitMargin.toFixed(1)}%</span></A2UIRow>}
                        {financialMetrics.profitability.returnOnEquity != null && <A2UIRow><span className="k">ROE</span><span className="v">{financialMetrics.profitability.returnOnEquity.toFixed(1)}%</span></A2UIRow>}
                      </A2UICard>
                    )}
                    {financialMetrics.financialHealth && (
                      <A2UICard>
                        <A2UICardTitle>Financial health</A2UICardTitle>
                        {financialMetrics.financialHealth.debtToEquity != null && <A2UIRow><span className="k">Debt/Equity</span><span className="v">{financialMetrics.financialHealth.debtToEquity.toFixed(2)}×</span></A2UIRow>}
                        {financialMetrics.financialHealth.currentRatio != null && <A2UIRow><span className="k">Current</span><span className="v">{financialMetrics.financialHealth.currentRatio.toFixed(2)}×</span></A2UIRow>}
                        {financialMetrics.financialHealth.quickRatio != null && <A2UIRow><span className="k">Quick</span><span className="v">{financialMetrics.financialHealth.quickRatio.toFixed(2)}×</span></A2UIRow>}
                      </A2UICard>
                    )}
                  </A2UIGrid>

                  {financialMetrics.earnings && financialMetrics.earnings.length > 0 && (
                    <EarningsTable>
                      <SubSubEyebrow>Earnings history</SubSubEyebrow>
                      {financialMetrics.earnings.map((er, idx) => (
                        <EarningsRow key={idx}>
                          <span className="q">Q{er.quarter} {er.year}</span>
                          <span className="vals">
                            {er.actual != null && <span>${er.actual.toFixed(2)}</span>}
                            {er.estimate != null && <span className="est">vs ${er.estimate.toFixed(2)}</span>}
                          </span>
                          {er.surprisePercent != null && (
                            <span className={`surprise ${er.beat ? 'pos' : 'neg'}`}>
                              {er.beat ? 'beat' : 'missed'} {er.surprisePercent >= 0 ? '+' : ''}{er.surprisePercent.toFixed(1)}%
                            </span>
                          )}
                        </EarningsRow>
                      ))}
                    </EarningsTable>
                  )}

                  {financialMetrics.analystConsensus && (
                    <AnalystBlock>
                      <SubSubEyebrow>Analyst consensus<span className="count">· {financialMetrics.analystConsensus.total} analysts</span></SubSubEyebrow>
                      <AnalystBar>
                        {(['strongBuy', 'buy', 'hold', 'sell', 'strongSell'] as const).map((bucket) => {
                          const count = financialMetrics.analystConsensus![bucket] || 0;
                          if (count === 0) return null;
                          const pct = (count / financialMetrics.analystConsensus!.total) * 100;
                          return <span key={bucket} className={`seg ${bucket}`} style={{ width: `${pct}%` }} title={`${count} ${bucket}`} />;
                        })}
                      </AnalystBar>
                      <AnalystLegend>
                        <span><i className="dot buy" />{financialMetrics.analystConsensus.strongBuy} strong buy</span>
                        <span><i className="dot buy-light" />{financialMetrics.analystConsensus.buy} buy</span>
                        <span><i className="dot hold" />{financialMetrics.analystConsensus.hold} hold</span>
                        <span><i className="dot sell" />{financialMetrics.analystConsensus.sell + financialMetrics.analystConsensus.strongSell} sell</span>
                      </AnalystLegend>
                    </AnalystBlock>
                  )}
                </>
              )}
            </SubPanel>
          )}

          {isNewsAgent && (
            <SubPanel>
              <Eyebrow>Market events · context{newsEvents.length > 0 && <span className="count">· {newsEvents.length} event{newsEvents.length === 1 ? '' : 's'}</span>}</Eyebrow>
              {loadingMarketData && <SubStatus>Loading market events…</SubStatus>}
              {!loadingMarketData && newsEvents.length === 0 && (
                <SubStatus>No news or social events for this window.</SubStatus>
              )}
              {!loadingMarketData && newsEvents.length > 0 && (
                <MktList>
                  {newsEvents.slice(0, 20).map((event, idx) => {
                    const headline = (event.rawData?.headline || event.rawData?.title || 'No headline') as string;
                    const summary = (event.rawData?.summary) as string | undefined;
                    const source = event.source || event.symbol;
                    const eventTime = new Date(event.timestamp);
                    const timeStr = eventTime.toLocaleString('en-GB', {
                      day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit',
                      timeZone: 'America/New_York',
                    });
                    const isSocial = event.eventType === 'SOCIAL';
                    const score = event.rawData?.score as number | undefined;
                    const expanded = expandedEvents.has(idx);
                    return (
                      <MktRow key={`${event.timestamp}-${idx}`}>
                        <MktHeader $clickable={!!summary} onClick={() => summary && toggleSummary(idx)}>
                          <span className="when"><span className={`swatch ${isSocial ? 'social' : 'news'}`} />{timeStr} ET</span>
                          <span className="what">
                            {headline}
                            <span className="tag">{isSocial ? 'social' : 'news'}{source ? ` · ${source}` : ''}</span>
                          </span>
                          {summary
                            ? <span className="ex">{expanded ? '−' : '+'}</span>
                            : (score !== undefined ? <span className="score">{score}</span> : null)}
                        </MktHeader>
                        {expanded && summary && <MktSummary>{summary}</MktSummary>}
                      </MktRow>
                    );
                  })}
                  {newsEvents.length > 20 && <SubStatus>…and {newsEvents.length - 20} more events</SubStatus>}
                </MktList>
              )}
            </SubPanel>
          )}

          {uiPayload && (
            <SubPanel>
              <Eyebrow>Structured payload<span className="count">· a2ui</span></Eyebrow>
              <A2UIRenderer payload={uiPayload} />
            </SubPanel>
          )}
        </Body>

        <Foot>
          <FootHint><Kbd>esc</Kbd> closes this agent</FootHint>
          <FootActions>
            {canRestart && onRestartFrom && (
              <FootBtn type="button" $variant="amber" onClick={onRestartFrom}>Restart from here</FootBtn>
            )}
            <FootBtn type="button" onClick={onClose}>Close</FootBtn>
          </FootActions>
        </Foot>
      </Frame>
    </Overlay>
  );

  return createPortal(overlay, document.body);
}

function agentRoleClass(type: string | undefined): 'tech' | 'fund' | 'sent' | 'orch' | 'risk' | 'other' {
  const t = (type || '').toUpperCase();
  if (t === 'TECHNICAL') return 'tech';
  if (t === 'FUNDAMENTAL') return 'fund';
  if (t === 'SENTIMENT') return 'sent';
  if (t === 'ORCHESTRATOR') return 'orch';
  if (t === 'RISK_MANAGER' || t === 'RISK') return 'risk';
  return 'other';
}

// ---------- styled components ----------------------------------------------

const Overlay = styled.div`
  position: fixed;
  inset: 0;
  background: rgba(4, 7, 12, 0.78);
  backdrop-filter: blur(3px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  z-index: 1100;
`;

const Frame = styled.div`
  background: ${p => p.theme.colors.surfaceElevated};
  border: 1px solid ${p => p.theme.colors.borderHighlight};
  border-radius: 4px;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.65);
  width: 100%;
  max-width: 760px;
  max-height: 88vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
`;

const Head = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  flex-wrap: wrap;
`;

const HeadLeft = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  min-width: 0;
`;

const AgentTitle = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13.5px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.theme.colors.fgStrong};
`;

const HeadTs = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const CloseBtn = styled.button`
  width: 26px; height: 26px;
  display: inline-flex; align-items: center; justify-content: center;
  border: 1px solid ${p => p.theme.colors.border};
  background: transparent;
  color: ${p => p.theme.colors.fgMuted};
  border-radius: 2px;
  cursor: pointer;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 16px;
  line-height: 1;
  flex-shrink: 0;
  transition: color 120ms ease, border-color 120ms ease;
  &:hover { color: ${p => p.theme.colors.foreground}; border-color: ${p => p.theme.colors.fgMuted}; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
`;

const MetaRow = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 22px;
  align-items: baseline;
  padding: 10px 16px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  background: ${p => p.theme.colors.background};
`;

const MetaItem = styled.span`
  display: inline-flex;
  align-items: baseline;
  gap: 7px;

  & > .lbl {
    font-size: 10px;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.fgDimmed};
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-weight: ${p => p.theme.typography.fontWeight.medium};
  }
  & > .val {
    font-size: 12px;
    color: ${p => p.theme.colors.foreground};
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-variant-numeric: tabular-nums;
  }
`;

const KindChip = styled.span<{ $variant: 'llm' | 'rule' }>`
  ${tagBase}
  ${labelCompact}
  color: ${p => (p.$variant === 'llm' ? hueColor.cyan : hueColor.dim)(p.theme.colors)};
`;

const Body = styled.div`
  flex: 1;
  overflow-y: auto;
  padding: 14px 16px 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
`;

const ConfidenceRow = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;

  & > .row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    flex-wrap: wrap;
  }
  & > .row .left {
    display: inline-flex;
    align-items: center;
    gap: 8px;
  }
  & > .row .pct {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-variant-numeric: tabular-nums;
    font-size: 13px;
    color: ${p => p.theme.colors.fgStrong};
  }
  & > .row .lbl {
    font-size: 10px;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.fgDimmed};
    font-weight: ${p => p.theme.typography.fontWeight.medium};
  }
  & > .row .aux {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11px;
    color: ${p => p.theme.colors.fgDimmed};
  }
`;

const ConfBar = styled.div`
  height: 2px;
  background: ${p => p.theme.colors.borderSubtle};
  border-radius: 1px;
  overflow: hidden;

  & > i {
    display: block;
    height: 100%;
    background: ${p => p.theme.colors.brand};
    border-radius: 1px;
  }
`;

const SubPanel = styled.div`
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-left: 2px solid ${p => p.theme.colors.brand};
  border-radius: 2px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
`;

const Eyebrow = styled.div`
  font-size: 10px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
  font-weight: ${p => p.theme.typography.fontWeight.medium};

  & .count {
    margin-left: 8px;
    color: ${p => p.theme.colors.fgSubtle};
    font-family: ${p => p.theme.typography.fontFamily.mono};
    letter-spacing: 0;
    text-transform: none;
  }
`;

const SubSubEyebrow = styled.div`
  font-size: 9.5px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  margin-bottom: 4px;

  & .count {
    margin-left: 6px;
    color: ${p => p.theme.colors.fgSubtle};
    font-family: ${p => p.theme.typography.fontFamily.mono};
    letter-spacing: 0;
    text-transform: none;
  }
`;

const ReasoningText = styled.p`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13px;
  line-height: 1.55;
  color: ${p => p.theme.colors.foreground};
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
  text-wrap: pretty;
`;

const SubStatus = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  color: ${p => p.theme.colors.fgDimmed};
  padding: 6px 0;
`;

const VAList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 1px;
  background: ${p => p.theme.colors.borderSubtle};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 2px;
  overflow: hidden;
`;

const VARow = styled.div`
  display: grid;
  grid-template-columns: 140px 1fr auto;
  gap: 14px;
  align-items: center;
  padding: 9px 12px;
  background: ${p => p.theme.colors.surfaceElevated};
`;

const VAName = styled.span`
  font-size: 11px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  letter-spacing: 0.05em;
  text-transform: uppercase;
  display: flex;
  align-items: center;
  gap: 6px;
  color: ${p => p.theme.colors.foreground};
  min-width: 0;

  & > .dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    flex-shrink: 0;
    background: ${p => p.theme.colors.fgDimmed};
  }
  &.tech, &.tech > .dot { color: ${p => p.theme.colors.technical}; }
  &.tech > .dot { background: ${p => p.theme.colors.technical}; }
  &.fund, &.fund > .dot { color: ${p => p.theme.colors.fundamental}; }
  &.fund > .dot { background: ${p => p.theme.colors.fundamental}; }
  &.sent, &.sent > .dot { color: ${p => p.theme.colors.sentiment}; }
  &.sent > .dot { background: ${p => p.theme.colors.sentiment}; }
  &.risk, &.risk > .dot { color: ${p => p.theme.colors.terminalPink}; }
  &.risk > .dot { background: ${p => p.theme.colors.terminalPink}; }
`;

const VAReason = styled.span`
  font-size: 12px;
  color: ${p => p.theme.colors.fgMuted};
  line-height: 1.4;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
`;

const VARight = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 6px;

  & > .pct {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11px;
    color: ${p => p.theme.colors.fgMuted};
    font-variant-numeric: tabular-nums;
  }
`;

const IndGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;

  & + & {
    border-top: 1px dashed ${p => p.theme.colors.borderSubtle};
    padding-top: 10px;
  }
`;

const IndTickerLabel = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgMuted};
`;

const A2UIGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 200px), 1fr));
  gap: 8px;
`;

const A2UICard = styled.div`
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 2px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
`;

const A2UICardTitle = styled.div`
  font-size: 10px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  margin-bottom: 4px;
`;

const A2UIRow = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 8px;

  & > .k {
    font-size: 11.5px;
    color: ${p => p.theme.colors.fgMuted};
  }
  & > .v {
    font-size: 12px;
    color: ${p => p.theme.colors.fgStrong};
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-variant-numeric: tabular-nums;
  }
  & > .v.pos { color: ${p => p.theme.colors.positive}; }
  & > .v.neg { color: ${p => p.theme.colors.negative}; }
  & > .v.warn { color: ${p => p.theme.colors.terminalAmber}; }
  & > .v.info { color: ${p => p.theme.colors.info}; }
`;

const WarningStrip = styled.div`
  border-top: 1px dashed ${p => p.theme.colors.borderSubtle};
  padding-top: 8px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  color: ${p => p.theme.colors.terminalAmber};

  & .row { display: flex; gap: 6px; padding: 2px 0; }
  & .row .glyph { color: ${p => p.theme.colors.terminalAmber}; }
`;

const CompanyRow = styled.div`
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;

  & > .name {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 13px;
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
    color: ${p => p.theme.colors.fgStrong};
  }
  & > .ind {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 10px;
    color: ${p => p.theme.colors.fgMuted};
    background: ${p => p.theme.colors.background};
    border: 1px solid ${p => p.theme.colors.borderSubtle};
    padding: 1px 6px;
    border-radius: 2px;
    letter-spacing: 0.04em;
  }
  & > .cap {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11px;
    color: ${p => p.theme.colors.fgMuted};
    font-variant-numeric: tabular-nums;
  }
`;

const EarningsTable = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
`;

const EarningsRow = styled.div`
  display: grid;
  grid-template-columns: 80px 1fr auto;
  gap: 12px;
  align-items: baseline;
  padding: 6px 10px;
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 2px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;

  & > .q { color: ${p => p.theme.colors.fgStrong}; font-weight: ${p => p.theme.typography.fontWeight.semibold}; }
  & > .vals { color: ${p => p.theme.colors.fgMuted}; display: inline-flex; gap: 6px; }
  & > .vals .est { color: ${p => p.theme.colors.fgDimmed}; }
  & > .surprise {
    font-size: 10.5px;
    padding: 1px 6px;
    border-radius: 2px;
    border: 1px solid currentColor;
  }
  & > .surprise.pos { color: ${p => p.theme.colors.positive}; background: rgba(6, 255, 165, 0.08); }
  & > .surprise.neg { color: ${p => p.theme.colors.negative}; background: rgba(255, 51, 102, 0.08); }
`;

const AnalystBlock = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
`;

const AnalystBar = styled.div`
  display: flex;
  height: 8px;
  border-radius: 2px;
  overflow: hidden;
  background: ${p => p.theme.colors.borderSubtle};

  & > .seg { min-width: 2px; }
  & > .seg.strongBuy { background: ${p => p.theme.colors.action.buy}; }
  & > .seg.buy { background: ${p => p.theme.colors.brand}; }
  & > .seg.hold { background: ${p => p.theme.colors.action.hold}; }
  & > .seg.sell { background: ${p => p.theme.colors.action.sell}; }
  & > .seg.strongSell { background: ${p => p.theme.colors.action.sell}; opacity: 0.7; }
`;

const AnalystLegend = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 10.5px;
  color: ${p => p.theme.colors.fgMuted};

  & > span { display: inline-flex; align-items: center; gap: 5px; }
  & .dot {
    width: 6px; height: 6px; border-radius: 50%; display: inline-block;
    background: ${p => p.theme.colors.fgDimmed};
  }
  & .dot.buy { background: ${p => p.theme.colors.action.buy}; }
  & .dot.buy-light { background: ${p => p.theme.colors.brand}; }
  & .dot.hold { background: ${p => p.theme.colors.action.hold}; }
  & .dot.sell { background: ${p => p.theme.colors.action.sell}; }
`;

const MktList = styled.div`
  display: flex;
  flex-direction: column;
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 2px;
  overflow: hidden;
`;

const MktRow = styled.div`
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  background: ${p => p.theme.colors.surface};

  &:last-child { border-bottom: none; }
`;

const MktHeader = styled.div<{ $clickable: boolean }>`
  display: grid;
  grid-template-columns: 130px 1fr auto;
  gap: 12px;
  padding: 7px 12px;
  font-size: 11.5px;
  align-items: center;
  cursor: ${p => p.$clickable ? 'pointer' : 'default'};

  &:hover { background: ${p => p.$clickable ? 'rgba(255, 255, 255, 0.015)' : 'transparent'}; }

  & > .when {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    color: ${p => p.theme.colors.fgDimmed};
    font-size: 11px;
    display: inline-flex; align-items: center; gap: 6px;
  }
  & > .when .swatch { width: 6px; height: 6px; border-radius: 50%; flex-shrink: 0; }
  & > .when .swatch.news { background: ${p => p.theme.colors.info}; }
  & > .when .swatch.social { background: #a78bfa; }
  & > .what {
    color: ${p => p.theme.colors.foreground};
    font-size: 12px;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  & > .what .tag {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 10px;
    color: ${p => p.theme.colors.fgSubtle};
    margin-left: 8px;
  }
  & > .ex {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    color: ${p => p.theme.colors.fgDimmed};
    width: 16px;
    text-align: center;
  }
  & > .score {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 10.5px;
    color: ${p => p.theme.colors.fgMuted};
  }
`;

const MktSummary = styled.p`
  margin: 0;
  padding: 6px 12px 10px 144px;
  font-size: 11.5px;
  line-height: 1.5;
  color: ${p => p.theme.colors.fgMuted};
  background: ${p => p.theme.colors.surface};
`;

const Foot = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 16px;
  border-top: 1px solid ${p => p.theme.colors.borderSubtle};
  background: rgba(0, 0, 0, 0.18);
`;

const FootHint = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const Kbd = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 10px;
  padding: 1px 5px;
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 2px;
  color: ${p => p.theme.colors.fgMuted};
  background: ${p => p.theme.colors.background};
  margin: 0 3px;
`;

const FootActions = styled.div`
  display: inline-flex;
  gap: 8px;
`;

const FootBtn = styled.button<{ $variant?: 'amber' }>`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 12px;
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  padding: 6px 14px;
  border-radius: 2px;
  cursor: pointer;
  border: 1px solid ${p => p.$variant === 'amber' ? p.theme.colors.terminalAmber : p.theme.colors.border};
  background: ${p => p.$variant === 'amber' ? 'rgba(255, 176, 0, 0.08)' : 'transparent'};
  color: ${p => p.$variant === 'amber' ? p.theme.colors.terminalAmber : p.theme.colors.foreground};
  transition: background 120ms ease, border-color 120ms ease;

  &:hover {
    background: ${p => p.$variant === 'amber' ? 'rgba(255, 176, 0, 0.18)' : 'rgba(255, 255, 255, 0.04)'};
  }
  &:focus-visible {
    outline: 2px solid ${p => p.theme.colors.focusRing};
    outline-offset: 2px;
  }
`;
