'use client';

import { useState, useCallback } from 'react';
import styled from 'styled-components';
import { TerminalModal } from '../terminal/TerminalModal';
import { TerminalButton } from '../terminal/TerminalButton';
import { TerminalInput } from '../terminal/TerminalInput';
import { TerminalText } from '../terminal/TerminalText';
import { useCreateBrokerAccount } from '@/app/lib/api/hooks';
import type {
  BrokerType,
  CreateBrokerAccountRequest,
} from '@/app/lib/types/brokerAccount';

export interface ConnectBrokerModalProps {
  isOpen: boolean;
  onClose: () => void;
}

const FormContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.lg};
`;

const FormSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.md};
`;

const SectionTitle = styled(TerminalText)`
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.terminalBlue};
  text-transform: uppercase;
  letter-spacing: 0.08em;
  padding-bottom: ${p => p.theme.spacing.xs};
  border-bottom: 1px solid ${p => p.theme.colors.border};
`;

const BrokerTypeSelector = styled.div`
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: ${p => p.theme.spacing.sm};

  @media (max-width: 600px) {
    grid-template-columns: 1fr;
  }
`;

const BrokerTypeOption = styled.button<{ $active: boolean; $color: string }>`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: ${p => p.theme.spacing.xs};
  padding: ${p => p.theme.spacing.md};
  background: ${p => p.$active ? `color-mix(in srgb, ${p.$color} 12%, transparent)` : p.theme.colors.surface};
  border: 2px solid ${p => p.$active ? p.$color : p.theme.colors.border};
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    border-color: ${p => p.$color};
    background: color-mix(in srgb, ${p => p.$color} 6%, transparent);
  }
`;

const BrokerTypeLabel = styled(TerminalText)<{ $active: boolean; $color: string }>`
  font-size: ${p => p.theme.typography.fontSize.sm};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.$active ? p.$color : p.theme.colors.text};
`;

const BrokerTypeDescription = styled(TerminalText)`
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  text-align: center;
`;

const CredentialsWarning = styled.div`
  padding: ${p => p.theme.spacing.md};
  border: 1px solid ${p => p.theme.colors.terminalAmber};
  background: ${p => `${p.theme.colors.terminalAmber}10`};
`;

const ButtonGroup = styled.div`
  display: flex;
  gap: ${p => p.theme.spacing.sm};
  justify-content: flex-end;
  padding-top: ${p => p.theme.spacing.md};
  border-top: 1px solid ${p => p.theme.colors.border};
`;

const BROKER_TYPES: Array<{ type: BrokerType; label: string; description: string; color: string }> = [
  {
    type: 'ALPACA_PAPER',
    label: 'Alpaca Paper',
    description: 'Paper trading with real market data',
    color: 'var(--qt-agent-fundamental)',
  },
  {
    type: 'ALPACA_LIVE',
    label: 'Alpaca Live',
    description: 'Real trading with real money',
    color: 'var(--qt-agent-technical)',
  },
];

export function ConnectBrokerModal({ isOpen, onClose }: ConnectBrokerModalProps) {
  const createBrokerAccount = useCreateBrokerAccount();

  const [brokerType, setBrokerType] = useState<BrokerType>('ALPACA_PAPER');
  const [name, setName] = useState('');
  const [apiKey, setApiKey] = useState('');
  const [apiSecret, setApiSecret] = useState('');
  const [liveAcknowledged, setLiveAcknowledged] = useState(false);

  const resetForm = useCallback(() => {
    setBrokerType('ALPACA_PAPER');
    setName('');
    setApiKey('');
    setApiSecret('');
    setLiveAcknowledged(false);
  }, []);

  const handleClose = useCallback(() => {
    if (!createBrokerAccount.isPending) {
      resetForm();
      onClose();
    }
  }, [createBrokerAccount.isPending, resetForm, onClose]);

  const handleSubmit = useCallback(async () => {
    if (!name.trim() || !apiKey.trim() || !apiSecret.trim()) {
      return;
    }

    const request: CreateBrokerAccountRequest = {
      name: name.trim(),
      brokerType,
      credentials: {
        apiKey: apiKey.trim(),
        apiSecret: apiSecret.trim(),
      },
    };

    try {
      await createBrokerAccount.mutateAsync(request);
      handleClose();
    } catch {
      // Error handled by mutation
    }
  }, [name, brokerType, apiKey, apiSecret, createBrokerAccount, handleClose]);

  const isValid = name.trim() !== '' && apiKey.trim() !== '' && apiSecret.trim() !== ''
    && (brokerType !== 'ALPACA_LIVE' || liveAcknowledged);

  return (
    <TerminalModal
      isOpen={isOpen}
      onClose={handleClose}
      title="Connect Broker Account"
      width="lg"
    >
      <FormContainer>
        {/* Account Name */}
        <FormSection>
          <SectionTitle>Account Name</SectionTitle>
          <TerminalInput
            value={name}
            onChange={e => setName(e.target.value)}
            placeholder="My Trading Account"
            disabled={createBrokerAccount.isPending}
          />
        </FormSection>

        {/* Broker Type Selection */}
        <FormSection>
          <SectionTitle>Broker Type</SectionTitle>
          <BrokerTypeSelector>
            {BROKER_TYPES.map(({ type, label, description, color }) => (
              <BrokerTypeOption
                key={type}
                $active={brokerType === type}
                $color={color}
                onClick={() => setBrokerType(type)}
                disabled={createBrokerAccount.isPending}
                type="button"
              >
                <BrokerTypeLabel $active={brokerType === type} $color={color}>
                  {label}
                </BrokerTypeLabel>
                <BrokerTypeDescription>{description}</BrokerTypeDescription>
              </BrokerTypeOption>
            ))}
          </BrokerTypeSelector>
        </FormSection>

        {/* Alpaca Credentials */}
        <FormSection>
          <SectionTitle>Alpaca API Credentials</SectionTitle>

          {brokerType === 'ALPACA_LIVE' && (
            <CredentialsWarning>
              <TerminalText variant="warning" size="sm" weight="bold">
                Live Trading Warning
              </TerminalText>
              <TerminalText variant="muted" size="sm" style={{ marginTop: '8px' }}>
                You are connecting a live trading account. Trades will execute with real money.
                AI-generated trading signals may result in financial losses. Past backtest
                performance does not guarantee future results.
              </TerminalText>
              <label style={{ display: 'flex', alignItems: 'flex-start', gap: '8px', marginTop: '12px', cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  checked={liveAcknowledged}
                  onChange={(e) => setLiveAcknowledged(e.target.checked)}
                  style={{ marginTop: '2px', accentColor: 'var(--qt-agent-fundamental)' }}
                />
                <TerminalText variant="warning" size="xs">
                  I understand AI trading signals may cause financial losses and I accept full responsibility.
                </TerminalText>
              </label>
            </CredentialsWarning>
          )}

          <TerminalInput
            label="API Key"
            value={apiKey}
            onChange={e => setApiKey(e.target.value)}
            placeholder="AKXXXXXXXXXXXXXXXX"
            disabled={createBrokerAccount.isPending}
          />

          <TerminalInput
            label="API Secret"
            type="password"
            value={apiSecret}
            onChange={e => setApiSecret(e.target.value)}
            placeholder="Your API secret"
            disabled={createBrokerAccount.isPending}
          />

          <TerminalText variant="muted" size="xs">
            Get your API keys from the{' '}
            <a
              href="https://alpaca.markets/docs/market-data/getting-started/"
              target="_blank"
              rel="noopener noreferrer"
              style={{ color: 'var(--qt-info)' }}
            >
              Alpaca Dashboard
            </a>
            . Credentials are encrypted and stored securely.
          </TerminalText>
        </FormSection>

        {/* Actions */}
        <ButtonGroup>
          <TerminalButton
            variant="ghost"
            onClick={handleClose}
            disabled={createBrokerAccount.isPending}
          >
            Cancel
          </TerminalButton>
          <TerminalButton
            variant="primary"
            onClick={handleSubmit}
            disabled={!isValid || createBrokerAccount.isPending}
          >
            {createBrokerAccount.isPending ? 'Connecting...' : 'Connect Account'}
          </TerminalButton>
        </ButtonGroup>
      </FormContainer>
    </TerminalModal>
  );
}
