'use client';

import Link from 'next/link';
import styled from 'styled-components';
import { DashboardLayout } from '../../components/layouts/DashboardLayout';
import { Panel, Switch } from '@/app/components/shared/v2';
import {
  useNotificationPreferences,
  useUpdateNotificationPreference,
} from '@/app/lib/api/hooks';
import {
  type NotificationChannel,
  type NotificationPreference,
  type NotificationType,
  NOTIFICATION_CHANNEL_LABELS,
  NOTIFICATION_TYPE_LABELS,
} from '@/app/lib/types/notification';

const CHANNELS: NotificationChannel[] = ['IN_APP', 'EMAIL'];

const TYPE_DESCRIPTIONS: Record<NotificationType, string> = {
  INVESTMENT_EXECUTED: 'Successful contribution to a live or paper simulation.',
  INVESTMENT_SKIPPED: 'Tick ran but contribution was skipped — usually insufficient funds or no eligible strategy.',
  INVESTMENT_FAILED: 'Broker rejected the contribution outright.',
  PLAN_PAUSED: 'A recurring plan was paused — by you or the system after repeated failures.',
  MARKET_RESEARCH_STRATEGY_CREATED: 'Market research produced a new strategy you can review and run.',
  GENERIC: 'Other system messages that don\'t fit the categories above.',
};

export default function NotificationPreferencesPage() {
  const { data, isLoading } = useNotificationPreferences();
  const updatePref = useUpdateNotificationPreference();

  const toggle = (pref: NotificationPreference, channel: NotificationChannel) => {
    const has = pref.channels.includes(channel);
    const next = has
      ? pref.channels.filter((c) => c !== channel)
      : [...pref.channels, channel];
    updatePref.mutate({ type: pref.type, channels: next });
  };

  const rows = data ?? [];

  return (
    <DashboardLayout>
      <Page>
        <BackLink href="/notifications">
          <span aria-hidden="true">←</span> Back to inbox
        </BackLink>
        <PageHeader>
          <HeaderLeft>
            <H1>Notification preferences</H1>
            <Desc>
              Pick which events ping you in-app and which also reach your inbox. Unchecking every
              channel opts you out of that type entirely.
            </Desc>
          </HeaderLeft>
        </PageHeader>

        <Panel title="Channels per event type" count={rows.length} noBodyPadding>
          {isLoading ? (
            <SkeletonBlock>
              {Array.from({ length: 5 }).map((_, i) => (
                <SkeletonRow key={i}>
                  <SkelBar $w="50%" $h="13px" />
                  <SkelBar $w="40px" $h="16px" />
                  <SkelBar $w="40px" $h="16px" />
                </SkeletonRow>
              ))}
            </SkeletonBlock>
          ) : rows.length === 0 ? (
            <EmptyState>
              <EmptyIcon>·</EmptyIcon>
              <EmptyTitle>No preferences yet.</EmptyTitle>
              <EmptyBody>Sign in to manage your notification routing.</EmptyBody>
            </EmptyState>
          ) : (
            <Matrix>
              <colgroup>
                <col style={{ width: 'auto' }} />
                <col style={{ width: 110 }} />
                <col style={{ width: 110 }} />
              </colgroup>
              <thead>
                <tr>
                  <Th>Event type</Th>
                  <Th $center>{NOTIFICATION_CHANNEL_LABELS.IN_APP}</Th>
                  <Th $center>{NOTIFICATION_CHANNEL_LABELS.EMAIL}</Th>
                </tr>
              </thead>
              <tbody>
                {rows.map((pref) => (
                  <Tr key={pref.type}>
                    <Td>
                      <EvLabel>
                        <EvName>
                          {NOTIFICATION_TYPE_LABELS[pref.type as NotificationType] ?? pref.type}
                        </EvName>
                        <EvDesc>{TYPE_DESCRIPTIONS[pref.type as NotificationType] ?? ''}</EvDesc>
                      </EvLabel>
                    </Td>
                    {CHANNELS.map((c) => (
                      <ChCell key={c}>
                        <Switch
                          checked={pref.channels.includes(c)}
                          onChange={() => toggle(pref, c)}
                          label={`${pref.type} via ${NOTIFICATION_CHANNEL_LABELS[c]}`}
                          disabled={updatePref.isPending}
                        />
                      </ChCell>
                    ))}
                  </Tr>
                ))}
              </tbody>
            </Matrix>
          )}
        </Panel>
      </Page>
    </DashboardLayout>
  );
}

// ─── Styled ──────────────────────────────────────────────────────────────

const Page = styled.div`
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 4px 0 24px;
  min-width: 0;
  max-width: 900px;
`;

const BackLink = styled(Link)`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
  text-decoration: none;
  padding: 2px 4px;
  margin: 0 0 4px -4px;
  border-radius: 2px;
  width: max-content;
  transition: color 120ms ease;

  &:hover { color: ${p => p.theme.colors.foreground}; }
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
  gap: 8px;
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

const Desc = styled.p`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 14px;
  line-height: 1.55;
  color: ${p => p.theme.colors.fgMuted};
  margin: 0;
  max-width: 78ch;
`;

const Matrix = styled.table`
  width: 100%;
  border-collapse: collapse;
`;

const Th = styled.th<{ $center?: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
  padding: 10px 18px;
  text-align: ${p => p.$center ? 'center' : 'left'};
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
`;

const Tr = styled.tr`
  &:hover td { background: rgba(255,255,255,0.02); }
`;

const Td = styled.td`
  padding: 12px 18px;
  font-size: 14px;
  color: ${p => p.theme.colors.foreground};
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  vertical-align: middle;

  tr:last-child & { border-bottom: none; }
`;

const ChCell = styled.td`
  padding: 12px 18px;
  text-align: center;
  width: 110px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  vertical-align: middle;

  tr:last-child & { border-bottom: none; }
`;

const EvLabel = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
`;

const EvName = styled.span`
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  color: ${p => p.theme.colors.fgStrong};
  font-size: 14px;
`;

const EvDesc = styled.span`
  font-size: 12.5px;
  color: ${p => p.theme.colors.fgMuted};
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
`;

const SkeletonRow = styled.div`
  display: grid;
  grid-template-columns: 1fr 110px 110px;
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
  justify-self: center;

  &:first-child { justify-self: stretch; }

  @keyframes skel-shimmer {
    0% { background-position: 200% 0; }
    100% { background-position: -200% 0; }
  }
`;
