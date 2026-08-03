'use client';

import styled from 'styled-components';
import { Panel } from '@/app/components/shared/v2';
import type { ChallengeResponse } from '@/app/lib/types/challenge';
import { countdownTo, daysBetween, fmtDate, fmtMoney, fmtRange } from './helpers';

interface Props {
  ch: ChallengeResponse;
}

export function ChallengeRulesPanel({ ch }: Props) {
  const simDays = daysBetween(ch.simulationStartDate, ch.simulationEndDate);
  const sub =
    ch.status === 'UPCOMING' ? `opens in ${countdownTo(ch.participationOpen).label}` :
    ch.status === 'ACTIVE'   ? `closes in ${countdownTo(ch.participationClose).label}` :
    'closed';
  return (
    <Panel
      title="Rules · same for every entrant"
      count={ch.isBacktest ? 'backtest · deterministic-ish' : 'live · real-time'}
      noBodyPadding
    >
      <Body>
        <Rule>
          <K>ticker</K>
          <V>
            <TickerInline $crypto={ch.tickerType === 'CRYPTO'}>{ch.ticker}</TickerInline>
            <Dim> · {ch.tickerType.toLowerCase()}</Dim>
          </V>
        </Rule>
        <Rule>
          <K>starting cash</K>
          <V><Mono>{fmtMoney(ch.initialCash)}</Mono><Dim> · same wallet for every entrant</Dim></V>
        </Rule>
        <Rule>
          <K>sim window</K>
          <V><Mono>{fmtRange(ch)}</Mono><Dim> · {simDays} days</Dim></V>
        </Rule>
        <Rule>
          <K>participation</K>
          <V>
            <Mono>{fmtDate(ch.participationOpen)} → {fmtDate(ch.participationClose)}</Mono>
            <Dim> · {sub}</Dim>
          </V>
        </Rule>
        <Rule>
          <K>compliance</K>
          <V>
            <span>strategy must trade <Brand>{ch.ticker}</Brand></span>
            <Dim> · ineligible strategies show inline violations in the picker</Dim>
          </V>
        </Rule>
        <Rule>
          <K>visibility</K>
          <V>
            {ch.strategiesPublic
              ? <><Info>OPEN</Info><Dim> · entrant strategies revealed at completion</Dim></>
              : <><Mono>private</Mono><Dim> · leaderboard shows whose sim won but not how</Dim></>}
          </V>
        </Rule>
        <Rule>
          <K>prize</K>
          <V><Mono>none</Mono><Dim> · bragging rights only · no entry fee, no payout</Dim></V>
        </Rule>
      </Body>
    </Panel>
  );
}

const Body = styled.div`
  display: flex;
  flex-direction: column;
`;

const Rule = styled.div`
  display: grid;
  grid-template-columns: 130px 1fr;
  gap: 12px;
  align-items: baseline;
  padding: 9px 14px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  font-size: 13px;

  &:last-child { border-bottom: none; }
`;

const K = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
`;

const V = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  color: ${p => p.theme.colors.foreground};
  line-height: 1.5;
`;

const Mono = styled.b`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  color: ${p => p.theme.colors.fgStrong};
  font-variant-numeric: tabular-nums;
`;

const Dim = styled.span`
  color: ${p => p.theme.colors.fgDimmed};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
`;

const Brand = styled.b`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.brand};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
`;

const Info = styled.b`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.info};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  letter-spacing: 0.06em;
`;

const TickerInline = styled.span<{ $crypto: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  letter-spacing: 0.04em;
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  padding: 1px 7px;
  border-radius: 2px;
  border: 1px solid ${p => p.$crypto ? 'rgba(0, 217, 255, 0.4)' : p.theme.colors.border};
  color: ${p => p.$crypto ? p.theme.colors.info : p.theme.colors.fgStrong};
  background: ${p => p.$crypto ? 'rgba(0, 217, 255, 0.04)' : p.theme.colors.background};
`;
