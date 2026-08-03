'use client';

import styled from 'styled-components';

interface PriceDisplayProps {
  label?: unknown;
  price?: unknown;
  change?: unknown;
  changePercent?: unknown;
  [key: string]: unknown;
}

export function PriceDisplay({ label, price, change, changePercent }: PriceDisplayProps) {
  const priceNum = typeof price === 'number' ? price : 0;
  const changeNum = typeof change === 'number' ? change : undefined;
  const changePctNum = typeof changePercent === 'number' ? changePercent : undefined;

  const isPositive = (changeNum ?? 0) >= 0;
  const changeColor = isPositive ? 'var(--qt-action-buy)' : 'var(--qt-action-sell)';

  return (
    <Container>
      {label ? <Label>{String(label)}</Label> : null}
      <PriceRow>
        <Price>${priceNum.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</Price>
        {(changeNum !== undefined || changePctNum !== undefined) && (
          <Change $color={changeColor}>
            {isPositive ? '+' : ''}
            {changeNum !== undefined && `$${changeNum.toFixed(2)}`}
            {changePctNum !== undefined && ` (${changePctNum.toFixed(2)}%)`}
          </Change>
        )}
      </PriceRow>
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
`;

const Label = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textDimmed};
  text-transform: uppercase;
  letter-spacing: 0.5px;
`;

const PriceRow = styled.div`
  display: flex;
  align-items: baseline;
  gap: ${p => p.theme.spacing.sm};
`;

const Price = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  color: ${p => p.theme.colors.textLight};
`;

const Change = styled.span<{ $color: string }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.$color};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
`;
