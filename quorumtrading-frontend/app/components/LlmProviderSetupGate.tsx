'use client';

/**
 * First-run LLM provider setup takeover (self-host edition).
 *
 * Probes GET /api/v1/llm/providers on load. On the hosted SaaS the endpoint 404s (feature
 * disabled) and this component renders nothing. On a local deployment with no provider selected
 * yet, it covers the app with the "Choose your LLM provider" flow until the user picks one (or
 * skips for this session).
 *
 * Visuals are a faithful port of the claude.ai/design handoff "LLM Provider Setup.html"
 * (states: choose / confirmed / none-available; tile classes opt-available / opt-unavailable /
 * opt-configured / opt-needskey / opt-active). The takeover uses the design's literal dark
 * tokens rather than the app theme — it is a pre-app surface, identical in both app themes,
 * and a sibling of the backend-rendered /api/v1/llm/setup page.
 */

import { useState } from 'react';
import styled, { keyframes } from 'styled-components';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../lib/api/client';
import { useLlmProviderStatus } from '../lib/api/hooks';
import type { LlmProviderId } from '../lib/types/llmProvider';
import { pillBase } from '../styles/labels';

const SKIP_KEY = 'qt-llm-setup-skipped';

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function LlmProviderSetupGate() {
  const { data: status } = useLlmProviderStatus();
  const queryClient = useQueryClient();
  const [skipped, setSkipped] = useState<boolean>(() => {
    if (typeof window === 'undefined') return true;
    return window.sessionStorage.getItem(SKIP_KEY) === '1';
  });
  const [confirmed, setConfirmed] = useState<LlmProviderId | null>(null);
  const [apiKey, setApiKey] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const selectMutation = useMutation({
    mutationFn: (request: { provider: LlmProviderId; apiKey?: string }) =>
      apiClient.llm.selectProvider(request),
    onSuccess: (newStatus) => {
      setErrorMessage(null);
      setConfirmed(newStatus.selected);
      queryClient.setQueryData(['llm', 'providers'], newStatus);
    },
    onError: (e) => {
      setErrorMessage(e instanceof Error ? e.message : 'Selection failed — try again.');
    },
  });

  // Feature absent (404 → null), still loading, already selected, or skipped: render nothing.
  // `confirmed` keeps the success card visible for the just-made selection.
  if (!status || skipped) return null;
  if (status.selected !== null && !confirmed) return null;

  const local = status.providers.find((p) => p.id === 'LOCAL');
  const openRouter = status.providers.find((p) => p.id === 'OPENROUTER');
  const localAvailable = local?.available ?? false;
  const openRouterConfigured = openRouter?.available ?? false;
  const noneAvailable = !localAvailable && !openRouterConfigured;

  const skip = () => {
    window.sessionStorage.setItem(SKIP_KEY, '1');
    setSkipped(true);
  };

  const choose = (provider: LlmProviderId) => {
    selectMutation.mutate({
      provider,
      apiKey: provider === 'OPENROUTER' && apiKey.trim() ? apiKey.trim() : undefined,
    });
  };

  return (
    <Stage role="dialog" aria-modal="true" aria-label="Choose your LLM provider">
      {confirmed ? (
        <Card $state="confirmed" role="status" aria-live="polite">
          <AccentBar />
          <Body>
            <Wordmark />
            <StatusRing>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4"
                   strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M5 12.5 L10 17.5 L19 7" />
              </svg>
            </StatusRing>
            <Eyebrow>Local edition setup</Eyebrow>
            <Title>{providerLabel(confirmed)} is now active.</Title>
            <Lede>
              All agents on this installation will use this provider for analysis and trading
              decisions.
            </Lede>
            <Detail>
              <DetailRow>
                <DetailKey>LLM provider</DetailKey>
                <DetailValue>{providerLabel(confirmed)}</DetailValue>
              </DetailRow>
            </Detail>
            <SelectButton type="button" onClick={() => setConfirmed(null)} style={{ marginTop: 22 }}>
              Continue to QuorumTrading
            </SelectButton>
          </Body>
        </Card>
      ) : noneAvailable ? (
        <Card $state="none" role="alert" aria-live="assertive">
          <AccentBar />
          <Body>
            <Wordmark />
            <StatusRing>
              <Glyph aria-hidden="true">!</Glyph>
            </StatusRing>
            <Eyebrow>Local edition setup</Eyebrow>
            <Title>No LLM provider is available yet.</Title>
            <Lede>
              QuorumTrading needs one AI engine before agents can run. Make one of these available,
              then reload this page.
            </Lede>
            {errorMessage && <Alert role="alert">{errorMessage}</Alert>}
            <Steps>
              <li>
                <span>
                  Install the <strong>claude CLI</strong> and make sure <Code>claude</Code> is on
                  your <Code>PATH</Code> — this page will detect it automatically.
                </span>
              </li>
              <li>
                <span>
                  Or paste an <strong>OpenRouter API key</strong> below to use hosted models
                  instead.
                </span>
              </li>
            </Steps>
            <Options style={{ marginTop: 18 }}>
              <LocalTile available={false} active={false} onChoose={choose}
                         busy={selectMutation.isPending} />
              <OpenRouterTile configured={false} active={false} apiKey={apiKey}
                              onApiKeyChange={setApiKey} onChoose={choose}
                              busy={selectMutation.isPending} />
            </Options>
            <Foot>
              <FootLink type="button" onClick={skip}>Skip for now</FootLink>
            </Foot>
          </Body>
        </Card>
      ) : (
        <Card $state="choose">
          <AccentBar />
          <Body>
            <Wordmark />
            <Eyebrow>Local edition setup</Eyebrow>
            <Title>Choose your LLM provider</Title>
            <Lede>
              QuorumTrading needs one AI engine to power analysis and trading agents. You can
              change this anytime.
            </Lede>
            {errorMessage && <Alert role="alert">{errorMessage}</Alert>}
            <Options>
              <LocalTile available={localAvailable} active={false} onChoose={choose}
                         busy={selectMutation.isPending} />
              <OpenRouterTile configured={openRouterConfigured} active={false} apiKey={apiKey}
                              onApiKeyChange={setApiKey} onChoose={choose}
                              busy={selectMutation.isPending} />
            </Options>
            <Foot>
              <FootLink type="button" onClick={skip}>Skip for now</FootLink>
            </Foot>
          </Body>
        </Card>
      )}
    </Stage>
  );
}

function providerLabel(provider: LlmProviderId): string {
  return provider === 'LOCAL' ? 'Local (claude CLI)' : 'OpenRouter';
}

// ---------------------------------------------------------------------------
// Tiles
// ---------------------------------------------------------------------------

function LocalTile({ available, active, onChoose, busy }: {
  available: boolean;
  active: boolean;
  onChoose: (provider: LlmProviderId) => void;
  busy: boolean;
}) {
  if (!available) {
    return (
      <Opt $variant="unavailable" aria-disabled="true">
        <OptHead>
          <OptName>Local (<Code>claude</Code> CLI)</OptName>
          <Badge $kind="off"><BadgeDot aria-hidden="true" />Unavailable</Badge>
        </OptHead>
        <OptDesc>
          Runs on your machine via the <Code>claude</Code> command. No API key, no usage cost.
        </OptDesc>
        <OptReason>claude not found on PATH</OptReason>
      </Opt>
    );
  }
  return (
    <Opt $variant="available">
      <OptHead>
        <OptName>Local (<Code>claude</Code> CLI)</OptName>
        {active
          ? <ActivePill>Currently active</ActivePill>
          : <Badge $kind="ok"><BadgeDot aria-hidden="true" />Available</Badge>}
      </OptHead>
      <OptDesc>
        Runs on your machine via the <Code>claude</Code> command. No API key, no usage cost.
      </OptDesc>
      <OptReason>claude CLI detected</OptReason>
      {!active && (
        <OptForm>
          <SelectButton type="button" disabled={busy} onClick={() => onChoose('LOCAL')}>
            Use local (claude CLI)
          </SelectButton>
        </OptForm>
      )}
    </Opt>
  );
}

function OpenRouterTile({ configured, active, apiKey, onApiKeyChange, onChoose, busy }: {
  configured: boolean;
  active: boolean;
  apiKey: string;
  onApiKeyChange: (value: string) => void;
  onChoose: (provider: LlmProviderId) => void;
  busy: boolean;
}) {
  return (
    <Opt $variant={configured ? 'configured' : 'needskey'}>
      <OptHead>
        <OptName>OpenRouter</OptName>
        {active
          ? <ActivePill>Currently active</ActivePill>
          : configured
            ? <Badge $kind="ok"><BadgeDot aria-hidden="true" />Configured</Badge>
            : <Badge $kind="warn"><BadgeDot aria-hidden="true" />Needs key</Badge>}
      </OptHead>
      <OptDesc>
        Hosted models via your own OpenRouter API key. Usage billed to your account.
      </OptDesc>
      {configured && <OptReason>key configured</OptReason>}
      {!active && (
        <OptForm>
          {!configured && (
            <>
              <KeyField>
                <label htmlFor="qt-or-key">OpenRouter API key</label>
                <input
                  id="qt-or-key"
                  type="password"
                  autoComplete="off"
                  spellCheck={false}
                  placeholder="sk-or-…"
                  value={apiKey}
                  onChange={(e) => onApiKeyChange(e.target.value)}
                />
              </KeyField>
              <KeyHint>Stored encrypted in your local database. Only ever sent to OpenRouter.</KeyHint>
            </>
          )}
          <SelectButton
            type="button"
            disabled={busy || (!configured && !apiKey.trim())}
            onClick={() => onChoose('OPENROUTER')}
          >
            Use OpenRouter
          </SelectButton>
        </OptForm>
      )}
    </Opt>
  );
}

function Wordmark() {
  return (
    <WordmarkRow aria-label="QuorumTrading">
      <WordmarkMark aria-hidden="true">
        <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.4">
          <circle cx="10" cy="4" r="2.2" fill="#00ff41" stroke="none" />
          <circle cx="4" cy="15" r="2.2" fill="#00d9ff" stroke="none" />
          <circle cx="16" cy="15" r="2.2" fill="#ffb000" stroke="none" />
          <path d="M10 6.2 L5.6 13M10 6.2 L14.4 13M6.2 15 L13.8 15" stroke="#5a6a76"
                strokeWidth="0.8" strokeLinecap="round" />
        </svg>
      </WordmarkMark>
      <span>Quorum<WordmarkAccent>Trading</WordmarkAccent></span>
    </WordmarkRow>
  );
}

// ---------------------------------------------------------------------------
// Styles — resolved design tokens from "LLM Provider Setup.html" (dark, fixed)
// ---------------------------------------------------------------------------

const T = {
  bg: '#0a0e14',
  surface: '#141a23',
  surface2: '#1e2632',
  border: '#2a3344',
  borderSubtle: '#1a2030',
  fg: '#e6e6e6',
  fgStrong: '#ffffff',
  fgMuted: '#8a98a4',
  fgDimmed: '#5a6a76',
  fgSubtle: '#4a5562',
  brand: '#00ff41',
  success: '#06ffa5',
  amber: '#ffb000',
  focus: '#00d9ff',
  sans: '"IBM Plex Sans", system-ui, -apple-system, "Segoe UI", Helvetica, Arial, sans-serif',
  mono: '"IBM Plex Mono", "SF Mono", "Courier New", Courier, monospace',
} as const;

const cardIn = keyframes`
  from { transform: translateY(8px); opacity: 0.9; }
  to { transform: none; opacity: 1; }
`;

const Stage = styled.div`
  position: fixed;
  inset: 0;
  z-index: 10000;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 18px;
  padding: 40px 20px;
  overflow-y: auto;
  background: ${T.bg};
  background-image:
    radial-gradient(1200px 600px at 50% -10%, rgba(0, 217, 255, 0.04), transparent 60%),
    radial-gradient(900px 500px at 50% 120%, rgba(0, 255, 65, 0.03), transparent 60%);
  background-repeat: no-repeat;
  font-family: ${T.sans};
  color: ${T.fg};
  line-height: 1.5;

  @media (max-width: 480px) {
    padding: 28px 14px;
  }
`;

const Card = styled.main<{ $state: 'choose' | 'confirmed' | 'none' }>`
  width: 100%;
  max-width: 520px;
  background: ${T.surface};
  border: 1px solid ${T.border};
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2), 0 18px 50px rgba(0, 0, 0, 0.35);
  overflow: hidden;
  margin: auto 0;

  --accent: ${({ $state }) =>
    $state === 'confirmed' ? T.success : $state === 'none' ? T.amber : T.focus};
  --accent-soft: ${({ $state }) =>
    $state === 'confirmed' ? 'rgba(6,255,165,0.10)'
    : $state === 'none' ? 'rgba(255,176,0,0.10)'
    : 'rgba(0,217,255,0.08)'};
  --accent-line: ${({ $state }) =>
    $state === 'confirmed' ? 'rgba(6,255,165,0.30)'
    : $state === 'none' ? 'rgba(255,176,0,0.30)'
    : 'rgba(0,217,255,0.28)'};

  @media (prefers-reduced-motion: no-preference) {
    animation: ${cardIn} 320ms ease;
  }
`;

const AccentBar = styled.div`
  height: 2px;
  background: var(--accent);
  opacity: 0.9;
`;

const Body = styled.div`
  padding: 30px 32px 28px;

  @media (max-width: 480px) {
    padding: 24px 18px 22px;
  }
`;

const WordmarkRow = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 9px;
  font-size: 18px;
  font-weight: 600;
  color: ${T.fgStrong};
  letter-spacing: -0.01em;
`;

const WordmarkMark = styled.span`
  width: 22px;
  height: 22px;
  display: inline-flex;

  svg { width: 100%; height: 100%; }
`;

const WordmarkAccent = styled.span`
  color: ${T.brand};
`;

const StatusRing = styled.div`
  width: 52px;
  height: 52px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--accent-soft);
  border: 1px solid var(--accent-line);
  color: var(--accent);
  margin: 22px 0 18px;

  svg { width: 24px; height: 24px; display: block; }

  /* design: .status + .eyebrow collapses the eyebrow's top margin */
  & + p { margin-top: 0; }
`;

const Glyph = styled.span`
  font-family: ${T.mono};
  font-size: 26px;
  font-weight: 600;
  line-height: 1;
`;

const Eyebrow = styled.p`
  font-family: ${T.mono};
  font-size: 11px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: ${T.fgDimmed};
  margin: 22px 0 10px;
`;

const Title = styled.h1`
  margin: 0 0 10px;
  font-size: 23px;
  line-height: 1.25;
  font-weight: 600;
  color: ${T.fgStrong};
  letter-spacing: -0.01em;
  text-wrap: balance;

  @media (max-width: 480px) {
    font-size: 21px;
  }
`;

const Lede = styled.p`
  margin: 0;
  font-size: 15px;
  line-height: 1.6;
  color: ${T.fgMuted};
  max-width: 46ch;
`;

const Alert = styled.p`
  margin: 14px 0 0;
  font-size: 13.5px;
  line-height: 1.55;
  color: ${T.fg};
  padding: 11px 14px;
  background: rgba(255, 176, 0, 0.06);
  border: 1px solid rgba(255, 176, 0, 0.25);
  border-left: 2px solid ${T.amber};
  border-radius: 2px;
`;

const Options = styled.div`
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 22px;
`;

const Opt = styled.div<{ $variant: 'available' | 'unavailable' | 'configured' | 'needskey' }>`
  border: 1px solid ${T.borderSubtle};
  border-radius: 4px;
  background: ${T.bg};
  padding: 16px 16px 14px;

  ${({ $variant }) => ($variant === 'available' || $variant === 'configured') && `
    &:hover { border-color: ${T.border}; }
  `}
  ${({ $variant }) => $variant === 'unavailable' && `
    opacity: 0.55;
    h2 { color: ${T.fgMuted}; }
  `}

  @media (max-width: 480px) {
    padding: 14px 13px 12px;
  }
`;

const OptHead = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
`;

const OptName = styled.h2`
  margin: 0;
  font-size: 15.5px;
  font-weight: 600;
  color: ${T.fgStrong};
  letter-spacing: -0.005em;
`;

const Code = styled.code`
  font-family: ${T.mono};
  font-size: 12.5px;
  color: ${T.fg};
  background: ${T.surface2};
  border: 1px solid ${T.borderSubtle};
  border-radius: 2px;
  padding: 1px 5px;
`;

const OptDesc = styled.p`
  margin: 6px 0 0;
  font-size: 13.5px;
  line-height: 1.55;
  color: ${T.fgMuted};
  max-width: 52ch;
`;

const OptReason = styled.p`
  margin: 7px 0 0;
  font-family: ${T.mono};
  font-size: 11.5px;
  letter-spacing: 0.02em;
  color: ${T.fgDimmed};
`;

/** Provider state. `color` is the only per-kind property — border and fill mix out of it. */
const BADGE_COLOR = { ok: T.success, warn: T.amber, off: T.fgDimmed } as const;

const Badge = styled.span<{ $kind: 'ok' | 'warn' | 'off' }>`
  ${pillBase}
  color: ${({ $kind }) => BADGE_COLOR[$kind]};
`;

const BadgeDot = styled.span`
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  flex: 0 0 auto;
`;

/**
 * Outlined, not solid: solid fill is reserved for a live pill carrying real money, and the
 * selected LLM engine is neither. The label text is what separates it from "Available".
 */
const ActivePill = styled.span`
  ${pillBase}
  color: ${T.success};
`;

const OptForm = styled.div`
  margin: 14px 0 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
`;

const KeyField = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;

  label {
    font-family: ${T.mono};
    font-size: 10.5px;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: ${T.fgDimmed};
  }

  input {
    font-family: ${T.mono};
    font-size: 13px;
    color: ${T.fgStrong};
    background: ${T.surface};
    border: 1px solid ${T.border};
    border-radius: 2px;
    padding: 9px 11px;
    width: 100%;
    letter-spacing: 0.02em;

    &::placeholder { color: ${T.fgSubtle}; }
    &:focus-visible {
      outline: 2px solid ${T.focus};
      outline-offset: 1px;
      border-color: ${T.focus};
    }
  }
`;

const KeyHint = styled.p`
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: ${T.fgDimmed};
`;

const SelectButton = styled.button`
  appearance: none;
  font-family: ${T.sans};
  font-size: 13.5px;
  font-weight: 600;
  letter-spacing: 0.005em;
  border-radius: 2px;
  padding: 9px 16px;
  cursor: pointer;
  border: 1px solid transparent;
  align-self: flex-start;
  transition: background-color 120ms ease, border-color 120ms ease;
  background: ${T.brand};
  color: ${T.bg};

  &:hover:not(:disabled) { background: #33ff67; }
  &:active:not(:disabled) { background: #00e63a; }
  &:focus-visible { outline: 2px solid ${T.focus}; outline-offset: 2px; }
  &:disabled { opacity: 0.5; cursor: not-allowed; }

  @media (max-width: 480px) {
    align-self: stretch;
    text-align: center;
  }
`;

const Detail = styled.div`
  margin-top: 20px;
  border: 1px solid ${T.borderSubtle};
  border-radius: 4px;
  background: ${T.bg};
  overflow: hidden;
`;

const DetailRow = styled.div`
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  padding: 11px 14px;

  @media (max-width: 480px) {
    flex-direction: column;
    align-items: flex-start;
    gap: 3px;
  }
`;

const DetailKey = styled.span`
  font-family: ${T.mono};
  font-size: 10.5px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: ${T.fgDimmed};
  white-space: nowrap;
`;

const DetailValue = styled.span`
  font-size: 14px;
  color: ${T.fgStrong};
  font-weight: 500;
  text-align: right;
  min-width: 0;
  word-break: break-word;

  @media (max-width: 480px) {
    text-align: left;
  }
`;

const Steps = styled.ol`
  margin: 18px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 10px;
  counter-reset: step;

  li {
    display: flex;
    gap: 11px;
    align-items: flex-start;
    font-size: 13.5px;
    line-height: 1.55;
    color: ${T.fgMuted};

    &::before {
      counter-increment: step;
      content: counter(step, decimal-leading-zero);
      font-family: ${T.mono};
      font-size: 11px;
      color: ${T.amber};
      border: 1px solid rgba(255, 176, 0, 0.3);
      border-radius: 2px;
      padding: 1px 6px;
      margin-top: 2px;
      flex: 0 0 auto;
    }

    strong { color: ${T.fg}; font-weight: 600; }
  }
`;

const Foot = styled.p`
  margin: 18px 0 0;
  font-family: ${T.mono};
  font-size: 11.5px;
  letter-spacing: 0.04em;
  color: ${T.fgDimmed};
`;

const FootLink = styled.button`
  appearance: none;
  background: none;
  border: none;
  padding: 0;
  font: inherit;
  color: ${T.focus};
  cursor: pointer;
  border-radius: 2px;

  &:hover { text-decoration: underline; text-underline-offset: 2px; }
  &:focus-visible { outline: 2px solid ${T.focus}; outline-offset: 2px; }
`;
