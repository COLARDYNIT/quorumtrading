'use client';

import { useMemo } from 'react';
import Link from 'next/link';
import styled from 'styled-components';
import { Panel } from '@/app/components/shared/v2';
import { useInvestmentPlans } from '@/app/lib/api/hooks';
import type { InvestmentPlan } from '@/app/lib/types/investmentPlan';

interface UpcomingPanelProps {
  /** Number of upcoming items to show. Default 5. */
  limit?: number;
}

const List = styled.div`
  display: flex; flex-direction: column;
`;

const Row = styled(Link)`
  display: grid;
  grid-template-columns: 96px 1fr auto;
  gap: 10px;
  align-items: center;
  padding: 9px 12px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  font-size: 12.5px;
  text-decoration: none;
  color: inherit;
  transition: background 120ms ease;

  &:last-child { border-bottom: none; }
  &:hover { background: rgba(255, 255, 255, 0.02); }

  .countdown { font-family: ${p => p.theme.typography.fontFamily.mono}; color: ${p => p.theme.colors.info}; font-size: 11.5px; }
  .countdown.near { color: ${p => p.theme.colors.terminalAmber}; }
  .what { color: ${p => p.theme.colors.foreground}; min-width: 0; }
  .what .meta { color: ${p => p.theme.colors.fgDimmed}; font-size: 11.5px; display: block; margin-top: 1px; font-family: ${p => p.theme.typography.fontFamily.mono}; }
  .amt { font-family: ${p => p.theme.typography.fontFamily.mono}; color: ${p => p.theme.colors.positive}; font-size: 12.5px; text-align: right; }
`;

const Empty = styled.div`
  padding: 28px 16px; text-align: center;
  color: ${p => p.theme.colors.fgMuted}; font-size: 13px;

  a { color: ${p => p.theme.colors.brand}; text-decoration: none; }
  a:hover { text-decoration: underline; }
`;

function fmtCountdown(target: Date, now: Date): { text: string; near: boolean } {
  const ms = target.getTime() - now.getTime();
  if (ms < 0) return { text: 'overdue', near: true };
  const mins = Math.floor(ms / 60_000);
  const hours = Math.floor(mins / 60);
  const daysApprox = Math.floor(hours / 24);
  if (mins < 60) return { text: `in ${mins}m`, near: true };
  if (hours < 24) return { text: `in ${hours}h ${mins % 60}m`, near: hours < 6 };
  if (daysApprox < 14) return { text: `in ${daysApprox} day${daysApprox === 1 ? '' : 's'}`, near: false };
  return { text: target.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }), near: false };
}

function fmtMoney(amount: number | null, currency: string): string {
  if (amount == null) return '—';
  return amount.toLocaleString('en-US', { style: 'currency', currency: currency || 'USD', maximumFractionDigits: 2 });
}

function budgetLabel(plan: InvestmentPlan): string {
  if (plan.budgetMode === 'FIXED_AMOUNT' && plan.amount) return `Fixed ${fmtMoney(plan.amount, plan.currency)}`;
  if (plan.budgetMode === 'FIXED_OR_PARTIAL' && plan.amount) return `Up to ${fmtMoney(plan.amount, plan.currency)}`;
  return 'All available';
}

export function UpcomingPanel({ limit = 5 }: UpcomingPanelProps) {
  const { data: plans, isLoading } = useInvestmentPlans('ACTIVE');

  const rows = useMemo(() => {
    const all = (plans ?? []).filter(p => p.nextExecutionAt != null);
    all.sort((a, b) => new Date(a.nextExecutionAt!).getTime() - new Date(b.nextExecutionAt!).getTime());
    return all.slice(0, limit);
  }, [plans, limit]);

  const now = new Date();

  return (
    <Panel
      title="Upcoming"
      count={`${rows.length} scheduled`}
      noBodyPadding
      headerActions={<Link href="/investment-plans" style={{ fontSize: 10, color: 'var(--qt-fg-muted, #8a98a4)', textDecoration: 'none' }}>Manage →</Link>}
    >
      {isLoading ? (
        <Empty>Loading…</Empty>
      ) : rows.length === 0 ? (
        <Empty>
          No active DCA plans. <Link href="/investment-plans">Create a plan →</Link>
        </Empty>
      ) : (
        <List>
          {rows.map(plan => {
            const due = new Date(plan.nextExecutionAt!);
            const cd = fmtCountdown(due, now);
            return (
              <Row key={plan.id} href={`/investment-plans/${plan.id}`}>
                <span className={`countdown${cd.near ? ' near' : ''}`}>{cd.text}</span>
                <span className="what">
                  DCA contribution
                  <span className="meta">{budgetLabel(plan)} · day {plan.dayOfMonth} · {plan.pickerPolicy === 'AI_PICK' ? 'AI picks strategy' : plan.pickerPolicy}</span>
                </span>
                <span className="amt">{plan.amount != null ? `+${fmtMoney(plan.amount, plan.currency)}` : '—'}</span>
              </Row>
            );
          })}
        </List>
      )}
    </Panel>
  );
}
