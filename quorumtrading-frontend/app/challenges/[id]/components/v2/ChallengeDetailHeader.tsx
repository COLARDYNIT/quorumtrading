'use client';

import styled, { css } from 'styled-components';
import { MarkdownContainer } from '@/app/components/shared';
import { Tag } from '@/app/components/shared/v2';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { hueColor, labelColor, pillBase, pulse, tagBase } from '@/app/styles/labels';
import type { ChallengeResponse } from '@/app/lib/types/challenge';
import { countdownTo, fmtMoney, fmtRange } from './helpers';

interface Props {
  ch: ChallengeResponse;
  yourRank: number | null;
}

export function ChallengeDetailHeader({ ch, yourRank }: Props) {
  return (
    <Head>
      <TitleRow>
        <Crumb>Challenges / {ch.slug}</Crumb>
        <H1Row>
          <H1>{ch.name}</H1>
          <ChipRow>
            <StatusChip status={ch.status} />
            <FlavourChip live={!ch.isBacktest} />
            <TickerChip ticker={ch.ticker} kind={ch.tickerType} />
            <CountdownChip ch={ch} />
            {ch.strategiesPublic && <OpenChip />}
          </ChipRow>
        </H1Row>
      </TitleRow>
      {ch.description && (
        <Desc>
          <MarkdownContainer>
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{ch.description}</ReactMarkdown>
          </MarkdownContainer>
        </Desc>
      )}
      <MetaRow>
        <span><Dim>stake</Dim> <Mono>{fmtMoney(ch.initialCash)}</Mono></span>
        <Pipe>|</Pipe>
        <span><Dim>window</Dim> <Mono>{fmtRange(ch)}</Mono></span>
        <Pipe>|</Pipe>
        <span><Dim>entrants</Dim> <Mono>{ch.participantCount}</Mono></span>
        {yourRank != null && (
          <>
            <Pipe>|</Pipe>
            <span>
              <Dim>your rank</Dim>{' '}
              <MonoBrand>#{yourRank}</MonoBrand>
              <Dim> / {ch.participantCount}</Dim>
            </span>
          </>
        )}
        <Pipe>|</Pipe>
        <span><Dim>id</Dim> <Mono>#{ch.id.slice(0, 8).toUpperCase()}</Mono></span>
      </MetaRow>
    </Head>
  );
}

// ─── Sub-components ─────────────────────────────────────────────────────────

function StatusChip({ status }: { status: ChallengeResponse['status'] }) {
  return (
    <StatusPill $status={status}>
      <StatusDot $status={status} />
      {status === 'ACTIVE' ? 'Active' : status === 'UPCOMING' ? 'Upcoming' : 'Completed'}
    </StatusPill>
  );
}

function FlavourChip({ live }: { live: boolean }) {
  return (
    <FlavourPill $live={live} title={live ? 'LIVE — real-time, runs for the simulation window' : 'BACKTEST — replays history, results land in minutes'}>
      <GlyphDot />
      <span className="lbl">{live ? 'LIVE' : 'BACKTEST'}</span>
      <span className="sub">{live ? 'real-time' : 'replays history'}</span>
    </FlavourPill>
  );
}

function TickerChip({ ticker, kind }: { ticker: string; kind: 'STOCK' | 'CRYPTO' }) {
  // `id` — a ticker's casing is data. Uppercasing it would make it wrong to copy.
  return <Tag id hue={kind === 'CRYPTO' ? 'cyan' : 'dim'}>{ticker}</Tag>;
}

function CountdownChip({ ch }: { ch: ChallengeResponse }) {
  if (ch.status === 'COMPLETED') {
    return (
      <CountChip $kind="done">
        <span className="lbl">
          closed{' '}
          <Mono>{new Date(ch.participationClose).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}</Mono>
        </span>
      </CountChip>
    );
  }
  const target = ch.status === 'ACTIVE' ? ch.participationClose : ch.participationOpen;
  const cd = countdownTo(target);
  const kind: 'urgent' | 'soon' | 'active' | 'upcoming' =
    ch.status === 'ACTIVE'
      ? (cd.urgent ? 'urgent' : cd.soon ? 'soon' : 'active')
      : 'upcoming';
  const verb = ch.status === 'ACTIVE' ? 'closes' : 'opens';
  return (
    <CountChip $kind={kind}>
      <CountDot $kind={kind} />
      <span className="lbl">
        {verb} in <Mono>{cd.label}</Mono>
      </span>
    </CountChip>
  );
}

function OpenChip() {
  return (
    <OpenChipRoot title="Open challenge — all entrant strategies are revealed when the challenge completes">
      <span className="g">◇</span>
      open challenge
    </OpenChipRoot>
  );
}

// ─── Styles ─────────────────────────────────────────────────────────────────

const Head = styled.div`
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-bottom: 14px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
`;

const TitleRow = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;
`;

const Crumb = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  color: ${p => p.theme.colors.info};
  letter-spacing: 0.04em;
`;

const H1Row = styled.div`
  display: flex;
  align-items: baseline;
  gap: 12px;
  flex-wrap: wrap;
`;

const H1 = styled.h1`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 24px;
  letter-spacing: -0.012em;
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  margin: 0;
`;

const ChipRow = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
`;

const Mono = styled.b`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  font-variant-numeric: tabular-nums;
`;

const MonoBrand = styled(Mono)`
  color: ${p => p.theme.colors.brand};
`;

const Desc = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 14px;
  line-height: 1.6;
  color: ${p => p.theme.colors.fgMuted};
  max-width: 86ch;

  & p { margin: 0 0 6px; }
  & p:last-child { margin: 0; }
  & strong { color: ${p => p.theme.colors.fgStrong}; font-weight: ${p => p.theme.typography.fontWeight.semibold}; }
  & code {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    color: ${p => p.theme.colors.info};
    background: transparent;
    border: 0;
    padding: 0;
  }
`;

const MetaRow = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const Pipe = styled.span`
  color: ${p => p.theme.colors.border};
`;

const Dim = styled.span`
  color: ${p => p.theme.colors.fgDimmed};
`;

// ─── Pill / chip primitives ─────────────────────────────────────────────────

/**
 * A hue getter, the only per-kind property a label may set (DS v0.4.0). Border and
 * fill mix out of `currentColor` in `pillBase` / `tagBase`, so there is nothing else
 * per kind to keep in sync with the light palette.
 */
type HueFn = (typeof hueColor)['dim'];

const STATUS_HUE: Record<ChallengeResponse['status'], HueFn> = {
  ACTIVE: labelColor.live,
  UPCOMING: hueColor.amber,
  COMPLETED: labelColor.completed,
};

const StatusPill = styled.span<{ $status: ChallengeResponse['status'] }>`
  ${pillBase}
  color: ${p => STATUS_HUE[p.$status](p.theme.colors)};
`;

const StatusDot = styled.span<{ $status: ChallengeResponse['status'] }>`
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
  ${p => p.$status === 'ACTIVE' && css`animation: ${pulse} 1.4s ease-in-out infinite;`}
  ${p => p.$status === 'COMPLETED' && css`background: transparent; box-shadow: inset 0 0 0 1px currentColor;`}
`;

const FlavourPill = styled.span<{ $live: boolean }>`
  ${pillBase}
  padding: 3px 9px 3px 8px;
  color: ${p => (p.$live ? hueColor.amber : hueColor.cyan)(p.theme.colors)};

  & .lbl { font-weight: ${p => p.theme.typography.fontWeight.semibold}; }
  & .sub {
    font-size: ${p => p.theme.typography.fontSize.labelSm};
    letter-spacing: 0.02em;
    text-transform: none;
    color: ${p => p.theme.colors.fgDimmed};
  }
`;

const GlyphDot = styled.span`
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
`;

type CountKind = 'urgent' | 'soon' | 'active' | 'upcoming' | 'done';

const COUNT_HUE: Record<CountKind, HueFn> = {
  urgent: labelColor.live,
  soon: hueColor.amber,
  active: hueColor.dim,
  upcoming: hueColor.cyan,
  done: hueColor.dim,
};

const CountChip = styled.span<{ $kind: CountKind }>`
  ${pillBase}
  padding: 3px 9px;
  color: ${p => COUNT_HUE[p.$kind](p.theme.colors)};

  & .lbl { font-family: inherit; }
`;

const CountDot = styled.span<{ $kind: 'urgent' | 'soon' | 'active' | 'upcoming' }>`
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
  ${p => p.$kind === 'urgent' && css`animation: ${pulse} 1.2s ease-in-out infinite;`}
`;

const OpenChipRoot = styled.span`
  ${tagBase}
  padding: 3px 9px;
  /* Dashed on purpose: "open" is a promise about the future, not a settled fact. */
  border-style: dashed;
  color: ${p => hueColor.cyan(p.theme.colors)};
  cursor: help;

  & .g { font-size: ${p => p.theme.typography.fontSize.label}; line-height: 1; }
`;
