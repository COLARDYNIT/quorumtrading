'use client';

import styled from 'styled-components';
import { tagBase, tagIdentifier } from '@/app/styles/labels';

export type SortKey = 'featured' | 'sharpe' | 'best' | 'newest';

interface FilterBarProps {
  /** All distinct tickers across the library (parsed from frontmatter). */
  availableTickers: string[];
  /** All distinct consensus methods. */
  availableConsensus: string[];
  activeTickers: Set<string>;
  activeConsensus: Set<string>;
  sort: SortKey;
  onToggleTicker: (t: string) => void;
  onToggleConsensus: (c: string) => void;
  onSort: (key: SortKey) => void;
  onClearAll: () => void;
}

const Bar = styled.section`
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 16px;
  padding: 18px 0 24px;
  border-top: 1px solid ${p => p.theme.colors.borderSubtle};
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  margin-bottom: 28px;

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }
`;

const Chips = styled.div`
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
`;

const Group = styled.span`
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
  padding-right: 14px;
  margin-right: 4px;
  border-right: 1px solid ${p => p.theme.colors.borderSubtle};

  &:last-child {
    border-right: none;
    margin-right: 0;
    padding-right: 0;
  }
`;

const Label = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
  margin-right: 4px;
`;

/** Tickers and consensus methods are identifiers — rendered verbatim, never case-transformed. */
const Chip = styled.button<{ $on: boolean }>`
  ${tagBase}
  ${tagIdentifier}
  padding: 3px 10px;
  color: ${p => p.$on ? p.theme.colors.fgStrong : p.theme.colors.fgMuted};
  cursor: pointer;
  transition: color 120ms ease, border-color 120ms ease;

  &:hover { color: ${p => p.theme.colors.foreground}; }

  & > .x { color: ${p => p.theme.colors.fgDimmed}; }
`;

const Clear = styled.button`
  background: none;
  border: none;
  color: ${p => p.theme.colors.fgDimmed};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  padding: 3px 8px;
  cursor: pointer;
  margin-left: 4px;

  &:hover { color: ${p => p.theme.colors.foreground}; }
`;

const SortRight = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;

  & > .lbl {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11px;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.fgDimmed};
  }
`;

const Seg = styled.div`
  display: inline-flex;
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 3px;
  overflow: hidden;
`;

const SegButton = styled.button<{ $on: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 12.5px;
  padding: 4px 12px;
  border: none;
  border-left: 1px solid ${p => p.theme.colors.border};
  background: ${p => p.$on ? 'rgba(255, 255, 255, 0.04)' : 'transparent'};
  color: ${p => p.$on ? p.theme.colors.fgStrong : p.theme.colors.fgMuted};
  cursor: pointer;

  &:first-child { border-left: none; }
  &:hover { color: ${p => p.theme.colors.foreground}; }
`;

const SORT_LABELS: Record<SortKey, string> = {
  featured: 'Featured',
  sharpe: 'Sharpe',
  best: 'Best run',
  newest: 'Newest',
};

export function FilterBar({
  availableTickers,
  availableConsensus,
  activeTickers,
  activeConsensus,
  sort,
  onToggleTicker,
  onToggleConsensus,
  onSort,
  onClearAll,
}: FilterBarProps) {
  const activeCount = activeTickers.size + activeConsensus.size;
  return (
    <Bar>
      <Chips>
        {availableTickers.length > 0 && (
          <Group>
            <Label>Ticker</Label>
            {availableTickers.slice(0, 8).map(t => {
              const on = activeTickers.has(t);
              return (
                <Chip key={t} type="button" $on={on} onClick={() => onToggleTicker(t)}>
                  {t}{on && <span className="x">×</span>}
                </Chip>
              );
            })}
          </Group>
        )}
        {availableConsensus.length > 0 && (
          <Group>
            <Label>Consensus</Label>
            {availableConsensus.map(c => {
              const on = activeConsensus.has(c);
              return (
                <Chip key={c} type="button" $on={on} onClick={() => onToggleConsensus(c)}>
                  {c === 'ORCHESTRATOR_OVERRIDE' ? 'ORCH·OVR' : c}{on && <span className="x">×</span>}
                </Chip>
              );
            })}
          </Group>
        )}
        {activeCount > 0 && <Clear type="button" onClick={onClearAll}>clear all ({activeCount})</Clear>}
      </Chips>
      <SortRight>
        <span className="lbl">Sort</span>
        <Seg>
          {(Object.keys(SORT_LABELS) as SortKey[]).map(k => (
            <SegButton key={k} type="button" $on={sort === k} onClick={() => onSort(k)}>
              {SORT_LABELS[k]}
            </SegButton>
          ))}
        </Seg>
      </SortRight>
    </Bar>
  );
}
