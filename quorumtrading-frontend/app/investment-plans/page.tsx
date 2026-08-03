'use client';

import { useMemo } from 'react';
import Link from 'next/link';
import styled, { css } from 'styled-components';
import { DashboardLayout } from '../components/layouts/DashboardLayout';
import { Panel, StatusPill, KpiStrip, type KpiItem } from '@/app/components/shared/v2';
import { useInvestmentPlans, useBrokerAccounts } from '@/app/lib/api/hooks';
import type { InvestmentPlan, InvestmentPlanStatus } from '@/app/lib/types/investmentPlan';
import { BUDGET_MODE_LABELS } from '@/app/lib/types/investmentPlan';
import type { BrokerAccount } from '@/app/lib/types/brokerAccount';

function formatMoney(value: number | null | undefined, currency = 'USD'): string {
  if (value == null) return '—';
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
    maximumFractionDigits: 0,
  }).format(value);
}

function formatDate(iso: string | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  const day = String(d.getDate()).padStart(2, '0');
  const mo = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'][d.getMonth()];
  const yr = d.getFullYear();
  return `${day} ${mo} ${yr}`;
}

function planLabel(p: InvestmentPlan): string {
  if (p.budgetMode === 'ALL_AVAILABLE') return 'All available balance';
  return `${formatMoney(p.amount, p.currency)} / month`;
}

function planStatusKind(status: InvestmentPlanStatus): 'dca' | 'paused' | 'failed' {
  if (status === 'ACTIVE') return 'dca';
  if (status === 'PAUSED') return 'paused';
  return 'failed';
}

function brokerLabel(broker: BrokerAccount | undefined): string {
  if (!broker) return '—';
  if (broker.brokerType === 'ALPACA_LIVE') return `${broker.name} · Alpaca live`;
  if (broker.brokerType === 'ALPACA_PAPER') return `${broker.name} · Alpaca paper`;
  return `${broker.name} · Mock`;
}

export default function InvestmentPlansPage() {
  const { data: plansData, isLoading: plansLoading } = useInvestmentPlans();
  const { data: brokersData } = useBrokerAccounts();

  const plans = plansData ?? [];
  const brokersById = useMemo(() => {
    const map = new Map<string, BrokerAccount>();
    (brokersData ?? []).forEach((b) => map.set(b.id, b));
    return map;
  }, [brokersData]);

  const active = plans.filter((p) => p.status === 'ACTIVE');
  const paused = plans.filter((p) => p.status === 'PAUSED');
  const terminated = plans.filter((p) => p.status === 'TERMINATED');
  const monthlyCommitted = active
    .filter((p) => p.budgetMode !== 'ALL_AVAILABLE' && p.amount != null)
    .reduce((s, p) => s + (p.amount ?? 0), 0);
  const nextExec = active
    .map((p) => p.nextExecutionAt)
    .filter((d): d is string => !!d)
    .sort()[0] ?? null;

  const kpis: KpiItem[] = [
    {
      label: 'Active plans',
      value: String(active.length),
      delta:
        paused.length + terminated.length > 0
          ? `${paused.length} paused · ${terminated.length} terminated`
          : 'all active',
    },
    {
      label: 'Monthly committed',
      value: formatMoney(monthlyCommitted),
      delta:
        active.length > 0
          ? `across ${active.length} active plan${active.length === 1 ? '' : 's'}`
          : 'no scheduled spend',
    },
    {
      label: 'Next execution',
      value: nextExec ? formatDate(nextExec) : '—',
      delta: nextExec ? 'first across active plans' : 'no scheduled run',
    },
  ];

  return (
    <DashboardLayout>
      <Page>
        <PageHeader>
          <HeaderLeft>
            <H1>Plans</H1>
            <Desc>
              Recurring contributions that drip cash into a live or paper simulation on a schedule
              you choose. The AI picker decides what to fund each month.
            </Desc>
          </HeaderLeft>
          <HeaderRight>
            <GhostLink href="/notifications">Notifications</GhostLink>
            <NewLink href="/investment-plans/new">
              <span aria-hidden="true">+</span> New plan
            </NewLink>
          </HeaderRight>
        </PageHeader>

        {plans.length > 0 && <KpiStrip items={kpis} />}

        <Panel title="Plans" count={plans.length} noBodyPadding>
          {plansLoading ? (
            <SkeletonBlock>
              {Array.from({ length: 3 }).map((_, i) => (
                <SkeletonRow key={i}>
                  <SkelBar $w="60px" $h="20px" />
                  <SkelBar $w="55%" $h="13px" />
                  <SkelBar $w="100px" $h="12px" />
                  <SkelBar $w="80px" $h="12px" />
                  <SkelBar $w="60px" $h="20px" />
                </SkeletonRow>
              ))}
            </SkeletonBlock>
          ) : plans.length === 0 ? (
            <EmptyState>
              <EmptyIcon>+</EmptyIcon>
              <EmptyTitle>No plans yet.</EmptyTitle>
              <EmptyBody>
                Investment plans deposit cash into a live or paper simulation on a schedule. Create
                one when you&apos;ve found a simulation worth funding.
              </EmptyBody>
              <NewLink href="/investment-plans/new" style={{ marginTop: 8 }}>
                <span aria-hidden="true">+</span> Create your first plan
              </NewLink>
            </EmptyState>
          ) : (
            <Rows>
              <HeaderRowBar>
                <span>Status</span>
                <span>Plan</span>
                <span>Mode</span>
                <span>Next execution</span>
                <RightAligned>Actions</RightAligned>
              </HeaderRowBar>
              {plans.map((p) => (
                <PlanRow key={p.id} plan={p} broker={brokersById.get(p.brokerAccountId)} />
              ))}
            </Rows>
          )}
        </Panel>
      </Page>
    </DashboardLayout>
  );
}

interface PlanRowProps {
  plan: InvestmentPlan;
  broker: BrokerAccount | undefined;
}

function PlanRow({ plan, broker }: PlanRowProps) {
  return (
    <Row>
      <StatusCell>
        <StatusPill kind={planStatusKind(plan.status)}>{plan.status.toLowerCase()}</StatusPill>
      </StatusCell>
      <PlanInfo>
        <PlanName>{planLabel(plan)}</PlanName>
        <PlanSub>
          <Mono>#{plan.id.slice(0, 8)}</Mono>
          <SubSep>·</SubSep>
          <span>day {plan.dayOfMonth} of month</span>
          {broker && (
            <>
              <SubSep>·</SubSep>
              <span>{brokerLabel(broker)}</span>
            </>
          )}
        </PlanSub>
      </PlanInfo>
      <ModeCell>
        <ModeLabel>{BUDGET_MODE_LABELS[plan.budgetMode]}</ModeLabel>
        <ModeSub>{plan.pickerPolicy.replace('_', ' ').toLowerCase()}</ModeSub>
      </ModeCell>
      <NextExecCell>
        {plan.nextExecutionAt ? formatDate(plan.nextExecutionAt) : <Dim>—</Dim>}
      </NextExecCell>
      <RowActs>
        <RowActBtn as={Link} href={`/investment-plans/${plan.id}`} $primary>
          Open
        </RowActBtn>
      </RowActs>
    </Row>
  );
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
  display: inline-flex;
  align-items: center;
  gap: 4px;

  &:hover {
    color: ${p => p.theme.colors.fgStrong};
    border-color: ${p => p.theme.colors.fgMuted};
    background: rgba(255,255,255,0.02);
  }
`;

const GhostLink = styled(Link)`${ghostStyles}`;

const NewLink = styled(Link)`
  ${ghostStyles}
  color: ${p => p.theme.colors.brand};
  border-color: rgba(0,255,65,0.4);

  &:hover {
    color: ${p => p.theme.colors.brandHover};
    border-color: ${p => p.theme.colors.brand};
    background: rgba(0,255,65,0.08);
  }
`;

const Rows = styled.div`
  display: flex;
  flex-direction: column;
`;

const HeaderRowBar = styled.div`
  display: grid;
  grid-template-columns: 90px 1fr 200px 160px 100px;
  gap: 14px;
  align-items: center;
  padding: 10px 18px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
  background: ${p => p.theme.colors.surface};
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};

  @media (max-width: 768px) {
    display: none;
  }
`;

const RightAligned = styled.span`
  text-align: right;
`;

const Row = styled.div`
  display: grid;
  grid-template-columns: 90px 1fr 200px 160px 100px;
  gap: 14px;
  align-items: center;
  padding: 11px 18px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  font-size: 14px;
  color: ${p => p.theme.colors.foreground};
  transition: background 120ms ease;

  &:last-child { border-bottom: none; }
  &:hover { background: rgba(255,255,255,0.02); }

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
    gap: 10px;
    padding: 14px 18px;
  }
`;

const StatusCell = styled.div`
  display: inline-flex;
  align-items: center;
`;

const PlanInfo = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
`;

const PlanName = styled.span`
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  font-size: 14px;
`;

const PlanSub = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: ${p => p.theme.colors.fgMuted};
  font-size: 12.5px;
  flex-wrap: wrap;
`;

const Mono = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const SubSep = styled.span`
  color: ${p => p.theme.colors.border};
`;

const ModeCell = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
`;

const ModeLabel = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12.5px;
  color: ${p => p.theme.colors.foreground};
`;

const ModeSub = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
`;

const NextExecCell = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 13px;
  color: ${p => p.theme.colors.foreground};
`;

const Dim = styled.span`
  color: ${p => p.theme.colors.fgDimmed};
`;

const RowActs = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  justify-self: end;

  @media (max-width: 768px) {
    justify-self: stretch;
  }
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
`;

const SkeletonRow = styled.div`
  display: grid;
  grid-template-columns: 90px 1fr 200px 160px 100px;
  gap: 14px;
  align-items: center;
  padding: 12px 18px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};

  &:last-child { border-bottom: none; }

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
    gap: 8px;
  }
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
