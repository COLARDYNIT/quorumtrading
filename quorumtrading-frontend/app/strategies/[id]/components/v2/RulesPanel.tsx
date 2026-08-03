'use client';

import styled from 'styled-components';
import { Panel } from '@/app/components/shared/v2';
import type { ParsedStrategyConfig } from '../../lib/parseStrategyYaml';

interface Props {
  config: ParsedStrategyConfig;
  version: number;
}

const Grid = styled.div`
  display: grid;
  grid-template-columns: minmax(0, max-content) minmax(0, 1fr);
  row-gap: 8px;
  column-gap: 14px;
  font-size: 13px;
`;

const Row = styled.div`
  display: contents;
`;

const Label = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
  align-self: center;
  white-space: nowrap;
`;

const Value = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  color: ${p => p.theme.colors.foreground};
  font-size: 13px;
  min-width: 0;
  word-break: break-word;
  overflow-wrap: anywhere;
`;

const Mono = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  color: ${p => p.theme.colors.fgStrong};
`;

const TickerChip = styled.span`
  display: inline-block;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgStrong};
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  padding: 1px 6px;
  border-radius: 2px;
  margin-right: 4px;
`;

const Dim = styled.span`
  color: ${p => p.theme.colors.fgDimmed};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
`;

const Amber = styled.span`
  color: ${p => p.theme.colors.terminalAmber};
`;

const Empty = styled.span`
  color: ${p => p.theme.colors.fgDimmed};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12.5px;
`;

/**
 * Two-column rules summary parsed from the strategy YAML. Mirrors the
 * Bloomberg-leaning "Rules" panel from the design — every field uses a uniform
 * label / value grid, mono numbers, and "—" for missing values.
 */
export function RulesPanel({ config, version }: Props) {
  return (
    <Panel
      title="Rules"
      count={`v${version}`}
    >
      <Grid>
        <Row>
          <Label>Tickers</Label>
          <Value>
            {config.tickers.length > 0
              ? config.tickers.map(t => <TickerChip key={t}>{t}</TickerChip>)
              : <Empty>—</Empty>}
          </Value>
        </Row>
        <Row>
          <Label>Consensus</Label>
          <Value>
            {config.consensusMethod
              ? (
                <>
                  <Mono>{config.consensusMethod}</Mono>
                  {config.consensusQuorum && <Dim> ({config.consensusQuorum})</Dim>}
                </>
              ) : <Empty>—</Empty>}
          </Value>
        </Row>
        <Row>
          <Label>Evaluation</Label>
          <Value>
            {config.evaluationTimes.length > 0
              ? <Mono>{config.evaluationTimes.join(' · ')}{config.timezone ? ' ' + config.timezone : ''}</Mono>
              : <Empty>—</Empty>}
          </Value>
        </Row>
        <Row>
          <Label>Market window</Label>
          <Value>
            {config.marketHours
              ? <Mono>{config.marketHours.open}–{config.marketHours.close}</Mono>
              : config.marketOnly === true
                ? <Mono>NYSE · sessions only</Mono>
                : <Empty>—</Empty>}
          </Value>
        </Row>
        <Row>
          <Label>Max position</Label>
          <Value>{config.maxPositionPct != null ? <Mono>{config.maxPositionPct}%</Mono> : <Empty>—</Empty>}</Value>
        </Row>
        <Row>
          <Label>Cash floor</Label>
          <Value>{config.minCashFloorPct != null ? <Mono>{config.minCashFloorPct}%</Mono> : <Empty>—</Empty>}</Value>
        </Row>
        <Row>
          <Label>Max drawdown</Label>
          <Value>{config.maxDrawdownPct != null ? <Mono>{config.maxDrawdownPct}%</Mono> : <Empty>—</Empty>}</Value>
        </Row>
        <Row>
          <Label>Orchestrator</Label>
          <Value>
            {config.orchestratorOverride == null
              ? <Empty>—</Empty>
              : config.orchestratorOverride
                ? (
                  <Mono>
                    <Amber>override allowed</Amber>
                    {config.vetoWindow.length > 0 && <Dim> · veto on {config.vetoWindow.join(', ')}</Dim>}
                  </Mono>
                ) : <Mono>override disabled</Mono>}
          </Value>
        </Row>
      </Grid>
    </Panel>
  );
}
