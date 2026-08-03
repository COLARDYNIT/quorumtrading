'use client';

import { useMemo, useState } from 'react';
import Link from 'next/link';
import styled, { css } from 'styled-components';
import { DashboardLayout } from '../components/layouts/DashboardLayout';
import { Panel } from '@/app/components/shared/v2';
import {
  useNotifications,
  useMarkNotificationRead,
  useMarkAllNotificationsRead,
} from '@/app/lib/api/hooks';
import {
  type Notification,
  type NotificationType,
  NOTIFICATION_TYPE_LABELS,
} from '@/app/lib/types/notification';
import { notificationLink } from '@/app/lib/utils/notificationLink';
import { hueColor, tagBase, type LabelHue } from '@/app/styles/labels';

type TypeFilter = 'all' | 'plans' | 'budget' | 'other';

const PLAN_TYPES: NotificationType[] = [
  'INVESTMENT_EXECUTED',
  'INVESTMENT_SKIPPED',
  'INVESTMENT_FAILED',
  'PLAN_PAUSED',
];

type NotifKind = 'plan' | 'failure' | 'generic';

function inferKind(type: NotificationType): NotifKind {
  if (type === 'INVESTMENT_FAILED') return 'failure';
  if (PLAN_TYPES.includes(type)) return 'plan';
  return 'generic';
}

function formatRelative(iso: string): string {
  const now = Date.now();
  const then = new Date(iso).getTime();
  const sec = Math.max(0, Math.floor((now - then) / 1000));
  if (sec < 60) return 'just now';
  const min = Math.floor(sec / 60);
  if (min < 60) return `${min}m ago`;
  const hr = Math.floor(min / 60);
  if (hr < 24) return `${hr}h ago`;
  const days = Math.floor(hr / 24);
  if (days < 7) return `${days}d ago`;
  return new Date(iso).toLocaleDateString('en-US', { day: '2-digit', month: 'short', year: 'numeric' });
}

export default function NotificationsPage() {
  const [typeFilter, setTypeFilter] = useState<TypeFilter>('all');
  const { data, isLoading } = useNotifications({ limit: 100 });
  const markRead = useMarkNotificationRead();
  const markAllRead = useMarkAllNotificationsRead();

  const inApp = useMemo(
    () => (data ?? []).filter((n) => n.channel === 'IN_APP'),
    [data]
  );
  const unreadCount = inApp.filter((n) => n.status === 'DELIVERED').length;

  const counts = useMemo(() => ({
    all: inApp.length,
    plans: inApp.filter((n) => PLAN_TYPES.includes(n.type)).length,
    budget: 0, // backend has no BUDGET_ALERT type yet
    other: inApp.filter((n) => n.type === 'GENERIC').length,
  }), [inApp]);

  const rows = useMemo(() => {
    if (typeFilter === 'all') return inApp;
    if (typeFilter === 'plans') return inApp.filter((n) => PLAN_TYPES.includes(n.type));
    if (typeFilter === 'other') return inApp.filter((n) => n.type === 'GENERIC');
    return inApp;
  }, [inApp, typeFilter]);

  return (
    <DashboardLayout>
      <Page>
        <PageHeader>
          <HeaderLeft>
            <H1>Inbox</H1>
            <MetaRow>
              <strong>{inApp.length}</strong> total
              <Sep>·</Sep>
              {unreadCount > 0 ? (
                <UnreadMeta>{unreadCount} unread</UnreadMeta>
              ) : (
                <DimMeta>all read</DimMeta>
              )}
            </MetaRow>
          </HeaderLeft>
          <HeaderRight>
            <GhostBtn
              type="button"
              onClick={() => markAllRead.mutate()}
              disabled={unreadCount === 0 || markAllRead.isPending}
            >
              Mark all read
            </GhostBtn>
            <GhostLink href="/notifications/preferences">Preferences</GhostLink>
          </HeaderRight>
        </PageHeader>

        <Toolbar>
          <ToolbarLeft>
            <ToolbarLabel>Type</ToolbarLabel>
            <Seg>
              <SegBtn $on={typeFilter === 'all'} type="button" onClick={() => setTypeFilter('all')}>
                All <SegCount>{counts.all}</SegCount>
              </SegBtn>
              <SegBtn $on={typeFilter === 'plans'} type="button" onClick={() => setTypeFilter('plans')}>
                Plans <SegCount>{counts.plans}</SegCount>
              </SegBtn>
              <SegBtn $on={typeFilter === 'other'} type="button" onClick={() => setTypeFilter('other')}>
                Other <SegCount>{counts.other}</SegCount>
              </SegBtn>
            </Seg>
          </ToolbarLeft>
        </Toolbar>

        <Panel title="Inbox" count={rows.length} noBodyPadding>
          {isLoading ? (
            <SkeletonBlock>
              {Array.from({ length: 4 }).map((_, i) => (
                <SkeletonRow key={i}>
                  <SkelBar $w="80px" $h="11px" />
                  <SkelBar $w="60%" $h="13px" />
                  <SkelBar $w="90px" $h="20px" />
                </SkeletonRow>
              ))}
            </SkeletonBlock>
          ) : rows.length === 0 ? (
            <EmptyState>
              <EmptyIcon>✓</EmptyIcon>
              <EmptyTitle>You&apos;re caught up.</EmptyTitle>
              <EmptyBody>
                Nothing new in your inbox. Plan executions, simulation events and system messages will land here.
              </EmptyBody>
            </EmptyState>
          ) : (
            <Rows>
              {rows.map((n) => (
                <NotifRow
                  key={n.id}
                  notification={n}
                  onMarkRead={() => markRead.mutate(n.id)}
                />
              ))}
            </Rows>
          )}
        </Panel>
      </Page>
    </DashboardLayout>
  );
}

interface NotifRowProps {
  notification: Notification;
  onMarkRead: () => void;
}

function NotifRow({ notification: n, onMarkRead }: NotifRowProps) {
  const unread = n.status === 'DELIVERED';
  const kind = inferKind(n.type);
  const linkHref = relatedHref(n);

  return (
    <Row $unread={unread}>
      <Ts>{formatRelative(n.createdAt)}</Ts>
      <Body>
        <BodyTitle>{n.title || NOTIFICATION_TYPE_LABELS[n.type]}</BodyTitle>
        {n.body && <BodySub>{n.body}</BodySub>}
      </Body>
      <TypeChip $kind={kind}>{NOTIFICATION_TYPE_LABELS[n.type]}</TypeChip>
      <RowActs>
        {linkHref && (
          <RowActBtn as={Link} href={linkHref} $primary>
            Open
          </RowActBtn>
        )}
        {unread && (
          <RowActBtn type="button" onClick={onMarkRead}>
            Mark read
          </RowActBtn>
        )}
      </RowActs>
    </Row>
  );
}

function relatedHref(n: Notification): string | null {
  return notificationLink(n);
}

// ─── Styled ──────────────────────────────────────────────────────────────

const Page = styled.div`
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 4px 0 24px;
  min-width: 0;
`;

const PageHeader = styled.div`
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  flex-wrap: wrap;
  padding: 4px 0 0;
`;

const HeaderLeft = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
`;

const H1 = styled.h1`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 24px;
  letter-spacing: -0.012em;
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  margin: 0;
`;

const MetaRow = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 7px;
  flex-wrap: wrap;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13px;
  color: ${p => p.theme.colors.fgMuted};

  & > strong {
    color: ${p => p.theme.colors.fgStrong};
    font-weight: ${p => p.theme.typography.fontWeight.medium};
  }
`;

const Sep = styled.span`
  color: ${p => p.theme.colors.border};
`;

const UnreadMeta = styled.span`
  color: ${p => p.theme.colors.info};
`;

const DimMeta = styled.span`
  color: ${p => p.theme.colors.fgDimmed};
`;

const HeaderRight = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 8px;
`;

const ghostStyles = css`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  padding: 6px 12px;
  border-radius: 2px;
  border: 1px solid ${p => p.theme.colors.border};
  background: transparent;
  color: ${p => p.theme.colors.fgMuted};
  text-decoration: none;
  cursor: pointer;
  transition: color 120ms ease, border-color 120ms ease, background 120ms ease;

  &:hover { color: ${p => p.theme.colors.fgStrong}; border-color: ${p => p.theme.colors.fgMuted}; background: rgba(255,255,255,0.02); }
  &:disabled { opacity: 0.4; cursor: not-allowed; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
`;

const GhostBtn = styled.button`${ghostStyles}`;
const GhostLink = styled(Link)`${ghostStyles}`;

const Toolbar = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0;
`;

const ToolbarLeft = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
`;

const ToolbarLabel = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
`;

const Seg = styled.div`
  display: inline-flex;
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 2px;
  overflow: hidden;
`;

const SegBtn = styled.button<{ $on: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13px;
  background: ${p => p.$on ? 'rgba(255,255,255,0.05)' : 'transparent'};
  color: ${p => p.$on ? p.theme.colors.fgStrong : p.theme.colors.fgMuted};
  border: none;
  padding: 5px 12px;
  cursor: pointer;
  border-right: 1px solid ${p => p.theme.colors.border};
  transition: color 120ms ease, background 120ms ease;

  &:last-child { border-right: none; }
  &:hover { color: ${p => p.theme.colors.fgStrong}; }
`;

const SegCount = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  color: ${p => p.theme.colors.fgDimmed};
  margin-left: 4px;
`;

const Rows = styled.div`
  display: flex;
  flex-direction: column;
`;

const Row = styled.div<{ $unread: boolean }>`
  display: grid;
  grid-template-columns: 90px 1fr 160px auto;
  align-items: center;
  gap: 14px;
  padding: 11px 18px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  font-size: 14px;
  color: ${p => p.theme.colors.foreground};
  transition: background 120ms ease;

  &:last-child { border-bottom: none; }
  &:hover { background: rgba(255,255,255,0.02); }

  ${p => p.$unread && css`
    background: rgba(0, 217, 255, 0.025);
    border-left: 2px solid ${p.theme.colors.info};
    padding-left: 16px;
    &:hover { background: rgba(0, 217, 255, 0.05); }
  `}
`;

const Ts = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
  white-space: nowrap;
`;

const Body = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
`;

const BodyTitle = styled.span`
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  font-size: 14px;
`;

const BodySub = styled.span`
  color: ${p => p.theme.colors.fgMuted};
  font-size: 12.5px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const TYPE_CHIP_HUE: Record<NotifKind, LabelHue> = {
  plan: 'cyan',
  failure: 'pink',
  generic: 'dim',
};

const TypeChip = styled.span<{ $kind: NotifKind }>`
  ${tagBase}
  justify-self: start;
  color: ${p => hueColor[TYPE_CHIP_HUE[p.$kind]](p.theme.colors)};
`;

const RowActs = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  justify-self: end;
`;

const RowActBtn = styled.button<{ $primary?: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  padding: 4px 9px;
  border-radius: 2px;
  border: 1px solid ${p => p.$primary ? 'rgba(0,255,65,0.4)' : p.theme.colors.border};
  background: transparent;
  color: ${p => p.$primary ? p.theme.colors.brand : p.theme.colors.fgMuted};
  text-decoration: none;
  cursor: pointer;
  white-space: nowrap;
  line-height: 1;
  transition: color 120ms ease, border-color 120ms ease, background 120ms ease;

  &:hover {
    color: ${p => p.$primary ? p.theme.colors.brandHover : p.theme.colors.fgStrong};
    border-color: ${p => p.$primary ? p.theme.colors.brand : p.theme.colors.fgMuted};
    background: ${p => p.$primary ? 'rgba(0,255,65,0.08)' : 'rgba(255,255,255,0.02)'};
  }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
`;

const EmptyState = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 48px 32px;
  gap: 10px;
  color: ${p => p.theme.colors.fgMuted};
  font-size: 14px;
  line-height: 1.55;
  max-width: 56ch;
  margin: 0 auto;
`;

const EmptyIcon = styled.div`
  width: 40px;
  height: 40px;
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: ${p => p.theme.colors.fgDimmed};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 17px;
`;

const EmptyTitle = styled.h3`
  color: ${p => p.theme.colors.fgStrong};
  font-size: 15px;
  margin: 4px 0 0;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
`;

const EmptyBody = styled.p`
  margin: 0;
`;

const SkeletonBlock = styled.div`
  display: flex;
  flex-direction: column;
  gap: 1px;
`;

const SkeletonRow = styled.div`
  display: grid;
  grid-template-columns: 100px 1fr 100px;
  gap: 14px;
  align-items: center;
  padding: 12px 18px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};

  &:last-child { border-bottom: none; }
`;

const SkelBar = styled.div<{ $w: string; $h: string }>`
  width: ${p => p.$w};
  height: ${p => p.$h};
  border-radius: 3px;
  background: linear-gradient(
    90deg,
    ${p => p.theme.colors.surfaceElevated} 0%,
    ${p => p.theme.colors.borderSubtle} 50%,
    ${p => p.theme.colors.surfaceElevated} 100%
  );
  background-size: 200% 100%;
  animation: skel-shimmer 1.4s linear infinite;

  @keyframes skel-shimmer {
    0% { background-position: 200% 0; }
    100% { background-position: -200% 0; }
  }
`;
