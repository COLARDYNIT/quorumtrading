'use client';

import { useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import styled from 'styled-components';
import { useQueries } from '@tanstack/react-query';
import { apiClient } from '@/app/lib/api/client';
import { queryKeys, usePublicStrategies, useCloneStrategy } from '@/app/lib/api/hooks';
import { useAuth } from '@/app/hooks/useAuth';
import type { StrategyResponse, StrategyPerformanceResponse } from '@/app/lib/api/client';
import { LibraryHeader } from './LibraryHeader';
import { FilterBar, type SortKey } from './FilterBar';
import { MagHero } from './MagHero';
import { MagCard } from './MagCard';
import { LibraryEmpty } from './LibraryEmpty';
import { parseStrategyFrontmatter, allTickers } from './parseStrategyFrontmatter';

const Container = styled.div`
  width: 100%;
  max-width: 1440px;
  margin: 0 auto;
  padding: 0 32px 80px;

  @media (max-width: 768px) {
    padding: 0 20px 56px;
  }
`;

const Grid = styled.div`
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;

  @media (max-width: 1100px) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  @media (max-width: 720px) {
    grid-template-columns: 1fr;
  }
`;

const DerivedNote = styled.p`
  margin: 32px 0 0;
  padding: 12px 16px;
  border: 1px dashed ${p => p.theme.colors.border};
  border-radius: 3px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};

  & > b { color: ${p => p.theme.colors.fgMuted}; font-weight: ${p => p.theme.typography.fontWeight.medium}; }
`;

const LoadingNote = styled.p`
  margin: 48px 0;
  text-align: center;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13px;
  color: ${p => p.theme.colors.fgMuted};
`;

interface ScoredStrategy {
  strategy: StrategyResponse;
  performance: StrategyPerformanceResponse | undefined;
  tickers: string[];
  consensus?: string;
}

export function LibraryView() {
  const router = useRouter();
  const { user } = useAuth();
  const { data: strategies, isLoading } = usePublicStrategies();
  const cloneMutation = useCloneStrategy();

  // We need per-strategy performance to drive sorting + the hero pick.
  // useQueries deduplicates against existing useStrategyPerformance calls
  // in MagHero/MagCard so we don't fire two requests per strategy.
  const perfQueries = useQueries({
    queries: (strategies ?? []).map(s => ({
      queryKey: queryKeys.strategyPerformance(s.id),
      queryFn: () => apiClient.strategies.getPerformance(s.id),
      enabled: !!s.id,
      staleTime: 5 * 60 * 1000,
    })),
  });

  const enriched: ScoredStrategy[] = useMemo(() => {
    return (strategies ?? []).map((s, i) => {
      const fm = parseStrategyFrontmatter(s.markdownContent);
      return {
        strategy: s,
        performance: perfQueries[i]?.data as StrategyPerformanceResponse | undefined,
        tickers: allTickers(fm),
        consensus: fm.consensusMethod,
      };
    });
  }, [strategies, perfQueries]);

  // Collect distinct filter values for the chip bar.
  const availableTickers = useMemo(() => {
    const set = new Set<string>();
    enriched.forEach(e => e.tickers.forEach(t => set.add(t)));
    return Array.from(set).sort();
  }, [enriched]);
  const availableConsensus = useMemo(() => {
    const set = new Set<string>();
    enriched.forEach(e => { if (e.consensus) set.add(e.consensus); });
    return Array.from(set).sort();
  }, [enriched]);

  const [activeTickers, setActiveTickers] = useState<Set<string>>(new Set());
  const [activeConsensus, setActiveConsensus] = useState<Set<string>>(new Set());
  const [sort, setSort] = useState<SortKey>('featured');

  const filtered = useMemo(() => {
    return enriched.filter(({ tickers, consensus }) => {
      if (activeTickers.size > 0 && !tickers.some(t => activeTickers.has(t))) return false;
      if (activeConsensus.size > 0 && (!consensus || !activeConsensus.has(consensus))) return false;
      return true;
    });
  }, [enriched, activeTickers, activeConsensus]);

  const sorted = useMemo(() => {
    const arr = [...filtered];
    switch (sort) {
      case 'sharpe':
        arr.sort((a, b) => (b.performance?.avgSharpeRatio ?? -Infinity) - (a.performance?.avgSharpeRatio ?? -Infinity));
        break;
      case 'best':
        arr.sort((a, b) => (b.performance?.bestReturn ?? -Infinity) - (a.performance?.bestReturn ?? -Infinity));
        break;
      case 'newest':
        arr.sort((a, b) => new Date(b.strategy.createdAt).getTime() - new Date(a.strategy.createdAt).getTime());
        break;
      case 'featured':
      default: {
        // Featured = strategies with at least one completed sim, ranked by best return.
        // Falls back to creation date when no perf is available yet.
        arr.sort((a, b) => {
          const aBest = a.performance?.bestReturn ?? null;
          const bBest = b.performance?.bestReturn ?? null;
          if (aBest != null && bBest != null) return bBest - aBest;
          if (aBest != null) return -1;
          if (bBest != null) return 1;
          return new Date(b.strategy.createdAt).getTime() - new Date(a.strategy.createdAt).getTime();
        });
        break;
      }
    }
    return arr;
  }, [filtered, sort]);

  const toggleTicker = (t: string) => {
    setActiveTickers(prev => {
      const next = new Set(prev);
      if (next.has(t)) next.delete(t); else next.add(t);
      return next;
    });
  };
  const toggleConsensus = (c: string) => {
    setActiveConsensus(prev => {
      const next = new Set(prev);
      if (next.has(c)) next.delete(c); else next.add(c);
      return next;
    });
  };
  const clearAll = () => {
    setActiveTickers(new Set());
    setActiveConsensus(new Set());
  };

  const handleFork = (id: string) => {
    if (!user) {
      router.push('/auth/signin?returnTo=/strategies/public');
      return;
    }
    cloneMutation.mutate(id);
  };
  const canFork = !!user;

  // Author count: distinct ownerIds
  const authorCount = useMemo(() => {
    const set = new Set<string>();
    (strategies ?? []).forEach(s => { if (s.ownerId) set.add(s.ownerId); });
    return set.size;
  }, [strategies]);

  const featured = sorted[0]?.strategy;
  const rest = sorted.slice(1).map(e => e.strategy);

  return (
    <Container>
      <LibraryHeader count={strategies?.length ?? 0} authors={authorCount} />

      <FilterBar
        availableTickers={availableTickers}
        availableConsensus={availableConsensus}
        activeTickers={activeTickers}
        activeConsensus={activeConsensus}
        sort={sort}
        onToggleTicker={toggleTicker}
        onToggleConsensus={toggleConsensus}
        onSort={setSort}
        onClearAll={clearAll}
      />

      {isLoading ? (
        <LoadingNote>Loading public strategies…</LoadingNote>
      ) : (strategies?.length ?? 0) === 0 ? (
        <LibraryEmpty signedIn={canFork} />
      ) : sorted.length === 0 ? (
        <LoadingNote>No strategies match your filters. <button type="button" style={{ background: 'none', border: 'none', color: 'var(--qt-brand)', cursor: 'pointer' }} onClick={clearAll}>Clear filters →</button></LoadingNote>
      ) : (
        <>
          {featured && (
            <MagHero
              strategy={featured}
              isOwn={!!user && featured.ownerId === user.id}
              onFork={handleFork}
              canFork={canFork}
            />
          )}
          <Grid>
            {rest.map(s => (
              <MagCard
                key={s.id}
                strategy={s}
                isOwn={!!user && s.ownerId === user.id}
                onFork={handleFork}
                canFork={canFork}
              />
            ))}
          </Grid>
          <DerivedNote>
            <b>Note · </b>
            performance metrics are computed from the author&apos;s own completed simulations. Community-fork counts,
            cross-author backtest aggregates, and editorial &quot;featured&quot; curation are not yet wired through —
            engineering work needed before they can light up here.
          </DerivedNote>
        </>
      )}
    </Container>
  );
}
