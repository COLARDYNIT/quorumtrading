'use client';

import { useState, useMemo, useEffect } from 'react';
import { useSearchParams } from 'next/navigation';
import styled from 'styled-components';
import { DataAvailabilityFilters } from './DataAvailabilityFilters';
import { DataAvailabilityCalendar } from './DataAvailabilityCalendar';
import { BackfillPanel } from './BackfillPanel';
import { HistoricEventBackfillSelector } from './HistoricEventBackfillSelector';
import { useDataCoverage, useStrategy } from '@/app/lib/api/hooks';
import { EventType, DataGap } from '@/app/lib/types/dataAvailability';
import { format, startOfMonth, endOfMonth } from 'date-fns';

/**
 * Extract symbols from a strategy's markdown content (including YAML frontmatter).
 * Looks for patterns like:
 * - YAML list: `stock_tickers:` or `crypto_tickers:` followed by `- SYMBOL` lines
 * - YAML object keys: `position_limits:` followed by `SYMBOL: value` lines
 * - Inline: `symbols: BTC, ETH` or `symbols: [BTC, ETH]`
 * - Trading pairs like BTC-USD, ETH-USD
 */
function extractSymbolsFromMarkdown(markdown: string): string[] {
  const symbols = new Set<string>();

  // Pattern 1: YAML list items under stock_tickers or crypto_tickers
  // Matches patterns like:
  // stock_tickers:
  //   - AAPL
  //   - MSFT
  const tickerListRegex = /(?:stock_tickers|crypto_tickers):\s*\n((?:\s*-\s*[A-Z0-9]+\s*\n?)+)/gi;
  let tickerMatch;
  while ((tickerMatch = tickerListRegex.exec(markdown)) !== null) {
    const tickerLines = tickerMatch[1];
    const itemRegex = /-\s*([A-Z0-9]+)/gi;
    let itemMatch;
    while ((itemMatch = itemRegex.exec(tickerLines)) !== null) {
      symbols.add(itemMatch[1].toUpperCase());
    }
  }

  // Pattern 2: YAML object keys under position_limits
  // Matches patterns like:
  // position_limits:
  //   BTC: 0.40
  //   ETH: 0.30
  const positionLimitsMatch = markdown.match(/position_limits:\s*\n((?:\s+[A-Z0-9]+:\s*[\d.]+\s*\n?)+)/i);
  if (positionLimitsMatch) {
    const limitLines = positionLimitsMatch[1];
    const limitRegex = /^\s*([A-Z0-9]+):\s*[\d.]+/gim;
    let limitMatch;
    while ((limitMatch = limitRegex.exec(limitLines)) !== null) {
      symbols.add(limitMatch[1].toUpperCase());
    }
  }

  // Pattern 3: Inline symbols: BTC, ETH or symbols: [BTC, ETH]
  const symbolsMatch = markdown.match(/symbols?:\s*\[?([A-Z0-9,\s-]+)\]?/i);
  if (symbolsMatch) {
    symbolsMatch[1].split(',').forEach(s => {
      const symbol = s.trim().replace(/-USD$/i, '');
      if (symbol && /^[A-Z0-9]+$/i.test(symbol)) {
        symbols.add(symbol.toUpperCase());
      }
    });
  }

  return Array.from(symbols);
}

const DashboardGrid = styled.div`
  display: grid;
  grid-template-columns: 280px 1fr 320px 320px;
  gap: ${p => p.theme.spacing.lg};
  align-items: start;

  @media (max-width: 1600px) {
    grid-template-columns: 280px 1fr 320px;
  }

  @media (max-width: 1400px) {
    grid-template-columns: 1fr;
  }
`;

const FilterPane = styled.div`
  @media (max-width: 1400px) {
    grid-row: 1;
  }
`;

const CalendarPane = styled.div`
  @media (max-width: 1400px) {
    grid-row: 3;
  }
`;

const BackfillPane = styled.div`
  @media (max-width: 1400px) {
    grid-row: 2;
  }
`;

const HistoricEventPane = styled.div`
  @media (max-width: 1600px) {
    grid-column: 1 / -1;
    grid-row: 4;
  }

  @media (max-width: 1400px) {
    grid-row: 4;
  }
`;

export function DataAvailabilityDashboard() {
  const searchParams = useSearchParams();
  const strategyId = searchParams.get('strategyId') || undefined;

  // Fetch strategy if strategyId is provided
  const { data: strategy, isLoading: strategyLoading } = useStrategy(strategyId || '');

  // Available symbols - the full list that can be selected
  const [availableSymbols, setAvailableSymbols] = useState<string[]>(() => {
    const param = searchParams.get('symbols');
    return param ? param.split(',') : ['BTC', 'ETH'];
  });

  // Selected symbols - subset of available that are currently checked
  const [selectedSymbols, setSelectedSymbols] = useState<string[]>(() => {
    const param = searchParams.get('symbols');
    return param ? param.split(',') : ['BTC', 'ETH'];
  });

  // Track if available symbols have been initialized from strategy
  const [symbolsInitialized, setSymbolsInitialized] = useState(false);

  // Extract symbols from strategy markdown - must find symbols when strategy is provided
  const strategySymbols = useMemo(() => {
    if (strategy) {
      const extracted = extractSymbolsFromMarkdown(strategy.markdownContent);
      if (extracted.length === 0) {
        console.error(`Strategy "${strategy.name}" has no extractable symbols from markdown content`);
      }
      return extracted;
    }
    return [];
  }, [strategy]);

  // Initialize available and selected symbols from strategy when it loads.
  // useEffect, not useMemo: this is a side effect, and setState from a useMemo runs during render
  // — React documents that as a way to get an infinite render loop, and makes no guarantee about
  // when a memo runs or whether it is discarded and recomputed. The suppressions this used to
  // carry named react-hooks/set-state-in-effect while the rule actually firing was
  // set-state-in-render, so they silenced nothing.
  useEffect(() => {
    if (strategyId && strategy && !symbolsInitialized && strategySymbols.length > 0) {
      setAvailableSymbols(strategySymbols);
      setSelectedSymbols(strategySymbols); // Start with all selected
      setSymbolsInitialized(true);
    }
  }, [strategyId, strategy, symbolsInitialized, strategySymbols]);

  // Don't fetch data coverage until we have the symbols initialized
  const isWaitingForStrategy = strategyId && strategyLoading && !symbolsInitialized;

  // Handle symbol selection changes from filters (only changes selected, not available)
  const handleSymbolsChange = (newSelectedSymbols: string[]) => {
    setSelectedSymbols(newSelectedSymbols);
  };

  const [dataTypes, setDataTypes] = useState<EventType[]>(() => {
    const param = searchParams.get('dataTypes');
    return param ? param.split(',') as EventType[] : [EventType.PRICE, EventType.NEWS];
  });

  const [selectedMonth, setSelectedMonth] = useState(() => new Date());

  const from = format(startOfMonth(selectedMonth), 'yyyy-MM-dd');
  const to = format(endOfMonth(selectedMonth), 'yyyy-MM-dd');

  const { data: coverage, isLoading: coverageLoading, error } = useDataCoverage({
    symbols: selectedSymbols,
    dataTypes,
    from,
    to,
    strategyId,
  });

  // Show loading if waiting for strategy or loading coverage data
  const isLoading = isWaitingForStrategy || coverageLoading;

  const [selectedGaps, setSelectedGaps] = useState<DataGap[]>([]);

  return (
    <DashboardGrid>
      <FilterPane>
        <DataAvailabilityFilters
          availableSymbols={availableSymbols}
          selectedSymbols={selectedSymbols}
          onSymbolsChange={handleSymbolsChange}
          dataTypes={dataTypes}
          onDataTypesChange={setDataTypes}
          selectedMonth={selectedMonth}
          onMonthChange={setSelectedMonth}
        />
      </FilterPane>

      <CalendarPane>
        <DataAvailabilityCalendar
          coverage={coverage}
          isLoading={isLoading}
          error={error}
          selectedMonth={selectedMonth}
          onGapSelect={setSelectedGaps}
        />
      </CalendarPane>

      <BackfillPane>
        <BackfillPanel
          gaps={selectedGaps}
          symbols={selectedSymbols}
          dataTypes={dataTypes}
          from={from}
          to={to}
        />
      </BackfillPane>

      <HistoricEventPane>
        <HistoricEventBackfillSelector />
      </HistoricEventPane>
    </DashboardGrid>
  );
}
