'use client';

import { useMemo, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import styled, { css } from 'styled-components';
import { DashboardLayout } from '../../components/layouts/DashboardLayout';
import { Panel } from '@/app/components/shared/v2';
import {
  useBrokerAccounts,
  useCreateInvestmentPlan,
  useExecuteInvestmentPlanNow,
  useInstanceInfo,
} from '@/app/lib/api/hooks';
import type {
  BudgetMode,
  CreateInvestmentPlanRequest,
} from '@/app/lib/types/investmentPlan';
import { BUDGET_MODE_LABELS } from '@/app/lib/types/investmentPlan';

function parseTickers(csv: string): string[] {
  return csv
    .split(/[,\s]+/)
    .map((t) => t.trim().toUpperCase())
    .filter((t) => t.length > 0);
}

export default function NewInvestmentPlanPage() {
  const router = useRouter();
  const { data: brokers, isLoading: brokersLoading } = useBrokerAccounts();
  const create = useCreateInvestmentPlan();
  const executeNow = useExecuteInvestmentPlanNow();
  const { data: instance } = useInstanceInfo();
  // Undefined while the probe is in flight — only a confirmed `false` warns, so a slow request
  // does not flash a scary banner at someone on an instance where the scheduler is on.
  const automaticInvestmentsOff = instance?.automaticInvestmentsEnabled === false;

  const [brokerAccountId, setBrokerAccountId] = useState('');
  const [budgetMode, setBudgetMode] = useState<BudgetMode>('FIXED_AMOUNT');
  const [amount, setAmount] = useState('200');
  const [dayOfMonth, setDayOfMonth] = useState('1');
  const [riskTolerance, setRiskTolerance] = useState('moderate');
  const [allowedTickers, setAllowedTickers] = useState('');
  const [blockedTickers, setBlockedTickers] = useState('');
  const [preferExistingHoldings, setPreferExistingHoldings] = useState(true);
  const [executeNowAfter, setExecuteNowAfter] = useState(false);
  const [confirmLive, setConfirmLive] = useState(false);

  const selectedBroker = useMemo(
    () => brokers?.find((b) => b.id === brokerAccountId),
    [brokers, brokerAccountId]
  );
  const isLive = selectedBroker?.brokerType === 'ALPACA_LIVE';

  const isValid =
    brokerAccountId &&
    (budgetMode === 'ALL_AVAILABLE' || Number(amount) > 0) &&
    Number(dayOfMonth) >= 1 &&
    Number(dayOfMonth) <= 28 &&
    (!isLive || confirmLive);

  async function handleCreate() {
    if (!isValid) return;
    const body: CreateInvestmentPlanRequest = {
      brokerAccountId,
      budgetMode,
      amount: budgetMode === 'ALL_AVAILABLE' ? null : Number(amount),
      currency: 'USD',
      dayOfMonth: Number(dayOfMonth),
      pickerPolicy: 'AI_PICK',
      preferences: {
        riskTolerance: riskTolerance || null,
        allowedTickers: parseTickers(allowedTickers),
        blockedTickers: parseTickers(blockedTickers),
        maxStrategiesActive: null,
        preferExistingHoldings,
      },
      confirmLive,
    };
    const plan = await create.mutateAsync(body);
    if (executeNowAfter) {
      await executeNow.mutateAsync(plan.id);
    }
    router.push(`/investment-plans/${plan.id}`);
  }

  return (
    <DashboardLayout>
      <Page>
        <BackLink href="/investment-plans">
          <span aria-hidden="true">←</span> Back to plans
        </BackLink>
        <PageHeader>
          <HeaderLeft>
            <Crumb>Investment plans · New</Crumb>
            <H1>Create plan</H1>
            <Desc>
              Deposit cash into a live or paper simulation on a recurring schedule. The AI picker
              chooses what to fund each month based on your preferences.
            </Desc>
          </HeaderLeft>
        </PageHeader>

        <Panel title="Create plan">
          <Form onSubmit={(e) => { e.preventDefault(); handleCreate(); }}>
            <Field>
              <FieldLabel htmlFor="broker">Broker account</FieldLabel>
              <Select
                id="broker"
                value={brokerAccountId}
                onChange={(e) => setBrokerAccountId(e.target.value)}
                disabled={brokersLoading}
              >
                <option value="">— Select a broker account —</option>
                {(brokers ?? []).map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.name} — {b.brokerType} ({b.availableBalance.amount} {b.availableBalance.currency})
                  </option>
                ))}
              </Select>
              <Hint>
                Live accounts trade real money. Paper accounts simulate execution against live
                prices.
              </Hint>
              {isLive && (
                <LiveWarning>
                  <WarnIcon aria-hidden="true">⚠</WarnIcon>
                  <div>
                    <WarnTitle>Live trading guardrail</WarnTitle>
                    <WarnBody>
                      Every execution against this account moves real money. Confirm below to
                      enable plan creation.
                    </WarnBody>
                    <ConfirmRow>
                      <input
                        id="confirmLive"
                        type="checkbox"
                        checked={confirmLive}
                        onChange={(e) => setConfirmLive(e.target.checked)}
                      />
                      <label htmlFor="confirmLive">
                        I understand this plan will trade with real money each month.
                      </label>
                    </ConfirmRow>
                  </div>
                </LiveWarning>
              )}
            </Field>

            <FieldRow>
              <Field>
                <FieldLabel>Budget mode</FieldLabel>
                <Seg>
                  {(['FIXED_AMOUNT', 'FIXED_OR_PARTIAL', 'ALL_AVAILABLE'] as BudgetMode[]).map((m) => (
                    <SegBtn
                      key={m}
                      type="button"
                      $on={budgetMode === m}
                      onClick={() => setBudgetMode(m)}
                    >
                      {m === 'FIXED_AMOUNT' ? 'Fixed' : m === 'FIXED_OR_PARTIAL' ? 'Fixed / partial' : 'All available'}
                    </SegBtn>
                  ))}
                </Seg>
                <Hint>{BUDGET_MODE_LABELS[budgetMode]}</Hint>
              </Field>
              {budgetMode !== 'ALL_AVAILABLE' && (
                <Field>
                  <FieldLabel htmlFor="amount">Monthly cap (USD)</FieldLabel>
                  <Input
                    id="amount"
                    type="number"
                    min="1"
                    step="0.01"
                    value={amount}
                    onChange={(e) => setAmount(e.target.value)}
                  />
                  <Hint>Hard limit per execution.</Hint>
                </Field>
              )}
            </FieldRow>

            <FieldRow>
              <Field>
                <FieldLabel htmlFor="day">Execution day</FieldLabel>
                <Input
                  id="day"
                  type="number"
                  min="1"
                  max="28"
                  value={dayOfMonth}
                  onChange={(e) => setDayOfMonth(e.target.value)}
                />
                <Hint>Day of month, 1–28. Scheduled runs fire at 06:00 UTC.</Hint>
                {automaticInvestmentsOff && (
                  <LiveWarning>
                    <WarnIcon aria-hidden="true">⚠</WarnIcon>
                    <div>
                      <WarnTitle>Automatic execution is off on this instance</WarnTitle>
                      <WarnBody>
                        The plan will be created and shown as active, but nothing will run it on
                        this schedule — the recurring-investment scheduler is disabled by default
                        because it moves real money. Until an operator enables it, fund the plan
                        with <strong>Execute now</strong>.
                      </WarnBody>
                    </div>
                  </LiveWarning>
                )}
              </Field>
              <Field>
                <FieldLabel htmlFor="risk">Risk tolerance</FieldLabel>
                <Select
                  id="risk"
                  value={riskTolerance}
                  onChange={(e) => setRiskTolerance(e.target.value)}
                >
                  <option value="">— not specified —</option>
                  <option value="conservative">Conservative</option>
                  <option value="moderate">Moderate</option>
                  <option value="aggressive">Aggressive</option>
                </Select>
                <Hint>Hints the AI picker — does not enforce a cap.</Hint>
              </Field>
            </FieldRow>

            <FieldRow>
              <Field>
                <FieldLabel htmlFor="allowed">Allowed tickers</FieldLabel>
                <TextArea
                  id="allowed"
                  value={allowedTickers}
                  onChange={(e) => setAllowedTickers(e.target.value)}
                  placeholder="e.g. AAPL, MSFT, NVDA"
                />
                <Hint>Comma-separated. Empty means &ldquo;any&rdquo;.</Hint>
              </Field>
              <Field>
                <FieldLabel htmlFor="blocked">Blocked tickers</FieldLabel>
                <TextArea
                  id="blocked"
                  value={blockedTickers}
                  onChange={(e) => setBlockedTickers(e.target.value)}
                  placeholder="e.g. TSLA"
                />
                <Hint>Comma-separated. Optional.</Hint>
              </Field>
            </FieldRow>

            <Field>
              <Checkbox>
                <input
                  type="checkbox"
                  checked={preferExistingHoldings}
                  onChange={(e) => setPreferExistingHoldings(e.target.checked)}
                />
                <span>Prefer topping up an existing live simulation when possible</span>
              </Checkbox>
              <Checkbox>
                <input
                  type="checkbox"
                  checked={executeNowAfter}
                  onChange={(e) => setExecuteNowAfter(e.target.checked)}
                />
                <span>Run the first execution immediately after creating the plan</span>
              </Checkbox>
            </Field>

            <SubmitRow>
              <GhostBtn type="button" onClick={() => router.back()}>
                Cancel
              </GhostBtn>
              <SubmitRight>
                <PrimaryBtn
                  type="submit"
                  disabled={!isValid || create.isPending || executeNow.isPending}
                >
                  {create.isPending || executeNow.isPending ? 'Creating…' : 'Create plan'}
                </PrimaryBtn>
              </SubmitRight>
            </SubmitRow>
          </Form>
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
  gap: 6px;
  min-width: 0;
`;

const Crumb = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
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

const Form = styled.form`
  display: flex;
  flex-direction: column;
  gap: 18px;
`;

const Field = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
`;

const FieldRow = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;

  @media (max-width: 640px) {
    grid-template-columns: 1fr;
  }
`;

const FieldLabel = styled.label`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
`;

const inputStyles = css`
  padding: 8px 12px;
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 2px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 14px;
  color: ${p => p.theme.colors.foreground};
  width: 100%;
  box-sizing: border-box;
  transition: border-color 120ms ease;

  &:focus {
    outline: none;
    border-color: ${p => p.theme.colors.focusRing};
  }
`;

const Input = styled.input`${inputStyles}`;
const Select = styled.select`${inputStyles}`;
const TextArea = styled.textarea`
  ${inputStyles}
  min-height: 60px;
  resize: vertical;
  font-family: ${p => p.theme.typography.fontFamily.mono};
`;

const Hint = styled.span`
  font-size: 12.5px;
  color: ${p => p.theme.colors.fgMuted};
  line-height: 1.4;
`;

const Checkbox = styled.label`
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 14px;
  color: ${p => p.theme.colors.foreground};
  cursor: pointer;
  user-select: none;

  & input {
    width: 16px;
    height: 16px;
    accent-color: ${p => p.theme.colors.brand};
    cursor: pointer;
  }
`;

const LiveWarning = styled.div`
  display: grid;
  grid-template-columns: 24px 1fr;
  gap: 12px;
  padding: 14px 16px;
  background: rgba(255,176,0,0.06);
  border: 1px solid ${p => p.theme.colors.terminalAmber};
  border-radius: 2px;
  margin-top: 8px;
`;

const WarnIcon = styled.div`
  color: ${p => p.theme.colors.terminalAmber};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 17px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
`;

const WarnTitle = styled.div`
  color: ${p => p.theme.colors.terminalAmber};
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 14px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  margin-bottom: 4px;
`;

const WarnBody = styled.div`
  font-size: 13.5px;
  color: ${p => p.theme.colors.foreground};
  line-height: 1.5;
  margin-bottom: 10px;
`;

const ConfirmRow = styled.label`
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13.5px;
  color: ${p => p.theme.colors.foreground};
  cursor: pointer;

  & input {
    width: 16px;
    height: 16px;
    accent-color: ${p => p.theme.colors.terminalAmber};
  }
`;

const Seg = styled.div`
  display: inline-flex;
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 2px;
  overflow: hidden;
  width: max-content;
`;

const SegBtn = styled.button<{ $on: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13px;
  background: ${p => p.$on ? 'rgba(255,255,255,0.05)' : 'transparent'};
  color: ${p => p.$on ? p.theme.colors.fgStrong : p.theme.colors.fgMuted};
  border: none;
  padding: 7px 14px;
  cursor: pointer;
  border-right: 1px solid ${p => p.theme.colors.border};
  transition: color 120ms ease, background 120ms ease;

  &:last-child { border-right: none; }
  &:hover { color: ${p => p.theme.colors.fgStrong}; }
`;

const SubmitRow = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
  padding-top: 14px;
  border-top: 1px solid ${p => p.theme.colors.borderSubtle};
`;

const SubmitRight = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 8px;
`;

const btnStyles = css`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  padding: 8px 16px;
  border-radius: 2px;
  border: 1px solid ${p => p.theme.colors.border};
  background: transparent;
  color: ${p => p.theme.colors.fgMuted};
  cursor: pointer;
  transition: color 120ms ease, border-color 120ms ease, background 120ms ease;

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
