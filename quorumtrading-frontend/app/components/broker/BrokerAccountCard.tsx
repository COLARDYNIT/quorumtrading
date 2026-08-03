'use client';

import styled from 'styled-components';
import { AnimatedCard, CardHeader, CardContent, CardFooter } from '../shared';
import { TerminalText } from '../terminal/TerminalText';
import { TerminalButton } from '../terminal/TerminalButton';
import { TerminalBadge } from '../terminal/TerminalBadge';
import type { BrokerAccount } from '@/app/lib/types/brokerAccount';
import {
  getBrokerTypeLabel,
  getStatusLabel,
  getStatusColor,
  formatMoney,
} from '@/app/lib/types/brokerAccount';

export interface BrokerAccountCardProps {
  account: BrokerAccount;
  delay?: number;
  onEdit?: (account: BrokerAccount) => void;
  onSync?: (id: string) => void;
  onDelete?: (id: string) => void;
  isSyncing?: boolean;
  isDeleting?: boolean;
}

const CardWrapper = styled(AnimatedCard)`
  display: flex;
  flex-direction: column;
`;

const HeaderContent = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
`;

const AccountName = styled.span`
  font-size: ${p => p.theme.typography.fontSize.base};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
`;

const BrokerTypeBadge = styled(TerminalBadge)`
  text-transform: uppercase;
  font-size: ${p => p.theme.typography.fontSize.xs};
  margin-bottom: ${p => p.theme.spacing.md};
`;

const BalanceGrid = styled.div<{ $columns?: number }>`
  display: grid;
  grid-template-columns: repeat(${p => p.$columns ?? 3}, 1fr);
  gap: ${p => p.theme.spacing.md};
  margin-bottom: ${p => p.theme.spacing.md};

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
    gap: ${p => p.theme.spacing.sm};
  }
`;

const BalanceItem = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.xs};
`;

const BalanceLabel = styled(TerminalText)`
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  text-transform: uppercase;
  letter-spacing: 0.05em;
`;

const BalanceValue = styled(TerminalText)<{ $highlight?: boolean }>`
  font-size: ${p => p.theme.typography.fontSize.lg};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.$highlight ? p.theme.colors.terminalGreen : p.theme.colors.text};
  font-family: ${p => p.theme.typography.fontFamily.mono};
`;

const MetaInfo = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.xs};
  padding-top: ${p => p.theme.spacing.md};
  border-top: 1px solid ${p => p.theme.colors.border};
`;

const MetaRow = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
`;

const MetaLabel = styled(TerminalText)`
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
`;

const MetaValue = styled(TerminalText)`
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.text};
`;

const ButtonGroup = styled.div`
  display: flex;
  gap: ${p => p.theme.spacing.sm};
  justify-content: flex-end;
`;

const StatusBadge = styled(TerminalBadge)<{ $status: 'success' | 'error' | 'warning' }>`
  background: ${p => {
    switch (p.$status) {
      case 'success':
        return `${p.theme.colors.success}20`;
      case 'error':
        return `${p.theme.colors.error}20`;
      case 'warning':
        return `${p.theme.colors.warning}20`;
    }
  }};
  color: ${p => {
    switch (p.$status) {
      case 'success':
        return p.theme.colors.success;
      case 'error':
        return p.theme.colors.error;
      case 'warning':
        return p.theme.colors.warning;
    }
  }};
  border-color: ${p => {
    switch (p.$status) {
      case 'success':
        return p.theme.colors.success;
      case 'error':
        return p.theme.colors.error;
      case 'warning':
        return p.theme.colors.warning;
    }
  }};
`;

function formatDate(isoString: string): string {
  const date = new Date(isoString);
  return date.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
}

export function BrokerAccountCard({
  account,
  delay = 0,
  onEdit,
  onSync,
  onDelete,
  isSyncing = false,
  isDeleting = false,
}: BrokerAccountCardProps) {
  const statusColor = getStatusColor(account.status);
  const accentColor = account.brokerType === 'MOCK'
    ? 'var(--qt-info)' // terminalBlue for mock
    : account.brokerType === 'ALPACA_PAPER'
    ? 'var(--qt-agent-fundamental)' // terminalAmber for paper
    : 'var(--qt-agent-technical)'; // terminalGreen for live

  return (
    <CardWrapper $delay={delay} $accentColor={accentColor}>
      <CardHeader $color={accentColor}>
        <HeaderContent>
          <AccountName>{account.name}</AccountName>
          <StatusBadge $status={statusColor}>
            {getStatusLabel(account.status)}
          </StatusBadge>
        </HeaderContent>
      </CardHeader>

      <CardContent>
        <BrokerTypeBadge variant="neutral">
          {getBrokerTypeLabel(account.brokerType)}
        </BrokerTypeBadge>

        {account.brokerType !== 'MOCK' ? (
          <BalanceGrid $columns={4}>
            <BalanceItem>
              <BalanceLabel>Portfolio Value</BalanceLabel>
              <BalanceValue $highlight>
                {account.equity ? formatMoney(account.equity) : '\u2014'}
              </BalanceValue>
            </BalanceItem>
            <BalanceItem>
              <BalanceLabel>Cash</BalanceLabel>
              <BalanceValue>{formatMoney(account.totalBalance)}</BalanceValue>
            </BalanceItem>
            <BalanceItem>
              <BalanceLabel>Positions</BalanceLabel>
              <BalanceValue>
                {account.equity
                  ? formatMoney({
                      amount: account.equity.amount - account.totalBalance.amount,
                      currency: account.equity.currency,
                    })
                  : '\u2014'}
              </BalanceValue>
            </BalanceItem>
            <BalanceItem>
              <BalanceLabel>Reserved</BalanceLabel>
              <BalanceValue>{formatMoney(account.reservedBalance)}</BalanceValue>
            </BalanceItem>
          </BalanceGrid>
        ) : (
          <BalanceGrid>
            <BalanceItem>
              <BalanceLabel>Total Balance</BalanceLabel>
              <BalanceValue>{formatMoney(account.totalBalance)}</BalanceValue>
            </BalanceItem>
            <BalanceItem>
              <BalanceLabel>Available</BalanceLabel>
              <BalanceValue $highlight>{formatMoney(account.availableBalance)}</BalanceValue>
            </BalanceItem>
            <BalanceItem>
              <BalanceLabel>Reserved</BalanceLabel>
              <BalanceValue>{formatMoney(account.reservedBalance)}</BalanceValue>
            </BalanceItem>
          </BalanceGrid>
        )}

        <MetaInfo>
          <MetaRow>
            <MetaLabel>Created</MetaLabel>
            <MetaValue>{formatDate(account.createdAt)}</MetaValue>
          </MetaRow>
          <MetaRow>
            <MetaLabel>Last Updated</MetaLabel>
            <MetaValue>{formatDate(account.updatedAt)}</MetaValue>
          </MetaRow>
          {account.mockConfig && (
            <MetaRow>
              <MetaLabel>Cost Preset</MetaLabel>
              <MetaValue>{account.mockConfig.preset.replace('_', ' ')}</MetaValue>
            </MetaRow>
          )}
        </MetaInfo>
      </CardContent>

      <CardFooter $color={accentColor}>
        <ButtonGroup>
          {account.brokerType !== 'MOCK' && onEdit && (
            <TerminalButton
              size="sm"
              variant="secondary"
              onClick={() => onEdit(account)}
              disabled={isSyncing || isDeleting}
            >
              Edit
            </TerminalButton>
          )}
          {account.brokerType !== 'MOCK' && onSync && (
            <TerminalButton
              size="sm"
              variant="secondary"
              onClick={() => onSync(account.id)}
              disabled={isSyncing || isDeleting}
            >
              {isSyncing ? 'Syncing...' : 'Sync'}
            </TerminalButton>
          )}
          {onDelete && (
            <TerminalButton
              size="sm"
              variant="danger"
              onClick={() => onDelete(account.id)}
              disabled={isSyncing || isDeleting}
            >
              {isDeleting ? 'Deleting...' : 'Delete'}
            </TerminalButton>
          )}
        </ButtonGroup>
      </CardFooter>
    </CardWrapper>
  );
}
