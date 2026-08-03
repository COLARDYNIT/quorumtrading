'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import styled, { css } from 'styled-components';
import { DashboardLayout } from '@/app/components/layouts/DashboardLayout';
import { Panel } from '@/app/components/shared/v2';
import { useCreateMarketResearch, useModelPreferences, useAvailableModels, useEffectiveModels } from '@/app/lib/api/hooks';
import { effectiveModelFor } from '@/app/lib/types/config';
import type { IntervalUnit } from '@/app/lib/types/api';

export default function NewMarketResearchPage() {
  const router = useRouter();
  const createMutation = useCreateMarketResearch();
  const { data: preferences } = useModelPreferences();
  const { data: models } = useAvailableModels('RESEARCH');
  // The system default is computed by the backend for the ACTIVE engine, never restated here.
  const { data: effectiveModels } = useEffectiveModels();

  const researchDefault = effectiveModelFor(effectiveModels, 'RESEARCH');
  const activeModelId = preferences?.research ?? researchDefault;
  const activeModel = models?.find(m => m.id === activeModelId);

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [targetAssets, setTargetAssets] = useState('');
  const [intervalAmount, setIntervalAmount] = useState('7');
  const [intervalUnit, setIntervalUnit] = useState<IntervalUnit>('DAYS');
  const [autoBacktest, setAutoBacktest] = useState(false);
  const [backtestLength, setBacktestLength] = useState('P30D');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const data = {
      name: name.trim(),
      description: description.trim() || undefined,
      targetAssets: targetAssets.split(',').map(s => s.trim()).filter(Boolean),
      interval: {
        amount: parseInt(intervalAmount, 10),
        unit: intervalUnit,
      },
      autoBacktest,
      backtestConfig: autoBacktest ? { backtestLength } : undefined,
    };
    createMutation.mutate(data, {
      onSuccess: (response) => {
        router.push(`/research/${response.id}`);
      },
    });
  };

  const handleCancel = () => router.push('/research');
  const isLoading = createMutation.isPending;

  return (
    <DashboardLayout>
      <Page>
        <PageHeader>
          <HeaderLeft>
            <Crumb>
              <Link href="/research">Research</Link>
              <CrumbSep>/</CrumbSep>
              <CrumbCurrent>New</CrumbCurrent>
            </Crumb>
            <H1>New market research</H1>
            <Desc>
              Configure how AI discovers trading opportunities. The research agent analyzes markets based on
              your instructions and can optionally auto-backtest viable strategies.
            </Desc>
          </HeaderLeft>
        </PageHeader>

        <Form onSubmit={handleSubmit}>
          <Panel title="Research config">
            <FieldStack>
              <Field>
                <FieldLabel>Name</FieldLabel>
                <Input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="e.g. Weekly Crypto Momentum Scanner"
                  required
                  disabled={isLoading}
                />
                <FieldHint>Short label that surfaces in the research list and execution notifications.</FieldHint>
              </Field>

              <Field>
                <FieldLabel>What should the AI look for?</FieldLabel>
                <Textarea
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Example: Find cryptocurrencies with unusual volume spikes and positive sentiment. Focus on projects with recent partnership announcements or technical breakouts above key resistance levels."
                  rows={5}
                  disabled={isLoading}
                />
                <FieldHint>Describe patterns, events, or conditions the AI should identify. The more specific, the better.</FieldHint>
              </Field>

              <Field>
                <FieldLabel>Starting assets (optional)</FieldLabel>
                <Input
                  type="text"
                  value={targetAssets}
                  onChange={(e) => setTargetAssets(e.target.value)}
                  placeholder="AAPL, NVDA, BTC:CRYPTO"
                  disabled={isLoading}
                />
                <FieldHint>Comma-separated tickers — stocks by default; append :CRYPTO for crypto assets (e.g. BTC:CRYPTO). Leave empty to let the AI discover relevant assets from your instructions.</FieldHint>
              </Field>
            </FieldStack>
          </Panel>

          <Panel
            title="AI agent LLM"
            headerActions={<HeaderNote>shared with the rest of your council · change in settings</HeaderNote>}
          >
            <ModelBox>
              <ModelName>{activeModel?.name ?? activeModelId}</ModelName>
              {activeModel?.supportsWebSearch && <ModelBadge>Web search</ModelBadge>}
              <ModelChangeLink href="/settings/config">Change →</ModelChangeLink>
            </ModelBox>
            <ModelNote>
              Market research needs a strong reasoning model — Anthropic Claude Opus or similar tier is recommended.
              The research agent has web-search capability and pulls live news + sentiment during analysis.
            </ModelNote>
          </Panel>

          <Panel title="Schedule">
            <FieldRow>
              <Field>
                <FieldLabel>Run every</FieldLabel>
                <Input
                  type="number"
                  value={intervalAmount}
                  onChange={(e) => setIntervalAmount(e.target.value)}
                  min="1"
                  required
                  disabled={isLoading}
                />
              </Field>
              <Field>
                <FieldLabel>Period</FieldLabel>
                <Select
                  value={intervalUnit}
                  onChange={(e) => setIntervalUnit(e.target.value as IntervalUnit)}
                  disabled={isLoading}
                >
                  <option value="DAYS">Days</option>
                  <option value="WEEKS">Weeks</option>
                  <option value="MONTHS">Months</option>
                </Select>
              </Field>
            </FieldRow>
            <FieldHint>
              Research will execute every {intervalAmount} {intervalUnit.toLowerCase()}, analyzing markets and generating reports.
            </FieldHint>
          </Panel>

          <Panel title="Automation">
            <ToggleRow>
              <ToggleSwitch
                type="button"
                role="switch"
                aria-checked={autoBacktest}
                $on={autoBacktest}
                onClick={() => setAutoBacktest(v => !v)}
                disabled={isLoading}
              >
                <ToggleKnob $on={autoBacktest} />
              </ToggleSwitch>
              <ToggleCopy>
                <ToggleTitle>Auto-backtest viable strategies</ToggleTitle>
                <ToggleDesc>Automatically run a historical backtest when the research agent surfaces a viable strategy.</ToggleDesc>
              </ToggleCopy>
            </ToggleRow>

            {autoBacktest && (
              <BacktestField>
                <FieldLabel>Backtest duration</FieldLabel>
                <Input
                  type="text"
                  value={backtestLength}
                  onChange={(e) => setBacktestLength(e.target.value)}
                  placeholder="P30D"
                  disabled={isLoading}
                />
                <FieldHint>ISO-8601 duration: P30D (30 days), P3M (3 months), P1Y (1 year).</FieldHint>
              </BacktestField>
            )}
          </Panel>

          <SubmitRow>
            <GhostBtn type="button" onClick={handleCancel} disabled={isLoading}>
              Cancel
            </GhostBtn>
            <PrimaryBtn type="submit" disabled={isLoading || !name.trim()}>
              {isLoading ? 'Creating…' : 'Create research'}
            </PrimaryBtn>
          </SubmitRow>
        </Form>
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

const Crumb = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};

  & a {
    color: ${p => p.theme.colors.fgMuted};
    text-decoration: none;
    transition: color 120ms ease;
  }
  & a:hover { color: ${p => p.theme.colors.foreground}; }
`;

const CrumbSep = styled.span`
  color: ${p => p.theme.colors.border};
`;

const CrumbCurrent = styled.span`
  color: ${p => p.theme.colors.brand};
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

const HeaderNote = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const Form = styled.form`
  display: flex;
  flex-direction: column;
  gap: 14px;
`;

const FieldStack = styled.div`
  display: flex;
  flex-direction: column;
  gap: 14px;
`;

const FieldRow = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;

  @media (max-width: 640px) {
    grid-template-columns: 1fr;
  }
`;

const Field = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
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
  transition: border-color 120ms ease;

  &:focus { outline: none; border-color: ${p => p.theme.colors.focusRing}; }
  &::placeholder { color: ${p => p.theme.colors.fgDimmed}; }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
`;

const Input = styled.input`${inputStyles}`;
const Select = styled.select`${inputStyles} cursor: pointer;`;
const Textarea = styled.textarea`
  ${inputStyles}
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13.5px;
  line-height: 1.55;
  resize: vertical;
  min-height: 120px;
`;

const FieldHint = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 12.5px;
  color: ${p => p.theme.colors.fgMuted};
  line-height: 1.45;
`;

const ModelBox = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 2px;
  flex-wrap: wrap;
`;

const ModelName = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 14px;
  color: ${p => p.theme.colors.brand};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
`;

const ModelBadge = styled.span`
  display: inline-flex;
  align-items: center;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  padding: 2px 7px;
  border-radius: 2px;
  color: ${p => p.theme.colors.info};
  border: 1px solid rgba(0,217,255,0.3);
`;

const ModelChangeLink = styled(Link)`
  margin-left: auto;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.info};
  text-decoration: none;
  letter-spacing: 0.06em;

  &:hover { text-decoration: underline; }
`;

const ModelNote = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13px;
  color: ${p => p.theme.colors.fgMuted};
  line-height: 1.5;
  padding: 10px 12px;
  background: rgba(255,176,0,0.04);
  border-left: 2px solid rgba(255,176,0,0.4);
  border-radius: 0 2px 2px 0;
  margin-top: 10px;
`;

const ToggleRow = styled.div`
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 14px;
  align-items: center;
`;

const ToggleSwitch = styled.button<{ $on: boolean }>`
  position: relative;
  width: 36px;
  height: 20px;
  border-radius: 12px;
  border: 1px solid ${p => p.$on ? 'rgba(0,255,65,0.4)' : p.theme.colors.border};
  background: ${p => p.$on ? 'rgba(0,255,65,0.08)' : p.theme.colors.surfaceElevated};
  cursor: pointer;
  transition: background 120ms ease, border-color 120ms ease;

  &:disabled { opacity: 0.5; cursor: not-allowed; }
`;

const ToggleKnob = styled.span<{ $on: boolean }>`
  position: absolute;
  top: 2px;
  left: ${p => p.$on ? '18px' : '2px'};
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: ${p => p.$on ? p.theme.colors.brand : p.theme.colors.fgMuted};
  transition: left 120ms ease, background 120ms ease;
`;

const ToggleCopy = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
`;

const ToggleTitle = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 14px;
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
`;

const ToggleDesc = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13px;
  color: ${p => p.theme.colors.fgMuted};
  line-height: 1.45;
`;

const BacktestField = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px dashed ${p => p.theme.colors.borderSubtle};
`;

const SubmitRow = styled.div`
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
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

const GhostBtn = styled.button`
  ${baseBtnStyles}
  &:hover:not(:disabled) {
    color: ${p => p.theme.colors.fgStrong};
    border-color: ${p => p.theme.colors.fgMuted};
    background: rgba(255,255,255,0.02);
  }
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
