'use client';

import { useMemo, useState } from 'react';
import styled, { css } from 'styled-components';
import { DashboardLayout } from '../../components/layouts/DashboardLayout';
import { Panel, StatusPill, type StatusPillKind } from '@/app/components/shared/v2';
import { TerminalModal } from '../../components/terminal/TerminalModal';
import { TerminalText } from '../../components/terminal/TerminalText';
import { TerminalButton } from '../../components/terminal/TerminalButton';
import { ConnectBrokerModal, EditBrokerCredentialsModal } from '../../components/broker';
import {
  useBrokerAccounts,
  useDeleteBrokerAccount,
  useSyncBrokerAccount,
} from '@/app/lib/api/hooks';
import type { BrokerAccount } from '@/app/lib/types/brokerAccount';

function brokerKindLabel(kind: BrokerAccount['brokerType']): string {
  if (kind === 'ALPACA_LIVE') return 'Live';
  if (kind === 'ALPACA_PAPER') return 'Paper';
  return 'Mock';
}

function brokerStatusKind(status: BrokerAccount['status']): StatusPillKind {
  if (status === 'ACTIVE') return 'live';
  if (status === 'INVALID_CREDENTIALS') return 'failed';
  return 'pending';
}

function brokerStatusLabel(status: BrokerAccount['status']): string {
  if (status === 'ACTIVE') return 'connected';
  if (status === 'INVALID_CREDENTIALS') return 'auth failed';
  return 'disabled';
}

function formatMoney(value: number | null | undefined): string {
  if (value == null) return '—';
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 0,
  }).format(value);
}

function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  const day = String(d.getDate()).padStart(2, '0');
  const mo = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'][d.getMonth()];
  const yr = d.getFullYear();
  return `${day} ${mo} ${yr}`;
}

export default function BrokerAccountsPage() {
  const { data: accounts, isLoading, error } = useBrokerAccounts();
  const deleteBrokerAccount = useDeleteBrokerAccount();
  const syncBrokerAccount = useSyncBrokerAccount();

  const [isConnectModalOpen, setIsConnectModalOpen] = useState(false);
  const [accountToDelete, setAccountToDelete] = useState<BrokerAccount | null>(null);
  const [accountToEdit, setAccountToEdit] = useState<BrokerAccount | null>(null);
  const [syncingAccountId, setSyncingAccountId] = useState<string | null>(null);

  const alpacaAccounts = useMemo(() => {
    if (!accounts) return [];
    return accounts.filter((a) => a.brokerType !== 'MOCK');
  }, [accounts]);

  const handleSync = async (id: string) => {
    setSyncingAccountId(id);
    try {
      await syncBrokerAccount.mutateAsync(id);
    } finally {
      setSyncingAccountId(null);
    }
  };

  const handleDeleteConfirm = async () => {
    if (!accountToDelete) return;
    try {
      await deleteBrokerAccount.mutateAsync(accountToDelete.id);
      setAccountToDelete(null);
    } catch {
      // mutation handles error
    }
  };

  return (
    <DashboardLayout>
      <Page>
        <PageHeader>
          <HeaderLeft>
            <H1>Broker accounts</H1>
            <Desc>
              Authorize a broker to let paper and live simulations execute orders. We only store the
              masked key suffix shown below — full credentials live in your account at the broker.
            </Desc>
          </HeaderLeft>
          <HeaderRight>
            <PrimaryBtn type="button" onClick={() => setIsConnectModalOpen(true)}>
              <span aria-hidden="true">+</span> Add broker
            </PrimaryBtn>
          </HeaderRight>
        </PageHeader>

        <Panel title="Connected brokers" count={alpacaAccounts.length} noBodyPadding>
          {error ? (
            <EmptyState>
              <EmptyIcon>!</EmptyIcon>
              <EmptyTitle>Couldn&apos;t load broker accounts.</EmptyTitle>
              <EmptyBody>{(error as Error).message}</EmptyBody>
            </EmptyState>
          ) : isLoading ? (
            <SkeletonBlock>
              {Array.from({ length: 2 }).map((_, i) => (
                <SkeletonRow key={i}>
                  <SkelBar $w="32px" $h="32px" />
                  <SkelBar $w="50%" $h="13px" />
                  <SkelBar $w="120px" $h="20px" />
                  <SkelBar $w="60px" $h="20px" />
                </SkeletonRow>
              ))}
            </SkeletonBlock>
          ) : alpacaAccounts.length === 0 ? (
            <EmptyState>
              <EmptyIcon>◇</EmptyIcon>
              <EmptyTitle>No broker connected.</EmptyTitle>
              <EmptyBody>
                Without a broker, you can run mock backtests but not paper or live simulations.
                {' '}Mock accounts are created automatically per session — you only need this if you want
                real market data.
              </EmptyBody>
              <PrimaryBtn type="button" onClick={() => setIsConnectModalOpen(true)} style={{ marginTop: 8 }}>
                <span aria-hidden="true">+</span> Connect Alpaca
              </PrimaryBtn>
            </EmptyState>
          ) : (
            <Rows>
              <HeaderRowBar>
                <span />
                <span>Provider · name</span>
                <span>Available balance</span>
                <span>Status</span>
                <RightAligned>Actions</RightAligned>
              </HeaderRowBar>
              {alpacaAccounts.map((a) => (
                <BrokerRow
                  key={a.id}
                  account={a}
                  isSyncing={syncingAccountId === a.id}
                  onEdit={() => setAccountToEdit(a)}
                  onSync={() => handleSync(a.id)}
                  onDelete={() => setAccountToDelete(a)}
                />
              ))}
            </Rows>
          )}
        </Panel>
      </Page>

      <ConnectBrokerModal
        isOpen={isConnectModalOpen}
        onClose={() => setIsConnectModalOpen(false)}
      />

      {accountToEdit && (
        <EditBrokerCredentialsModal
          isOpen={!!accountToEdit}
          onClose={() => setAccountToEdit(null)}
          account={accountToEdit}
        />
      )}

      <TerminalModal
        isOpen={!!accountToDelete}
        onClose={() => setAccountToDelete(null)}
        title="Disconnect broker account"
        width="sm"
      >
        <ModalContent>
          <WarningBox>
            <TerminalText variant="error" weight="bold">
              This cannot be undone.
            </TerminalText>
          </WarningBox>
          <TerminalText>
            Disconnect <strong>{accountToDelete?.name}</strong>? Any simulations using this account
            will need to be reconfigured.
          </TerminalText>
          <ButtonGroup>
            <TerminalButton
              variant="ghost"
              onClick={() => setAccountToDelete(null)}
              disabled={deleteBrokerAccount.isPending}
            >
              Cancel
            </TerminalButton>
            <TerminalButton
              variant="danger"
              onClick={handleDeleteConfirm}
              disabled={deleteBrokerAccount.isPending}
            >
              {deleteBrokerAccount.isPending ? 'Disconnecting…' : 'Disconnect'}
            </TerminalButton>
          </ButtonGroup>
        </ModalContent>
      </TerminalModal>
    </DashboardLayout>
  );
}

interface BrokerRowProps {
  account: BrokerAccount;
  isSyncing: boolean;
  onEdit: () => void;
  onSync: () => void;
  onDelete: () => void;
}

function BrokerRow({ account, isSyncing, onEdit, onSync, onDelete }: BrokerRowProps) {
  const isLive = account.brokerType === 'ALPACA_LIVE';
  const isPaper = account.brokerType === 'ALPACA_PAPER';
  const kindLabel = brokerKindLabel(account.brokerType);
  return (
    <Row>
      <Glyph $kind={isLive ? 'live' : 'paper'}>AL</Glyph>
      <NameCell>
        <NameTop>{account.name}</NameTop>
        <NameSub>
          <KindTag $kind={isLive ? 'live' : 'paper'}>
            {isLive ? 'real money' : isPaper ? 'paper trading' : 'mock'}
          </KindTag>
          <SubSep>·</SubSep>
          <Mono>{kindLabel}</Mono>
          {account.status === 'INVALID_CREDENTIALS' && (
            <>
              <SubSep>·</SubSep>
              <Warn>Credentials invalid · re-authorize</Warn>
            </>
          )}
        </NameSub>
      </NameCell>
      <BalanceCell>
        <BalanceTop>{formatMoney(account.availableBalance.amount)}</BalanceTop>
        <BalanceSub>
          {account.equity ? `equity ${formatMoney(account.equity.amount)}` : `since ${formatDate(account.createdAt)}`}
        </BalanceSub>
      </BalanceCell>
      <StatusCell>
        <StatusPill kind={brokerStatusKind(account.status)}>
          {brokerStatusLabel(account.status)}
        </StatusPill>
      </StatusCell>
      <RowActs>
        <RowActBtn type="button" onClick={onSync} disabled={isSyncing}>
          {isSyncing ? 'Syncing…' : 'Sync'}
        </RowActBtn>
        <RowActBtn type="button" onClick={onEdit}>
          Edit
        </RowActBtn>
        <RowActBtn $danger type="button" onClick={onDelete}>
          Disconnect
        </RowActBtn>
      </RowActs>
    </Row>
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

const HeaderRight = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 8px;
`;

const baseBtnStyles = css`
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
  display: inline-flex;
  align-items: center;
  gap: 4px;

  &:disabled { opacity: 0.4; cursor: not-allowed; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
`;

const PrimaryBtn = styled.button`
  ${baseBtnStyles}
  color: ${p => p.theme.colors.brand};
  border-color: rgba(0,255,65,0.4);
  &:hover {
    color: ${p => p.theme.colors.brandHover};
    border-color: ${p => p.theme.colors.brand};
    background: rgba(0,255,65,0.08);
  }
`;

const Rows = styled.div`
  display: flex;
  flex-direction: column;
`;

const ROW_COLS = '40px minmax(0, 1fr) 180px 140px auto';

const HeaderRowBar = styled.div`
  display: grid;
  grid-template-columns: ${ROW_COLS};
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

  @media (max-width: 768px) {
    display: none;
  }
`;

const RightAligned = styled.span`
  text-align: right;
`;

const Row = styled.div`
  display: grid;
  grid-template-columns: ${ROW_COLS};
  gap: 14px;
  align-items: center;
  padding: 12px 18px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  font-size: 14px;
  color: ${p => p.theme.colors.foreground};
  transition: background 120ms ease;

  &:last-child { border-bottom: none; }
  &:hover { background: rgba(255,255,255,0.02); }

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
    gap: 10px;
    padding: 14px 18px;
  }
`;

const Glyph = styled.span<{ $kind: 'live' | 'paper' }>`
  width: 32px;
  height: 32px;
  border-radius: 2px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: ${p => p.theme.colors.surfaceElevated};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  letter-spacing: 0.04em;

  ${p => p.$kind === 'live' && css`
    color: ${p.theme.colors.brand};
    border-color: rgba(0,255,65,0.4);
  `}
  ${p => p.$kind === 'paper' && css`
    color: ${p.theme.colors.terminalAmber};
    border-color: rgba(255,176,0,0.4);
  `}
`;

const NameCell = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
`;

const NameTop = styled.span`
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const NameSub = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: ${p => p.theme.colors.fgMuted};
  font-size: 12.5px;
  flex-wrap: wrap;
`;

const KindTag = styled.span<{ $kind: 'live' | 'paper' }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: ${p => p.$kind === 'live' ? p.theme.colors.brand : p.theme.colors.terminalAmber};
`;

const Mono = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const SubSep = styled.span`
  color: ${p => p.theme.colors.border};
`;

const Warn = styled.span`
  color: ${p => p.theme.colors.terminalAmber};
  font-size: 12.5px;
`;

const BalanceCell = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
`;

const BalanceTop = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 14px;
  color: ${p => p.theme.colors.fgStrong};
`;

const BalanceSub = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const StatusCell = styled.div`
  display: inline-flex;
  align-items: center;
`;

const RowActs = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  justify-self: end;

  @media (max-width: 768px) {
    justify-self: stretch;
    flex-wrap: wrap;
  }
`;

const RowActBtn = styled.button<{ $danger?: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  padding: 4px 9px;
  border-radius: 2px;
  border: 1px solid ${p => p.$danger ? 'rgba(255,51,102,0.35)' : p.theme.colors.border};
  background: transparent;
  color: ${p => p.$danger ? p.theme.colors.negative : p.theme.colors.fgMuted};
  cursor: pointer;
  white-space: nowrap;
  line-height: 1;
  transition: color 120ms ease, border-color 120ms ease, background 120ms ease;

  &:hover {
    ${p => p.$danger
      ? css`
        border-color: ${p.theme.colors.negative};
        background: rgba(255,51,102,0.08);
      `
      : css`
        color: ${p.theme.colors.fgStrong};
        border-color: ${p.theme.colors.fgMuted};
        background: rgba(255,255,255,0.02);
      `}
  }
  &:disabled { opacity: 0.4; cursor: not-allowed; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
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
`;

const SkeletonRow = styled.div`
  display: grid;
  grid-template-columns: ${ROW_COLS};
  gap: 14px;
  align-items: center;
  padding: 14px 18px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};

  &:last-child { border-bottom: none; }

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
    gap: 8px;
  }
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

const ModalContent = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.md};
`;

const WarningBox = styled.div`
  padding: ${p => p.theme.spacing.md};
  border: 1px solid ${p => p.theme.colors.error};
  background: ${p => p.theme.colors.error}10;
`;

const ButtonGroup = styled.div`
  display: flex;
  gap: ${p => p.theme.spacing.sm};
  justify-content: flex-end;
`;
