'use client';

import { useEffect, useState, useMemo } from 'react';
import { createPortal } from 'react-dom';
import styled from 'styled-components';
import { ActionPill } from '@/app/components/shared/v2';
import type { ActionKind } from '@/app/components/shared/v2';
import { useConsensusWithAgentsById } from '@/app/lib/api/hooks';
import { parseActionLabel } from '@/app/lib/utils/decisionAction';
import { ReasoningModal } from '@/app/components/agent/ReasoningModal';
import type { LLMDecision } from '@/app/lib/types/api';

const AGENT_DISPLAY_NAMES: Record<string, string> = {
  TECHNICAL: 'Technical',
  FUNDAMENTAL: 'Fundamental',
  SENTIMENT: 'Sentiment',
  ORCHESTRATOR: 'Orchestrator',
};

const AGENT_ORDER = ['TECHNICAL', 'FUNDAMENTAL', 'SENTIMENT', 'ORCHESTRATOR'] as const;

const Overlay = styled.div`
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.78);
  backdrop-filter: blur(3px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 24px;
`;

const Frame = styled.div`
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.borderHighlight};
  border-radius: 4px;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.65);
  width: 100%;
  max-width: 820px;
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
`;

const HeadDate = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
  letter-spacing: 0.08em;
  text-transform: uppercase;
`;

const HeadSymbol = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 15px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.theme.colors.fgStrong};
  letter-spacing: 0.04em;
`;

const HeadId = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  color: ${p => p.theme.colors.fgSubtle};
`;

const CloseBtn = styled.button`
  background: transparent;
  border: 1px solid ${p => p.theme.colors.border};
  color: ${p => p.theme.colors.fgMuted};
  width: 28px;
  height: 28px;
  border-radius: 2px;
  font-size: 15px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: color 120ms ease, border-color 120ms ease;
  &:hover { color: ${p => p.theme.colors.foreground}; border-color: ${p => p.theme.colors.fgMuted}; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
`;

const Body = styled.div`
  padding: 14px 16px 12px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
`;

const MetaRow = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};

  & > span > .lbl {
    color: ${p => p.theme.colors.fgSubtle};
    letter-spacing: 0.08em;
    text-transform: uppercase;
    font-size: 9.5px;
    margin-right: 6px;
  }
  & > span > .val {
    color: ${p => p.theme.colors.foreground};
  }
`;

const Notes = styled.div`
  background: ${p => p.theme.colors.surfaceElevated};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-left: 3px solid ${p => p.theme.colors.brand};
  border-radius: 2px;
  padding: 10px 12px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13px;
  line-height: 1.55;
  color: ${p => p.theme.colors.foreground};

  & > .lbl {
    display: block;
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 9.5px;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.fgDimmed};
    margin-bottom: 6px;
  }
`;

const SectionTitle = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 9.5px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
`;

const AgentGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 320px), 1fr));
  gap: 10px;
`;

const AgentTile = styled.button<{ $disabled: boolean }>`
  text-align: left;
  background: ${p => p.theme.colors.surfaceElevated};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 2px;
  padding: 10px 12px;
  cursor: ${p => p.$disabled ? 'default' : 'pointer'};
  font-family: ${p => p.theme.typography.fontFamily.sans};
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: border-color 120ms ease, background 120ms ease, transform 120ms ease;

  &:hover {
    border-color: ${p => p.$disabled ? p.theme.colors.borderSubtle : p.theme.colors.fgMuted};
    transform: ${p => p.$disabled ? 'none' : 'translateY(-1px)'};
  }
  &:focus-visible {
    outline: 2px solid ${p => p.theme.colors.focusRing};
    outline-offset: 2px;
  }
`;

const AgentHead = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
`;

const AgentName = styled.span`
  font-size: 13px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.theme.colors.fgStrong};
`;

const AgentRight = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 6px;
`;

const ConfText = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  color: ${p => p.theme.colors.fgMuted};
`;

const ReasoningClamp = styled.div`
  font-size: 12.5px;
  line-height: 1.5;
  color: ${p => p.theme.colors.fgMuted};
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
`;

const AgentMeta = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 9.5px;
  color: ${p => p.theme.colors.fgSubtle};

  & > .pill {
    border: 1px solid ${p => p.theme.colors.borderSubtle};
    border-radius: 2px;
    padding: 1px 6px;
    background: ${p => p.theme.colors.background};
  }
`;

const InspectHint = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 9.5px;
  letter-spacing: 0.04em;
  color: ${p => p.theme.colors.info};
`;

const FailedNote = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  font-style: italic;
  color: ${p => p.theme.colors.fgSubtle};
`;

const Foot = styled.div`
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 10px 16px;
  border-top: 1px solid ${p => p.theme.colors.borderSubtle};
  background: rgba(0, 0, 0, 0.18);
`;

const FootBtn = styled.button<{ $primary?: boolean; $warn?: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13px;
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  padding: 6px 14px;
  border-radius: 2px;
  cursor: pointer;
  border: 1px solid ${p =>
    p.$warn ? p.theme.colors.terminalAmber :
    p.$primary ? p.theme.colors.brand :
    p.theme.colors.border};
  background: ${p =>
    p.$warn ? `${p.theme.colors.terminalAmber}15` :
    p.$primary ? p.theme.colors.brand :
    'transparent'};
  color: ${p =>
    p.$warn ? p.theme.colors.terminalAmber :
    p.$primary ? p.theme.colors.fgInverse :
    p.theme.colors.foreground};
  transition: background 120ms ease, border-color 120ms ease;

  &:hover {
    background: ${p =>
      p.$warn ? `${p.theme.colors.terminalAmber}28` :
      p.$primary ? p.theme.colors.brandHover :
      'rgba(255, 255, 255, 0.04)'};
  }
  &:focus-visible {
    outline: 2px solid ${p => p.theme.colors.focusRing};
    outline-offset: 2px;
  }
`;

const StatusLine = styled.div`
  padding: 32px 16px;
  text-align: center;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgMuted};
`;

function actionToKind(action: string): ActionKind {
  const a = action.toUpperCase();
  if (a === 'BUY') return 'buy';
  if (a === 'SELL') return 'sell';
  return 'hold';
}

function getShortModelName(model: string | undefined): string {
  if (!model) return '';
  const parts = model.split('/');
  return parts[parts.length - 1];
}

function formatTokens(tokens: number | undefined): string {
  if (tokens == null) return '';
  if (tokens >= 1000) return `${(tokens / 1000).toFixed(1)}K`;
  return tokens.toString();
}

function formatCost(cost: number | undefined): string {
  if (cost == null) return '';
  if (cost === 0) return '$0';
  if (cost < 0.01) return `$${cost.toFixed(4)}`;
  return `$${cost.toFixed(2)}`;
}

function formatTimestamp(iso: string): string {
  try {
    return new Date(iso).toLocaleString('en-US', {
      day: '2-digit', month: 'short', year: 'numeric',
      hour: '2-digit', minute: '2-digit',
      timeZone: 'America/New_York',
    }) + ' ET';
  } catch { return iso; }
}

interface ConsensusInspectModalProps {
  consensusId: string;
  onClose: () => void;
  canRestart?: boolean;
  onRestartFrom?: (consensusId: string, decisionTimestamp: string) => void;
}

export function ConsensusInspectModal({
  consensusId,
  onClose,
  canRestart,
  onRestartFrom,
}: ConsensusInspectModalProps) {
  const numericId = Number(consensusId);
  const validId = Number.isFinite(numericId) ? numericId : null;
  const { data: detail, isLoading, error } = useConsensusWithAgentsById(validId);
  const [selectedAgent, setSelectedAgent] = useState<LLMDecision | null>(null);

  useEffect(() => {
    const onEsc = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return;
      // Inner reasoning modal handles its own Esc — only close the outer when nothing else is open.
      if (selectedAgent) return;
      onClose();
    };
    window.addEventListener('keydown', onEsc);
    return () => window.removeEventListener('keydown', onEsc);
  }, [onClose, selectedAgent]);

  useEffect(() => {
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = prev; };
  }, []);

  const consensus = detail?.consensusDecision;
  const agents = detail?.agentDecisions ?? [];

  const sortedAgents = useMemo(() => {
    const byType = new Map<string, LLMDecision>();
    for (const a of agents) {
      const key = (a.agentType || '').toUpperCase();
      if (key) byType.set(key, a);
    }
    const ordered: LLMDecision[] = [];
    for (const t of AGENT_ORDER) {
      const a = byType.get(t);
      if (a) ordered.push(a);
    }
    for (const a of agents) {
      const key = (a.agentType || '').toUpperCase();
      if (!AGENT_ORDER.includes(key as (typeof AGENT_ORDER)[number]) && !ordered.includes(a)) {
        ordered.push(a);
      }
    }
    return ordered;
  }, [agents]);

  const finalLabel = consensus ? parseActionLabel(consensus.finalAction) : '';
  const finalKind = finalLabel ? actionToKind(finalLabel) : null;

  const overlay = (
    <Overlay onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <Frame onMouseDown={(e) => e.stopPropagation()}>
        <Head>
          <HeadLeft>
            {consensus && finalKind && (
              <ActionPill action={finalKind}>{finalLabel}</ActionPill>
            )}
            <HeadSymbol>{consensus?.symbol ?? '—'}</HeadSymbol>
            <HeadDate>{consensus ? formatTimestamp(consensus.decisionTimestamp) : '…'}</HeadDate>
            {consensus?.id && <HeadId>#{String(consensus.id).slice(0, 7).toUpperCase()}</HeadId>}
          </HeadLeft>
          <CloseBtn type="button" onClick={onClose} aria-label="Close inspector">×</CloseBtn>
        </Head>

        <Body>
          {isLoading && <StatusLine>Loading consensus detail…</StatusLine>}
          {error && <StatusLine>Could not load consensus detail.</StatusLine>}

          {consensus && (
            <>
              <MetaRow>
                <span><span className="lbl">method</span><span className="val">{consensus.consensusMethod?.replace(/_/g, ' ').toLowerCase()}</span></span>
                <span><span className="lbl">executed</span><span className="val">{consensus.executed ? 'yes' : 'no'}</span></span>
                {consensus.executionPrice != null && (
                  <span><span className="lbl">price</span><span className="val">${consensus.executionPrice.toFixed(2)}</span></span>
                )}
                {consensus.quantity != null && (
                  <span><span className="lbl">qty</span><span className="val">{consensus.quantity.toLocaleString('en-US', { maximumFractionDigits: 4 })}</span></span>
                )}
              </MetaRow>

              {consensus.notes && (
                <Notes>
                  <span className="lbl">Consensus notes</span>
                  {consensus.notes}
                </Notes>
              )}

              <SectionTitle>Agent votes ({sortedAgents.length})</SectionTitle>

              {sortedAgents.length === 0 ? (
                <StatusLine>No agent decisions recorded for this consensus.</StatusLine>
              ) : (
                <AgentGrid>
                  {sortedAgents.map(agent => {
                    const label = parseActionLabel(agent.suggestedAction);
                    const failed = !label || label === 'SKIP' || agent.modelUsed === 'fallback';
                    const kind: ActionKind | null = label ? actionToKind(label) : null;
                    const reasoning = agent.reasoning || '';
                    return (
                      <AgentTile
                        key={agent.id}
                        type="button"
                        $disabled={failed}
                        onClick={failed ? undefined : () => setSelectedAgent(agent)}
                        aria-label={failed ? `${agent.agentType} — failed` : `Inspect ${agent.agentType} reasoning`}
                      >
                        <AgentHead>
                          <AgentName>{AGENT_DISPLAY_NAMES[agent.agentType] || agent.agentType}</AgentName>
                          <AgentRight>
                            {kind && <ActionPill action={kind}>{label}</ActionPill>}
                            {!failed && <ConfText>{Math.round(agent.confidence * 100)}%</ConfText>}
                          </AgentRight>
                        </AgentHead>
                        {failed ? (
                          <FailedNote>parse failure — unparseable response{agent.modelUsed ? ` (${getShortModelName(agent.modelUsed)})` : ''}</FailedNote>
                        ) : (
                          <>
                            <ReasoningClamp>{reasoning || '—'}</ReasoningClamp>
                            <AgentMeta>
                              {agent.modelUsed && <span className="pill" title={agent.modelUsed}>{getShortModelName(agent.modelUsed)}</span>}
                              {agent.totalTokens != null && agent.totalTokens > 0 && <span className="pill">{formatTokens(agent.totalTokens)} tok</span>}
                              {agent.rawCostUsd != null && <span className="pill">{formatCost(agent.rawCostUsd)}</span>}
                              <span style={{ flex: 1 }} />
                              <InspectHint>open →</InspectHint>
                            </AgentMeta>
                          </>
                        )}
                      </AgentTile>
                    );
                  })}
                </AgentGrid>
              )}
            </>
          )}
        </Body>

        <Foot>
          {canRestart && consensus && onRestartFrom && (
            <FootBtn
              type="button"
              $warn
              onClick={() => onRestartFrom(String(consensus.id), consensus.decisionTimestamp)}
            >
              Restart from here
            </FootBtn>
          )}
          <FootBtn type="button" onClick={onClose}>Close</FootBtn>
        </Foot>
      </Frame>

      {selectedAgent && (
        <ReasoningModal
          isOpen
          onClose={() => setSelectedAgent(null)}
          agentName={AGENT_DISPLAY_NAMES[selectedAgent.agentType] || selectedAgent.agentType}
          action={parseActionLabel(selectedAgent.suggestedAction) || 'UNKNOWN'}
          confidence={selectedAgent.confidence * 100}
          reasoning={selectedAgent.reasoning || ''}
          model={selectedAgent.modelUsed}
          duration={selectedAgent.executionTimeMs != null ? selectedAgent.executionTimeMs / 1000 : undefined}
          timestamp={selectedAgent.decisionTimestamp}
          tokens={selectedAgent.totalTokens}
          cost={selectedAgent.rawCostUsd}
          executionTimeMs={selectedAgent.executionTimeMs}
          indicatorSnapshot={selectedAgent.indicatorSnapshot}
          uiPayload={selectedAgent.uiPayload}
          simulationId={selectedAgent.simulationId}
          agentRole={selectedAgent.agentType}
          ticker={selectedAgent.ticker}
          otherAgents={sortedAgents.filter(a => a.id !== selectedAgent.id)}
          canRestart={canRestart}
          onRestartFrom={canRestart && onRestartFrom && consensus
            ? () => { setSelectedAgent(null); onRestartFrom(String(consensus.id), consensus.decisionTimestamp); }
            : undefined}
        />
      )}
    </Overlay>
  );

  if (typeof document === 'undefined') return null;
  return createPortal(overlay, document.body);
}
