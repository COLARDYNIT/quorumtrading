'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import styled, { useTheme } from 'styled-components';
import { DashboardLayout } from '@/app/components/layouts/DashboardLayout';
import { SectionTitle } from '@/app/components/shared/SectionTitle';
import { TerminalButton } from '@/app/components/terminal/TerminalButton';
import { useAuth } from '@/app/hooks/useAuth';
import { apiClient } from '@/app/lib/api/client';
import { fadeInUp } from '@/app/styles/animations';
import toast from 'react-hot-toast';

const PageContainer = styled.div`
  max-width: 720px;
  animation: ${fadeInUp} 0.4s ease-out;
`;

const Form = styled.form`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.lg};
`;

const Card = styled.div`
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  padding: ${p => p.theme.spacing.xl};
`;

const Field = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.xs};
`;

const Label = styled.label`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.text};
  font-weight: 500;
`;

const HelpText = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
`;

const Input = styled.input`
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  color: ${p => p.theme.colors.text};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  transition: border-color 0.2s;

  &:focus {
    outline: none;
    border-color: ${p => p.theme.colors.primary}66;
  }
`;

const Textarea = styled.textarea`
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  color: ${p => p.theme.colors.text};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  resize: vertical;
  min-height: 100px;
  transition: border-color 0.2s;

  &:focus {
    outline: none;
    border-color: ${p => p.theme.colors.primary}66;
  }
`;

const Row = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: ${p => p.theme.spacing.md};

  @media (max-width: 600px) {
    grid-template-columns: 1fr;
  }
`;

const AssetTypeRow = styled.div`
  display: flex;
  gap: ${p => p.theme.spacing.sm};
`;

const AssetTypeButton = styled.button<{ $selected: boolean }>`
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.lg};
  background: ${p => p.$selected ? p.theme.colors.primary + '15' : 'transparent'};
  border: 1px solid ${p => p.$selected ? p.theme.colors.primary + '44' : p.theme.colors.border};
  color: ${p => p.$selected ? p.theme.colors.primary : p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: ${p => p.theme.colors.primary}44;
  }
`;

const SectionLabel = styled.h3`
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  margin: 0;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  text-transform: uppercase;
  letter-spacing: 0.15em;
  font-weight: 500;
`;

const ButtonRow = styled.div`
  display: flex;
  gap: ${p => p.theme.spacing.md};
  justify-content: flex-end;
`;

const ErrorText = styled.div`
  color: ${p => p.theme.colors.error};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
`;

export default function NewChallengePage() {
  const theme = useTheme();
  const router = useRouter();
  const { user } = useAuth();

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [ticker, setTicker] = useState('');
  const [investmentType, setInvestmentType] = useState<'STOCK' | 'CRYPTO'>('STOCK');
  const [initialCash, setInitialCash] = useState('100000');
  const [simStartDate, setSimStartDate] = useState('');
  const [simEndDate, setSimEndDate] = useState('');
  const [partOpen, setPartOpen] = useState('');
  const [partClose, setPartClose] = useState('');
  const [strategiesPublic, setStrategiesPublic] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!name.trim() || !ticker.trim() || !simStartDate || !simEndDate || !partOpen || !partClose) {
      setError('All fields except description are required.');
      return;
    }

    setIsSubmitting(true);
    try {
      const result = await apiClient.challenges.create({
        name: name.trim(),
        description: description.trim() || undefined,
        ticker: ticker.trim().toUpperCase(),
        investmentType,
        initialCash: Number(initialCash),
        simulationStartDate: new Date(simStartDate).toISOString(),
        simulationEndDate: new Date(simEndDate).toISOString(),
        participationOpen: new Date(partOpen).toISOString(),
        participationClose: new Date(partClose).toISOString(),
        strategiesPublic,
      });
      toast.success(`Challenge "${result.name}" created!`);
      router.push(`/challenges/${result.id}`);
    } catch (err: any) {
      setError(err?.message || 'Failed to create challenge');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (user?.role !== 'ADMIN') {
    return (
      <DashboardLayout>
        <PageContainer>
          <SectionTitle>Access Denied</SectionTitle>
          <p style={{ color: theme.colors.textMuted, fontFamily: theme.typography.fontFamily.mono }}>
            Only admins can create challenges.
          </p>
        </PageContainer>
      </DashboardLayout>
    );
  }

  return (
    <DashboardLayout>
      <PageContainer>
        <SectionTitle>New Challenge</SectionTitle>

        <Form onSubmit={handleSubmit}>
          <Card>
            <SectionLabel style={{ marginBottom: theme.spacing.lg }}>Challenge Details</SectionLabel>

            <div style={{ display: 'flex', flexDirection: 'column', gap: theme.spacing.lg }}>
              <Field>
                <Label>Challenge Name</Label>
                <Input
                  value={name}
                  onChange={e => setName(e.target.value)}
                  placeholder="e.g., BTC March Madness"
                />
              </Field>

              <Field>
                <Label>Description (Markdown)</Label>
                <HelpText>Explain the challenge rules, goals, and any special conditions. Shown on the challenge page.</HelpText>
                <Textarea
                  value={description}
                  onChange={e => setDescription(e.target.value)}
                  placeholder="Beat the market with your best BTC strategy. All participants start with $100,000 and are ranked by total portfolio value."
                />
              </Field>

              <Row>
                <Field>
                  <Label>Ticker</Label>
                  <Input
                    value={ticker}
                    onChange={e => setTicker(e.target.value.toUpperCase())}
                    placeholder="e.g., BTC, AAPL, SPY"
                    maxLength={5}
                  />
                </Field>

                <Field>
                  <Label>Asset Type</Label>
                  <AssetTypeRow>
                    <AssetTypeButton
                      type="button"
                      $selected={investmentType === 'STOCK'}
                      onClick={() => setInvestmentType('STOCK')}
                    >
                      Stock
                    </AssetTypeButton>
                    <AssetTypeButton
                      type="button"
                      $selected={investmentType === 'CRYPTO'}
                      onClick={() => setInvestmentType('CRYPTO')}
                    >
                      Crypto
                    </AssetTypeButton>
                  </AssetTypeRow>
                </Field>
              </Row>

              <Field>
                <Label>Initial Cash (USD)</Label>
                <Input
                  type="number"
                  value={initialCash}
                  onChange={e => setInitialCash(e.target.value)}
                  min="1000"
                  step="1000"
                />
              </Field>
            </div>
          </Card>

          <Card>
            <SectionLabel style={{ marginBottom: theme.spacing.lg }}>Simulation Period</SectionLabel>
            <HelpText style={{ marginBottom: theme.spacing.md }}>
              When the trading simulation runs. For backtest challenges, set historical dates (e.g., Feb–Apr 2020).
            </HelpText>

            <Row>
              <Field>
                <Label>Simulation Start</Label>
                <Input
                  type="date"
                  value={simStartDate}
                  onChange={e => setSimStartDate(e.target.value)}
                />
              </Field>
              <Field>
                <Label>Simulation End</Label>
                <Input
                  type="date"
                  value={simEndDate}
                  onChange={e => setSimEndDate(e.target.value)}
                />
              </Field>
            </Row>
          </Card>

          <Card>
            <SectionLabel style={{ marginBottom: theme.spacing.lg }}>Participation Window</SectionLabel>
            <HelpText style={{ marginBottom: theme.spacing.md }}>
              When users can join. For backtest challenges, this is typically a current date range while the simulation period is historical.
            </HelpText>

            <Row>
              <Field>
                <Label>Opens</Label>
                <Input
                  type="date"
                  value={partOpen}
                  onChange={e => setPartOpen(e.target.value)}
                />
              </Field>
              <Field>
                <Label>Closes</Label>
                <Input
                  type="date"
                  value={partClose}
                  onChange={e => setPartClose(e.target.value)}
                />
              </Field>
            </Row>
          </Card>

          <Card>
            <SectionLabel style={{ marginBottom: theme.spacing.lg }}>Visibility</SectionLabel>
            <HelpText style={{ marginBottom: theme.spacing.md }}>
              Open challenges share all strategies after completion. Closed challenges keep strategies private.
            </HelpText>
            <AssetTypeRow>
              <AssetTypeButton type="button" $selected={!strategiesPublic} onClick={() => setStrategiesPublic(false)}>
                Closed
              </AssetTypeButton>
              <AssetTypeButton type="button" $selected={strategiesPublic} onClick={() => setStrategiesPublic(true)}>
                Open
              </AssetTypeButton>
            </AssetTypeRow>
          </Card>

          {error && <ErrorText>{error}</ErrorText>}

          <ButtonRow>
            <TerminalButton type="button" variant="secondary" onClick={() => router.back()}>
              Cancel
            </TerminalButton>
            <TerminalButton type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Creating...' : 'Create Challenge'}
            </TerminalButton>
          </ButtonRow>
        </Form>
      </PageContainer>
    </DashboardLayout>
  );
}
