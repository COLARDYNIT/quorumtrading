'use client';

import { useState, useMemo, useEffect } from 'react';
import { useSearchParams } from 'next/navigation';
import styled from 'styled-components';
import { format, startOfMonth, endOfMonth } from 'date-fns';
import { Panel } from '@/app/components/shared/v2';
import { useDataCoverage, useDataHorizon, useStrategy } from '@/app/lib/api/hooks';
import { useBackfillProgress } from '@/app/lib/websocket/useBackfillProgress';
import { EventType, GapStatus } from '@/app/lib/types/dataAvailability';
import type { HistoricalEventResponse } from '@/app/lib/types/api';
import { VerdictBand } from './VerdictBand';
import { PeriodBar } from './PeriodBar';
import { PriceGrid } from './PriceGrid';
import { BackfillColumn } from './BackfillColumn';
import { EventRows, eventRangeLabel } from './EventRows';
import { VerifyBanner } from './VerifyBanner';
import { FilterRail } from './FilterRail';
import { buildDays, indexDaily, buildVerdict } from './coverageDerive';

/**
 * Extract symbols from a strategy's markdown content (incl. YAML frontmatter).
 * Ported verbatim from the legacy DataAvailabilityDashboard so the strategyId
 * query-param flow is preserved exactly.
 */
function extractSymbolsFromMarkdown(markdown: string): string[] {
  const symbols = new Set<string>();

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

  const positionLimitsMatch = markdown.match(
    /position_limits:\s*\n((?:\s+[A-Z0-9]+:\s*[\d.]+\s*\n?)+)/i
  );
  if (positionLimitsMatch) {
    const limitLines = positionLimitsMatch[1];
    const limitRegex = /^\s*([A-Z0-9]+):\s*[\d.]+/gim;
    let limitMatch;
    while ((limitMatch = limitRegex.exec(limitLines)) !== null) {
      symbols.add(limitMatch[1].toUpperCase());
    }
  }

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

const Page = styled.div`
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const Head = styled.div`
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};

  .eyebrow {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11px;
    letter-spacing: 0.14em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.fgDimmed};
  }
  h1 {
    font-size: 22px;
    font-weight: 600;
    color: ${p => p.theme.colors.fgStrong};
    letter-spacing: -0.01em;
    margin: 4px 0 0;
  }
  .strategy {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 12px;
    color: ${p => p.theme.colors.fgMuted};
  }
`;

const Grid = styled.div`
  display: grid;
  grid-template-columns: 218px 1fr 338px;
  gap: 16px;
  align-items: start;

  @media (max-width: 1400px) {
    grid-template-columns: 218px 1fr;
  }

  @media (max-width: 1000px) {
    grid-template-columns: 1fr;
  }
`;

const FilterPane = styled.div`
  @media (max-width: 1000px) {
    grid-row: 1;
  }
`;

const CoveragePane = styled.div`
  min-width: 0;
`;

const BackfillPane = styled.div`
  @media (max-width: 1400px) {
    grid-column: 1 / -1;
  }
`;

interface VerifyState {
  event: HistoricalEventResponse;
  symbols: string[];
  rangeLabel: string;
}

export function DataAvailabilityDashboardV2() {
  const searchParams = useSearchParams();
  const strategyId = searchParams.get('strategyId') || undefined;

  const { data: strategy, isLoading: strategyLoading } = useStrategy(strategyId || '');

  const [availableSymbols, setAvailableSymbols] = useState<string[]>(() => {
    const param = searchParams.get('symbols');
    return param ? param.split(',') : ['BTC', 'ETH'];
  });

  const [selectedSymbols, setSelectedSymbols] = useState<string[]>(() => {
    const param = searchParams.get('symbols');
    return param ? param.split(',') : ['BTC', 'ETH'];
  });

  const [symbolsInitialized, setSymbolsInitialized] = useState(false);

  const strategySymbols = useMemo(() => {
    if (strategy) {
      const extracted = extractSymbolsFromMarkdown(strategy.markdownContent);
      if (extracted.length === 0) {
        console.error(
          `Strategy "${strategy.name}" has no extractable symbols from markdown content`
        );
      }
      return extracted;
    }
    return [];
  }, [strategy]);

  // useEffect, not useMemo — see the identical initialiser in DataAvailabilityDashboard: setState
  // from a memo runs during render and risks an infinite loop, and the suppressions here named a
  // different rule than the one firing, so they silenced nothing.
  useEffect(() => {
    if (strategyId && strategy && !symbolsInitialized && strategySymbols.length > 0) {
      setAvailableSymbols(strategySymbols);
      setSelectedSymbols(strategySymbols);
      setSymbolsInitialized(true);
    }
  }, [strategyId, strategy, symbolsInitialized, strategySymbols]);

  const isWaitingForStrategy = strategyId && strategyLoading && !symbolsInitialized;

  const [dataTypes, setDataTypes] = useState<EventType[]>(() => {
    const param = searchParams.get('dataTypes');
    return param ? (param.split(',') as EventType[]) : [EventType.PRICE, EventType.NEWS];
  });

  const [selectedMonth, setSelectedMonth] = useState(() => new Date());

  // Verify-loop banner state (lifted from EventRows so the calendar jumps).
  const [verify, setVerify] = useState<VerifyState | null>(null);

  const from = format(startOfMonth(selectedMonth), 'yyyy-MM-dd');
  const to = format(endOfMonth(selectedMonth), 'yyyy-MM-dd');

  const {
    data: coverage,
    isLoading: coverageLoading,
  } = useDataCoverage({ symbols: selectedSymbols, dataTypes, from, to, strategyId });

  const isLoading = !!isWaitingForStrategy || coverageLoading;

  // Provider data horizons (earliest available date per symbol, PRICE only).
  const { data: horizonData } = useDataHorizon({
    symbols: selectedSymbols,
    dataTypes: ['PRICE'],
  });

  // symbol → earliest ISO date — only for symbols the provider actually has data
  // for. Symbols with hasData=false or null earliestAvailable stay UNKNOWN
  // (omitted), so the UI keeps its hedged em-dash for them — never invented.
  const horizon = useMemo<Record<string, string>>(() => {
    const map: Record<string, string> = {};
    horizonData?.horizons.forEach(h => {
      if (h.dataType === 'PRICE' && h.hasData && h.earliestAvailable) {
        map[h.symbol] = h.earliestAvailable;
      }
    });
    return map;
  }, [horizonData]);

  // Live FAILED gaps from the most-recent job → drive `failed` cells.
  const { allJobs } = useBackfillProgress();
  const failedGaps = useMemo(() => {
    const sorted = [...allJobs].sort(
      (a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
    );
    const latest = sorted[0];
    if (!latest) return [];
    return latest.gapDetails.filter(g => g.status === GapStatus.FAILED);
  }, [allJobs]);

  // Derived day model + indexed daily coverage.
  const dailyIndex = useMemo(() => indexDaily(coverage?.dailyCoverage), [coverage]);
  const days = useMemo(
    () => buildDays(selectedMonth.getFullYear(), selectedMonth.getMonth()),
    [selectedMonth]
  );

  const verdict = useMemo(() => {
    if (!coverage) return null;
    return buildVerdict({
      symbols: selectedSymbols,
      dataTypes,
      days,
      daily: dailyIndex,
      gaps: coverage.gaps,
      failedGaps,
      horizon,
    });
  }, [coverage, selectedSymbols, dataTypes, days, dailyIndex, failedGaps, horizon]);

  const rangeShort = format(selectedMonth, 'MMM');
  const rangeSub = `${format(selectedMonth, 'yyyy')} · ${days.length} days`;

  const monthLabel = format(selectedMonth, 'MMM yyyy');

  const handleBackfilled = (event: HistoricalEventResponse) => {
    // Jump the calendar to the event's symbols + start month (the design's fix).
    const start = new Date(event.startDate + 'T00:00:00');
    const eventSymbols = event.suggestedAssets;
    setAvailableSymbols(prev => Array.from(new Set([...prev, ...eventSymbols])));
    setSelectedSymbols(eventSymbols);
    setSelectedMonth(new Date(start.getFullYear(), start.getMonth(), 1));
    setVerify({ event, symbols: eventSymbols, rangeLabel: eventRangeLabel(event) });
  };

  return (
    <Page>
      <Head>
        <div>
          <span className="eyebrow">Admin / Market data</span>
          <h1>Data availability</h1>
        </div>
        {strategy && <span className="strategy">strategy · {strategy.name}</span>}
      </Head>

      <VerdictBand
        verdict={verdict}
        rangeShort={rangeShort}
        rangeSub={rangeSub}
        loading={isLoading}
      />

      {verify && (
        <VerifyBanner
          eventName={verify.event.name}
          symbols={verify.symbols}
          rangeLabel={verify.rangeLabel}
          onView={() => setVerify(null)}
        />
      )}

      <PeriodBar selectedMonth={selectedMonth} onMonthChange={setSelectedMonth} horizon={horizon} />

      <Grid>
        <FilterPane>
          <Panel title="Filters">
            <FilterRail
              availableSymbols={availableSymbols}
              selectedSymbols={selectedSymbols}
              onSymbolsChange={setSelectedSymbols}
              dataTypes={dataTypes}
              onDataTypesChange={setDataTypes}
            />
          </Panel>
        </FilterPane>

        <CoveragePane>
          <Panel title="Coverage" count={monthLabel}>
            <PriceGrid
              symbols={selectedSymbols}
              dataTypes={dataTypes}
              selectedMonth={selectedMonth}
              daily={dailyIndex}
              gaps={coverage?.gaps ?? []}
              failedGaps={failedGaps}
              horizon={horizon}
              justFilledSymbols={verify?.symbols}
              loading={isLoading}
            />
          </Panel>
        </CoveragePane>

        <BackfillPane>
          <BackfillColumn
            gaps={coverage?.gaps ?? []}
            symbols={selectedSymbols}
            dataTypes={dataTypes}
            from={from}
            to={to}
            horizon={horizon}
          />
        </BackfillPane>
      </Grid>

      <Panel title="Historic event backfill" count="price · suggested assets">
        <EventRows onBackfilled={handleBackfilled} />
      </Panel>
    </Page>
  );
}
