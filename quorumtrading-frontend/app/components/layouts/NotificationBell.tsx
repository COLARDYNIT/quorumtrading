'use client';

import styled from 'styled-components';
import Link from 'next/link';
import { useUnreadNotificationCount } from '@/app/lib/api/hooks';

const BellLink = styled(Link)`
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid ${(p) => p.theme.colors.border};
  border-radius: 50%;
  color: ${(p) => p.theme.colors.textMuted};
  text-decoration: none;
  transition: all 0.15s ease;
  flex-shrink: 0;

  svg {
    width: 16px;
    height: 16px;
  }

  &:hover {
    color: ${(p) => p.theme.colors.terminalGreen};
    border-color: ${(p) => p.theme.colors.terminalGreen};
  }
`;

const Badge = styled.span`
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  background: ${(p) => p.theme.colors.terminalPink};
  color: #fff;
  border-radius: 8px;
  font-size: 10px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
`;

interface NotificationBellProps {
  /** Disable polling for unauthenticated layouts. */
  enabled?: boolean;
}

export function NotificationBell({ enabled = true }: NotificationBellProps) {
  const { data } = useUnreadNotificationCount({ enabled });
  const count = data?.unread ?? 0;
  const display = count > 99 ? '99+' : count > 0 ? String(count) : null;

  return (
    <BellLink
      href="/notifications"
      aria-label={count > 0 ? `${count} unread notifications` : 'Notifications'}
      title={count > 0 ? `${count} unread notifications` : 'Notifications'}
    >
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
        <path d="M13.73 21a2 2 0 0 1-3.46 0" />
      </svg>
      {display !== null && <Badge>{display}</Badge>}
    </BellLink>
  );
}
