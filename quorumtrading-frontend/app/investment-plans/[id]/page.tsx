'use client';

import { useMemo } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import styled, { css } from 'styled-components';
import { DashboardLayout } from '../../components/layouts/DashboardLayout';
import { Panel, StatusPill, KpiStrip, type KpiItem } from '@/app/components/shared/v2';
import {
  useInvestmentPlan,
  useInvestmentPlanAllocations,
  useBrokerAccounts,
  usePauseInvestmentPlan,
  useResumeInvestmentPlan,
  useTerminateInvestmentPlan,
  useExecuteInvestmentPlanNow,
} from '@/app/lib/api/hooks';
import {
  ALLOCATION_STATUS_LABELS,
  BUDGET_MODE_LABELS,
  type AllocationStatus,
  type InvestmentAllocation,
  type InvestmentPlan,
  type InvestmentPlanStatus,
} from '@/app/lib/types/investmentPlan';
import type { BrokerAccount } from '@/app/lib/types/brokerAccount';

type AllocKind = 'success' | 'skip' | 'failure';

function allocKind(status: AllocationStatus): AllocKind {
  if (status === 'EXECUTED') return 'success';
  if (status.startsWith('SKIPPED')) return 'skip';
  return 'failure';
}

function planStatusKind(status: InvestmentPlanStatus): 'dca' | 'paused' | 'failed' {
  if (status === 'ACTIVE') return 'dca';
  if (status === 'PAUSED') return 'paused';
  return 'failed';
}

/**
 * The caption under the next-execution date.
 *
 * This used to read 'on schedule' for every ACTIVE plan — including on the installs where the DCA
 * scheduler is off, which is all of them by default. The date is real; whether anything will act
 * on it is a separate question, and this is the screen a user opens to find out whether their
 * money moved.
 */
export function nextExecutionDelta(plan: InvestmentPlan): string {
  if (plan.status === 'TERMINATED') return 'plan terminated';
  if (plan.status === 'PAUSED') return 'plan paused';
  if (!plan.automaticExecutionEnabled) return 'automatic execution off — run manually';
  return 'on schedule';
}

function formatMoney(value: number | null | undefined, currency = 'USD', decimals = 0): string {
  if (value == null) return '—';
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  }).format(value);
}

function formatDate(iso: string | null, withTime = false): string {
  if (!iso) return '—';
  const d = new Date(iso);
  const day = String(d.getDate()).padStart(2, '0');
  const mo = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'][d.getMonth()];
  const yr = d.getFullYear();
  if (!withTime) return `${day} ${mo} ${yr}`;
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  return `${day} ${mo} ${yr} · ${hh}:${mm}`;
}

function brokerLabel(broker: BrokerAccount | undefined): string {
  if (!broker) return '—';
  if (broker.brokerType === 'ALPACA_LIVE') return `${broker.name} · Alpaca live`;
  if (broker.brokerType === 'ALPACA_PAPER') return `${broker.name} · Alpaca paper`;
  return `${broker.name} · Mock`;
}

export default function InvestmentPlanDetailPage() {
  const params = useParams<{ id: string }>();
  const id = params.id;
  const router = useRouter();

  const { data: plan, isLoading, isError, error } = useInvestmentPlan(id);
  const { data: allocationsData } = useInvestmentPlanAllocations(id);
  const { data: brokersData } = useBrokerAccounts();
  const allocations = allocationsData ?? [];

  const broker = useMemo(
    () => brokersData?.find((b) => b.id === plan?.brokerAccountId),
    [brokersData, plan]
  );

  const pause = usePauseInvestmentPlan();
  const resume = useResumeInvestmentPlan();
  const terminate = useTerminateInvestmentPlan();
  const executeNow = useExecuteInvestmentPlanNow();

  if (isLoading) {
    return (
      <DashboardLayout>
        <Page>
          <SkeletonBlock>
            <SkelBar $w="220px" $h="22px" />
            <SkelBar $w="60%" $h="13px" />
            <SkelStrip>
              {Array.from({ length: 4 }).map((_, i) => <SkelBar key={i} $w="100%" $h="48px" />)}
            </SkelStrip>
            <SkelBar $w="100%" $h="240px" />
          </SkeletonBlock>
        </Page>
      </DashboardLayout>
    );
  }

  // Without this branch a 404 (or any fetch error) leaves `plan` undefined and
  // `isLoading` false — the skeleton would render forever. Surface a real
  // not-found page so users see what went wrong instead of an endless spinner.
  if (isError || !plan) {
    const status = (error as { status?: number } | null)?.status;
    return (
      <DashboardLayout>
        <Page>
          <NotFoundCard>
            <h2>{status === 404 ? 'Investment plan not found' : 'Could not load this plan'}</h2>
            <p>
              {status === 404
                ? 'The plan you opened no longer exists, was terminated, or this link points at an allocation ID instead of a plan ID.'
                : 'Something went wrong fetching this plan. Try again, or open the list to verify the ID.'}
            </p>
            <Link href="/investment-plans">← Back to investment plans</Link>
          </NotFoundCard>
        </Page>
      </DashboardLayout>
    );
  }

  const succeeded = allocations.filter((a) => a.status === 'EXECUTED').length;
  const skipped = allocations.filter((a) => a.status.startsWith('SKIPPED')).length;
  const failed = allocations.filter((a) => a.status.startsWith('FAILED')).length;
  const totalContributed = allocations
    .filter((a) => a.status === 'EXECUTED')
    .reduce((s, a) => s + (a.actualAmount ?? 0), 0);

  const kpis: KpiItem[] = [
    {
      label: 'Next execution',
      value: plan.nextExecutionAt ? formatDate(plan.nextExecutionAt, true) : '—',
      delta: nextExecutionDelta(plan),
    },
    {
      label: 'Total contributed',
      value: formatMoney(totalContributed),
      delta: `across ${succeeded} successful tick${succeeded === 1 ? '' : 's'}`,
    },
    {
      label: 'Allocations',
      value: `${succeeded}/${allocations.length || 0}`,
      delta: (
        <>
          <Positive>{succeeded} ok</Positive>
          {skipped > 0 && <><DeltaSep>·</DeltaSep><Warn>{skipped} skipped</Warn></>}
          {failed > 0 && <><DeltaSep>·</DeltaSep><Neg>{failed} failed</Neg></>}
        </>
      ),
    },
    {
      label: 'Last execution',
      value: plan.lastExecutionAt ? formatDate(plan.lastExecutionAt) : '—',
      delta: plan.lastExecutionAt ? 'most recent tick' : 'no executions yet',
    },
  ];

  return (
    <DashboardLayout>
      <Page>
        <BackLink href="/investment-plans">
          <span aria-hidden="true">←</span> Back to plans
        </BackLink>
        <PageHeader>
          <HeaderLeft>
            <Crumb>
              <Mono>#{plan.id.slice(0, 8)}</Mono>
              <CrumbSep>·</CrumbSep>
              Investment plan
            </Crumb>
            <H1>
              {plan.budgetMode === 'ALL_AVAILABLE'
                ? 'All available balance'
                : `${formatMoney(plan.amount, plan.currency)} / month`}
            </H1>
            <Desc>
              {BUDGET_MODE_LABELS[plan.budgetMode]} · day {plan.dayOfMonth} of month
              {broker && (
                <>
                  {' '}· {brokerLabel(broker)}
                </>
              )}
            </Desc>
          </HeaderLeft>
          <HeaderRight>
            <StatusPill kind={planStatusKind(plan.status)}>{plan.status.toLowerCase()}</StatusPill>
            {plan.status === 'ACTIVE' && (
              <GhostBtn type="button" onClick={() => pause.mutate(id)} disabled={pause.isPending}>
                Pause
              </GhostBtn>
            )}
            {plan.status === 'PAUSED' && (
              <PrimaryBtn type="button" onClick={() => resume.mutate(id)} disabled={resume.isPending}>
                Resume
              </PrimaryBtn>
            )}
            {plan.status !== 'TERMINATED' && (
              <>
                <PrimaryBtn
                  type="button"
                  onClick={() => executeNow.mutate(id)}
                  disabled={executeNow.isPending}
                  title="Triggers a real picker call and broker action"
                >
                  Execute now
                </PrimaryBtn>
                <DangerBtn
                  type="button"
                  onClick={() => {
                    if (confirm('Terminate this plan? Existing allocations remain for audit.')) {
                      terminate.mutate(id, {
                        onSuccess: () => router.push('/investment-plans'),
                      });
                    }
                  }}
                  disabled={terminate.isPending}
                >
                  Terminate
                </DangerBtn>
              </>
            )}
          </HeaderRight>
        </PageHeader>

        <KpiStrip items={kpis} />

        <Panel
          title="Allocation timeline"
          count={allocations.length}
          noBodyPadding
          headerActions={
            <HeaderAction>
              {plan.automaticExecutionEnabled
                ? `immutable · executes 06:00 UTC on day ${plan.dayOfMonth}`
                : `immutable · scheduled for day ${plan.dayOfMonth}, automatic execution off`}
            </HeaderAction>
          }
        >
          {allocations.length === 0 ? (
            <EmptyState>
              <EmptyIcon>—</EmptyIcon>
              <EmptyTitle>No allocations yet.</EmptyTitle>
              <EmptyBody>
                {plan.automaticExecutionEnabled
                  ? 'The plan hasn’t run yet. Its first allocation will land here on the next scheduled execution.'
                  : 'The plan hasn’t run yet, and automatic execution is switched off on this instance — it will not run on its own. Use “Execute now” to fund it, or ask an operator to enable the recurring-investment scheduler.'}
              </EmptyBody>
            </EmptyState>
          ) : (
            <Rows>
              <HeaderRowBar>
                <span />
                <span>Executed</span>
                <span>Reason / outcome</span>
                <span>Amount</span>
              </HeaderRowBar>
              {allocations.map((a) => (
                <AllocRow key={a.id} allocation={a} plan={plan} />
              ))}
            </Rows>
          )}
        </Panel>
      </Page>
    </DashboardLayout>
  );
}

interface AllocRowProps {
  allocation: InvestmentAllocation;
  plan: InvestmentPlan;
}

function AllocRow({ allocation: a, plan }: AllocRowProps) {
  const kind = allocKind(a.status);
  const glyph = kind === 'success' ? '✓' : kind === 'skip' ? '‖' : '×';
  // Picker reasoning describes *which strategy* the picker chose — useful
  // context, but NOT why the contribution failed. Rendering it as the only
  // sub-line on a failure row misleads users into reading it as the failure
  // explanation. Surface the failure/skip reason first, and label the picker
  // line explicitly so it can't be mistaken for an error message.
  const needsExplanation = kind === 'failure' || kind === 'skip';
  const explanation = needsExplanation
    ? (a.failureReason
        ? a.failureReason
        : 'No diagnostic was recorded for this attempt — check the backend logs (Dynatrace / CloudWatch) for the underlying exception. New failures after the 2026-05-17 fix capture the exception class name automatically.')
    : null;
  return (
    <AllocRowEl $kind={kind}>
      <Glyph $kind={kind}>{glyph}</Glyph>
      <When>{formatDate(a.executionDate, true)}</When>
      <Reason $kind={kind}>
        <ReasonPrimary>{ALLOCATION_STATUS_LABELS[a.status]}</ReasonPrimary>
        {explanation && <ReasonSub $kind="failure">{explanation}</ReasonSub>}
        {a.pickerReasoning && (
          <ReasonSub>
            <PickerLabel>Strategy pick:</PickerLabel> {a.pickerReasoning}
          </ReasonSub>
        )}
        <ReasonLinks>
          {a.targetSimulationId && (
            <AllocLink href={`/simulations/${a.targetSimulationId}`}>→ Simulation</AllocLink>
          )}
          {a.chosenStrategyId && (
            <AllocLink href={`/strategies/${a.chosenStrategyId}`}>→ Strategy</AllocLink>
          )}
        </ReasonLinks>
      </Reason>
      <Amount $kind={kind}>
        {a.actualAmount != null
          ? formatMoney(a.actualAmount, a.currency, 2)
          : <DimAmount>{formatMoney(a.intendedAmount, a.currency, 2)} intended</DimAmount>}
      </Amount>
    </AllocRowEl>
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
  gap: 6px;
  min-width: 0;
`;

const Crumb = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
`;

const CrumbSep = styled.span`
  color: ${p => p.theme.colors.border};
`;

const Mono = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
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
  flex-wrap: wrap;
`;

const btnStyles = css`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  padding: 6px 12px;
  border-radius: 2px;
  border: 1px solid ${p => p.theme.colors.border};
  background: transparent;
  color: ${p => p.theme.colors.fgMuted};
  cursor: pointer;
  transition: color 120ms ease, border-color 120ms ease, background 120ms ease;
  white-space: nowrap;

  &:disabled { opacity: 0.4; cursor: not-allowed; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
`;

const GhostBtn = styled.button`
  ${btnStyles}
  &:hover {
    color: ${p => p.theme.colors.fgStrong};
    border-color: ${p => p.theme.colors.fgMuted};
    background: rgba(255,255,255,0.02);
  }
`;

const PrimaryBtn = styled.button`
  ${btnStyles}
  color: ${p => p.theme.colors.brand};
  border-color: rgba(0,255,65,0.4);
  &:hover {
    color: ${p => p.theme.colors.brandHover};
    border-color: ${p => p.theme.colors.brand};
    background: rgba(0,255,65,0.08);
  }
`;

const DangerBtn = styled.button`
  ${btnStyles}
  color: ${p => p.theme.colors.negative};
  border-color: rgba(255,51,102,0.35);
  &:hover {
    color: ${p => p.theme.colors.negative};
    border-color: ${p => p.theme.colors.negative};
    background: rgba(255,51,102,0.08);
  }
`;

const HeaderAction = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const Positive = styled.span` color: ${p => p.theme.colors.positive}; `;
const Warn = styled.span` color: ${p => p.theme.colors.terminalAmber}; `;
const Neg = styled.span` color: ${p => p.theme.colors.negative}; `;
const DeltaSep = styled.span`
  margin: 0 4px;
  color: ${p => p.theme.colors.border};
`;

const Rows = styled.div`
  display: flex;
  flex-direction: column;
`;

const HeaderRowBar = styled.div`
  display: grid;
  grid-template-columns: 24px 160px 1fr 130px;
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

  & > span:last-child { text-align: right; }

  @media (max-width: 768px) {
    display: none;
  }
`;

const AllocRowEl = styled.div<{ $kind: AllocKind }>`
  display: grid;
  grid-template-columns: 24px 160px 1fr 130px;
  grid-template-areas: "glyph when reason amount";
  /* start, not center — Reason now wraps multi-line, so centering against
     single-line neighbours pulled the headline visually into the row gutter. */
  align-items: start;
  padding: 14px 18px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  gap: 14px;
  font-size: 13.5px;
  transition: background 120ms ease;

  &:last-child { border-bottom: none; }
  &:hover { background: rgba(255,255,255,0.02); }

  @media (max-width: 768px) {
    grid-template-columns: 24px 1fr auto;
    grid-template-areas:
      "glyph when amount"
      "reason reason reason";
    gap: 8px 10px;
    padding: 14px 18px;
  }
`;

const Glyph = styled.span<{ $kind: AllocKind }>`
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  border: 1px solid ${p => p.theme.colors.border};
  color: ${p => p.theme.colors.fgMuted};
  grid-area: glyph;

  ${p => p.$kind === 'success' && css`
    color: ${p.theme.colors.positive};
    border-color: rgba(6,255,165,0.5);
    background: rgba(6,255,165,0.08);
  `}
  ${p => p.$kind === 'skip' && css`
    color: ${p.theme.colors.terminalAmber};
    border-color: rgba(255,176,0,0.5);
    background: rgba(255,176,0,0.08);
  `}
  ${p => p.$kind === 'failure' && css`
    color: ${p.theme.colors.negative};
    border-color: rgba(255,51,102,0.5);
    background: rgba(255,51,102,0.08);
  `}
`;

const When = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 12.5px;
  color: ${p => p.theme.colors.fgDimmed};
  grid-area: when;
`;

const Reason = styled.div<{ $kind: AllocKind }>`
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13.5px;
  /* min-width:0 lets this grid item shrink so the nested ReasonSub
     respects its parent cell width instead of dictating the column. */
  min-width: 0;
  overflow: hidden;
  color: ${p => p.theme.colors.foreground};
  grid-area: reason;
`;

const ReasonPrimary = styled.span`
  display: block;
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  font-size: 14px;
  line-height: 1.25;
`;

/*
 * Picker reasoning + failure messages can run long (the failure case in
 * particular can be a stack-trace-derived sentence). Wrapping to a small
 * line clamp keeps the row compact while still preserving the message —
 * `white-space: nowrap` + `text-overflow: ellipsis` was eating most of it
 * and visually crowded the headline above when the cell was narrow.
 */
const ReasonSub = styled.span<{ $kind?: 'failure' }>`
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-width: 0;
  font-size: 12.5px;
  line-height: 1.4;
  color: ${p => p.$kind === 'failure' ? p.theme.colors.negative : p.theme.colors.fgMuted};
  font-style: ${p => p.$kind === 'failure' ? 'normal' : 'italic'};
`;

const PickerLabel = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 10.5px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
  margin-right: 6px;
  font-style: normal;
`;

const ReasonLinks = styled.div`
  display: inline-flex;
  gap: 12px;
  margin-top: 2px;
  flex-wrap: wrap;
`;

const AllocLink = styled(Link)`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.info};
  text-decoration: none;
  &:hover { text-decoration: underline; }
`;

const Amount = styled.div<{ $kind: AllocKind }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 13.5px;
  text-align: right;
  color: ${p =>
    p.$kind === 'success'
      ? p.theme.colors.fgStrong
      : p.theme.colors.fgDimmed};
  grid-area: amount;
`;

const DimAmount = styled.span`
  color: ${p => p.theme.colors.fgDimmed};
  font-size: 12px;
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
  gap: 14px;
`;

const NotFoundCard = styled.div`
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  padding: 24px 22px;
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 6px;
  background: ${p => p.theme.colors.surface};
  font-family: ${p => p.theme.typography.fontFamily.sans};

  h2 {
    margin: 0;
    font-size: 16px;
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
    color: ${p => p.theme.colors.fgStrong};
  }
  p {
    margin: 0;
    color: ${p => p.theme.colors.fgMuted};
    font-size: 13.5px;
    line-height: 1.5;
  }
  a {
    color: ${p => p.theme.colors.brand};
    text-decoration: none;
    font-size: 13px;
    font-weight: ${p => p.theme.typography.fontWeight.medium};
  }
  a:hover { text-decoration: underline; }
`;

const SkelStrip = styled.div`
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1px;
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
