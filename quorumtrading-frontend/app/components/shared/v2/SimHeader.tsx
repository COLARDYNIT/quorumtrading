'use client';

import styled from 'styled-components';
import type { ReactNode } from 'react';
import { StatusPill, type StatusPillKind } from './StatusPill';
import { KpiStrip, type KpiItem } from './KpiStrip';

interface SimHeaderProps {
  title: string;
  ticker?: string;
  pills?: Array<{ kind: StatusPillKind; label: string }>;
  meta?: Array<{ label: string; value: ReactNode }>;
  kpis: KpiItem[];
  dense?: boolean;
  /** Operator glyphs on the KPI seams — see {@link KpiStrip}'s `ops`. */
  kpiOps?: Array<string | null>;
  /** Panel docked flush under the KPI strip (e.g. an expandable explainer). */
  kpiFooter?: ReactNode;
}

const HeaderRoot = styled.div`
  padding: 20px 24px 18px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
`;

const Row1 = styled.div`
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 12px;
`;

const TitleWrap = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;

  h1 {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 24px;
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
    color: ${p => p.theme.colors.fgStrong};
    letter-spacing: -0.005em;
    margin: 0;
  }
`;

const Ticker = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 14px;
  color: ${p => p.theme.colors.fgMuted};
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  padding: 2px 8px;
  border-radius: 2px;
`;

const SubMeta = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 13px;
  color: ${p => p.theme.colors.fgMuted};
  display: flex;
  gap: 16px;
  flex-wrap: wrap;

  .dim {
    color: ${p => p.theme.colors.fgDimmed};
  }
  b {
    color: ${p => p.theme.colors.fgStrong};
    font-weight: ${p => p.theme.typography.fontWeight.medium};
  }
`;

export function SimHeader({
  title, ticker, pills, meta, kpis, dense = true, kpiOps, kpiFooter,
}: SimHeaderProps) {
  return (
    <HeaderRoot>
      <Row1>
        <TitleWrap>
          <h1>{title}</h1>
          {ticker && <Ticker>{ticker}</Ticker>}
          {pills?.map((pill, i) => (
            <StatusPill key={i} kind={pill.kind}>{pill.label}</StatusPill>
          ))}
        </TitleWrap>
        {meta && meta.length > 0 && (
          <SubMeta>
            {meta.map((m, i) => (
              <span key={i}>
                <span className="dim">{m.label}</span> <b>{m.value}</b>
              </span>
            ))}
          </SubMeta>
        )}
      </Row1>
      <KpiStrip items={kpis} dense={dense} ops={kpiOps} flatBottom={kpiFooter != null} />
      {kpiFooter}
    </HeaderRoot>
  );
}
