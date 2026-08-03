'use client';

import { useMemo, useState } from 'react';
import styled from 'styled-components';
import { Panel, HedgedValue } from '@/app/components/shared/v2';
import { useSimulationData } from '../../contexts/SimulationDataContext';
import { LiquidateConfirmModal } from '../LiquidateConfirmModal';
import { canSellPositions, type SimulationResponse } from '@/app/lib/types/api';
import { deploymentSplit, cadenceLabel } from '../../lib/investedCapital';

const numberFmt = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', minimumFractionDigits: 2, maximumFractionDigits: 2 });
const qtyFmt = (n: number) => n.toLocaleString('en-US', { minimumFractionDigits: 4, maximumFractionDigits: 4 });
const nextDecisionFmt = new Intl.DateTimeFormat('en-US', {
  day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', hour12: false,
});

/**
 * Deployment strip — leads the panel because it answers the question the
 * holdings table cannot: how much of the money is actually working. A
 * simulation can hold one tiny position beside a large cash balance and the
 * table alone makes that look like a portfolio.
 */
const DeployStrip = styled.div`
  padding: 10px 12px 12px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};

  .line1 {
    display: flex;
    justify-content: space-between;
    align-items: baseline;
    gap: 10px;
    margin-bottom: 7px;
  }
  .line1 .lbl {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 11px;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.fgDimmed};
  }
  .line1 .v {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-variant-numeric: tabular-nums;
    font-size: 13px;
    color: ${p => p.theme.colors.fgStrong};
  }
  .line1 .idle {
    color: ${p => p.theme.colors.fgMuted};
  }
  /* Mostly-idle capital is the headline, not a footnote. */
  .line1 .idle-high {
    color: ${p => p.theme.colors.terminalAmber};
  }

  .bar {
    display: flex;
    height: 6px;
    border-radius: 1px;
    overflow: hidden;
    background: ${p => p.theme.colors.background};
    border: 1px solid ${p => p.theme.colors.borderSubtle};
  }
  .bar .w {
    background: ${p => p.theme.colors.brand};
    opacity: 0.8;
  }
  .bar .i {
    background: rgba(255, 255, 255, 0.07);
  }

  .line2 {
    display: flex;
    justify-content: space-between;
    gap: 10px;
    margin-top: 6px;
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-variant-numeric: tabular-nums;
    font-size: 11px;
  }
  .line2 .w { color: ${p => p.theme.colors.brand}; }
  .line2 .i { color: ${p => p.theme.colors.fgMuted}; }

  .nexteval {
    margin-top: 8px;
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11px;
    color: ${p => p.theme.colors.fgMuted};
  }
  .nexteval .k {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    color: ${p => p.theme.colors.fgDimmed};
    letter-spacing: 0.08em;
    text-transform: uppercase;
    font-size: 10px;
    margin-right: 6px;
  }
`;

const TableScroll = styled.div`
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
`;

const HoldingsTable = styled.table`
  width: 100%;
  border-collapse: collapse;
  font-family: ${p => p.theme.typography.fontFamily.sans};

  thead th {
    font-size: 11px;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.fgDimmed};
    font-weight: ${p => p.theme.typography.fontWeight.medium};
    padding: 6px 10px;
    border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
    text-align: left;
    background: ${p => p.theme.colors.background};
  }
  thead th.r { text-align: right; }
  td {
    padding: 6px 10px;
    border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
    font-size: 12.5px;
    vertical-align: middle;
  }
  tr:last-child td { border-bottom: none; }

  .sym {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-weight: ${p => p.theme.typography.fontWeight.medium};
    color: ${p => p.theme.colors.fgStrong};
  }
  .name {
    color: ${p => p.theme.colors.fgMuted};
    font-size: 11.5px;
  }
  .num {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-variant-numeric: tabular-nums;
    text-align: right;
    color: ${p => p.theme.colors.foreground};
  }
  .pos { color: ${p => p.theme.colors.positive}; }
  .neg { color: ${p => p.theme.colors.negative}; }
`;

const AllocBar = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  justify-content: flex-end;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;

  .track {
    width: 60px;
    height: 4px;
    background: ${p => p.theme.colors.borderSubtle};
    border-radius: 1px;
    position: relative;
    overflow: hidden;
  }
  .fill {
    position: absolute;
    inset: 0 auto 0 0;
    background: ${p => p.theme.colors.info};
    border-radius: 1px;
  }
`;

const CashRow = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  border-top: 1px solid ${p => p.theme.colors.borderSubtle};
  margin-top: 4px;

  .c {
    padding: 8px 10px;
    border-right: 1px solid ${p => p.theme.colors.borderSubtle};
    min-width: 0;
  }
  .c:last-child { border-right: none; }
  .lbl {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 11px;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.fgDimmed};
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .v {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-variant-numeric: tabular-nums;
    font-size: 14px;
    color: ${p => p.theme.colors.fgStrong};
    margin-top: 2px;
  }
`;

const LiquidateButton = styled.button`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 11px;
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  padding: 3px 8px;
  border-radius: 2px;
  border: 1px solid ${p => p.theme.colors.error};
  background: transparent;
  color: ${p => p.theme.colors.error};
  cursor: pointer;
  transition: background-color 120ms ease;
  white-space: nowrap;
  flex-shrink: 0;

  &:hover { background: rgba(255, 51, 102, 0.08); }
  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
`;

const EmptyMsg = styled.div`
  padding: 16px 12px;
  text-align: center;
  color: ${p => p.theme.colors.fgMuted};
  font-size: 13px;
`;

export function HoldingsPanel() {
  const {
    portfolioData, simulation, consensusInfo, evaluationIntervalHours, tradingModeInfo,
  } = useSimulationData();
  const [showLiquidate, setShowLiquidate] = useState(false);

  const rows = useMemo(() => {
    if (!portfolioData?.holdings) return [];
    return portfolioData.holdings.filter(h => h.amount > 0).map(h => {
      const marketValue = h.amount * h.currentPrice;
      return {
        sym: h.symbol,
        name: h.type === 'crypto' ? 'Cryptocurrency' : 'Stock',
        qty: h.amount,
        currentPrice: h.currentPrice,
        marketValue,
      };
    });
  }, [portfolioData]);

  const totalEquity = portfolioData?.totalValue ?? 0;
  const cashAvailable = portfolioData?.cash ?? 0;
  // Note: a `cashReserved` tile lived here but the API doesn't surface that
  // figure today. Hidden until the backend exposes it on PortfolioData; until
  // then any derived value would mislead during rounding drift.

  const rowsWithWeight = rows.map(r => ({
    ...r,
    weight: totalEquity > 0 ? (r.marketValue / totalEquity) * 100 : 0,
  }));

  const sellEnabled = simulation
    ? canSellPositions(simulation as unknown as SimulationResponse) && rows.length > 0
    : false;

  // How much of the invested capital the strategy has actually put to work.
  // Measured against total invested (seed + contributions) and at cost, so a
  // price move cannot masquerade as a deployment decision.
  const sim = simulation as unknown as { initialCash?: number; totalInvested?: number } | null;
  const invested = sim?.totalInvested ?? sim?.initialCash ?? 0;
  const deployment = useMemo(
    () => deploymentSplit(portfolioData?.holdings ?? [], invested),
    [portfolioData, invested]
  );
  const cadence = cadenceLabel(evaluationIntervalHours, tradingModeInfo?.evaluationTimes);
  const nextDecision = consensusInfo?.nextConsensus ?? null;
  const showDeployment = invested > 0 && portfolioData != null;

  return (
    <>
      <Panel
        title="Holdings & cash"
        count={`${rows.length} position${rows.length === 1 ? '' : 's'}`}
        noBodyPadding
        headerActions={
          <LiquidateButton
            type="button"
            disabled={!sellEnabled}
            onClick={() => setShowLiquidate(true)}
          >
            Liquidate all
          </LiquidateButton>
        }
      >
        {showDeployment && (
          <DeployStrip>
            <div className="line1">
              <span className="lbl">Capital deployed</span>
              <span className="v">
                {deployment.workPct.toFixed(1)}%
                {' · '}
                <span className={deployment.idlePct > 50 ? 'idle-high' : 'idle'}>
                  {deployment.idlePct.toFixed(1)}% idle
                </span>
              </span>
            </div>
            <div className="bar" role="img"
              aria-label={`${deployment.workPct.toFixed(1)} percent deployed, ${deployment.idlePct.toFixed(1)} percent idle`}>
              <span className="w" style={{ width: `${deployment.workPct}%` }} />
              <span className="i" style={{ width: `${deployment.idlePct}%` }} />
            </div>
            <div className="line2">
              <span className="w">{numberFmt.format(deployment.deployedCost)} at work</span>
              <span className="i">{numberFmt.format(cashAvailable)} in cash</span>
            </div>
            <div className="nexteval">
              <span className="k">Next decision</span>
              {cadence ? `${cadence} evaluation · ` : ''}
              {nextDecision
                ? nextDecisionFmt.format(nextDecision)
                : <HedgedValue label="not scheduled" title="No next evaluation could be derived — the strategy states no schedule this page can read" />}
            </div>
          </DeployStrip>
        )}
        {rowsWithWeight.length === 0 ? (
          <EmptyMsg>No open positions. Holding 100% cash.</EmptyMsg>
        ) : (
          <TableScroll>
            <HoldingsTable>
              <thead>
                <tr>
                  <th>Sym</th>
                  <th>Qty</th>
                  <th className="r">Price</th>
                  <th className="r">Mkt value</th>
                  <th className="r">Weight</th>
                </tr>
              </thead>
              <tbody>
                {rowsWithWeight.map((r) => (
                  <tr key={r.sym}>
                    <td>
                      <div className="sym">{r.sym}</div>
                      <div className="name">{r.name}</div>
                    </td>
                    <td className="num">{qtyFmt(r.qty)}</td>
                    <td className="num">{numberFmt.format(r.currentPrice)}</td>
                    <td className="num">{numberFmt.format(r.marketValue)}</td>
                    <td className="num">
                      <AllocBar>
                        <span>{r.weight.toFixed(1)}%</span>
                        <span className="track"><span className="fill" style={{ width: `${Math.min(100, r.weight)}%` }} /></span>
                      </AllocBar>
                    </td>
                  </tr>
                ))}
              </tbody>
            </HoldingsTable>
          </TableScroll>
        )}
        <CashRow>
          <div className="c">
            <div className="lbl">Cash available</div>
            <div className="v">{numberFmt.format(cashAvailable)}</div>
          </div>
          <div className="c">
            <div className="lbl">Total equity</div>
            <div className="v">{numberFmt.format(totalEquity)}</div>
          </div>
        </CashRow>
      </Panel>
      {simulation && (
        <LiquidateConfirmModal
          isOpen={showLiquidate}
          simulationId={simulation.id}
          holdings={rowsWithWeight.map(r => ({ symbol: r.sym, amount: r.qty, value: r.marketValue }))}
          onClose={() => setShowLiquidate(false)}
        />
      )}
    </>
  );
}
