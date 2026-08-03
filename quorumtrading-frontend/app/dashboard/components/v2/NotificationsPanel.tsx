'use client';

import Link from 'next/link';
import styled, { css } from 'styled-components';
import { Panel } from '@/app/components/shared/v2';
import { useNotifications, useMarkAllNotificationsRead } from '@/app/lib/api/hooks';
import { NOTIFICATION_TYPE_LABELS, type Notification, type NotificationType } from '@/app/lib/types/notification';

interface NotificationsPanelProps {
  /** Max rows to show. Default 5. */
  limit?: number;
}

type Kind = 'warn' | 'ok' | 'info' | 'neg';

const List = styled.div`
  display: flex; flex-direction: column;
`;

const Row = styled.div<{ $kind: Kind; $unread: boolean }>`
  display: grid;
  grid-template-columns: 22px 1fr 64px;
  gap: 10px;
  align-items: flex-start;
  padding: 9px 12px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  font-size: 12.5px;

  &:last-child { border-bottom: none; }

  ${p => p.$unread && css`
    background: rgba(0, 255, 65, 0.025);
    position: relative;
    &::before {
      content: ''; position: absolute; left: 0; top: 0; bottom: 0;
      width: 2px; background: ${p.theme.colors.brand};
    }
  `}

  .glyph {
    width: 18px; height: 18px;
    display: inline-flex; align-items: center; justify-content: center;
    border-radius: 50%;
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11px;
    margin-top: 1px;
    ${p => {
      switch (p.$kind) {
        case 'warn': return css`background: rgba(255,176,0,0.12); color: ${p.theme.colors.terminalAmber};`;
        case 'ok':   return css`background: rgba(6,255,165,0.10); color: ${p.theme.colors.positive};`;
        case 'info': return css`background: rgba(0,217,255,0.10); color: ${p.theme.colors.info};`;
        case 'neg':  return css`background: rgba(255,51,102,0.12); color: ${p.theme.colors.negative};`;
      }
    }}
  }

  .msg { color: ${p => p.theme.colors.foreground}; line-height: 1.42; }
  .msg .title { color: ${p => p.theme.colors.fgStrong}; font-weight: ${p => p.theme.typography.fontWeight.medium}; }
  /*
   * Clamped because a notification body is not length-bounded at the source: a skipped
   * investment quotes the picker's reasoning, which lists rejected strategies. One of those
   * rendered a single row 1,244px tall in a list whose rows are 17px, pushing every other
   * notification off the panel. The row is a summary — the detail lives on the linked page.
   */
  .msg .body {
    color: ${p => p.theme.colors.fgMuted};
    font-size: 12px;
    display: -webkit-box;
    -webkit-line-clamp: 3;
    -webkit-box-orient: vertical;
    overflow: hidden;
    margin-top: 2px;
  }
  .ts { font-family: ${p => p.theme.typography.fontFamily.mono}; color: ${p => p.theme.colors.fgDimmed}; font-size: 11px; text-align: right; }
`;

const Empty = styled.div`
  padding: 28px 16px; text-align: center;
  color: ${p => p.theme.colors.fgMuted}; font-size: 13px;
`;

const MarkAll = styled.button`
  background: none; border: none;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 11px;
  color: ${p => p.theme.colors.fgMuted};
  cursor: pointer;
  padding: 3px 8px;

  &:hover { color: ${p => p.theme.colors.foreground}; }
  &:disabled { opacity: 0.4; cursor: not-allowed; }
`;

function classify(type: NotificationType): { kind: Kind; glyph: string } {
  switch (type) {
    case 'INVESTMENT_EXECUTED': return { kind: 'ok', glyph: '✓' };
    case 'INVESTMENT_SKIPPED':  return { kind: 'warn', glyph: '!' };
    case 'INVESTMENT_FAILED':   return { kind: 'neg', glyph: '×' };
    case 'PLAN_PAUSED':         return { kind: 'warn', glyph: '‖' };
    default:                    return { kind: 'info', glyph: 'i' };
  }
}

function shortTs(iso: string): string {
  try {
    const d = new Date(iso);
    const now = new Date();
    const sameDay = d.toDateString() === now.toDateString();
    if (sameDay) return d.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false });
    const diffDays = Math.floor((now.getTime() - d.getTime()) / (24 * 3600_000));
    if (diffDays < 7) return `${diffDays}d`;
    return d.toLocaleDateString('en-US', { day: '2-digit', month: 'short' });
  } catch { return iso; }
}

export function NotificationsPanel({ limit = 5 }: NotificationsPanelProps) {
  const { data: notifications, isLoading } = useNotifications({ limit });
  const markAll = useMarkAllNotificationsRead();

  const inAppOnly = (notifications ?? []).filter(n => n.channel === 'IN_APP').slice(0, limit);
  const unreadCount = inAppOnly.filter(n => n.status !== 'READ').length;

  return (
    <Panel
      title="Notifications"
      count={unreadCount > 0 ? `${unreadCount} unread` : 'all read'}
      noBodyPadding
      headerActions={
        <MarkAll
          type="button"
          onClick={() => markAll.mutate()}
          disabled={unreadCount === 0 || markAll.isPending}
        >
          Mark all read
        </MarkAll>
      }
    >
      {isLoading ? (
        <Empty>Loading…</Empty>
      ) : inAppOnly.length === 0 ? (
        <Empty>No notifications yet.</Empty>
      ) : (
        <List>
          {inAppOnly.map((n: Notification) => {
            const meta = classify(n.type);
            const unread = n.status !== 'READ';
            return (
              <Row key={n.id} $kind={meta.kind} $unread={unread}>
                <span className="glyph">{meta.glyph}</span>
                <div className="msg">
                  <span className="title">{n.title || NOTIFICATION_TYPE_LABELS[n.type]}</span>
                  {n.body && <span className="body">{n.body}</span>}
                </div>
                <span className="ts">{shortTs(n.createdAt)}</span>
              </Row>
            );
          })}
          <Row $kind="info" $unread={false} style={{ borderTop: '1px solid var(--qt-border-subtle, #1a2030)' }}>
            <span />
            <Link href="/notifications" style={{ fontSize: 11, color: 'var(--qt-fg-muted, #8a98a4)', textDecoration: 'none' }}>
              View all notifications →
            </Link>
            <span />
          </Row>
        </List>
      )}
    </Panel>
  );
}
