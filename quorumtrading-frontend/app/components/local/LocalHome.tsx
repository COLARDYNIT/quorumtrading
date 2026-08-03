'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import styled, { css, keyframes } from 'styled-components';
import toast from 'react-hot-toast';
import {
  useInstanceInfo,
  useLlmProviderStatus,
  useStrategies,
  useSimulations,
  useBrokerAccounts,
  LLM_SETUP_URL,
} from '@/app/lib/api/hooks';
import { apiClient, APIError, AuthSessionExpiredError } from '@/app/lib/api/client';
import type { LlmProviderEntry } from '@/app/lib/api/client';
import type { BrokerAccount } from '@/app/lib/types/brokerAccount';
import { useAuth } from '@/app/hooks/useAuth';
import { getDevLoginUrl } from '@/app/lib/api/auth';
import { pillBase, tagBase, tagIdentifier } from '@/app/styles/labels';

/**
 * Index page of the standalone (self-host) edition — implements the claude.ai/design handoff
 * "Local Home.html" (sibling of "LLM Provider Setup.html", same tokens). A POST-BOOT HOME, not
 * an install guide: confirm it's alive, one CTA into the app (dev-login), three next steps with
 * live done/not-done states, community links, quiet orientation footer.
 *
 * Live data: GET /api/v1/instance (edition + broker regions), GET /api/v1/llm/providers
 * (selected engine). Auth-gated, progressive enhancement only: the "Workspace" strip
 * (strategy/simulation/broker counts) — ABSENT without a session, never zeroed.
 * The MCP bearer token is deliberately NOT fetchable from the browser: the snippet ships a
 * literal `<token from boot banner>` placeholder and the copy button copies it as-is.
 */

// Design-literal tokens from the handoff (kept verbatim for pixel fidelity; the app theme
// shares most values but not all — e.g. --border / --fg-subtle have no theme key).
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
  sans: "'IBM Plex Sans', system-ui, -apple-system, 'Segoe UI', Helvetica, Arial, sans-serif",
  mono: "'IBM Plex Mono', 'SF Mono', 'Courier New', Courier, monospace",
} as const;

const GITHUB_URL = 'https://github.com/COLARDYNIT/quorumtrading';
const REDDIT_URL = 'https://reddit.com/r/quorumtrading';
const SETUP_GUIDE_URL = `${GITHUB_URL}/blob/main/LOCAL_SETUP.md`;

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8085/api/v1';
const MCP_URL = API_BASE.replace(/\/api\/v1\/?$/, '/mcp');
const MCP_HOST = MCP_URL.replace(/^https?:\/\//, '');
const TOKEN_PLACEHOLDER = '<token from boot banner>';
const MCP_COMMAND = `claude mcp add --transport http quorumtrading ${MCP_URL} --header "Authorization: Bearer ${TOKEN_PLACEHOLDER}"`;

export function LocalHome() {
  const { isAuthenticated } = useAuth();
  // Auth-gated queries live in a child so they are never fired without a session.
  return isAuthenticated ? <LocalHomeAuthed /> : <LocalHomeView />;
}

function LocalHomeAuthed() {
  const { data: strategies } = useStrategies();
  const { data: simulations } = useSimulations();
  const { data: brokerAccounts } = useBrokerAccounts();
  return (
    <LocalHomeView
      session
      strategyCount={strategies?.length}
      simulationCount={simulations?.length}
      brokerAccounts={brokerAccounts}
    />
  );
}

interface ViewProps {
  session?: boolean;
  strategyCount?: number;
  simulationCount?: number;
  brokerAccounts?: BrokerAccount[];
}

function LocalHomeView({ session = false, strategyCount, simulationCount, brokerAccounts }: ViewProps) {
  const { data: instance } = useInstanceInfo();
  const { data: llmStatus } = useLlmProviderStatus();

  const [uiHost, setUiHost] = useState('localhost:3000');
  useEffect(() => {
    setUiHost(window.location.host);
  }, []);

  const selected: LlmProviderEntry | undefined = llmStatus?.providers.find((p) => p.selected);
  const offersEu = instance?.brokers.includes('SAXO') ?? false;
  const regionsLabel = offersEu ? 'EU · US' : 'US';

  const realAccounts = (brokerAccounts ?? []).filter((a) => a.brokerType !== 'MOCK');
  const euConnected = realAccounts.some((a) => a.brokerType.startsWith('SAXO'));
  const usConnected = realAccounts.some((a) => a.brokerType.startsWith('ALPACA'));
  const brokerConnected = euConnected || usConnected;

  const cells = buildSysline({ session, selected, regionsLabel, euConnected, usConnected, uiHost });

  return (
    <Stage>
      <Home>
        <Masthead>
          <Wordmark aria-label="QuorumTrading">
            <Mark aria-hidden="true">
              <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.4">
                <circle cx="10" cy="4" r="2.2" fill="#00ff41" stroke="none" />
                <circle cx="4" cy="15" r="2.2" fill="#00d9ff" stroke="none" />
                <circle cx="16" cy="15" r="2.2" fill="#ffb000" stroke="none" />
                <path
                  d="M10 6.2 L5.6 13M10 6.2 L14.4 13M6.2 15 L13.8 15"
                  stroke="#5a6a76"
                  strokeWidth="0.8"
                  strokeLinecap="round"
                />
              </svg>
            </Mark>
            <span>
              Quorum<Accent>Trading</Accent>
            </span>
          </Wordmark>
          <EditionPill>
            <BDot aria-hidden="true" $color={T.focus} />
            Standalone
          </EditionPill>
        </Masthead>

        <Hero>
          <AccentBar />
          <HeroBody>
            <Alive>
              <PulseDot aria-hidden="true" />
              System up
            </Alive>
            <H1>QuorumTrading is running on this machine.</H1>
            <Lede>
              Single-user standalone edition. Everything below runs locally — your strategies, your
              keys, your data.
            </Lede>

            <Sysline role="list">
              {cells.map((cell) => (
                <SysCell key={cell.k} role="listitem">
                  <SysK>{cell.k}</SysK>
                  <SysV $ok={cell.ok}>{cell.v}</SysV>
                </SysCell>
              ))}
            </Sysline>

            <CtaRow>
              <PrimaryBtn href={getDevLoginUrl()}>
                Enter QuorumTrading <Arrow aria-hidden="true">→</Arrow>
              </PrimaryBtn>
              <CtaHint>
                single local user · no account, no password
                <br />
                GET /api/v1/auth/dev-login
              </CtaHint>
            </CtaRow>
          </HeroBody>
        </Hero>

        {session && (
          <section aria-labelledby="ws">
            <Eyebrow id="ws">Workspace</Eyebrow>
            <Kpis>
              <KpiCell>
                <KpiN>{strategyCount ?? '—'}</KpiN>
                <KpiK>Strategies</KpiK>
              </KpiCell>
              <KpiCell>
                <KpiN>{simulationCount ?? '—'}</KpiN>
                <KpiK>Simulations</KpiK>
              </KpiCell>
              <KpiCell>
                <KpiN>{brokerAccounts ? brokerAccounts.length : '—'}</KpiN>
                <KpiK>Broker accounts</KpiK>
              </KpiCell>
            </Kpis>
          </section>
        )}

        <section aria-labelledby="next">
          <Eyebrow id="next">Next steps</Eyebrow>
          <Steps>
            <McpStep />
            <LlmStep selected={selected} />
            <BrokerStep
              offersEu={offersEu}
              euConnected={euConnected}
              usConnected={usConnected}
              brokerConnected={brokerConnected}
            />
          </Steps>
        </section>

        <section aria-labelledby="comm">
          <Eyebrow id="comm">Community</Eyebrow>
          <Community>
            <CommCard href={GITHUB_URL} target="_blank" rel="noopener">
              <CommKicker>GitHub</CommKicker>
              <CommTitle>COLARDYNIT/quorumtrading</CommTitle>
              <CommDesc>Star the repo, file issues, send PRs.</CommDesc>
            </CommCard>
            <CommCard href={REDDIT_URL} target="_blank" rel="noopener">
              <CommKicker>Reddit</CommKicker>
              <CommTitle>r/quorumtrading</CommTitle>
              <CommDesc>Strategies, setups, results from other self-hosters.</CommDesc>
            </CommCard>
            <CommCard href={SETUP_GUIDE_URL} target="_blank" rel="noopener">
              <CommKicker>Docs</CommKicker>
              <CommTitle>LOCAL_SETUP.md</CommTitle>
              <CommDesc>The setup guide — help someone else run this.</CommDesc>
            </CommCard>
          </Community>
        </section>

        {/* next/link, not <a>: a raw anchor to an internal route does a full document reload,
            dropping the client cache and re-running the whole app boot. Orient styles descendant
            `a` elements, which is what Link renders, so the appearance is unchanged. */}
        <Orient>
          <Link href="/dashboard">Dashboard</Link>
          <Sep>·</Sep>
          <Link href="/strategies">Strategies</Link>
          <Sep>·</Sep>
          <Link href="/simulations">Simulations</Link>
          <Sep>·</Sep>
          <Link href="/blog">Blog / Changelog</Link>
        </Orient>
      </Home>
    </Stage>
  );
}

interface SyslineInput {
  session: boolean;
  selected: LlmProviderEntry | undefined;
  regionsLabel: string;
  euConnected: boolean;
  usConnected: boolean;
  uiHost: string;
}

interface SysCellData {
  k: string;
  v: string;
  ok?: boolean;
}

// The sysline stays a fixed four-cell strip in every state; cells swap as facts become known
// (broker regions → LLM engine → session), per the design handoff.
function buildSysline({ session, selected, regionsLabel, euConnected, usConnected, uiHost }: SyslineInput): SysCellData[] {
  const edition = { k: 'Edition', v: 'standalone' };
  if (!session) {
    return [
      edition,
      { k: 'UI', v: uiHost },
      { k: 'MCP endpoint', v: MCP_HOST },
      selected
        ? { k: 'LLM engine', v: selected.id, ok: true }
        : { k: 'Broker regions', v: regionsLabel },
    ];
  }
  const connectedLabel =
    euConnected && usConnected ? 'EU · US' : euConnected ? 'EU · Saxo' : 'US · Alpaca';
  return [
    edition,
    selected ? { k: 'LLM engine', v: selected.id, ok: true } : { k: 'LLM engine', v: '—' },
    euConnected || usConnected
      ? { k: 'Broker', v: connectedLabel, ok: true }
      : { k: 'Broker regions', v: regionsLabel },
    { k: 'Session', v: 'Local' },
  ];
}

function McpStep() {
  const [copied, setCopied] = useState(false);
  const copy = () => {
    navigator.clipboard.writeText(MCP_COMMAND).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 1600);
    });
  };
  return (
    <Step>
      <StepHead>
        <StepNo>01</StepNo>
        <StepH2>Connect Claude Code</StepH2>
        <Badge $kind="off">
          <BDot aria-hidden="true" />
          Terminal step
        </Badge>
      </StepHead>
      <StepDesc>
        Drive the whole platform over MCP — create strategies, trigger consensus runs, read results
        — straight from Claude Code.
      </StepDesc>
      <Snippet>
        <CopyBtn type="button" onClick={copy} className={copied ? 'copied' : undefined}>
          {copied ? 'Copied' : 'Copy'}
        </CopyBtn>
        <SnippetPre>
          {`claude mcp add --transport http quorumtrading ${MCP_URL} --header "Authorization: Bearer `}
          <Ph>{TOKEN_PLACEHOLDER}</Ph>
          {`"`}
        </SnippetPre>
      </Snippet>
      <StepHint>
        <HintDot aria-hidden="true" />
        <span>
          The bearer token is printed <strong>once</strong> in the boot banner — scroll up in the
          terminal you started the stack from, or check <code>docker compose logs</code>. It is
          never shown in the browser.
        </span>
      </StepHint>
    </Step>
  );
}

function LlmStep({ selected }: { selected: LlmProviderEntry | undefined }) {
  if (selected) {
    return (
      <Step className="step-done">
        <StepHead>
          <StepNo $done>02</StepNo>
          <StepH2>Choose your LLM engine</StepH2>
          <Badge $kind="ok">
            <BDot aria-hidden="true" />
            Active
          </Badge>
        </StepHead>
        <Detail>
          <DetailRow>
            <DetailK>Engine</DetailK>
            <DetailV>
              {selected.id === 'LOCAL' ? (
                <>
                  Local (<code>claude</code> CLI) — no usage cost
                </>
              ) : selected.id === 'LOCAL_HTTP' ? (
                <>Local server (Ollama / OpenAI-compatible) — no usage cost</>
              ) : (
                <>OpenRouter — your API key</>
              )}
            </DetailV>
          </DetailRow>
        </Detail>
        <StepHint className="neutral">
          <HintDot aria-hidden="true" $neutral />
          <span>
            <ChangeLink href={LLM_SETUP_URL}>Change engine</ChangeLink> — switches all agents on
            this installation.
          </span>
        </StepHint>
      </Step>
    );
  }
  return (
    <Step>
      <StepHead>
        <StepNo>02</StepNo>
        <StepH2>Choose your LLM engine</StepH2>
        <Badge $kind="warn">
          <BDot aria-hidden="true" />
          Not selected
        </Badge>
      </StepHead>
      <StepDesc>
        One engine powers all analysis and voting agents. Run a <strong>local model</strong> for
        free — a <strong>local server</strong> (Ollama / LM Studio, works in Docker) or your own{' '}
        <code>claude</code> CLI — or use <strong>OpenRouter</strong> with your own API key.
      </StepDesc>
      <StepHint style={{ marginTop: 14 }}>
        <HintDot aria-hidden="true" />
        <span>
          <LinkBtn href={LLM_SETUP_URL} style={{ marginRight: 10 }}>
            Choose engine
          </LinkBtn>{' '}
          Agents can&apos;t run until an engine is selected.
        </span>
      </StepHint>
    </Step>
  );
}

interface BrokerStepProps {
  offersEu: boolean;
  euConnected: boolean;
  usConnected: boolean;
  brokerConnected: boolean;
}

function BrokerStep({ offersEu, euConnected, usConnected, brokerConnected }: BrokerStepProps) {
  const [saxoBusy, setSaxoBusy] = useState(false);

  const connectSaxo = async () => {
    if (saxoBusy) return;
    setSaxoBusy(true);
    try {
      const start = await apiClient.brokerAccounts.saxoOnboardStart();
      window.location.href = start.authorizationUrl;
    } catch (e) {
      // The api client maps 401 to AuthSessionExpiredError (APIError-401 kept as belt-and-braces).
      if (e instanceof AuthSessionExpiredError || (e instanceof APIError && e.status === 401)) {
        // No session yet. Standalone is single-user, so establish the local session silently
        // via dev-login (the Set-Cookie lands even though we don't follow its redirect) and
        // retry once — no page navigation, no second click.
        try {
          await fetch(getDevLoginUrl(), { credentials: 'include', redirect: 'manual' });
          const start = await apiClient.brokerAccounts.saxoOnboardStart();
          window.location.href = start.authorizationUrl;
          return;
        } catch (retryErr) {
          setSaxoBusy(false);
          toast.error(
            retryErr instanceof Error ? retryErr.message : 'Could not start the Saxo consent flow'
          );
          return;
        }
      }
      setSaxoBusy(false);
      toast.error(e instanceof Error ? e.message : 'Could not start the Saxo consent flow');
    }
  };

  const badgeLabel =
    euConnected && usConnected
      ? 'Connected'
      : euConnected
        ? 'EU connected'
        : usConnected
          ? 'US connected'
          : 'Optional';

  return (
    <Step className={brokerConnected ? 'step-done' : undefined}>
      <StepHead>
        <StepNo $done={brokerConnected}>03</StepNo>
        <StepH2>Connect a broker</StepH2>
        <Badge $kind={brokerConnected ? 'ok' : 'off'}>
          <BDot aria-hidden="true" />
          {badgeLabel}
        </Badge>
      </StepHead>
      {!brokerConnected && (
        <StepDesc>
          Only needed for live or paper trading against a real account. Backtests run fine with
          zero brokers — the built-in mock broker covers simulation.
        </StepDesc>
      )}
      <Regions>
        {offersEu && (
          <Region>
            <Flag aria-hidden="true">🇪🇺</Flag>
            <RegionName>EU — Saxo</RegionName>
            <RegionMeta>
              {euConnected
                ? 'Linked via Saxo consent flow.'
                : "Connects in the browser via Saxo's consent flow."}
            </RegionMeta>
            {euConnected ? (
              <Badge $kind="ok">
                <BDot aria-hidden="true" />
                Connected
              </Badge>
            ) : (
              <LinkBtn as="button" type="button" onClick={connectSaxo} disabled={saxoBusy}>
                {saxoBusy ? 'Starting…' : 'Connect'}
              </LinkBtn>
            )}
          </Region>
        )}
        <Region>
          <Flag aria-hidden="true">🇺🇸</Flag>
          <RegionName>US — Alpaca</RegionName>
          <RegionMeta>
            {usConnected ? 'API keys configured in settings.' : 'Paste your Alpaca API keys in settings.'}
          </RegionMeta>
          {usConnected ? (
            <Badge $kind="ok">
              <BDot aria-hidden="true" />
              Connected
            </Badge>
          ) : (
            <LinkBtn href="/settings/broker-accounts">Add keys</LinkBtn>
          )}
        </Region>
      </Regions>
    </Step>
  );
}

/* ---- styled components (design CSS, translated 1:1) ---------------------- */

const Stage = styled.div`
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 18px;
  padding: 40px 24px 64px;
  background: ${T.bg};
  color: ${T.fg};
  font-family: ${T.sans};
  line-height: 1.5;
  -webkit-font-smoothing: antialiased;
  background-image:
    radial-gradient(1200px 600px at 50% -10%, rgba(0, 217, 255, 0.04), transparent 60%),
    radial-gradient(900px 500px at 50% 120%, rgba(0, 255, 65, 0.03), transparent 60%);
  background-repeat: no-repeat;

  a:focus-visible,
  :focus-visible {
    outline: 2px solid ${T.focus};
    outline-offset: 2px;
    border-radius: 2px;
  }

  @media (max-width: 768px) {
    padding: 28px 16px 48px;
  }
`;

const Home = styled.div`
  width: 100%;
  max-width: 760px;
  display: flex;
  flex-direction: column;
  gap: 28px;
`;

const Masthead = styled.header`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 2px 2px 0;
`;

const Wordmark = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 9px;
  font-size: 18px;
  font-weight: 600;
  color: ${T.fgStrong};
  letter-spacing: -0.01em;
`;

const Mark = styled.span`
  width: 22px;
  height: 22px;
  display: inline-flex;

  svg {
    width: 100%;
    height: 100%;
  }
`;

const Accent = styled.span`
  color: ${T.brand};
`;

/** Edition name — an identifier, so it renders verbatim rather than shouted. */
const EditionPill = styled.span`
  ${tagBase}
  ${tagIdentifier}
  color: ${T.fgMuted};
`;

const BDot = styled.span<{ $color?: string }>`
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: ${(p) => p.$color ?? 'currentColor'};
  flex: 0 0 auto;
`;

const panelCss = css`
  background: ${T.surface};
  border: 1px solid ${T.border};
  border-radius: 8px;
  overflow: hidden;
`;

const Hero = styled.main`
  ${panelCss};
  box-shadow:
    0 4px 12px rgba(0, 0, 0, 0.2),
    0 18px 50px rgba(0, 0, 0, 0.35);
`;

const AccentBar = styled.div`
  height: 2px;
  background: ${T.success};
  opacity: 0.9;
`;

const HeroBody = styled.div`
  padding: 28px 32px 26px;

  @media (max-width: 768px) {
    padding: 22px 20px 20px;
  }
`;

const pulse = keyframes`
  0%, 100% { box-shadow: 0 0 0 0 rgba(6, 255, 165, 0.35); }
  50%      { box-shadow: 0 0 0 5px rgba(6, 255, 165, 0); }
`;

const Alive = styled.p`
  display: inline-flex;
  align-items: center;
  gap: 9px;
  font-family: ${T.mono};
  font-size: 11px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: ${T.success};
  margin: 0 0 14px;
`;

const PulseDot = styled.span`
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: ${T.success};
  flex: 0 0 auto;

  @media (prefers-reduced-motion: no-preference) {
    animation: ${pulse} 2.4s ease-in-out infinite;
  }
`;

const H1 = styled.h1`
  margin: 0 0 8px;
  font-size: 26px;
  line-height: 1.2;
  font-weight: 600;
  color: ${T.fgStrong};
  letter-spacing: -0.01em;
  text-wrap: balance;

  @media (max-width: 768px) {
    font-size: 22px;
  }
`;

const Lede = styled.p`
  margin: 0;
  font-size: 15px;
  line-height: 1.6;
  color: ${T.fgMuted};
  max-width: 56ch;
`;

const Sysline = styled.div`
  display: flex;
  flex-wrap: wrap;
  border: 1px solid ${T.borderSubtle};
  border-radius: 4px;
  background: ${T.bg};
  margin-top: 20px;
  overflow: hidden;
`;

const SysCell = styled.div`
  flex: 1 1 auto;
  min-width: 140px;
  padding: 11px 16px 10px;
  border-right: 1px solid ${T.borderSubtle};
  border-bottom: 1px solid ${T.borderSubtle};
  margin-right: -1px;
  margin-bottom: -1px;
`;

const SysK = styled.span`
  display: block;
  font-family: ${T.mono};
  font-size: 10px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: ${T.fgDimmed};
  margin-bottom: 3px;
`;

const SysV = styled.span<{ $ok?: boolean }>`
  font-family: ${T.mono};
  font-size: 13px;
  color: ${(p) => (p.$ok ? T.success : T.fgStrong)};
  word-break: break-all;
`;

const CtaRow = styled.div`
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: wrap;
  margin-top: 22px;
`;

const PrimaryBtn = styled.a`
  appearance: none;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-family: ${T.sans};
  font-weight: 600;
  letter-spacing: 0.005em;
  border-radius: 2px;
  cursor: pointer;
  border: 1px solid transparent;
  text-decoration: none;
  transition: background-color 120ms ease, border-color 120ms ease;
  background: ${T.brand};
  color: #0a0e14;
  font-size: 15px;
  padding: 12px 22px;

  &:hover {
    background: #33ff67;
  }
  &:active {
    background: #00e63a;
  }
  &:focus-visible {
    outline: 2px solid ${T.focus};
    outline-offset: 2px;
  }

  @media (max-width: 768px) {
    width: 100%;
    justify-content: center;
  }
`;

const Arrow = styled.span`
  font-family: ${T.mono};
  font-weight: 500;
`;

const CtaHint = styled.p`
  margin: 0;
  font-family: ${T.mono};
  font-size: 11.5px;
  letter-spacing: 0.03em;
  color: ${T.fgDimmed};
  line-height: 1.6;
`;

const Kpis = styled.div`
  ${panelCss};
  display: flex;
  flex-wrap: wrap;
`;

const KpiCell = styled.div`
  flex: 1 1 0;
  min-width: 150px;
  padding: 14px 20px 13px;
  border-right: 1px solid ${T.borderSubtle};

  &:last-child {
    border-right: none;
  }
`;

const KpiN = styled.div`
  font-family: ${T.mono};
  font-size: 22px;
  font-weight: 500;
  color: ${T.fgStrong};
  line-height: 1.2;
`;

const KpiK = styled.span`
  display: block;
  font-family: ${T.mono};
  font-size: 10px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: ${T.fgDimmed};
  margin-top: 2px;
`;

const Eyebrow = styled.p`
  font-family: ${T.mono};
  font-size: 11px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: ${T.fgDimmed};
  margin: 0 0 12px;
  padding-left: 2px;
`;

const Steps = styled.ol`
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

const Step = styled.li`
  ${panelCss};
  padding: 18px 20px 17px;

  @media (max-width: 768px) {
    padding: 16px 16px 15px;
  }
`;

const StepHead = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
`;

const StepNo = styled.span<{ $done?: boolean }>`
  font-family: ${T.mono};
  font-size: 11px;
  color: ${(p) => (p.$done ? T.success : T.fgDimmed)};
  border: 1px solid ${(p) => (p.$done ? 'rgba(6,255,165,0.30)' : T.borderSubtle)};
  border-radius: 2px;
  padding: 1px 6px;
  flex: 0 0 auto;
`;

const StepH2 = styled.h2`
  margin: 0;
  font-size: 15.5px;
  font-weight: 600;
  color: ${T.fgStrong};
  letter-spacing: -0.005em;
  flex: 1 1 auto;
`;

const StepDesc = styled.p`
  margin: 8px 0 0;
  font-size: 13.5px;
  line-height: 1.55;
  color: ${T.fgMuted};
  max-width: 62ch;

  code {
    font-family: ${T.mono};
    font-size: 12.5px;
    color: ${T.fg};
    background: ${T.surface2};
    border: 1px solid ${T.borderSubtle};
    border-radius: 2px;
    padding: 1px 5px;
  }

  strong {
    color: ${T.fgStrong};
    font-weight: 600;
  }
`;

/** Step state. `color` is the only per-kind property — border and fill mix out of it. */
const BADGE_COLOR = { ok: T.success, warn: T.amber, off: T.fgDimmed } as const;

const Badge = styled.span<{ $kind: 'ok' | 'warn' | 'off' }>`
  ${pillBase}
  color: ${(p) => BADGE_COLOR[p.$kind]};
`;

const Snippet = styled.div`
  position: relative;
  margin-top: 14px;
  border: 1px solid ${T.borderSubtle};
  border-radius: 4px;
  background: ${T.bg};
`;

const SnippetPre = styled.pre`
  margin: 0;
  padding: 14px 96px 14px 16px;
  font-family: ${T.mono};
  font-size: 12.5px;
  line-height: 1.65;
  color: ${T.fg};
  white-space: pre-wrap;
  word-break: break-all;

  @media (max-width: 768px) {
    padding-right: 16px;
    padding-top: 38px;
  }
`;

const Ph = styled.span`
  color: ${T.amber};
`;

const CopyBtn = styled.button`
  position: absolute;
  top: 9px;
  right: 9px;
  appearance: none;
  font-family: ${T.mono};
  font-size: 10.5px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: ${T.fgMuted};
  background: ${T.surface2};
  border: 1px solid ${T.border};
  border-radius: 2px;
  padding: 4px 9px;
  cursor: pointer;
  transition: color 120ms ease, border-color 120ms ease;

  &:hover {
    color: ${T.fgStrong};
    border-color: ${T.fgDimmed};
  }
  &:focus-visible {
    outline: 2px solid ${T.focus};
    outline-offset: 1px;
  }
  &.copied {
    color: ${T.success};
    border-color: rgba(6, 255, 165, 0.3);
  }
`;

const StepHint = styled.p`
  display: flex;
  align-items: flex-start;
  gap: 9px;
  margin: 12px 0 0;
  font-size: 12.5px;
  line-height: 1.55;
  color: ${T.fgDimmed};

  code {
    font-family: ${T.mono};
    font-size: 11.5px;
    color: ${T.fgMuted};
  }

  strong {
    color: ${T.fgMuted};
    font-weight: 600;
  }
`;

const HintDot = styled.span<{ $neutral?: boolean }>`
  flex: 0 0 auto;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: ${(p) => (p.$neutral ? T.fgSubtle : T.amber)};
  margin-top: 6px;
`;

const Detail = styled.div`
  margin-top: 14px;
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

  & + & {
    border-top: 1px solid ${T.borderSubtle};
  }
`;

const DetailK = styled.span`
  font-family: ${T.mono};
  font-size: 10.5px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: ${T.fgDimmed};
  white-space: nowrap;
`;

const DetailV = styled.span`
  font-size: 14px;
  color: ${T.fgStrong};
  font-weight: 500;
  text-align: right;
  min-width: 0;
  word-break: break-word;

  code {
    font-family: ${T.mono};
    font-size: 12.5px;
    color: ${T.fg};
    background: ${T.surface2};
    border: 1px solid ${T.borderSubtle};
    border-radius: 2px;
    padding: 1px 5px;
  }
`;

const ChangeLink = styled.a`
  color: ${T.focus};
  text-decoration: none;

  &:hover {
    text-decoration: underline;
  }
`;

const Regions = styled.div`
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 14px;
`;

const Region = styled.div`
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  border: 1px solid ${T.borderSubtle};
  border-radius: 4px;
  background: ${T.bg};
  padding: 13px 16px;
`;

const Flag = styled.span`
  font-size: 17px;
  line-height: 1;
  flex: 0 0 auto;
`;

const RegionName = styled.span`
  font-size: 14px;
  font-weight: 600;
  color: ${T.fgStrong};
  white-space: nowrap;
`;

const RegionMeta = styled.span`
  flex: 1 1 200px;
  font-size: 12.5px;
  line-height: 1.5;
  color: ${T.fgDimmed};

  @media (max-width: 768px) {
    flex-basis: 100%;
    order: 4;
  }
`;

const LinkBtn = styled.a`
  appearance: none;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-family: ${T.sans};
  font-size: 13px;
  font-weight: 600;
  color: ${T.focus};
  background: transparent;
  border: 1px solid rgba(0, 217, 255, 0.28);
  border-radius: 2px;
  padding: 7px 14px;
  cursor: pointer;
  text-decoration: none;
  white-space: nowrap;
  transition: background-color 120ms ease, border-color 120ms ease;

  &:hover {
    background: rgba(0, 217, 255, 0.08);
    border-color: rgba(0, 217, 255, 0.5);
  }
  &:focus-visible {
    outline: 2px solid ${T.focus};
    outline-offset: 2px;
  }
  &:disabled {
    opacity: 0.6;
    cursor: wait;
  }
`;

const Community = styled.div`
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
  }
`;

const CommCard = styled.a`
  display: block;
  text-decoration: none;
  background: ${T.surface};
  border: 1px solid ${T.border};
  border-radius: 8px;
  padding: 16px 18px 15px;
  transition: border-color 120ms ease;

  &:hover {
    border-color: ${T.fgDimmed};
  }
`;

const CommKicker = styled.span`
  display: block;
  font-family: ${T.mono};
  font-size: 10px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: ${T.fgDimmed};
  margin-bottom: 6px;
`;

const CommTitle = styled.span`
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: ${T.fgStrong};
  margin-bottom: 4px;
`;

const CommDesc = styled.span`
  display: block;
  font-size: 12.5px;
  line-height: 1.5;
  color: ${T.fgMuted};
`;

const Orient = styled.footer`
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 6px 0 0;
  font-family: ${T.mono};
  font-size: 11.5px;
  letter-spacing: 0.04em;
  color: ${T.fgSubtle};

  a {
    color: ${T.fgDimmed};
    text-decoration: none;
    padding: 3px 6px;
    border-radius: 2px;
  }

  a:hover {
    color: ${T.fg};
    text-decoration: underline;
    text-underline-offset: 3px;
  }
`;

const Sep = styled.span`
  color: ${T.fgSubtle};
  user-select: none;
`;
