'use client';

import React, { useState, useCallback } from 'react';
import styled, { keyframes } from 'styled-components';
import { POPULAR_OVERLAYS } from '@/app/lib/types/benchmark';
import { getOverlayColor } from '@/app/lib/utils/colors';

interface OverlaySelectorProps {
  activeOverlays: string[];
  onToggle: (symbol: string) => void;
  loadingSymbols?: string[];
}

const Container = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
`;

const Label = styled.span`
  font-size: 11px;
  font-weight: bold;
  color: ${p => p.theme.colors.textMuted};
  text-transform: uppercase;
  letter-spacing: 0.05em;
  white-space: nowrap;
`;

const spin = keyframes`
  to { transform: rotate(360deg); }
`;

const Chip = styled.button<{ $active: boolean; $color: string }>`
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  font-size: 12px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-weight: bold;
  letter-spacing: 0.03em;
  border-radius: 3px;
  cursor: pointer;
  transition: all 0.15s ease;
  white-space: nowrap;

  background: ${p => p.$active ? `${p.$color}22` : 'transparent'};
  border: 1px solid ${p => p.$active ? p.$color : p.theme.colors.border};
  color: ${p => p.$active ? p.$color : p.theme.colors.textMuted};

  &:hover {
    border-color: ${p => p.$color};
    color: ${p => p.$color};
    background: ${p => `${p.$color}11`};
  }
`;

const ChipDash = styled.span<{ $color: string }>`
  display: inline-block;
  width: 12px;
  height: 0;
  border-top: 2px dashed ${p => p.$color};
`;

const Spinner = styled.span<{ $color: string }>`
  display: inline-block;
  width: 10px;
  height: 10px;
  border: 1.5px solid ${p => `${p.$color}40`};
  border-top-color: ${p => p.$color};
  border-radius: 50%;
  animation: ${spin} 0.6s linear infinite;
`;

const CloseIcon = styled.span`
  font-size: 15px;
  line-height: 1;
  margin-left: 2px;
  opacity: 0.7;

  &:hover {
    opacity: 1;
  }
`;

const CustomInput = styled.input`
  width: 60px;
  padding: 3px 6px;
  font-size: 11px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  background: transparent;
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 3px;
  color: ${p => p.theme.colors.foreground};
  text-transform: uppercase;
  letter-spacing: 0.05em;

  &::placeholder {
    color: ${p => p.theme.colors.textMuted};
    text-transform: none;
    letter-spacing: normal;
  }

  &:focus {
    outline: none;
    border-color: ${p => p.theme.colors.primary};
  }
`;

export function OverlaySelector({ activeOverlays, onToggle, loadingSymbols = [] }: OverlaySelectorProps) {
  const [customTicker, setCustomTicker] = useState('');

  const handleCustomSubmit = useCallback((e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      const ticker = customTicker.trim().toUpperCase();
      if (ticker && /^[A-Z]{1,5}$/.test(ticker)) {
        onToggle(ticker);
        setCustomTicker('');
      }
    }
  }, [customTicker, onToggle]);

  return (
    <Container>
      <Label>vs Market:</Label>
      {POPULAR_OVERLAYS.map(({ symbol, label }) => {
        const active = activeOverlays.includes(symbol);
        const loading = loadingSymbols.includes(symbol);
        const color = getOverlayColor(symbol);

        return (
          <Chip
            key={symbol}
            $active={active}
            $color={color}
            onClick={() => onToggle(symbol)}
            title={label}
          >
            {loading ? (
              <Spinner $color={color} />
            ) : active ? (
              <ChipDash $color={color} />
            ) : null}
            {symbol}
          </Chip>
        );
      })}
      <CustomInput
        value={customTicker}
        onChange={e => setCustomTicker(e.target.value.toUpperCase())}
        onKeyDown={handleCustomSubmit}
        placeholder="AAPL..."
        maxLength={5}
      />
      {activeOverlays
        .filter(symbol => !POPULAR_OVERLAYS.some(p => p.symbol === symbol))
        .map(symbol => {
          const color = getOverlayColor(symbol);
          return (
            <Chip
              key={`custom-${symbol}`}
              $active={true}
              $color={color}
              onClick={() => onToggle(symbol)}
              title={`Remove ${symbol}`}
            >
              <ChipDash $color={color} />
              {symbol}
              <CloseIcon>&times;</CloseIcon>
            </Chip>
          );
        })}
    </Container>
  );
}
