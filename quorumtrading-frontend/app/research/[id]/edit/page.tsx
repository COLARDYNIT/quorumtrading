'use client';

import { useState, useEffect } from 'react';
import { useRouter, useParams } from 'next/navigation';
import Link from 'next/link';
import styled from 'styled-components';
import { DashboardLayout } from '@/app/components/layouts/DashboardLayout';
import { TerminalButton } from '@/app/components/terminal/TerminalButton';
import { TerminalText } from '@/app/components/terminal/TerminalText';
import { TerminalSkeleton } from '@/app/components/terminal/TerminalSkeleton';
import { SectionTitle } from '@/app/components/shared';
import { useMarketResearchDetails, useUpdateMarketResearch, useModelPreferences, useAvailableModels, useEffectiveModels } from '@/app/lib/api/hooks';
import { effectiveModelFor } from '@/app/lib/types/config';
import { fadeInUp, shadows, textShadows } from '@/app/styles/animations';
import type { IntervalUnit } from '@/app/lib/types/api';

const PageContainer = styled.div`
  max-width: 900px;
  margin: 0 auto;
  padding: ${p => p.theme.spacing.lg};
  animation: ${fadeInUp} 0.5s ease-out;
`;

const PageHeader = styled.div`
  margin-bottom: ${p => p.theme.spacing.xl};
`;

const Subtitle = styled(TerminalText)`
  color: ${p => p.theme.colors.textMuted};
  font-size: ${p => p.theme.typography.fontSize.sm};
  line-height: 1.6;
`;

const FormCard = styled.form`
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.lg};
  box-shadow: ${shadows.cardPremium};
  overflow: hidden;
`;

const FormSection = styled.div<{ $delay?: number }>`
  padding: ${p => p.theme.spacing.lg} ${p => p.theme.spacing.xl};
  border-bottom: 1px solid ${p => p.theme.colors.border};
  animation: ${fadeInUp} 0.4s ease-out both;
  animation-delay: ${p => p.$delay || 0}ms;

  &:last-child {
    border-bottom: none;
  }

  @media (max-width: 480px) {
    padding: ${p => p.theme.spacing.md} ${p => p.theme.spacing.sm};
  }
`;

const SectionHeader = styled.div`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.sm};
  margin-bottom: ${p => p.theme.spacing.md};
`;

const SectionNumber = styled.span`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: ${p => p.theme.colors.terminalGreen}15;
  border: 1px solid ${p => p.theme.colors.terminalGreen}40;
  color: ${p => p.theme.colors.terminalGreen};
  font-size: 12px;
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  line-height: 1;
  padding-top: 1px;
`;

const SectionLabel = styled.span`
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.theme.colors.textMuted};
  text-transform: uppercase;
  letter-spacing: 0.1em;
`;

const FieldGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.md};
`;

const Field = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.xs};
`;

const Label = styled.label`
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.text};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  font-family: ${p => p.theme.typography.fontFamily.mono};
`;

const Input = styled.input`
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  color: ${p => p.theme.colors.text};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  transition: border-color 0.2s ease, box-shadow 0.2s ease;

  &:focus {
    outline: none;
    border-color: ${p => p.theme.colors.terminalGreen};
    box-shadow: 0 0 0 1px ${p => p.theme.colors.terminalGreen}40;
  }

  &::placeholder {
    color: ${p => p.theme.colors.textMuted};
    opacity: 0.6;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

const Textarea = styled.textarea`
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  color: ${p => p.theme.colors.text};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  resize: vertical;
  min-height: 200px;
  line-height: 1.5;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;

  &:focus {
    outline: none;
    border-color: ${p => p.theme.colors.terminalGreen};
    box-shadow: 0 0 0 1px ${p => p.theme.colors.terminalGreen}40;
  }

  &::placeholder {
    color: ${p => p.theme.colors.textMuted};
    opacity: 0.6;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

const HelperText = styled.span`
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  line-height: 1.4;
`;

const InlineFields = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: ${p => p.theme.spacing.md};

  @media (max-width: 480px) {
    grid-template-columns: 1fr;
  }
`;

const Select = styled.select`
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  color: ${p => p.theme.colors.text};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;

  &:focus {
    outline: none;
    border-color: ${p => p.theme.colors.terminalGreen};
    box-shadow: 0 0 0 1px ${p => p.theme.colors.terminalGreen}40;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

const FormActions = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: ${p => p.theme.spacing.lg} ${p => p.theme.spacing.xl};
  background: ${p => p.theme.colors.background};
  border-top: 1px solid ${p => p.theme.colors.border};

  @media (max-width: 480px) {
    padding: ${p => p.theme.spacing.md} ${p => p.theme.spacing.sm};
  }
`;

const ActionHint = styled.span`
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};

  @media (max-width: 480px) {
    display: none;
  }
`;

const ButtonGroup = styled.div`
  display: flex;
  gap: ${p => p.theme.spacing.sm};
`;

const LlmInfoBox = styled.div`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.md};
  padding: ${p => p.theme.spacing.md};
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
`;

const LlmModelName = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.terminalGreen};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
`;

const LlmBadge = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  font-size: 11px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  text-transform: uppercase;
  letter-spacing: 0.05em;
  border-radius: ${p => p.theme.borderRadius.sm};
  background: ${p => p.theme.colors.terminalBlue}15;
  color: ${p => p.theme.colors.terminalBlue};
  border: 1px solid ${p => p.theme.colors.terminalBlue}30;
`;

const LlmChangeLink = styled(Link)`
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.terminalBlue};
  text-decoration: none;
  margin-left: auto;
  flex-shrink: 0;

  &:hover {
    text-decoration: underline;
  }
`;

const LoadingContainer = styled.div`
  padding: ${p => p.theme.spacing.xl};
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.md};
`;

const ErrorContainer = styled.div`
  padding: ${p => p.theme.spacing.xl};
  text-align: center;
`;

export default function EditMarketResearchPage() {
  const params = useParams();
  const router = useRouter();
  const researchId = params?.id as string;

  const { data, isLoading, error } = useMarketResearchDetails(researchId);
  const updateMutation = useUpdateMarketResearch();
  const { data: preferences } = useModelPreferences();
  // The system default is computed by the backend for the ACTIVE engine, never restated here.
  const { data: effectiveModels } = useEffectiveModels();
  const { data: models } = useAvailableModels('RESEARCH');

  const researchDefault = effectiveModelFor(effectiveModels, 'RESEARCH');
  const activeModelId = preferences?.research ?? researchDefault;
  const activeModel = models?.find(m => m.id === activeModelId);

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [targetAssets, setTargetAssets] = useState('');
  const [intervalAmount, setIntervalAmount] = useState('7');
  const [intervalUnit, setIntervalUnit] = useState<IntervalUnit>('DAYS');
  const [hasChanges, setHasChanges] = useState(false);

  // Initialize form when data loads
  useEffect(() => {
    if (data?.research) {
      const r = data.research;
      setName(r.name);
      setDescription(r.description || '');
      setTargetAssets(r.targetAssets.join(', '));
      setIntervalAmount(String(r.interval.amount));
      setIntervalUnit(r.interval.unit);
      setHasChanges(false);
    }
  }, [data?.research]);

  // Track changes
  useEffect(() => {
    if (!data?.research) return;
    const r = data.research;
    const changed =
      name !== r.name ||
      description !== (r.description || '') ||
      targetAssets !== r.targetAssets.join(', ') ||
      intervalAmount !== String(r.interval.amount) ||
      intervalUnit !== r.interval.unit;
    setHasChanges(changed);
  }, [name, description, targetAssets, intervalAmount, intervalUnit, data?.research]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!hasChanges) return;

    try {
      await updateMutation.mutateAsync({
        id: researchId,
        data: {
          name: name.trim(),
          description: description.trim() || undefined,
          targetAssets: targetAssets.split(',').map(s => s.trim()).filter(Boolean),
          interval: {
            amount: parseInt(intervalAmount, 10),
            unit: intervalUnit,
          },
        },
      });
      router.push(`/research/${researchId}`);
    } catch {
      // Error toast handled by mutation hook
    }
  };

  const handleCancel = () => {
    if (hasChanges) {
      const confirmed = window.confirm('You have unsaved changes. Are you sure you want to leave?');
      if (!confirmed) return;
    }
    router.push(`/research/${researchId}`);
  };

  const handleReset = () => {
    if (!data?.research) return;
    const confirmed = window.confirm('Are you sure you want to discard all changes?');
    if (confirmed) {
      const r = data.research;
      setName(r.name);
      setDescription(r.description || '');
      setTargetAssets(r.targetAssets.join(', '));
      setIntervalAmount(String(r.interval.amount));
      setIntervalUnit(r.interval.unit);
      setHasChanges(false);
    }
  };

  const isSubmitting = updateMutation.isPending;

  if (isLoading) {
    return (
      <DashboardLayout>
        <PageContainer>
          <LoadingContainer>
            <TerminalSkeleton width="60%" height="32px" />
            <TerminalSkeleton width="40%" height="20px" />
            <TerminalSkeleton width="100%" height="400px" />
          </LoadingContainer>
        </PageContainer>
      </DashboardLayout>
    );
  }

  if (error || !data) {
    return (
      <DashboardLayout>
        <PageContainer>
          <ErrorContainer>
            <TerminalText variant="error">
              {error ? 'Failed to load market research' : 'Market research not found'}
            </TerminalText>
            <TerminalButton onClick={() => router.push('/research')} style={{ marginTop: '16px' }}>
              Back to Research
            </TerminalButton>
          </ErrorContainer>
        </PageContainer>
      </DashboardLayout>
    );
  }

  return (
    <DashboardLayout>
      <PageContainer>
        <PageHeader>
          <SectionTitle $size="lg" $color="cyan">Edit Market Research</SectionTitle>
          <Subtitle>
            Update your market research configuration. Changes will take effect on the next scheduled execution.
          </Subtitle>
        </PageHeader>

        <FormCard onSubmit={handleSubmit}>
          <FormSection $delay={50}>
            <SectionHeader>
              <SectionNumber>1</SectionNumber>
              <SectionLabel>Research Identity</SectionLabel>
            </SectionHeader>
            <FieldGroup>
              <Field>
                <Label>Name</Label>
                <Input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="e.g., Weekly Crypto Momentum Scanner"
                  required
                  disabled={isSubmitting}
                />
              </Field>
            </FieldGroup>
          </FormSection>

          <FormSection $delay={100}>
            <SectionHeader>
              <SectionNumber>2</SectionNumber>
              <SectionLabel>Research Instructions</SectionLabel>
            </SectionHeader>
            <FieldGroup>
              <Field>
                <Label>What should the AI look for?</Label>
                <Textarea
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Describe patterns, events, or conditions the AI should identify."
                  disabled={isSubmitting}
                  rows={6}
                />
                <HelperText>
                  The more specific your instructions, the better the results.
                </HelperText>
              </Field>

              <Field>
                <Label>Target Assets</Label>
                <Input
                  type="text"
                  value={targetAssets}
                  onChange={(e) => setTargetAssets(e.target.value)}
                  placeholder="AAPL, NVDA, BTC:CRYPTO"
                  disabled={isSubmitting}
                />
                <HelperText>
                  Comma-separated tickers — stocks by default; append :CRYPTO for crypto assets (e.g. BTC:CRYPTO). Leave empty to let AI discover relevant assets.
                </HelperText>
              </Field>
            </FieldGroup>
          </FormSection>

          <FormSection $delay={150}>
            <SectionHeader>
              <SectionNumber>3</SectionNumber>
              <SectionLabel>AI Agent LLM</SectionLabel>
            </SectionHeader>
            <FieldGroup>
              <LlmInfoBox>
                <LlmModelName>{activeModel?.name ?? activeModelId}</LlmModelName>
                {activeModel?.supportsWebSearch && <LlmBadge>Web Search</LlmBadge>}
                <LlmChangeLink href="/settings/config">Change</LlmChangeLink>
              </LlmInfoBox>
            </FieldGroup>
          </FormSection>

          <FormSection $delay={200}>
            <SectionHeader>
              <SectionNumber>4</SectionNumber>
              <SectionLabel>Schedule</SectionLabel>
            </SectionHeader>
            <FieldGroup>
              <InlineFields>
                <Field>
                  <Label>Run Every</Label>
                  <Input
                    type="number"
                    value={intervalAmount}
                    onChange={(e) => setIntervalAmount(e.target.value)}
                    min="1"
                    required
                    disabled={isSubmitting}
                  />
                </Field>
                <Field>
                  <Label>Period</Label>
                  <Select
                    value={intervalUnit}
                    onChange={(e) => setIntervalUnit(e.target.value as IntervalUnit)}
                    disabled={isSubmitting}
                  >
                    <option value="DAYS">Days</option>
                    <option value="WEEKS">Weeks</option>
                    <option value="MONTHS">Months</option>
                  </Select>
                </Field>
              </InlineFields>
              <HelperText>
                Research will execute every {intervalAmount} {intervalUnit.toLowerCase()}.
              </HelperText>
            </FieldGroup>
          </FormSection>

          <FormActions>
            <ActionHint>
              {hasChanges ? 'You have unsaved changes' : 'No changes'}
            </ActionHint>
            <ButtonGroup>
              <TerminalButton
                type="button"
                variant="ghost"
                onClick={handleReset}
                disabled={!hasChanges || isSubmitting}
              >
                Reset
              </TerminalButton>
              <TerminalButton
                type="button"
                variant="secondary"
                onClick={handleCancel}
                disabled={isSubmitting}
              >
                Cancel
              </TerminalButton>
              <TerminalButton
                type="submit"
                disabled={!hasChanges || isSubmitting || !name.trim()}
              >
                {isSubmitting ? 'Saving...' : 'Save Changes'}
              </TerminalButton>
            </ButtonGroup>
          </FormActions>
        </FormCard>
      </PageContainer>
    </DashboardLayout>
  );
}
