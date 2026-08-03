'use client';

import { useState } from 'react';
import styled, { css } from 'styled-components';
import { DashboardLayout } from '../../components/layouts/DashboardLayout';
import { Panel, StatusPill } from '@/app/components/shared/v2';
import { useApiTokens, useCreateApiToken, useRevokeApiToken } from '@/app/lib/api/hooks';
import type { ApiToken, ApiTokenCreated } from '@/app/lib/types/apiToken';

function formatDate(iso: string | null): string {
  if (!iso) return 'never';
  const d = new Date(iso);
  const day = String(d.getDate()).padStart(2, '0');
  const mo = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'][d.getMonth()];
  const yr = d.getFullYear();
  return `${day} ${mo} ${yr}`;
}

function tokenStatus(t: ApiToken): { label: string; kind: 'dca' | 'failed' | 'pending' } {
  if (t.revoked) return { label: 'revoked', kind: 'failed' };
  if (t.expiresAt && new Date(t.expiresAt) < new Date()) return { label: 'expired', kind: 'pending' };
  return { label: 'active', kind: 'dca' };
}

export default function ApiTokensPage() {
  const [name, setName] = useState('');
  const [expiresInDays, setExpiresInDays] = useState<string>('never');
  const [createdToken, setCreatedToken] = useState<ApiTokenCreated | null>(null);
  const [copied, setCopied] = useState(false);

  const { data: tokens, isLoading } = useApiTokens();
  const createMutation = useCreateApiToken();
  const revokeMutation = useRevokeApiToken();

  const handleCreate = async () => {
    if (!name.trim()) return;
    const data = {
      name: name.trim(),
      expiresInDays: expiresInDays === 'never' ? null : parseInt(expiresInDays, 10),
    };
    try {
      const result = await createMutation.mutateAsync(data);
      setCreatedToken(result);
      setName('');
      setExpiresInDays('never');
    } catch {
      // hook handles toast
    }
  };

  const handleCopy = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // ignore
    }
  };

  const handleRevoke = (tokenId: string) => {
    revokeMutation.mutate(tokenId);
    if (createdToken?.id === tokenId) setCreatedToken(null);
  };

  const list = tokens ?? [];
  const activeCount = list.filter((t) => !t.revoked).length;

  return (
    <DashboardLayout>
      <Page>
        <PageHeader>
          <HeaderLeft>
            <H1>API tokens</H1>
            <Desc>
              Long-lived tokens for programmatic access to the REST API and MCP clients like Claude
              Code. Tokens are shown once at creation and cannot be retrieved later.
            </Desc>
          </HeaderLeft>
        </PageHeader>

        <Panel title="Create token">
          <Form onSubmit={(e) => { e.preventDefault(); handleCreate(); }}>
            <FieldRow>
              <Field $grow>
                <FieldLabel htmlFor="tokenName">Token name</FieldLabel>
                <Input
                  id="tokenName"
                  type="text"
                  placeholder="e.g. Claude Code on laptop"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  maxLength={100}
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="expires">Expires</FieldLabel>
                <Select
                  id="expires"
                  value={expiresInDays}
                  onChange={(e) => setExpiresInDays(e.target.value)}
                >
                  <option value="never">Never</option>
                  <option value="30">30 days</option>
                  <option value="90">90 days</option>
                  <option value="365">1 year</option>
                </Select>
              </Field>
              <PrimaryBtn type="submit" disabled={!name.trim() || createMutation.isPending}>
                {createMutation.isPending ? 'Generating…' : 'Generate token'}
              </PrimaryBtn>
            </FieldRow>

            {createdToken && (
              <RevealBox>
                <RevealLabel>Token generated — copy now</RevealLabel>
                <RevealWarning>This is the only time you&apos;ll see the full value. Store it somewhere safe.</RevealWarning>
                <TokenValueRow>
                  <TokenCode>{createdToken.rawToken}</TokenCode>
                  <RowActBtn type="button" onClick={() => handleCopy(createdToken.rawToken)}>
                    {copied ? 'Copied' : 'Copy'}
                  </RowActBtn>
                </TokenValueRow>
                <Snippet>
                  claude mcp add --transport http quorumtrading https://api.quorumtrading.com/mcp --header &quot;Authorization: Bearer {createdToken.rawToken}&quot;
                </Snippet>
              </RevealBox>
            )}
          </Form>
        </Panel>

        <Panel
          title="Your tokens"
          count={list.length}
          headerActions={
            list.length > 0 ? (
              <HeaderNote>
                <b>{activeCount}</b> active · <b>{list.length - activeCount}</b> revoked
              </HeaderNote>
            ) : null
          }
          noBodyPadding
        >
          {isLoading ? (
            <SkeletonBlock>
              {Array.from({ length: 2 }).map((_, i) => (
                <SkeletonRow key={i}>
                  <SkelBar $w="55%" $h="13px" />
                  <SkelBar $w="80px" $h="12px" />
                  <SkelBar $w="80px" $h="12px" />
                  <SkelBar $w="60px" $h="20px" />
                </SkeletonRow>
              ))}
            </SkeletonBlock>
          ) : list.length === 0 ? (
            <EmptyState>
              <EmptyIcon>↯</EmptyIcon>
              <EmptyTitle>No tokens yet.</EmptyTitle>
              <EmptyBody>
                Issue a token to use the REST API or hook up Claude Code via MCP. The secret is
                shown once at creation — copy it then, you can&apos;t retrieve it later. Rotate by
                revoking and issuing a new one.
              </EmptyBody>
            </EmptyState>
          ) : (
            <Rows>
              <HeaderRowBar>
                <span>Name · prefix</span>
                <span>Created</span>
                <span>Last used</span>
                <span>Status</span>
                <RightAligned>Actions</RightAligned>
              </HeaderRowBar>
              {list.map((token) => (
                <TokenRow
                  key={token.id}
                  token={token}
                  onRevoke={() => handleRevoke(token.id)}
                  isPending={revokeMutation.isPending}
                />
              ))}
            </Rows>
          )}
        </Panel>
      </Page>
    </DashboardLayout>
  );
}

interface TokenRowProps {
  token: ApiToken;
  onRevoke: () => void;
  isPending: boolean;
}

function TokenRow({ token, onRevoke, isPending }: TokenRowProps) {
  const status = tokenStatus(token);
  const isActive = !token.revoked && status.kind === 'dca';
  return (
    <Row $dim={!isActive}>
      <NameCell>
        <NameTop>{token.name}</NameTop>
        <NameSub>
          <Mono>{token.tokenPrefix}…</Mono>
          {token.expiresAt && (
            <>
              <SubSep>·</SubSep>
              <span>expires {formatDate(token.expiresAt)}</span>
            </>
          )}
        </NameSub>
      </NameCell>
      <Cell>{formatDate(token.createdAt)}</Cell>
      <Cell>{token.lastUsedAt ? formatDate(token.lastUsedAt) : <Dim>never</Dim>}</Cell>
      <StatusCell>
        <StatusPill kind={status.kind}>{status.label}</StatusPill>
      </StatusCell>
      <RowActs>
        {isActive ? (
          <RowActBtn $danger type="button" onClick={onRevoke} disabled={isPending}>
            Revoke
          </RowActBtn>
        ) : (
          <Mono>—</Mono>
        )}
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

const HeaderNote = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};

  & b {
    color: ${p => p.theme.colors.foreground};
    font-weight: ${p => p.theme.typography.fontWeight.medium};
  }
`;

const Form = styled.form`
  display: flex;
  flex-direction: column;
  gap: 14px;
`;

const FieldRow = styled.div`
  display: flex;
  align-items: flex-end;
  gap: 12px;
  flex-wrap: wrap;
`;

const Field = styled.div<{ $grow?: boolean }>`
  display: flex;
  flex-direction: column;
  gap: 6px;
  ${p => p.$grow && css`flex: 1; min-width: 220px;`}
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
`;

const Input = styled.input`${inputStyles}`;
const Select = styled.select`${inputStyles}`;

const baseBtnStyles = css`
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

const RevealBox = styled.div`
  padding: 14px 16px;
  background: rgba(0,255,65,0.04);
  border: 1px solid rgba(0,255,65,0.3);
  border-radius: 2px;
  display: flex;
  flex-direction: column;
  gap: 10px;
`;

const RevealLabel = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.brand};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
`;

const RevealWarning = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13.5px;
  color: ${p => p.theme.colors.terminalAmber};
`;

const TokenValueRow = styled.div`
  display: flex;
  gap: 8px;
  align-items: center;
`;

const TokenCode = styled.code`
  flex: 1;
  padding: 8px 10px;
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 2px;
  color: ${p => p.theme.colors.brand};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13px;
  word-break: break-all;
  user-select: all;
`;

const Snippet = styled.div`
  padding: 8px 10px;
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 2px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12.5px;
  color: ${p => p.theme.colors.fgMuted};
  word-break: break-all;
  line-height: 1.5;
`;

const Rows = styled.div`
  display: flex;
  flex-direction: column;
`;

const ROW_COLS = 'minmax(0, 1fr) 110px 110px 110px auto';

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

const Row = styled.div<{ $dim?: boolean }>`
  display: grid;
  grid-template-columns: ${ROW_COLS};
  gap: 14px;
  align-items: center;
  padding: 12px 18px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  font-size: 14px;
  color: ${p => p.theme.colors.foreground};
  transition: background 120ms ease;
  ${p => p.$dim && css`opacity: 0.55;`}

  &:last-child { border-bottom: none; }
  &:hover { background: rgba(255,255,255,0.02); }

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
    gap: 10px;
    padding: 14px 18px;
  }
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

const Mono = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const SubSep = styled.span`
  color: ${p => p.theme.colors.border};
`;

const Cell = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 12.5px;
  color: ${p => p.theme.colors.fgMuted};
`;

const Dim = styled.span`
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
