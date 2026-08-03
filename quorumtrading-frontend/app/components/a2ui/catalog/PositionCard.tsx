'use client';

import styled from 'styled-components';

interface PositionCardProps {
  ticker?: unknown;
  quantity?: unknown;
  avgPrice?: unknown;
  currentPrice?: unknown;
  totalValue?: unknown;
  pnl?: unknown;
  pnlPercent?: unknown;
  [key: string]: unknown;
}

export function PositionCard({
  ticker,
  quantity,
  avgPrice,
  currentPrice,
  totalValue,
  pnl,
  pnlPercent,
}: PositionCardProps) {
  const pnlNum = typeof pnl === 'number' ? pnl : 0;
  const isPositive = pnlNum >= 0;
  const pnlColor = isPositive ? 'var(--qt-action-buy)' : 'var(--qt-action-sell)';

  return (
    <Card>
      <CardHeader>
        <Ticker>{String(ticker || 'UNKNOWN')}</Ticker>
        {totalValue !== undefined && (
          <TotalValue>${Number(totalValue).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</TotalValue>
        )}
      </CardHeader>
      <CardBody>
        <Stat>
          <StatLabel>Qty</StatLabel>
          <StatValue>{typeof quantity === 'number' ? quantity.toFixed(4) : String(quantity ?? '-')}</StatValue>
        </Stat>
        <Stat>
          <StatLabel>Avg Price</StatLabel>
          <StatValue>{typeof avgPrice === 'number' ? `$${avgPrice.toFixed(2)}` : '-'}</StatValue>
        </Stat>
        <Stat>
          <StatLabel>Current</StatLabel>
          <StatValue>{typeof currentPrice === 'number' ? `$${currentPrice.toFixed(2)}` : '-'}</StatValue>
        </Stat>
        {(pnl !== undefined || pnlPercent !== undefined) && (
          <Stat>
            <StatLabel>P&L</StatLabel>
            <PnlValue $color={pnlColor}>
              {isPositive ? '+' : ''}
              {typeof pnl === 'number' ? `$${pnl.toFixed(2)}` : ''}
              {typeof pnlPercent === 'number' ? ` (${pnlPercent.toFixed(2)}%)` : ''}
            </PnlValue>
          </Stat>
        )}
      </CardBody>
    </Card>
  );
}

const Card = styled.div`
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.md};
  overflow: hidden;
`;

const CardHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  border-bottom: 1px solid ${p => p.theme.colors.border};
  background: ${p => p.theme.colors.background};
`;

const Ticker = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  color: ${p => p.theme.colors.terminalGreen};
  letter-spacing: 1px;
`;

const TotalValue = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  color: ${p => p.theme.colors.textLight};
`;

const CardBody = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(100px, 1fr));
  gap: ${p => p.theme.spacing.sm};
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
`;

const Stat = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
`;

const StatLabel = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textDimmed};
  text-transform: uppercase;
  letter-spacing: 0.5px;
`;

const StatValue = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
`;

const PnlValue = styled.span<{ $color: string }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.$color};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
`;
