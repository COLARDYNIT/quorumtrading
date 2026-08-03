'use client';

import { useState, useEffect, useMemo } from 'react';
import styled from 'styled-components';

// Types
interface MarketStatus {
  isOpen: boolean;
  currentNYTime: Date;
  nextStateChange: Date;
  nextStateLabel: 'opens' | 'closes';
  reason: string;
}

interface MarketStatusIndicatorProps {
  /** Show detailed info (default: true) */
  detailed?: boolean;
  /** Timestamp to check if it falls within market hours */
  checkTimestamp?: Date | null;
  /** Callback when market status changes */
  onStatusChange?: (status: MarketStatus) => void;
}

// Styled Components
const Container = styled.div`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.md};
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
`;

const StatusSection = styled.div`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.sm};
`;

const StatusDot = styled.div<{ $isOpen: boolean }>`
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: ${p => p.$isOpen ? p.theme.colors.success : p.theme.colors.error};
  box-shadow: 0 0 8px ${p => p.$isOpen ? p.theme.colors.success : p.theme.colors.error}88;
  animation: ${p => p.$isOpen ? 'pulse 2s infinite' : 'none'};

  @keyframes pulse {
    0%, 100% { opacity: 1; }
    50% { opacity: 0.6; }
  }
`;

const StatusText = styled.span<{ $isOpen: boolean }>`
  color: ${p => p.$isOpen ? p.theme.colors.success : p.theme.colors.error};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  text-transform: uppercase;
  letter-spacing: 0.05em;
`;

const Divider = styled.div`
  width: 1px;
  height: 20px;
  background: ${p => p.theme.colors.border};
`;

const TimeSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
`;

const TimeLabel = styled.span`
  color: ${p => p.theme.colors.textMuted};
  font-size: ${p => p.theme.typography.fontSize.xs};
  text-transform: uppercase;
`;

const TimeValue = styled.span`
  color: ${p => p.theme.colors.primary};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
`;

const CountdownSection = styled.div`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.xs};
`;

const CountdownLabel = styled.span`
  color: ${p => p.theme.colors.textMuted};
`;

const CountdownValue = styled.span<{ $isOpen: boolean }>`
  color: ${p => p.$isOpen ? p.theme.colors.warning : p.theme.colors.success};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
`;

const CryptoNote = styled.span`
  color: ${p => p.theme.colors.info};
  font-size: ${p => p.theme.typography.fontSize.xs};
  opacity: 0.8;
`;

const TimestampWarning = styled.span`
  color: ${p => p.theme.colors.warning};
  font-size: ${p => p.theme.typography.fontSize.xs};
`;

// Compact version for inline use
const CompactContainer = styled.div`
  display: inline-flex;
  align-items: center;
  gap: ${p => p.theme.spacing.xs};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
`;

const CompactDot = styled.div<{ $isOpen: boolean }>`
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: ${p => p.$isOpen ? p.theme.colors.success : p.theme.colors.error};
`;

const CompactText = styled.span<{ $isOpen: boolean }>`
  color: ${p => p.$isOpen ? p.theme.colors.success : p.theme.colors.error};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
`;

// Utility functions
const NY_TIMEZONE = 'America/New_York';

/**
 * Get NY time components from a Date object.
 * Returns the hour, minute, day of week as they would appear in NY timezone.
 */
function getNYTimeComponents(date: Date = new Date()): { hour: number; minute: number; dayOfWeek: number; dateStr: string } {
  const formatter = new Intl.DateTimeFormat('en-US', {
    timeZone: NY_TIMEZONE,
    hour: 'numeric',
    minute: 'numeric',
    weekday: 'short',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour12: false,
  });

  const parts = formatter.formatToParts(date);
  const hour = parseInt(parts.find(p => p.type === 'hour')?.value || '0', 10);
  const minute = parseInt(parts.find(p => p.type === 'minute')?.value || '0', 10);
  const weekday = parts.find(p => p.type === 'weekday')?.value || 'Mon';
  const year = parts.find(p => p.type === 'year')?.value || '2025';
  const month = parts.find(p => p.type === 'month')?.value || '01';
  const day = parts.find(p => p.type === 'day')?.value || '01';

  const dayMap: Record<string, number> = { Sun: 0, Mon: 1, Tue: 2, Wed: 3, Thu: 4, Fri: 5, Sat: 6 };
  const dayOfWeek = dayMap[weekday] ?? 1;

  return { hour, minute, dayOfWeek, dateStr: `${year}-${month}-${day}` };
}

/**
 * Calculate milliseconds until a specific time in NY timezone.
 * @param targetHour - Target hour in NY time (0-23)
 * @param targetMinute - Target minute in NY time (0-59)
 * @param daysOffset - Number of days to add (0 = today, 1 = tomorrow, etc.)
 */
function getMsUntilNYTime(targetHour: number, targetMinute: number, daysOffset: number = 0): number {
  const now = new Date();
  const { dateStr } = getNYTimeComponents(now);

  // Create target date string in NY timezone
  const [year, month, day] = dateStr.split('-').map(Number);
  const targetDate = new Date(year, month - 1, day + daysOffset);

  // Format target as ISO string in NY timezone, then parse back
  const targetStr = `${targetDate.getFullYear()}-${String(targetDate.getMonth() + 1).padStart(2, '0')}-${String(targetDate.getDate()).padStart(2, '0')}T${String(targetHour).padStart(2, '0')}:${String(targetMinute).padStart(2, '0')}:00`;

  // Use toLocaleString trick to get the UTC offset for NY at this time
  const testDate = new Date(targetStr);
  const nyString = testDate.toLocaleString('en-US', { timeZone: NY_TIMEZONE });
  const localString = testDate.toLocaleString('en-US');
  const nyDate = new Date(nyString);
  const localDate = new Date(localString);
  const offsetMs = localDate.getTime() - nyDate.getTime();

  // Calculate actual target time in UTC
  const targetUtc = new Date(targetStr).getTime() + offsetMs;

  return targetUtc - now.getTime();
}

function formatNYTime(date: Date): string {
  return date.toLocaleTimeString('en-US', {
    timeZone: NY_TIMEZONE,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }) + ' ET';
}

function formatCountdown(ms: number): string {
  if (ms <= 0) return '0m';

  const hours = Math.floor(ms / (1000 * 60 * 60));
  const minutes = Math.floor((ms % (1000 * 60 * 60)) / (1000 * 60));
  const seconds = Math.floor((ms % (1000 * 60)) / 1000);

  if (hours > 0) {
    return `${hours}h ${minutes}m`;
  } else if (minutes > 0) {
    return `${minutes}m ${seconds}s`;
  } else {
    return `${seconds}s`;
  }
}

function getMarketStatus(now: Date = new Date()): MarketStatus {
  // Get current time components in NY timezone
  const { hour, minute, dayOfWeek } = getNYTimeComponents(now);

  // Weekend check
  const isWeekend = dayOfWeek === 0 || dayOfWeek === 6;

  // Market hours: 9:30 AM - 4:00 PM ET
  const currentMinutes = hour * 60 + minute;
  const marketOpenMinutes = 9 * 60 + 30; // 9:30 AM
  const marketCloseMinutes = 16 * 60; // 4:00 PM

  const isWithinHours = currentMinutes >= marketOpenMinutes && currentMinutes < marketCloseMinutes;
  const isOpen = !isWeekend && isWithinHours;

  // Calculate milliseconds until next state change
  let msUntilChange: number;
  let nextStateLabel: 'opens' | 'closes';
  let reason: string;

  if (isWeekend) {
    // Weekend - find next Monday 9:30 AM
    const daysUntilMonday = dayOfWeek === 0 ? 1 : (8 - dayOfWeek);
    msUntilChange = getMsUntilNYTime(9, 30, daysUntilMonday);
    nextStateLabel = 'opens';
    reason = dayOfWeek === 0 ? 'Sunday' : 'Saturday';
  } else if (currentMinutes < marketOpenMinutes) {
    // Before market open - opens at 9:30 today
    msUntilChange = getMsUntilNYTime(9, 30, 0);
    nextStateLabel = 'opens';
    reason = 'Pre-market';
  } else if (currentMinutes >= marketCloseMinutes) {
    // After market close - next open is tomorrow (or Monday if Friday)
    const daysUntilNextOpen = dayOfWeek === 5 ? 3 : 1;
    msUntilChange = getMsUntilNYTime(9, 30, daysUntilNextOpen);
    nextStateLabel = 'opens';
    reason = 'After hours';
  } else {
    // Market is open - calculate time until close at 4:00 PM
    msUntilChange = getMsUntilNYTime(16, 0, 0);
    nextStateLabel = 'closes';
    reason = 'Regular hours';
  }

  // Create nextStateChange date from milliseconds
  const nextStateChange = new Date(now.getTime() + msUntilChange);

  return {
    isOpen,
    currentNYTime: now, // We'll format this using formatNYTime which handles timezone
    nextStateChange,
    nextStateLabel,
    reason,
  };
}

/**
 * Check if a given timestamp falls within NYSE regular market hours.
 * Useful for checking if a scheduled consensus will occur during trading hours.
 */
export function isWithinMarketHours(timestamp: Date): boolean {
  const status = getMarketStatus(timestamp);
  return status.isOpen;
}

/**
 * Get a human-readable description of market status at a given time.
 */
export function getMarketStatusDescription(timestamp: Date): string {
  const status = getMarketStatus(timestamp);
  if (status.isOpen) {
    return 'During market hours - stock trades will execute';
  }
  return `Outside market hours (${status.reason}) - only crypto trades will execute`;
}

/**
 * MarketStatusIndicator Component
 *
 * Displays the current NYSE market status with:
 * - Open/Closed indicator with visual dot
 * - Current NY time
 * - Countdown to next open/close
 * - Note about crypto trading 24/7
 */
export function MarketStatusIndicator({
  detailed = true,
  checkTimestamp,
  onStatusChange
}: MarketStatusIndicatorProps) {
  const [now, setNow] = useState(new Date());

  // Update every second
  useEffect(() => {
    const interval = setInterval(() => {
      setNow(new Date());
    }, 1000);
    return () => clearInterval(interval);
  }, []);

  const status = useMemo(() => getMarketStatus(now), [now]);

  // Calculate countdown
  const countdown = useMemo(() => {
    const diff = status.nextStateChange.getTime() - now.getTime();
    return Math.max(0, diff);
  }, [status.nextStateChange, now]);

  // Check if a specific timestamp is within market hours
  const timestampStatus = useMemo(() => {
    if (!checkTimestamp) return null;
    return getMarketStatus(checkTimestamp);
  }, [checkTimestamp]);

  // Notify parent of status changes
  useEffect(() => {
    onStatusChange?.(status);
  }, [status.isOpen, onStatusChange]);

  if (!detailed) {
    // Compact version
    return (
      <CompactContainer>
        <CompactDot $isOpen={status.isOpen} />
        <CompactText $isOpen={status.isOpen}>
          NYSE {status.isOpen ? 'OPEN' : 'CLOSED'}
        </CompactText>
      </CompactContainer>
    );
  }

  return (
    <Container>
      <StatusSection>
        <StatusDot $isOpen={status.isOpen} />
        <StatusText $isOpen={status.isOpen}>
          NYSE {status.isOpen ? 'OPEN' : 'CLOSED'}
        </StatusText>
      </StatusSection>

      <Divider />

      <TimeSection>
        <TimeLabel>New York</TimeLabel>
        <TimeValue>{formatNYTime(now)}</TimeValue>
      </TimeSection>

      <Divider />

      <CountdownSection>
        <CountdownLabel>
          {status.nextStateLabel === 'opens' ? 'Opens in' : 'Closes in'}
        </CountdownLabel>
        <CountdownValue $isOpen={status.isOpen}>
          {formatCountdown(countdown)}
        </CountdownValue>
      </CountdownSection>

      <Divider />

      <CryptoNote>
        Crypto trades 24/7
      </CryptoNote>

      {timestampStatus && !timestampStatus.isOpen && (
        <>
          <Divider />
          <TimestampWarning>
            Next consensus: crypto only
          </TimestampWarning>
        </>
      )}
    </Container>
  );
}

/**
 * ConsensusMarketWarning Component
 *
 * Shows a warning when attempting consensus outside market hours.
 * Use this near the "REACH CONSENSUS" button.
 */
const WarningContainer = styled.div`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.xs};
  padding: ${p => p.theme.spacing.xs} ${p => p.theme.spacing.sm};
  background: ${p => p.theme.colors.warning}15;
  border: 1px solid ${p => p.theme.colors.warning}44;
  border-radius: 4px;
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.warning};
`;

const WarningIcon = styled.span`
  font-size: 14px;
`;

export function ConsensusMarketWarning() {
  const [status, setStatus] = useState<MarketStatus | null>(null);

  useEffect(() => {
    const updateStatus = () => setStatus(getMarketStatus());
    updateStatus();
    const interval = setInterval(updateStatus, 1000);
    return () => clearInterval(interval);
  }, []);

  if (!status || status.isOpen) {
    return null; // No warning needed when market is open
  }

  return (
    <WarningContainer>
      <WarningIcon>⚠</WarningIcon>
      <span>
        Market closed ({status.reason}) - stock trades will be skipped, crypto only
      </span>
    </WarningContainer>
  );
}

/**
 * NextConsensusMarketBadge Component
 *
 * Shows whether a scheduled consensus time falls within market hours.
 */
const BadgeContainer = styled.div<{ $inMarketHours: boolean }>`
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  background: ${p => p.$inMarketHours ? p.theme.colors.success : p.theme.colors.warning}22;
  border: 1px solid ${p => p.$inMarketHours ? p.theme.colors.success : p.theme.colors.warning}66;
  color: ${p => p.$inMarketHours ? p.theme.colors.success : p.theme.colors.warning};
`;

interface NextConsensusMarketBadgeProps {
  nextConsensusTime: Date;
}

export function NextConsensusMarketBadge({ nextConsensusTime }: NextConsensusMarketBadgeProps) {
  const inMarketHours = useMemo(() => {
    return isWithinMarketHours(nextConsensusTime);
  }, [nextConsensusTime]);

  return (
    <BadgeContainer $inMarketHours={inMarketHours}>
      {inMarketHours ? (
        <>
          <span>✓</span>
          <span>During market hours</span>
        </>
      ) : (
        <>
          <span>○</span>
          <span>After hours - crypto only</span>
        </>
      )}
    </BadgeContainer>
  );
}

export default MarketStatusIndicator;
