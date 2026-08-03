'use client';

import { useMemo } from 'react';
import styled from 'styled-components';
import { fadeInUp } from '@/app/styles/animations';
import { useDividendInfo, useStrategies } from '@/app/lib/api/hooks';
import type { SimulationResponse, DividendInfoItem } from '@/app/lib/types/api';

interface DividendMismatchWarningProps {
  simulations: SimulationResponse[];
  overlaySymbols?: string[];
}

const Banner = styled.div`
  padding: ${p => p.theme.spacing.md};
  border: 1px solid ${p => p.theme.colors.terminalAmber}40;
  border-left: 3px solid ${p => p.theme.colors.terminalAmber};
  background: ${p => p.theme.colors.terminalAmber}08;
  animation: ${fadeInUp} 0.3s ease-out;
`;

const Title = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  color: ${p => p.theme.colors.terminalAmber};
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: ${p => p.theme.spacing.xs};
`;

const Message = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.textMuted};
  line-height: 1.6;
`;

/**
 * Parse stock_tickers from strategy YAML frontmatter.
 */
function parseTickersFromMarkdown(markdown: string): string[] {
  const tickerMatch = markdown.match(/stock_tickers:\s*\n((?:\s*-\s*\w+\n?)+)/);
  if (tickerMatch) {
    return tickerMatch[1].match(/\w+/g) || [];
  }
  return [];
}

export function DividendMismatchWarning({ simulations, overlaySymbols = [] }: DividendMismatchWarningProps) {
  const { data: strategies } = useStrategies();

  // Collect all tickers from all strategies in the benchmark + overlay symbols
  const allTickers = useMemo(() => {
    if (!strategies || simulations.length === 0) return [];
    const tickers = new Set<string>();
    for (const sim of simulations) {
      const strategy = strategies.find(s => s.id === sim.strategyId);
      if (strategy?.markdownContent) {
        parseTickersFromMarkdown(strategy.markdownContent).forEach(t => tickers.add(t));
      }
    }
    overlaySymbols.forEach(s => tickers.add(s));
    return [...tickers];
  }, [strategies, simulations, overlaySymbols]);

  const { data: dividendData } = useDividendInfo(allTickers);

  // Build warning message
  const warningMessage = useMemo(() => {
    if (!dividendData?.items || !strategies || simulations.length === 0) return null;

    const dividendMap = new Map(
      dividendData.items.map((item: DividendInfoItem) => [item.ticker, item])
    );

    // Check each simulation's strategy tickers for dividends
    const simDividendProfiles: { name: string; payers: string[]; nonPayers: string[] }[] = [];

    for (const sim of simulations) {
      const strategy = strategies.find(s => s.id === sim.strategyId);
      if (!strategy?.markdownContent) continue;

      const tickers = parseTickersFromMarkdown(strategy.markdownContent);
      const payers: string[] = [];
      const nonPayers: string[] = [];

      for (const t of tickers) {
        const info = dividendMap.get(t.toUpperCase());
        if (info?.paysDividends) {
          const yieldStr = info.annualYieldEstimate != null
            ? ` ~$${info.annualYieldEstimate.toFixed(2)}/share`
            : '';
          payers.push(`${t}${yieldStr}`);
        } else {
          nonPayers.push(t);
        }
      }

      simDividendProfiles.push({ name: strategy.name, payers, nonPayers });
    }

    // Check overlays
    const overlayPayers: string[] = [];
    for (const s of overlaySymbols) {
      const info = dividendMap.get(s.toUpperCase());
      if (info?.paysDividends) {
        const yieldStr = info.annualYieldEstimate != null
          ? ` ~$${info.annualYieldEstimate.toFixed(2)}/share`
          : '';
        overlayPayers.push(`${s}${yieldStr}`);
      }
    }

    // Determine if there's a mismatch (some strategies have dividend payers, some don't)
    const hasMixed = simDividendProfiles.some(s => s.payers.length > 0)
      && simDividendProfiles.some(s => s.payers.length === 0);
    const hasOverlayMismatch = overlayPayers.length > 0;

    if (!hasMixed && !hasOverlayMismatch) return null;

    const parts: string[] = [];

    if (hasMixed) {
      const withDiv = simDividendProfiles.filter(s => s.payers.length > 0);
      const withoutDiv = simDividendProfiles.filter(s => s.payers.length === 0);

      parts.push(
        withDiv.map(s => `"${s.name}" holds dividend-paying stocks (${s.payers.join(', ')})`).join('; ') +
        ` while ` +
        withoutDiv.map(s => `"${s.name}"`).join(', ') +
        ` does not.`
      );
    }

    if (hasOverlayMismatch) {
      parts.push(
        `${overlayPayers.join(', ')} overlay${overlayPayers.length > 1 ? 's' : ''} exclude${overlayPayers.length === 1 ? 's' : ''} dividend income in the buy-and-hold comparison.`
      );
    }

    parts.push('Returns shown are price-only — dividend income is not reflected.');

    return parts.join(' ');
  }, [dividendData, strategies, simulations, overlaySymbols]);

  if (!warningMessage) return null;

  return (
    <Banner>
      <Title>Dividend Comparison Notice</Title>
      <Message>{warningMessage}</Message>
    </Banner>
  );
}
