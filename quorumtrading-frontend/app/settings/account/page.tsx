'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import styled, { css } from 'styled-components';
import { DashboardLayout } from '../../components/layouts/DashboardLayout';
import { Panel } from '@/app/components/shared/v2';
import { TerminalModal } from '../../components/terminal/TerminalModal';
import { TerminalText } from '../../components/terminal/TerminalText';
import { useDeleteAccount } from '@/app/lib/api/hooks';
import { useUser } from '@/app/hooks/useAuth';

const CONFIRM_PHRASE = 'delete my account';

function providerGlyph(provider: string): string {
  const p = provider.toLowerCase();
  if (p.includes('github')) return 'GH';
  if (p.includes('google')) return 'G';
  if (p.includes('microsoft')) return 'MS';
  if (p.includes('gitlab')) return 'GL';
  if (p.includes('dev')) return '•';
  return provider.slice(0, 2).toUpperCase();
}

function formatJoined(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  const day = String(d.getDate()).padStart(2, '0');
  const mo = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'][d.getMonth()];
  return `${day} ${mo} ${d.getFullYear()}`;
}

export default function AccountSettingsPage() {
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [confirmText, setConfirmText] = useState('');
  const router = useRouter();
  const deleteAccount = useDeleteAccount();
  const { user } = useUser();

  const isConfirmed = confirmText.toLowerCase() === CONFIRM_PHRASE;

  const handleDelete = () => {
    if (!isConfirmed || deleteAccount.isPending) return;
    deleteAccount.mutate(undefined, {
      onSuccess: () => router.push('/?deleted=1'),
    });
  };

  const handleCloseModal = () => {
    setShowDeleteModal(false);
    setConfirmText('');
  };

  return (
    <DashboardLayout>
      <Page>
        <PageHeader>
          <HeaderLeft>
            <H1>Account</H1>
            <Desc>
              Profile is read-only at the API layer — your sign-in provider owns email and display
              name. Two-factor auth and active-session management aren&apos;t yet exposed; the danger
              zone below is the only action available here.
            </Desc>
          </HeaderLeft>
        </PageHeader>

        {user && (
          <Panel title="Profile" noBodyPadding>
            <ProfileRow>
              <ProfileLabel>Email</ProfileLabel>
              <ProfileValue $mono>{user.email}</ProfileValue>
              <ProfileMeta>from sign-in</ProfileMeta>
            </ProfileRow>
            <ProfileRow>
              <ProfileLabel>Display name</ProfileLabel>
              <ProfileValue>{user.displayName || <Dim>—</Dim>}</ProfileValue>
              <ProfileMeta>shown on shared benchmarks</ProfileMeta>
            </ProfileRow>
            <ProfileRow>
              <ProfileLabel>Signed in with</ProfileLabel>
              <ProfileValue>
                {user.providers.length > 0 ? (
                  <Providers>
                    {user.providers.map((p) => (
                      <ProviderChip key={p}>
                        <ProviderGlyph>{providerGlyph(p)}</ProviderGlyph>
                        {p}
                      </ProviderChip>
                    ))}
                  </Providers>
                ) : (
                  <Dim>none</Dim>
                )}
              </ProfileValue>
              <ProfileMeta>OIDC providers</ProfileMeta>
            </ProfileRow>
            <ProfileRow>
              <ProfileLabel>Joined</ProfileLabel>
              <ProfileValue $mono>{formatJoined(user.createdAt)}</ProfileValue>
              <ProfileMeta>
                {user.lastLoginAt ? <>last seen {formatJoined(user.lastLoginAt)}</> : null}
              </ProfileMeta>
            </ProfileRow>
          </Panel>
        )}

        <Panel kind="negative" title="Danger zone" noBodyPadding>
          <DangerRow>
            <DangerLabel>Delete account</DangerLabel>
            <DangerBody>
              Permanently delete your account and everything attached to it. This action is irreversible.
            </DangerBody>
            <DangerActions>
              <DangerBtn type="button" onClick={() => setShowDeleteModal(true)}>
                Delete account
              </DangerBtn>
            </DangerActions>
          </DangerRow>

          <WarningList>
            <WarningTitle>What gets deleted</WarningTitle>
            <ul>
              <li>All trading strategies and their version history.</li>
              <li>All simulations, decisions, and portfolio snapshots.</li>
              <li>All market research configurations and executions.</li>
              <li>Broker account connections (open positions at the broker are not affected — close them yourself first).</li>
              <li>All API tokens.</li>
              <li>Notification history and payment history.</li>
            </ul>
          </WarningList>
        </Panel>
      </Page>

      <TerminalModal
        isOpen={showDeleteModal}
        onClose={handleCloseModal}
        title="// CONFIRM ACCOUNT DELETION"
        width="md"
        footer={
          <DangerBtn
            disabled={!isConfirmed || deleteAccount.isPending}
            onClick={handleDelete}
            $solid={isConfirmed}
          >
            {deleteAccount.isPending ? 'Deleting…' : 'Permanently delete account'}
          </DangerBtn>
        }
      >
        <ModalContent>
          <TerminalText size="sm" variant="muted">
            This will permanently delete your account and all data. This cannot be undone.
          </TerminalText>
          <div>
            <FieldLabel>Type &quot;{CONFIRM_PHRASE}&quot; to confirm</FieldLabel>
            <Input
              type="text"
              value={confirmText}
              onChange={(e) => setConfirmText(e.target.value)}
              placeholder={CONFIRM_PHRASE}
              autoComplete="off"
              spellCheck={false}
            />
          </div>
        </ModalContent>
      </TerminalModal>
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

const ProfileRow = styled.div`
  display: grid;
  grid-template-columns: 180px 1fr 200px;
  align-items: center;
  gap: 14px;
  padding: 14px 18px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};

  &:last-child { border-bottom: none; }

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
    gap: 6px;
    padding: 14px 18px;
  }
`;

const ProfileLabel = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
`;

const ProfileValue = styled.span<{ $mono?: boolean }>`
  font-family: ${p => p.$mono ? p.theme.typography.fontFamily.mono : p.theme.typography.fontFamily.sans};
  font-variant-numeric: ${p => p.$mono ? 'tabular-nums' : 'normal'};
  font-size: ${p => p.$mono ? '12.5px' : '13px'};
  color: ${p => p.theme.colors.fgStrong};
  min-width: 0;
  overflow-wrap: anywhere;
`;

const ProfileMeta = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  letter-spacing: 0.06em;
  color: ${p => p.theme.colors.fgDimmed};
  justify-self: end;

  @media (max-width: 768px) {
    justify-self: start;
  }
`;

const Dim = styled.span`
  color: ${p => p.theme.colors.fgDimmed};
`;

const Providers = styled.span`
  display: inline-flex;
  gap: 6px;
  flex-wrap: wrap;
`;

const ProviderChip = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  background: ${p => p.theme.colors.surfaceElevated};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 2px;
  padding: 2px 8px;
  color: ${p => p.theme.colors.foreground};
`;

const ProviderGlyph = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  letter-spacing: 0.04em;
  color: ${p => p.theme.colors.brand};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
`;

const DangerRow = styled.div`
  display: grid;
  grid-template-columns: 180px 1fr auto;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
    gap: 10px;
  }
`;

const DangerLabel = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 14px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.theme.colors.negative};
`;

const DangerBody = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13.5px;
  color: ${p => p.theme.colors.fgMuted};
  line-height: 1.5;
`;

const DangerActions = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 8px;
  justify-self: end;

  @media (max-width: 768px) {
    justify-self: stretch;
  }
`;

const DangerBtn = styled.button<{ $solid?: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  padding: 8px 14px;
  border-radius: 2px;
  border: 1px solid rgba(255,51,102,0.35);
  background: ${p => p.$solid ? p.theme.colors.negative : 'transparent'};
  color: ${p => p.$solid ? p.theme.colors.background : p.theme.colors.negative};
  cursor: pointer;
  transition: color 120ms ease, border-color 120ms ease, background 120ms ease;

  &:hover:not(:disabled) {
    ${p => p.$solid
      ? css`background: ${p.theme.colors.negative};`
      : css`
        border-color: ${p.theme.colors.negative};
        background: rgba(255,51,102,0.08);
      `}
  }
  &:disabled { opacity: 0.4; cursor: not-allowed; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
`;

const WarningList = styled.div`
  padding: 14px 18px;

  & ul {
    margin: 0;
    padding-left: 18px;
    color: ${p => p.theme.colors.fgMuted};
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 13.5px;
    line-height: 1.7;
  }
  & li {
    margin-bottom: 2px;
  }
`;

const WarningTitle = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
  margin-bottom: 8px;
`;

const ModalContent = styled.div`
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

const FieldLabel = styled.label`
  display: block;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
  margin-bottom: 6px;
`;

const Input = styled.input`
  width: 100%;
  padding: 8px 12px;
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 2px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 14px;
  color: ${p => p.theme.colors.foreground};
  transition: border-color 120ms ease;

  &:focus { outline: none; border-color: ${p => p.theme.colors.negative}; }
  &::placeholder { color: ${p => p.theme.colors.fgDimmed}; }
`;
