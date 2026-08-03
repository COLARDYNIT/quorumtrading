'use client';

import { useState } from 'react';
import styled from 'styled-components';
import { useHistoricalEvents, useRequestBackfill } from '@/app/lib/api/hooks';
import { EventType } from '@/app/lib/types/dataAvailability';
import type { HistoricalEventResponse } from '@/app/lib/types/api';
import { eventCatClass, eventCatLabel } from './coverageDerive';

interface EventRowsProps {
  /** Called after a backfill is requested: lift symbols + start month to the page. */
  onBackfilled: (event: HistoricalEventResponse) => void;
}

const Rows = styled.div`
  display: flex;
  flex-direction: column;
`;

const Row = styled.div<{ $sel: boolean }>`
  display: grid;
  grid-template-columns: 104px 1.4fr 120px 1fr auto;
  align-items: center;
  gap: 16px;
  padding: 9px 14px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  cursor: pointer;
  transition: background ${p => p.theme.transition.fast};

  &:last-child {
    border-bottom: none;
  }
  &:hover {
    background: rgba(255, 255, 255, 0.02);
  }
  ${p => p.$sel && `background: rgba(0,217,255,0.05); box-shadow: inset 2px 0 0 ${p.theme.colors.info};`}

  @media (max-width: 760px) {
    grid-template-columns: 1fr;
    gap: 6px;
  }
`;

const Cat = styled.span<{ $cat: string }>`
  justify-self: start;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 9.5px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  padding: 2px 8px;
  border-radius: 9999px;
  white-space: nowrap;
  display: inline-block;
  ${p => {
    switch (p.$cat) {
      case 'crisis':
        return `color: #ff3366; border: 1px solid rgba(255,51,102,0.4);`;
      case 'bull':
        return `color: #06ffa5; border: 1px solid rgba(6,255,165,0.4);`;
      case 'vol':
        return `color: #ffb000; border: 1px solid rgba(255,176,0,0.4);`;
      case 'cat':
        return `color: ${p.theme.colors.info}; border: 1px solid rgba(0,217,255,0.4);`;
      default:
        return `color: ${p.theme.colors.fgMuted}; border: 1px solid ${p.theme.colors.border};`;
    }
  }}
`;

const Name = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13px;
  font-weight: 600;
  color: ${p => p.theme.colors.fgStrong};
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const Range = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const Tickers = styled.span`
  display: inline-flex;
  gap: 5px;
  flex-wrap: wrap;
`;

const Tk = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 10px;
  padding: 1px 6px;
  border-radius: ${p => p.theme.borderRadius.sm};
  border: 1px solid ${p => p.theme.colors.border};
  color: ${p => p.theme.colors.fgMuted};
`;

const Act = styled.span`
  justify-self: end;
  @media (max-width: 760px) {
    justify-self: start;
  }
`;

const RunBtn = styled.button<{ $primary: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 11px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  padding: 5px 11px;
  border-radius: ${p => p.theme.borderRadius.sm};
  cursor: pointer;
  white-space: nowrap;
  transition: ${p => p.theme.transition.fast};
  ${p =>
    p.$primary
      ? `border: 1px solid ${p.theme.colors.info}; background: rgba(0,217,255,0.12); color: ${p.theme.colors.info};`
      : `border: 1px solid ${p.theme.colors.border}; background: transparent; color: ${p.theme.colors.fgMuted};`}

  &:hover {
    border-color: ${p => p.theme.colors.info};
    color: ${p => p.theme.colors.info};
  }
  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

const Empty = styled.div`
  padding: 20px 14px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
`;

function rangeLabel(ev: HistoricalEventResponse): string {
  const fmt = (iso: string) => {
    const d = new Date(iso + 'T00:00:00');
    return d.toLocaleDateString('en-US', { month: 'short', year: 'numeric' });
  };
  const a = fmt(ev.startDate);
  const b = fmt(ev.endDate);
  return a === b ? a : `${a} – ${b}`;
}

export function EventRows({ onBackfilled }: EventRowsProps) {
  const { data: events, isLoading } = useHistoricalEvents();
  const { mutate: requestBackfill, isPending } = useRequestBackfill();
  const [sel, setSel] = useState<number | null>(null);

  if (isLoading) {
    return <Empty>Loading events…</Empty>;
  }
  if (!events || events.length === 0) {
    return <Empty>No historic events available.</Empty>;
  }

  const handleRun = (ev: HistoricalEventResponse) => {
    setSel(ev.id);
    requestBackfill(
      {
        symbols: ev.suggestedAssets,
        dataTypes: [EventType.PRICE, EventType.NEWS],
        from: ev.startDate,
        to: ev.endDate,
        forceBackfill: false,
      },
      {
        // On submit (202 / 200) jump the calendar to the event's symbols + range.
        onSuccess: () => onBackfilled(ev),
      }
    );
  };

  return (
    <Rows>
      {events.map(ev => {
        const cat = eventCatClass(ev.category);
        return (
          <Row key={ev.id} $sel={sel === ev.id} onClick={() => setSel(ev.id)}>
            <Cat $cat={cat}>{eventCatLabel(ev.category)}</Cat>
            <Name title={ev.name}>{ev.name}</Name>
            <Range>{rangeLabel(ev)}</Range>
            <Tickers>
              {ev.suggestedAssets.map(t => (
                <Tk key={t}>{t}</Tk>
              ))}
            </Tickers>
            <Act>
              <RunBtn
                $primary={sel === ev.id}
                disabled={isPending}
                onClick={e => {
                  e.stopPropagation();
                  handleRun(ev);
                }}
              >
                Backfill &amp; verify →
              </RunBtn>
            </Act>
          </Row>
        );
      })}
    </Rows>
  );
}

export { rangeLabel as eventRangeLabel };
