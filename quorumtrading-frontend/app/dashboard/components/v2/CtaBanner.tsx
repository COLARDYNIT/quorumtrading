'use client';

import Link from 'next/link';
import styled from 'styled-components';
import { useNotifications } from '@/app/lib/api/hooks';
import { NOTIFICATION_TYPE_LABELS, type Notification } from '@/app/lib/types/notification';
import { notificationLink } from '@/app/lib/utils/notificationLink';

const Banner = styled.div`
  display: flex; align-items: center; justify-content: space-between;
  gap: 16px;
  padding: 12px 16px;
  border: 1px solid rgba(255, 176, 0, 0.4);
  background: rgba(255, 176, 0, 0.04);
  border-radius: 4px;
  font-size: 13px;
  font-family: ${p => p.theme.typography.fontFamily.sans};

  .msg { color: ${p => p.theme.colors.foreground}; line-height: 1.4; }
  .msg b { color: ${p => p.theme.colors.terminalAmber}; }
  .msg .meta { color: ${p => p.theme.colors.fgDimmed}; display: block; font-family: ${p => p.theme.typography.fontFamily.mono}; font-size: 11.5px; margin-top: 2px; }

  .actions { display: flex; gap: 8px; flex-shrink: 0; }
`;

const BtnGhost = styled(Link)`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 12px;
  padding: 6px 12px;
  border: 1px solid ${p => p.theme.colors.border};
  background: transparent;
  color: ${p => p.theme.colors.foreground};
  text-decoration: none;
  border-radius: 2px;
  transition: border-color 120ms ease;

  &:hover { border-color: ${p => p.theme.colors.fgMuted}; }
`;

const BtnPrimary = styled(Link)`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 12px;
  padding: 6px 12px;
  border: 1px solid ${p => p.theme.colors.brand};
  background: ${p => p.theme.colors.brandTint};
  color: ${p => p.theme.colors.brand};
  text-decoration: none;
  border-radius: 2px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};

  &:hover { background: rgba(0, 255, 65, 0.14); }
`;

function isAlerting(n: Notification): boolean {
  return n.status !== 'READ' && (
    n.type === 'INVESTMENT_SKIPPED' ||
    n.type === 'INVESTMENT_FAILED' ||
    n.type === 'PLAN_PAUSED'
  );
}

function relatedLink(n: Notification): string {
  return notificationLink(n) ?? '/notifications';
}

/**
 * Renders only when there is a real attention-grabbing signal (skipped/failed
 * investment, plan paused). Returns null otherwise.
 */
export function CtaBanner() {
  const { data: notifications } = useNotifications({ status: 'unread', limit: 20 });
  const top = (notifications ?? []).find(isAlerting);
  if (!top) return null;

  return (
    <Banner role="alert">
      <div className="msg">
        <span><b>{top.title || NOTIFICATION_TYPE_LABELS[top.type]}</b>{top.body ? ` — ${top.body}` : ''}</span>
        <span className="meta">{new Date(top.createdAt).toLocaleString('en-US', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', hour12: false })}</span>
      </div>
      <div className="actions">
        <BtnGhost href="/notifications">All alerts</BtnGhost>
        <BtnPrimary href={relatedLink(top)}>Review →</BtnPrimary>
      </div>
    </Banner>
  );
}
