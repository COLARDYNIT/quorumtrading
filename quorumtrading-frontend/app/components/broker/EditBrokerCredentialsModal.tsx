'use client';

import { useState, useCallback } from 'react';
import styled from 'styled-components';
import { TerminalModal } from '../terminal/TerminalModal';
import { TerminalButton } from '../terminal/TerminalButton';
import { TerminalInput } from '../terminal/TerminalInput';
import { TerminalText } from '../terminal/TerminalText';
import { useUpdateBrokerAccountCredentials } from '@/app/lib/api/hooks';
import type { BrokerAccount } from '@/app/lib/types/brokerAccount';

export interface EditBrokerCredentialsModalProps {
  isOpen: boolean;
  onClose: () => void;
  account: BrokerAccount;
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

const ButtonGroup = styled.div`
  display: flex;
  gap: ${p => p.theme.spacing.sm};
  justify-content: flex-end;
  padding-top: ${p => p.theme.spacing.md};
  border-top: 1px solid ${p => p.theme.colors.border};
`;

export function EditBrokerCredentialsModal({ isOpen, onClose, account }: EditBrokerCredentialsModalProps) {
  const updateCredentials = useUpdateBrokerAccountCredentials();

  const [name, setName] = useState(account.name);
  const [apiKey, setApiKey] = useState('');
  const [apiSecret, setApiSecret] = useState('');

  const resetForm = useCallback(() => {
    setName(account.name);
    setApiKey('');
    setApiSecret('');
  }, [account.name]);

  const handleClose = useCallback(() => {
    if (!updateCredentials.isPending) {
      resetForm();
      onClose();
    }
  }, [updateCredentials.isPending, resetForm, onClose]);

  const handleSubmit = useCallback(async () => {
    if (!apiKey.trim() || !apiSecret.trim()) {
      return;
    }

    try {
      await updateCredentials.mutateAsync({
        id: account.id,
        data: {
          name: name.trim() !== account.name ? name.trim() : undefined,
          credentials: {
            apiKey: apiKey.trim(),
            apiSecret: apiSecret.trim(),
          },
        },
      });
      handleClose();
    } catch {
      // Error handled by mutation
    }
  }, [account.id, account.name, name, apiKey, apiSecret, updateCredentials, handleClose]);

  const isValid = apiKey.trim() !== '' && apiSecret.trim() !== '';

  return (
    <TerminalModal
      isOpen={isOpen}
      onClose={handleClose}
      title="Edit Broker Credentials"
      width="md"
    >
      <FormContainer>
        <FormSection>
          <SectionTitle>Account Name</SectionTitle>
          <TerminalInput
            value={name}
            onChange={e => setName(e.target.value)}
            placeholder="Account name"
            disabled={updateCredentials.isPending}
          />
        </FormSection>

        <FormSection>
          <SectionTitle>New API Credentials</SectionTitle>

          <TerminalInput
            label="API Key"
            value={apiKey}
            onChange={e => setApiKey(e.target.value)}
            placeholder="AKXXXXXXXXXXXXXXXX"
            disabled={updateCredentials.isPending}
          />

          <TerminalInput
            label="API Secret"
            type="password"
            value={apiSecret}
            onChange={e => setApiSecret(e.target.value)}
            placeholder="Your API secret"
            disabled={updateCredentials.isPending}
          />

          <TerminalText variant="muted" size="xs">
            Enter your new API credentials. For security, existing credentials are never displayed.
            Get your API keys from the{' '}
            <a
              href="https://alpaca.markets/docs/market-data/getting-started/"
              target="_blank"
              rel="noopener noreferrer"
              style={{ color: 'var(--qt-info)' }}
            >
              Alpaca Dashboard
            </a>.
          </TerminalText>
        </FormSection>

        <ButtonGroup>
          <TerminalButton
            variant="ghost"
            onClick={handleClose}
            disabled={updateCredentials.isPending}
          >
            Cancel
          </TerminalButton>
          <TerminalButton
            variant="primary"
            onClick={handleSubmit}
            disabled={!isValid || updateCredentials.isPending}
          >
            {updateCredentials.isPending ? 'Updating...' : 'Update Credentials'}
          </TerminalButton>
        </ButtonGroup>
      </FormContainer>
    </TerminalModal>
  );
}
