'use client';

import { Suspense, useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import styled, { css } from 'styled-components';
import toast from 'react-hot-toast';
import { DashboardLayout } from '../../components/layouts/DashboardLayout';
import { Panel, KpiStrip, type KpiItem } from '@/app/components/shared/v2';
import { pillBase, tagBase } from '@/app/styles/labels';
import {
  useBudget,
  useTopUpBudget,
  useLLMUsage,
  usePaymentConfig,
  useCreateCheckout,
  useUpdateDailyLimit,
} from '@/app/lib/api/hooks';
import type { UserBudget } from '@/app/lib/types/budget';

const TOP_UP_AMOUNTS = [5, 10, 50];

// UI thresholds for the runway read — display only, NOT backend fields.
const RUNWAY_WARN_DAYS = 7; // below this, the runway read + bar go amber
const RUNWAY_HORIZON = 30; // the bar's "comfortable" full mark (clamped)
const BALANCE_FLOOR = 0.01; // treat near-zero as depleted (council stops at $0)

type Health = 'available' | 'warning' | 'depleted' | 'unlimited';

interface Derived {
  remaining: number;
  dailyBurn: number;
  hasBurn: boolean;
  runwayDays: number | null;
  health: Health;
  runwayPct: number;
}

/**
 * Everything below is a real UserBudget field or honestly derived from one.
 *   dailyBurn = weeklySpend / 7   (last-7-day average)
 *   runway    = remainingBudget / dailyBurn   (null when there's no recent burn)
 * The lifetime ratio (lifetimeSpend / limitAmount) is deliberately NOT used as a
 * gauge — it only ever climbs and pins near 100% after every top-up.
 */
function deriveBudget(b: UserBudget): Derived {
  const remaining = b.remainingBudget ?? 0;
  const dailyBurn = b.weeklySpend / 7;
  const hasBurn = dailyBurn >= 0.005;
  const runwayDays = hasBurn ? remaining / dailyBurn : null;

  let health: Health;
  if (b.isUnlimited) health = 'unlimited';
  else if (remaining <= BALANCE_FLOOR) health = 'depleted';
  else if (runwayDays != null && runwayDays < RUNWAY_WARN_DAYS) health = 'warning';
  else health = 'available';

  const runwayPct =
    runwayDays == null
      ? health === 'depleted'
        ? 0
        : 100
      : Math.max(0, Math.min(100, (runwayDays / RUNWAY_HORIZON) * 100));

  return { remaining, dailyBurn, hasBurn, runwayDays, health, runwayPct };
}

function formatCurrency(value: number | null | undefined, decimals = 2): string {
  if (value == null) return '—';
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  }).format(value);
}

function formatDays(d: number | null): string {
  if (d == null) return '—';
  if (d >= 100) return '100+';
  if (d >= 10) return Math.round(d).toString();
  return d.toFixed(d < 1 ? 1 : 0);
}

function formatDate(iso: string | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  const day = String(d.getDate()).padStart(2, '0');
  const mo = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'][d.getMonth()];
  return `${day} ${mo} ${d.getFullYear()}`;
}

function weekdayShort(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('en-US', { weekday: 'short' });
}

const HEALTH_LABEL: Record<Health, string> = {
  available: 'Available',
  warning: 'Low balance',
  depleted: 'Depleted · paused',
  unlimited: 'Unlimited',
};

function PaymentReturnHandler() {
  const searchParams = useSearchParams();
  const router = useRouter();

  useEffect(() => {
    const paymentStatus = searchParams.get('payment');
    if (paymentStatus === 'success') {
      toast.success('Payment successful! Your budget will be updated shortly.');
      router.replace('/settings/budget');
    } else if (paymentStatus === 'cancelled') {
      toast('Payment was cancelled.', { icon: 'i' });
      router.replace('/settings/budget');
    }
  }, [searchParams, router]);

  return null;
}

export default function BudgetPage() {
  const { data: budget, isLoading: budgetLoading, error: budgetError } = useBudget();
  const { data: usage, isLoading: usageLoading } = useLLMUsage();
  const { data: paymentConfig } = usePaymentConfig();
  const topUpMutation = useTopUpBudget();
  const checkoutMutation = useCreateCheckout();
  const dailyLimitMutation = useUpdateDailyLimit();
  const [selectedAmount, setSelectedAmount] = useState<number | null>(null);
  const [chosenAmount, setChosenAmount] = useState<number>(10);
  const [dailyLimitInput, setDailyLimitInput] = useState('');

  useEffect(() => {
    if (budget?.dailySpendLimit != null) {
      setDailyLimitInput(budget.dailySpendLimit.toString());
    } else {
      setDailyLimitInput('');
    }
  }, [budget?.dailySpendLimit]);

  const isStripeConfigured = paymentConfig?.configured === true;

  const handleTopUp = (amount: number) => {
    setSelectedAmount(amount);

    if (isStripeConfigured) {
      const baseUrl = window.location.origin + '/settings/budget';
      checkoutMutation.mutate(
        {
          amount,
          successUrl: `${baseUrl}?payment=success`,
          cancelUrl: `${baseUrl}?payment=cancelled`,
        },
        {
          onSuccess: (data) => {
            if (!data.checkoutUrl?.startsWith('https://checkout.stripe.com/')) {
              toast.error('Invalid checkout URL received. Please try again.');
              return;
            }
            window.location.href = data.checkoutUrl;
          },
          onSettled: () => setSelectedAmount(null),
        },
      );
    } else {
      topUpMutation.mutate(amount, {
        onSettled: () => setSelectedAmount(null),
      });
    }
  };

  const handleSaveDailyLimit = () => {
    const value = dailyLimitInput.trim();
    if (!value) return;
    const num = parseFloat(value);
    if (isNaN(num) || num <= 0) {
      toast.error('Daily limit must be a positive number');
      return;
    }
    dailyLimitMutation.mutate(num);
  };

  const handleClearDailyLimit = () => {
    dailyLimitMutation.mutate(null);
  };

  const isTopUpLoading = isStripeConfigured ? checkoutMutation.isPending : topUpMutation.isPending;

  const recentUsage =
    usage?.dailyUsage
      ?.filter((d) => d.billedCostUsd > 0)
      .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime())
      .slice(0, 30) ?? [];

  const derived = budget ? deriveBudget(budget) : null;

  const dailyPct =
    budget && budget.dailySpendLimit != null && budget.dailySpendLimit > 0
      ? (budget.todaySpend / budget.dailySpendLimit) * 100
      : null;

  const kpis: KpiItem[] =
    budget && derived
      ? [
          {
            label: budget.isUnlimited ? 'Lifetime spend' : 'Remaining balance',
            value: <Mono>{formatCurrency(budget.isUnlimited ? budget.lifetimeSpend : budget.remainingBudget)}</Mono>,
            tone:
              derived.health === 'depleted'
                ? 'negative'
                : derived.health === 'warning'
                  ? 'hold'
                  : 'default',
            delta: budget.isUnlimited ? (
              'unlimited budget'
            ) : derived.health === 'depleted' ? (
              <Tone $c="pink">council paused</Tone>
            ) : !derived.hasBurn ? (
              <Tone $c="dim">no recent usage</Tone>
            ) : (
              <Tone $c={derived.health === 'warning' ? 'amber' : 'mint'}>
                ≈ {formatDays(derived.runwayDays)}d runway
              </Tone>
            ),
          },
          {
            label: 'This month',
            value: <Mono>{formatCurrency(budget.currentSpend)}</Mono>,
            delta: 'billed · resets 1st',
          },
          {
            label: 'This week',
            value: <Mono>{formatCurrency(budget.weeklySpend)}</Mono>,
            delta: `${formatCurrency(derived.dailyBurn)}/day avg`,
          },
          {
            label: 'Today',
            value: <Mono>{formatCurrency(budget.todaySpend)}</Mono>,
            delta:
              budget.dailySpendLimit != null
                ? `of ${formatCurrency(budget.dailySpendLimit, 0)} cap`
                : 'no daily cap',
          },
        ]
      : [];

  return (
    <DashboardLayout>
      <Suspense>
        <PaymentReturnHandler />
      </Suspense>
      <Page>
        <PageHeader>
          <HeaderLeft>
            <H1>AI budget</H1>
            <Desc>
              Your council of agents votes on every trading decision — each vote bills the raw model
              cost plus the platform markup. Prepay into your wallet; the council pauses when the balance
              hits zero. The daily cap is a separate guard that stops a runaway agent from draining
              the wallet in one day.
            </Desc>
          </HeaderLeft>
        </PageHeader>

        {budgetError ? (
          <Panel title="Budget" noBodyPadding>
            <EmptyState>
              <EmptyIcon>!</EmptyIcon>
              <EmptyTitle>Couldn&apos;t load budget.</EmptyTitle>
              <EmptyBody>{(budgetError as Error).message}</EmptyBody>
            </EmptyState>
          </Panel>
        ) : budgetLoading || !budget || !derived ? (
          <Panel title="Budget" noBodyPadding>
            <SkeletonBlock>
              {Array.from({ length: 3 }).map((_, i) => (
                <SkeletonRow key={i}>
                  <SkelBar $w="120px" $h="13px" />
                  <SkelBar $w="80px" $h="13px" />
                  <SkelBar $w="80px" $h="20px" />
                </SkeletonRow>
              ))}
            </SkeletonBlock>
          </Panel>
        ) : (
          <>
            <KpiStrip items={kpis} />

            {/* ── Wallet balance: balance-first hero + runway read ─────────── */}
            <Panel
              title="Wallet balance"
              headerActions={<HealthPill $kind={derived.health}>
                <PillDot $kind={derived.health} />
                {HEALTH_LABEL[derived.health]}
              </HealthPill>}
              noBodyPadding
            >
              {derived.health === 'depleted' && (
                <DepletedBanner>
                  <BannerIc>■</BannerIc>
                  <span>
                    <b>Council paused.</b> Balance reached $0.00 — votes are suspended until you add
                    funds. Your daily cap and strategies are untouched.
                  </span>
                </DepletedBanner>
              )}

              <WalletHero>
                <Lead>
                  <Balance $health={derived.health} data-health={derived.health}>
                    <BalanceAmt>
                      {budget.isUnlimited ? 'Unlimited' : formatCurrency(budget.remainingBudget)}
                    </BalanceAmt>
                    <BalanceCap>
                      {budget.isUnlimited
                        ? 'Prepaid balance · no limit set'
                        : 'Prepaid balance · funds remaining'}
                    </BalanceCap>
                  </Balance>

                  {!budget.isUnlimited && (
                    <Runway $health={derived.health} data-health={derived.health}>
                      <RunwayLine>
                        {derived.health === 'depleted' ? (
                          <>
                            <Days>No runway</Days>
                            <span>— add funds to resume the council.</span>
                          </>
                        ) : !derived.hasBurn ? (
                          <>
                            <Days>Plenty</Days>
                            <span>
                              — no recent council activity to estimate a burn rate.
                              <DerivedMark title="Δ derived client-side from weeklySpend — not a backend field">
                                {' '}Δ
                              </DerivedMark>
                            </span>
                          </>
                        ) : (
                          <>
                            <span>≈</span>
                            <Days>{formatDays(derived.runwayDays)} days</Days>
                            <span>
                              of runway at your recent pace
                              <DerivedMark title="Δ derived client-side from weeklySpend — not a backend field">
                                {' '}Δ
                              </DerivedMark>
                            </span>
                            <Pace>· {formatCurrency(derived.dailyBurn)}/day · last 7 days</Pace>
                          </>
                        )}
                      </RunwayLine>

                      <RunwayBar $health={derived.health}>
                        <RunwayTrack>
                          <RunwayFill $health={derived.health} style={{ width: `${derived.runwayPct}%` }} />
                          <WarnMark
                            style={{ left: `${(RUNWAY_WARN_DAYS / RUNWAY_HORIZON) * 100}%` }}
                            title={`${RUNWAY_WARN_DAYS}-day warn line`}
                          />
                        </RunwayTrack>
                        <RunwayScale>
                          <span>$0 · empty</span>
                          <WarnLbl>▲ {RUNWAY_WARN_DAYS}d warn</WarnLbl>
                          <span>{RUNWAY_HORIZON}d+ comfortable</span>
                        </RunwayScale>
                      </RunwayBar>
                    </Runway>
                  )}
                </Lead>

                <Facts>
                  <Fact>
                    <FactKey>Funded to date</FactKey>
                    <FactVal>{formatCurrency(budget.limitAmount)}</FactVal>
                  </Fact>
                  <Fact>
                    <FactKey>Spent to date</FactKey>
                    <FactVal>{formatCurrency(budget.lifetimeSpend)}</FactVal>
                  </Fact>
                  <Fact>
                    <FactKey>Last top-up</FactKey>
                    <FactVal>{budget.lastTopUpAt ? formatDate(budget.lastTopUpAt) : 'free credits'}</FactVal>
                  </Fact>
                  <Fact>
                    <FactKey>Status</FactKey>
                    <FactVal>{HEALTH_LABEL[derived.health]}</FactVal>
                  </Fact>
                </Facts>
              </WalletHero>
            </Panel>

            <TwoCol>
              {/* ── Daily spend cap: a bounded, daily-resetting meter (kept) ── */}
              <Panel
                title="Daily spend cap"
                headerActions={<HeaderNote>resets 00:00 UTC</HeaderNote>}
              >
                {dailyPct != null && budget.dailySpendLimit != null ? (
                  <CapMeter>
                    <CapHead>
                      <CapSpent>{formatCurrency(budget.todaySpend)}</CapSpent>
                      <CapTotal>
                        of <b>{formatCurrency(budget.dailySpendLimit, 0)}</b> today · {dailyPct.toFixed(0)}%
                      </CapTotal>
                    </CapHead>
                    <CapTrack>
                      <CapFill $hot={dailyPct > 80} style={{ width: `${Math.min(100, dailyPct)}%` }} />
                    </CapTrack>
                    <CapLabels>
                      <span>$0</span>
                      <span>{formatCurrency(budget.dailySpendLimit, 0)}</span>
                    </CapLabels>
                  </CapMeter>
                ) : (
                  <NoCapNote>No daily cap set — the council runs until the wallet balance is gone.</NoCapNote>
                )}

                <FieldGroup>
                  <FieldLabel htmlFor="daily">Daily limit</FieldLabel>
                  <FieldHint>
                    Pauses the council for the rest of the UTC day once today&apos;s billed spend hits
                    this. Independent of your balance — a runaway agent can&apos;t drain the wallet in
                    one day.
                  </FieldHint>
                  <DailyLimitRow>
                    <Currency>$</Currency>
                    <Input
                      id="daily"
                      type="text"
                      inputMode="decimal"
                      pattern="[0-9]*\.?[0-9]*"
                      placeholder="none"
                      autoComplete="off"
                      value={dailyLimitInput}
                      onChange={(e) => {
                        const val = e.target.value;
                        if (val === '' || /^\d*\.?\d*$/.test(val)) {
                          setDailyLimitInput(val);
                        }
                      }}
                    />
                    <PrimaryBtn
                      type="button"
                      onClick={handleSaveDailyLimit}
                      disabled={dailyLimitMutation.isPending || !dailyLimitInput.trim()}
                    >
                      Save
                    </PrimaryBtn>
                    {budget.dailySpendLimit != null && (
                      <GhostBtn
                        type="button"
                        onClick={handleClearDailyLimit}
                        disabled={dailyLimitMutation.isPending}
                      >
                        Clear
                      </GhostBtn>
                    )}
                  </DailyLimitRow>
                </FieldGroup>
              </Panel>

              {/* ── Add funds: pick a preset, preview the new balance, confirm ─ */}
              <Panel
                title="Add funds"
                headerActions={
                  !isStripeConfigured ? (
                    <MockBadge>[dev] mock top-up · no payment processed</MockBadge>
                  ) : derived.health === 'depleted' ? (
                    <ResumeNote>resume the council</ResumeNote>
                  ) : (
                    <HeaderNote>instant · top-up only</HeaderNote>
                  )
                }
              >
                <Fund>
                  <PresetRow>
                    {TOP_UP_AMOUNTS.map((amount) => {
                      const sub = derived.hasBurn
                        ? `≈ ${formatDays(amount / derived.dailyBurn)} days`
                        : null;
                      return (
                        <Preset
                          key={amount}
                          type="button"
                          $sel={chosenAmount === amount}
                          onClick={() => setChosenAmount(amount)}
                          disabled={isTopUpLoading}
                        >
                          <PresetAmt>{formatCurrency(amount, 0)}</PresetAmt>
                          {sub && <PresetSub>{sub}</PresetSub>}
                        </Preset>
                      );
                    })}
                  </PresetRow>
                  <FundFoot>
                    {!budget.isUnlimited && (
                      <AfterText>
                        Balance after top-up: <b>{formatCurrency(derived.remaining + chosenAmount)}</b>
                      </AfterText>
                    )}
                    <SolidBtn
                      type="button"
                      onClick={() => handleTopUp(chosenAmount)}
                      disabled={isTopUpLoading}
                      $loading={selectedAmount === chosenAmount && isTopUpLoading}
                    >
                      Add {formatCurrency(chosenAmount, 0)}
                    </SolidBtn>
                  </FundFoot>
                </Fund>
              </Panel>
            </TwoCol>

            <Panel
              title="Recent spending"
              count={recentUsage.length}
              headerActions={<HeaderNote>billed cost · last 30 days with activity</HeaderNote>}
              noBodyPadding
            >
              {usageLoading ? (
                <SkeletonBlock>
                  {Array.from({ length: 3 }).map((_, i) => (
                    <SkeletonRow key={i}>
                      <SkelBar $w="120px" $h="13px" />
                      <SkelBar $w="100px" $h="13px" />
                    </SkeletonRow>
                  ))}
                </SkeletonBlock>
              ) : recentUsage.length === 0 ? (
                <EmptyState>
                  <EmptyIcon>·</EmptyIcon>
                  <EmptyTitle>No spending yet.</EmptyTitle>
                  <EmptyBody>Run a backtest or research execution and the meter will start counting.</EmptyBody>
                </EmptyState>
              ) : (
                <Rows>
                  <HeaderRowBar>
                    <span>Day</span>
                    <RightAligned>Cost</RightAligned>
                  </HeaderRowBar>
                  {recentUsage.map((day) => (
                    <UsageRow key={day.date}>
                      <DayCell>
                        <DayName>{weekdayShort(day.date)}</DayName>
                        <DayDate>{formatDate(day.date)}</DayDate>
                      </DayCell>
                      <UsageCost>{formatCurrency(day.billedCostUsd)}</UsageCost>
                    </UsageRow>
                  ))}
                </Rows>
              )}
            </Panel>
          </>
        )}
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

const Mono = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
`;

const Tone = styled.span<{ $c: 'mint' | 'amber' | 'pink' | 'dim' }>`
  color: ${p =>
    p.$c === 'mint'
      ? p.theme.colors.terminalMint
      : p.$c === 'amber'
        ? p.theme.colors.terminalAmber
        : p.$c === 'pink'
          ? p.theme.colors.terminalPink
          : p.theme.colors.fgDimmed};
`;

const HeaderNote = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const ResumeNote = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  color: ${p => p.theme.colors.terminalPink};
`;

// ── Health pill ──────────────────────────────────────────────────────────

/**
 * Health → hue. `color` is the only per-kind property a label may set (DS v0.4.0): the pill's
 * border and fill mix out of `currentColor`, so there are no per-kind rgba literals to keep
 * in step with the light theme.
 */
const healthHue = (kind: Health, theme: import('styled-components').DefaultTheme): string => {
  switch (kind) {
    case 'warning':
      return theme.colors.terminalAmber;
    case 'depleted':
      return theme.colors.terminalPink;
    case 'unlimited':
      return theme.colors.focusRing;
    default:
      return theme.colors.terminalMint;
  }
};

const HealthPill = styled.span<{ $kind: Health }>`
  ${pillBase}
  color: ${p => healthHue(p.$kind, p.theme)};
`;

/**
 * Still by design. A pulsing dot claims work is in flight; a depleted budget is a settled
 * state — the council has already stopped — so it emphasises with a glow, never motion.
 */
const PillDot = styled.span<{ $kind: Health }>`
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  flex-shrink: 0;
  ${p =>
    p.$kind === 'depleted' &&
    css`
      box-shadow: 0 0 6px currentColor;
    `}
`;

// ── Wallet hero ────────────────────────────────────────────────────────────

const DepletedBanner = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  background: rgba(255, 51, 102, 0.06);
  border-bottom: 1px solid rgba(255, 51, 102, 0.22);
  font-size: 13.5px;
  color: ${p => p.theme.colors.foreground};

  & b {
    color: ${p => p.theme.colors.terminalPink};
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
  }
`;

const BannerIc = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.terminalPink};
  font-size: 15px;
`;

const WalletHero = styled.div`
  display: grid;
  grid-template-columns: 1fr 248px;
  gap: 28px;
  padding: 20px 20px 22px;

  @media (max-width: 720px) {
    grid-template-columns: 1fr;
    gap: 18px;
  }
`;

const Lead = styled.div`
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
`;

const Balance = styled.div<{ $health: Health }>`
  display: flex;
  flex-direction: column;
  gap: 3px;
`;

const BalanceAmt = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 52px;
  line-height: 1;
  letter-spacing: -0.02em;
  color: ${p => p.theme.colors.fgStrong};

  ${Balance}[data-health='warning'] & { color: ${p => p.theme.colors.terminalAmber}; }
  ${Balance}[data-health='depleted'] & { color: ${p => p.theme.colors.terminalPink}; }
`;

const BalanceCap = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
`;

const Runway = styled.div<{ $health: Health }>`
  display: flex;
  flex-direction: column;
  gap: 9px;
  max-width: 460px;
`;

const RunwayLine = styled.div`
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 14.5px;
  color: ${p => p.theme.colors.fgMuted};
`;

const Days = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 17px;
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.medium};

  ${Runway}[data-health='warning'] & { color: ${p => p.theme.colors.terminalAmber}; }
  ${Runway}[data-health='depleted'] & { color: ${p => p.theme.colors.terminalPink}; }
`;

const Pace = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const DerivedMark = styled.sup`
  color: ${p => p.theme.colors.terminalAmber};
  font-size: 9px;
  cursor: help;
`;

const RunwayBar = styled.div<{ $health: Health }>`
  display: flex;
  flex-direction: column;
  gap: 6px;
`;

const RunwayTrack = styled.div`
  position: relative;
  height: 9px;
  background: ${p => p.theme.colors.surfaceElevated};
  border-radius: 5px;
  overflow: hidden;
`;

const RunwayFill = styled.div<{ $health: Health }>`
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  border-radius: 5px;
  transition: width 300ms ease;
  background: ${p =>
    p.$health === 'warning'
      ? p.theme.colors.terminalAmber
      : p.$health === 'depleted'
        ? p.theme.colors.terminalPink
        : p.theme.colors.terminalMint};
`;

const WarnMark = styled.div`
  position: absolute;
  top: -3px;
  bottom: -3px;
  width: 2px;
  background: ${p => p.theme.colors.terminalAmber};
  opacity: 0.7;
`;

const RunwayScale = styled.div`
  display: flex;
  justify-content: space-between;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 10.5px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const WarnLbl = styled.span`
  color: ${p => p.theme.colors.terminalAmber};
`;

const Facts = styled.div`
  display: flex;
  flex-direction: column;
  border-left: 1px solid ${p => p.theme.colors.borderSubtle};
  padding-left: 24px;

  @media (max-width: 720px) {
    border-left: none;
    border-top: 1px solid ${p => p.theme.colors.borderSubtle};
    padding-left: 0;
    padding-top: 6px;
  }
`;

const Fact = styled.div`
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 0;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};

  &:last-child { border-bottom: none; }
`;

const FactKey = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 10.5px;
  letter-spacing: 0.09em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
`;

const FactVal = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 12.5px;
  color: ${p => p.theme.colors.fgMuted};
`;

// ── Layout ──────────────────────────────────────────────────────────────

const TwoCol = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;

  @media (max-width: 860px) {
    grid-template-columns: 1fr;
  }
`;

// ── Daily cap meter ─────────────────────────────────────────────────────

const CapMeter = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 6px 4px 14px;
`;

const CapHead = styled.div`
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  font-family: ${p => p.theme.typography.fontFamily.mono};
`;

const CapSpent = styled.span`
  font-size: 24px;
  color: ${p => p.theme.colors.fgStrong};
  font-variant-numeric: tabular-nums;
`;

const CapTotal = styled.span`
  font-size: 14px;
  color: ${p => p.theme.colors.fgMuted};

  & b {
    color: ${p => p.theme.colors.foreground};
    font-weight: ${p => p.theme.typography.fontWeight.medium};
  }
`;

const CapTrack = styled.div`
  position: relative;
  height: 8px;
  background: ${p => p.theme.colors.surfaceElevated};
  border-radius: 4px;
  overflow: hidden;
`;

const CapFill = styled.div<{ $hot?: boolean }>`
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  border-radius: 4px;
  transition: width 200ms ease;
  background: ${p => (p.$hot ? p.theme.colors.terminalAmber : p.theme.colors.terminalMint)};
`;

const CapLabels = styled.div`
  display: flex;
  justify-content: space-between;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const NoCapNote = styled.p`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13px;
  color: ${p => p.theme.colors.fgMuted};
  margin: 4px 4px 14px;
  line-height: 1.5;
`;

const FieldGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
`;

const FieldLabel = styled.label`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
`;

const FieldHint = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 12.5px;
  color: ${p => p.theme.colors.fgMuted};
  line-height: 1.4;
`;

const DailyLimitRow = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
`;

const Currency = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.fgDimmed};
  font-size: 15px;
`;

const Input = styled.input`
  flex: 1;
  min-width: 120px;
  padding: 8px 12px;
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 2px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 14px;
  color: ${p => p.theme.colors.foreground};
  transition: border-color 120ms ease;

  &:focus { outline: none; border-color: ${p => p.theme.colors.focusRing}; }
  &::placeholder { color: ${p => p.theme.colors.fgDimmed}; }
`;

const baseBtnStyles = css`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  padding: 8px 14px;
  border-radius: 2px;
  border: 1px solid ${p => p.theme.colors.border};
  background: transparent;
  color: ${p => p.theme.colors.fgMuted};
  cursor: pointer;
  transition: color 120ms ease, border-color 120ms ease, background 120ms ease;

  &:disabled { opacity: 0.4; cursor: not-allowed; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
`;

const PrimaryBtn = styled.button`
  ${baseBtnStyles}
  color: ${p => p.theme.colors.brand};
  border-color: rgba(0,255,65,0.4);
  &:hover:not(:disabled) {
    color: ${p => p.theme.colors.brandHover};
    border-color: ${p => p.theme.colors.brand};
    background: rgba(0,255,65,0.08);
  }
`;

const GhostBtn = styled.button`
  ${baseBtnStyles}
  &:hover:not(:disabled) {
    color: ${p => p.theme.colors.fgStrong};
    border-color: ${p => p.theme.colors.fgMuted};
    background: rgba(255,255,255,0.02);
  }
`;

// ── Add funds ───────────────────────────────────────────────────────────

const Fund = styled.div`
  display: flex;
  flex-direction: column;
  gap: 14px;
`;

const PresetRow = styled.div`
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
`;

const Preset = styled.button<{ $sel: boolean }>`
  flex: 1;
  min-width: 92px;
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => (p.$sel ? p.theme.colors.brand : p.theme.colors.border)};
  border-radius: 4px;
  padding: 14px 12px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 3px;
  align-items: flex-start;
  transition: border-color 120ms ease, background 120ms ease;
  ${p => p.$sel && css`background: rgba(0,255,65,0.05);`}

  &:hover:not(:disabled) {
    border-color: ${p => (p.$sel ? p.theme.colors.brand : p.theme.colors.fgMuted)};
  }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
`;

const PresetAmt = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 20px;
  color: ${p => p.theme.colors.fgStrong};
`;

const PresetSub = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 10.5px;
  letter-spacing: 0.06em;
  color: ${p => p.theme.colors.fgDimmed};
`;

const FundFoot = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
  padding-top: 4px;
`;

const AfterText = styled.span`
  font-size: 13px;
  color: ${p => p.theme.colors.fgMuted};

  & b {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-variant-numeric: tabular-nums;
    color: ${p => p.theme.colors.terminalMint};
    font-weight: ${p => p.theme.typography.fontWeight.medium};
  }
`;

const SolidBtn = styled.button<{ $loading?: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  padding: 9px 18px;
  border-radius: 2px;
  border: 1px solid ${p => p.theme.colors.brand};
  background: rgba(0,255,65,0.08);
  color: ${p => p.theme.colors.brand};
  cursor: pointer;
  position: relative;
  overflow: hidden;
  transition: background 120ms ease, color 120ms ease;

  &:hover:not(:disabled) { background: rgba(0,255,65,0.16); color: ${p => p.theme.colors.brandHover}; }
  &:disabled { opacity: 0.55; cursor: not-allowed; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }

  ${p => p.$loading && css`
    &::after {
      content: '';
      position: absolute;
      inset: 0;
      background: linear-gradient(90deg, transparent, rgba(0,255,65,0.25), transparent);
      background-size: 200% 100%;
      animation: budget-shimmer 1.5s infinite;
    }
    @keyframes budget-shimmer {
      0% { background-position: 200% 0; }
      100% { background-position: -200% 0; }
    }
  `}
`;

/** Dashed on purpose: this is not a real charge, and the broken border says so at a glance. */
const MockBadge = styled.span`
  ${tagBase}
  border-style: dashed;
  color: ${p => p.theme.colors.terminalAmber};
`;

// ── Recent spending ─────────────────────────────────────────────────────

const Rows = styled.div`
  display: flex;
  flex-direction: column;
`;

const HeaderRowBar = styled.div`
  display: grid;
  grid-template-columns: 1fr 110px;
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
`;

const RightAligned = styled.span`
  text-align: right;
`;

const UsageRow = styled.div`
  display: grid;
  grid-template-columns: 1fr 110px;
  gap: 14px;
  align-items: center;
  padding: 11px 18px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};

  &:last-child { border-bottom: none; }
  &:hover { background: rgba(255,255,255,0.02); }
`;

const DayCell = styled.div`
  display: flex;
  align-items: baseline;
  gap: 8px;
`;

const DayName = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
`;

const DayDate = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 14px;
  color: ${p => p.theme.colors.fgStrong};
`;

const UsageCost = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 14px;
  color: ${p => p.theme.colors.fgStrong};
  text-align: right;
`;

// ── States ──────────────────────────────────────────────────────────────

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
  grid-template-columns: 1fr 1fr 1fr;
  gap: 14px;
  align-items: center;
  padding: 14px 18px;
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
