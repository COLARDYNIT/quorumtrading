'use client';

import styled from 'styled-components';
import { actionColor } from '@/app/lib/utils/themeColors';

interface TimelineEvent {
  timestamp: string;
  action: string; // BUY, SELL, HOLD, or custom text
  detail: string;
  ticker?: string;
}

interface TimelineCardProps {
  label?: unknown;
  /** JSON array of events: [{"timestamp":"2026-01-03","action":"BUY","detail":"Bought 8 EQIX @ $765.39","ticker":"EQIX"}] */
  events?: unknown;
  [key: string]: unknown;
}

const ACTION_ICONS: Record<string, string> = {
  BUY: '▲',
  SELL: '▼',
  HOLD: '—',
  SKIP: '⏭',
};

export function TimelineCard({ label, events }: TimelineCardProps) {
  let data: TimelineEvent[] = [];

  if (typeof events === 'string') {
    try { data = JSON.parse(events); } catch { return null; }
  } else if (Array.isArray(events)) {
    data = events as TimelineEvent[];
  } else {
    return null;
  }

  if (data.length === 0) return null;

  return (
    <Container>
      {label ? <CardLabel>{String(label)}</CardLabel> : null}
      <EventList>
        {data.map((event, i) => {
          const actionUpper = event.action.toUpperCase();
          const color = actionColor(actionUpper);
          const icon = ACTION_ICONS[actionUpper] || '●';

          return (
            <EventRow key={i}>
              <TimelineGutter>
                <Dot $color={color}>{icon}</Dot>
                {i < data.length - 1 && <Line />}
              </TimelineGutter>
              <EventContent>
                <EventHeader>
                  <ActionTag $color={color}>{event.action}</ActionTag>
                  {event.ticker && <Ticker>{event.ticker}</Ticker>}
                  <Timestamp>{event.timestamp}</Timestamp>
                </EventHeader>
                <EventDetail>{event.detail}</EventDetail>
              </EventContent>
            </EventRow>
          );
        })}
      </EventList>
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;
`;

const CardLabel = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textDimmed};
  text-transform: uppercase;
  letter-spacing: 0.5px;
`;

const EventList = styled.div`
  display: flex;
  flex-direction: column;
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
  padding: ${p => p.theme.spacing.sm};
`;

const EventRow = styled.div`
  display: flex;
  gap: 10px;
`;

const TimelineGutter = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 16px;
  flex-shrink: 0;
`;

const Dot = styled.div<{ $color: string }>`
  width: 16px;
  height: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 9px;
  color: ${p => p.$color};
  flex-shrink: 0;
`;

const Line = styled.div`
  width: 1px;
  flex: 1;
  min-height: 12px;
  background: ${p => p.theme.colors.border};
`;

const EventContent = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-bottom: 10px;
  min-width: 0;
`;

const EventHeader = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
`;

const ActionTag = styled.span<{ $color: string }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  color: ${p => p.$color};
  text-transform: uppercase;
  letter-spacing: 0.3px;
`;

const Ticker = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.theme.colors.terminalBlue};
`;

const Timestamp = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textDimmed};
  margin-left: auto;
`;

const EventDetail = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textLight};
  line-height: 1.4;
`;
