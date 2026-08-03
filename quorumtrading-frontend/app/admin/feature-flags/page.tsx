'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import styled from 'styled-components';
import { DashboardLayout } from '@/app/components/layouts/DashboardLayout';
import { Panel, StatusPill } from '@/app/components/shared/v2';
import { alpha } from '@/app/styles/theme/alpha';
import { labelCompact, tagBase, tagIdentifier } from '@/app/styles/labels';
import { useAuth } from '@/app/hooks/useAuth';
import { useFeatureFlags, useUpdateFeatureFlag } from '@/app/lib/api/hooks';
import type { FeatureFlag } from '@/app/lib/types/features';

/* ===========================================================================
   /admin/feature-flags

   The design's one job: **enabled and audience are different things.**
     · the labelled switch = intent      (Enabled / Disabled)
     · the pill            = actual reach (off · on for everyone · on for N people)

   The amber "restricted" pill exists so a flag that is on for two named people
   can never be glanced as "everyone has it" — which is exactly what the first
   version of this page allowed, with the audience buried in an input below a
   green toggle.
   =========================================================================== */

const PageContainer = styled.div`
  padding: ${p => p.theme.spacing.xl};
  max-width: 1120px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;

  @media (max-width: 480px) {
    padding: ${p => p.theme.spacing.sm};
  }
`;

const Head = styled.div`
  padding-bottom: 12px;
  margin-bottom: 16px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  display: flex;
  flex-direction: column;
  gap: 4px;
`;

const Crumb = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
`;

const Title = styled.h1`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: ${p => p.theme.typography.fontSize.xl};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.theme.colors.fgStrong};
  margin: 0;
`;

const Intro = styled.p`
  font-size: 13px;
  line-height: 1.55;
  color: ${p => p.theme.colors.fgMuted};
  margin: 2px 0 0;
  max-width: 92ch;
`;

/* ---------- ledger grid: header and rows share one template --------------- */
const GRID = `
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(0, 0.85fr) 168px 186px 232px;
  gap: 14px;
  align-items: center;

  @media (max-width: 1000px) {
    grid-template-columns: 1fr;
    gap: 8px;
  }
`;

const HeadRow = styled.div`
  ${GRID}
  padding: 7px 18px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};

  span {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 10px;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.fgSubtle};
  }

  @media (max-width: 1000px) {
    display: none;
  }
`;

const Item = styled.div`
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};

  &:last-of-type {
    border-bottom: none;
  }
`;

const Row = styled.div`
  ${GRID}
  padding: 13px 18px;
  transition: background 120ms ease;

  &:hover {
    background: ${p => alpha(p.theme.colors.fgStrong, 0.018)};
  }
`;

const NameCell = styled.div`
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;

  .id {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 13.5px;
    font-weight: ${p => p.theme.typography.fontWeight.medium};
    color: ${p => p.theme.colors.fgStrong};
  }

  .desc {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 12.5px;
    color: ${p => p.theme.colors.fgMuted};
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
`;

const AudienceCellBox = styled.div<{ $kept: boolean }>`
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  align-items: center;
  min-width: 0;

  .everyone {
    font-size: 12.5px;
    color: ${p => p.theme.colors.fgMuted};
  }

  .nobody {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 12px;
    color: ${p => p.theme.colors.fgSubtle};
  }

  .chip {
    opacity: ${p => (p.$kept ? 0.5 : 1)};
  }

  .kept-note {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 10px;
    letter-spacing: 0.06em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.fgSubtle};
  }
`;

/** Holds email addresses — `tagIdentifier` keeps them verbatim, never uppercased. */
const AudienceChip = styled.span<{ $more?: boolean }>`
  ${tagBase}
  ${tagIdentifier}
  max-width: 180px;
  overflow: hidden;
  color: ${p => (p.$more ? p.theme.colors.fgMuted : p.theme.colors.terminalAmber)};
`;

const UpdatedCellBox = styled.div`
  display: flex;
  flex-direction: column;
  gap: 3px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  color: ${p => p.theme.colors.fgDimmed};
  white-space: nowrap;

  .who {
    font-size: 10.5px;
    color: ${p => p.theme.colors.fgSubtle};
  }

  .sync {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 10.5px;
    color: ${p => p.theme.colors.info};
  }

  .sync .dot {
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: ${p => p.theme.colors.info};
    animation: ff-pulse 1.4s ease-in-out infinite;
  }

  @keyframes ff-pulse {
    0%, 100% { opacity: 1; }
    50% { opacity: 0.35; }
  }
`;

/** Dashed border is meaning, not decoration: this row was never touched by an administrator. */
const SeedTag = styled.span`
  ${tagBase}
  ${labelCompact}
  width: max-content;
  border-style: dashed;
  color: ${p => p.theme.colors.fgDimmed};
`;

const Controls = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 10px;
  justify-self: end;

  .saving {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 10.5px;
    color: ${p => p.theme.colors.fgDimmed};
  }

  @media (max-width: 1000px) {
    justify-self: start;
  }
`;

const Spinner = styled.span`
  width: 12px;
  height: 12px;
  border: 1.5px solid ${p => p.theme.colors.fgDimmed};
  border-top-color: transparent;
  border-radius: 50%;
  animation: ff-spin 0.7s linear infinite;
  flex-shrink: 0;

  @keyframes ff-spin {
    to { transform: rotate(360deg); }
  }
`;

/** Labelled switch. The word carries the state; the track is confirmation, not the only signal. */
const Toggle = styled.button<{ $on: boolean }>`
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: transparent;
  border: none;
  padding: 4px 6px;
  cursor: pointer;
  border-radius: 2px;
  transition: 120ms ease;

  &:hover:not(:disabled) {
    background: ${p => alpha(p.theme.colors.fgStrong, 0.03)};
  }

  &:disabled {
    opacity: 0.45;
    cursor: default;
  }

  .tlbl {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: ${p => p.theme.typography.fontSize.labelSm};
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: ${p => (p.$on ? p.theme.colors.textPrimary : p.theme.colors.fgDimmed)};
  }

  .track {
    position: relative;
    display: inline-block;
    width: 36px;
    height: 20px;
    border-radius: 9999px;
    flex-shrink: 0;
    transition: 120ms ease;
    background: ${p => (p.$on ? alpha(p.theme.colors.brand, 0.12) : p.theme.colors.background)};
    border: 1px solid ${p => (p.$on ? p.theme.colors.brand : p.theme.colors.border)};
  }

  .track::after {
    content: '';
    position: absolute;
    top: 2px;
    left: ${p => (p.$on ? '18px' : '2px')};
    width: 14px;
    height: 14px;
    border-radius: 50%;
    transition: 120ms ease;
    background: ${p => (p.$on ? p.theme.colors.brand : p.theme.colors.fgMuted)};
  }
`;

const ActionButton = styled.button<{ $on?: boolean }>`
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 10px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  padding: 4px 9px;
  border-radius: 2px;
  cursor: pointer;
  white-space: nowrap;
  line-height: 1;
  transition: 120ms ease;
  background: ${p => (p.$on ? alpha(p.theme.colors.fgStrong, 0.04) : 'transparent')};
  border: 1px solid ${p => (p.$on ? p.theme.colors.fgMuted : p.theme.colors.border)};
  color: ${p => (p.$on ? p.theme.colors.fgStrong : p.theme.colors.fgMuted)};

  &:hover:not(:disabled) {
    color: ${p => p.theme.colors.fgStrong};
    border-color: ${p => p.theme.colors.fgMuted};
    background: ${p => p.theme.colors.hover};
  }

  &:disabled {
    opacity: 0.45;
    cursor: not-allowed;
  }
`;

const DangerActionButton = styled(ActionButton)`
  color: ${p => p.theme.colors.terminalPink};
  border-color: ${p => alpha(p.theme.colors.terminalPink, 0.35)};

  &:hover:not(:disabled) {
    color: ${p => p.theme.colors.terminalPink};
    border-color: ${p => p.theme.colors.terminalPink};
    background: ${p => alpha(p.theme.colors.terminalPink, 0.08)};
  }
`;

/* ---------- audience editor, expanded under a row ------------------------- */
const Editor = styled.div`
  margin: 0 18px 14px;
  padding: 12px 14px;
  border-radius: 2px;
  background: ${p => alpha(p.theme.colors.fgStrong, 0.015)};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  display: flex;
  flex-direction: column;
  gap: 8px;

  .lbl {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 10.5px;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.fgDimmed};
  }

  .erow {
    display: flex;
    gap: 8px;
    align-items: center;
    flex-wrap: wrap;
  }

  .hint {
    font-size: 12px;
    line-height: 1.45;
    color: ${p => p.theme.colors.fgDimmed};
  }

  .hint code {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11px;
  }
`;

const EmailInput = styled.input`
  flex: 1;
  min-width: 240px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12.5px;
  padding: 7px 10px;
  border-radius: 2px;
  color: ${p => p.theme.colors.textPrimary};
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  outline: none;
  transition: border-color 120ms ease;

  &::placeholder {
    color: ${p => p.theme.colors.fgDimmed};
  }

  &:hover {
    border-color: ${p => p.theme.colors.fgMuted};
  }

  &:focus-visible {
    border-color: ${p => p.theme.colors.focusRing};
  }
`;

const SaveButton = styled.button`
  flex-shrink: 0;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13px;
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  padding: 6px 14px;
  border-radius: 2px;
  cursor: pointer;
  background: transparent;
  border: 1px solid ${p => p.theme.colors.border};
  color: ${p => p.theme.colors.textPrimary};
  transition: 120ms ease;

  &:hover:not(:disabled) {
    border-color: ${p => p.theme.colors.fgMuted};
    color: ${p => p.theme.colors.fgStrong};
  }

  &:disabled {
    color: ${p => p.theme.colors.disabledFg};
    border-color: ${p => p.theme.colors.disabledBg};
    cursor: not-allowed;
  }
`;

/* ---------- per-row save-error strip -------------------------------------- */
const ErrorStrip = styled.div`
  margin: 0 18px 14px;
  padding: 9px 12px;
  border-radius: 2px;
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12.5px;
  flex-wrap: wrap;
  color: ${p => p.theme.colors.terminalPink};
  border: 1px solid ${p => alpha(p.theme.colors.terminalPink, 0.35)};
  background: ${p => alpha(p.theme.colors.terminalPink, 0.05)};

  .dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: ${p => p.theme.colors.terminalPink};
    flex-shrink: 0;
  }

  .msg {
    min-width: 0;
  }

  .msg code {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11.5px;
  }

  .acts {
    margin-left: auto;
    display: inline-flex;
    gap: 6px;
    flex-shrink: 0;
  }
`;

const Foot = styled.div`
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 10px 18px;
  border-top: 1px solid ${p => p.theme.colors.borderSubtle};
  background: ${p => alpha(p.theme.colors.fgStrong, 0.008)};
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
  flex-wrap: wrap;

  .k {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 10px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.fgSubtle};
    flex-shrink: 0;
  }

  code {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11px;
  }
`;

/* ---------- page-level states --------------------------------------------- */
const LoadCap = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 14px 18px 0;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  color: ${p => p.theme.colors.fgDimmed};

  .dot {
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: ${p => p.theme.colors.fgDimmed};
    animation: ff-pulse 1.4s ease-in-out infinite;
  }

  @keyframes ff-pulse {
    0%, 100% { opacity: 1; }
    50% { opacity: 0.35; }
  }
`;

const Skeleton = styled.div`
  padding: 14px 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;

  .row {
    display: grid;
    grid-template-columns: 2fr 1fr 1fr;
    gap: 14px;
  }

  .bar {
    height: 12px;
    border-radius: 2px;
    background: ${p => p.theme.colors.borderSubtle};
    animation: ff-shimmer 1.6s ease-in-out infinite;
  }

  @keyframes ff-shimmer {
    0%, 100% { opacity: 1; }
    50% { opacity: 0.45; }
  }
`;

const EmptyState = styled.div`
  padding: 40px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  text-align: center;

  .ic {
    width: 34px;
    height: 34px;
    border-radius: 50%;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    font-family: ${p => p.theme.typography.fontFamily.mono};
    border: 1px solid ${p => p.theme.colors.border};
    color: ${p => p.theme.colors.fgMuted};
    margin-bottom: 4px;
  }

  .ic.err {
    color: ${p => p.theme.colors.terminalPink};
    border-color: ${p => alpha(p.theme.colors.terminalPink, 0.4)};
  }

  h3 {
    font-size: 15px;
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
    color: ${p => p.theme.colors.fgStrong};
    margin: 0;
  }

  span {
    font-size: 12.5px;
    color: ${p => p.theme.colors.fgMuted};
    max-width: 62ch;
  }

  span.reason {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 12px;
    color: ${p => p.theme.colors.fgDimmed};
  }
`;

/* ========================================================================= */

const INTRO =
  'Runtime switches for whole product areas. Flags live in the database — a change applies across '
  + 'the instance within ~5 s and survives redeploys. Switching a feature off hides its navigation '
  + 'and makes its endpoints refuse, naming the flag.';

/** The comma-separated list an existing `userWithId` strategy targets. */
function targetedEmails(flag: FeatureFlag): string[] {
  const strategy = flag.strategies.find(s => s.name === 'userWithId');
  const raw = strategy?.parameters?.userIds ?? '';
  return raw.split(',').map(e => e.trim()).filter(Boolean);
}

function parseEmails(input: string): string[] {
  return input.split(',').map(e => e.trim()).filter(Boolean);
}

function formatUpdatedAt(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return iso;
  }
  return date.toLocaleString(undefined, {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

/**
 * Effective reach — deliberately a different signal from the switch.
 *
 * Grey off · green everyone · amber "on for N people". The amber case is the whole reason this
 * component exists: a flag can be Enabled and still reach almost nobody.
 */
function EffectivePill({ flag }: { flag: FeatureFlag }) {
  const count = targetedEmails(flag).length;
  if (!flag.enabled) {
    return <StatusPill kind="done">off for everyone</StatusPill>;
  }
  if (count > 0) {
    return (
      <StatusPill kind="restricted">
        on for {count} {count === 1 ? 'person' : 'people'}
      </StatusPill>
    );
  }
  return <StatusPill kind="live">on for everyone</StatusPill>;
}

function AudienceCell({ flag }: { flag: FeatureFlag }) {
  const emails = targetedEmails(flag);

  if (emails.length === 0) {
    return (
      <AudienceCellBox $kept={false}>
        {flag.enabled
          ? <span className="everyone">Everyone</span>
          : <span className="nobody">—</span>}
      </AudienceCellBox>
    );
  }

  const shown = emails.slice(0, 2);
  const rest = emails.slice(2);

  return (
    <AudienceCellBox $kept={!flag.enabled}>
      {shown.map(email => (
        <AudienceChip key={email} className="chip" title={email}>{email}</AudienceChip>
      ))}
      {rest.length > 0 && (
        <AudienceChip className="chip" $more title={rest.join(', ')}>+{rest.length}</AudienceChip>
      )}
      {/* The backend stores the strategy on a disabled flag, so hiding it would misrepresent
          what re-enabling the flag does. */}
      {!flag.enabled && (
        <span
          className="kept-note"
          title="The audience is stored with the flag — it applies again if the flag is re-enabled."
        >
          kept
        </span>
      )}
    </AudienceCellBox>
  );
}

type RowState = 'idle' | 'saving' | 'applied' | 'error';

function rowState(isPending: boolean, isError: boolean, applied: boolean): RowState {
  if (isPending) return 'saving';
  if (isError) return 'error';
  if (applied) return 'applied';
  return 'idle';
}

function updatedDetail(flag: FeatureFlag, state: RowState) {
  if (state === 'applied') {
    return (
      <span className="sync" title="Saved. The instance-wide cache refreshes within ~5 s.">
        <span className="dot" />applied · syncing ≤5 s
      </span>
    );
  }
  if (flag.updatedBy) {
    return <span className="who">by {flag.updatedBy.slice(0, 8)}</span>;
  }
  return (
    <SeedTag title="Seeded from configuration at first boot — never edited by an administrator.">
      seeded · never edited
    </SeedTag>
  );
}

function UpdatedCell({ flag, state }: { flag: FeatureFlag; state: RowState }) {
  return (
    <UpdatedCellBox>
      <span>{formatUpdatedAt(flag.updatedAt)}</span>
      {updatedDetail(flag, state)}
    </UpdatedCellBox>
  );
}

function FlagRow({ flag }: { flag: FeatureFlag }) {
  const update = useUpdateFeatureFlag();
  const stored = targetedEmails(flag).join(', ');

  const [expanded, setExpanded] = useState(false);
  const [emails, setEmails] = useState(stored);
  const [applied, setApplied] = useState(false);
  const appliedTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Re-seed the draft when the server's copy changes under us (our own save landing, or another
  // administrator's). Comparing against the stored value means a draft being typed is never
  // clobbered by an unrelated refetch.
  const [lastStored, setLastStored] = useState(stored);
  if (stored !== lastStored) {
    setLastStored(stored);
    setEmails(stored);
  }

  useEffect(() => () => {
    if (appliedTimer.current) {
      clearTimeout(appliedTimer.current);
    }
  }, []);

  const dirty = emails.trim() !== stored;

  const state = rowState(update.isPending, update.isError, applied);

  const save = (enabled: boolean, emailList: string) => {
    const list = parseEmails(emailList);
    update.mutate(
      { name: flag.name, request: { enabled, enabledForEmails: list.length > 0 ? list : undefined } },
      {
        onSuccess: () => {
          // "applied · syncing" is honest about the propagation delay rather than pretending the
          // change is everywhere the instant the request returns.
          setApplied(true);
          if (appliedTimer.current) {
            clearTimeout(appliedTimer.current);
          }
          appliedTimer.current = setTimeout(() => setApplied(false), 6000);
        },
      }
    );
  };

  return (
    <Item>
      <Row>
        <NameCell>
          <span className="id">{flag.name}</span>
          <span className="desc" title={flag.description}>{flag.description}</span>
        </NameCell>

        <AudienceCell flag={flag} />

        <div><EffectivePill flag={flag} /></div>

        <UpdatedCell flag={flag} state={state} />

        <Controls>
          {state === 'saving' && (
            <>
              <Spinner />
              <span className="saving">saving…</span>
            </>
          )}
          <Toggle
            $on={flag.enabled}
            role="switch"
            aria-checked={flag.enabled}
            aria-label={`${flag.enabled ? 'Disable' : 'Enable'} ${flag.name}`}
            disabled={update.isPending}
            onClick={() => save(!flag.enabled, emails)}
          >
            <span className="tlbl">{flag.enabled ? 'Enabled' : 'Disabled'}</span>
            <span className="track" />
          </Toggle>
          <ActionButton
            $on={expanded}
            aria-expanded={expanded}
            onClick={() => setExpanded(v => !v)}
          >
            Audience
          </ActionButton>
        </Controls>
      </Row>

      {expanded && (
        <Editor>
          <div className="lbl">Audience · comma-separated emails · empty = everyone</div>
          <div className="erow">
            <EmailInput
              value={emails}
              placeholder="Empty — on for everyone"
              aria-label={`Restrict ${flag.name} to these email addresses`}
              onChange={e => setEmails(e.target.value)}
            />
            <SaveButton
              disabled={!dirty || update.isPending}
              title={dirty ? undefined : 'No change to save'}
              onClick={() => save(flag.enabled, emails)}
            >
              Save audience
            </SaveButton>
            {stored.length > 0 && (
              <ActionButton title="Back to everyone" onClick={() => save(flag.enabled, '')}>
                Clear · everyone
              </ActionButton>
            )}
          </div>
          <div className="hint">
            Audience only matters while the flag is on — an off flag is off for everyone.
            Saving builds the <code>userWithId</code> strategy for you.
          </div>
        </Editor>
      )}

      {state === 'error' && (
        <ErrorStrip role="alert">
          <span className="dot" />
          <span className="msg">
            Could not save <code>{flag.name}</code> — <code>{(update.error as Error)?.message ?? 'unknown error'}</code>.
            The stored value is unchanged.
          </span>
          <span className="acts">
            <DangerActionButton onClick={() => save(flag.enabled, emails)}>Retry</DangerActionButton>
            <ActionButton onClick={() => update.reset()}>Dismiss</ActionButton>
          </span>
        </ErrorStrip>
      )}
    </Item>
  );
}

export default function FeatureFlagsAdminPage() {
  const router = useRouter();
  const { user, isLoading: authLoading } = useAuth();

  // Plain role check, matching the backend and the sidebar. A self-hoster reaches this screen
  // because the local bootstrap provisions its owner as an ADMIN, not because this page makes an
  // exception for them — an exception here would have left the sidebar still hiding the entry.
  const mayManage = user?.role === 'ADMIN';

  const { data: flags, isLoading, isError, error } = useFeatureFlags(mayManage);

  if (!authLoading && !mayManage) {
    router.replace('/dashboard');
    return null;
  }
  if (authLoading) return null;

  return (
    <DashboardLayout>
      <PageContainer>
        <Head>
          <Crumb>ADMIN / FEATURE-FLAGS</Crumb>
          <Title>Feature flags</Title>
          <Intro>{INTRO}</Intro>
        </Head>

        <Panel title="Flags" count={isLoading || isError ? '—' : (flags?.length ?? 0)} noBodyPadding>
          {isLoading && (
            <>
              <LoadCap><span className="dot" />Loading…</LoadCap>
              <Skeleton>
                <div className="row"><div className="bar" /><div className="bar" /><div className="bar" /></div>
                <div className="row"><div className="bar" /><div className="bar" /><div className="bar" /></div>
              </Skeleton>
            </>
          )}

          {isError && (
            <EmptyState>
              <span className="ic err">!</span>
              <h3>Could not load feature flags</h3>
              <span className="reason">{(error as Error)?.message ?? 'unknown error'}</span>
              {/* Said explicitly: an empty list and a failed read look identical otherwise, and
                  one of them would wrongly suggest the registry is empty. */}
              <span>
                This is a read failure, not an empty registry — the flags themselves are untouched.
              </span>
            </EmptyState>
          )}

          {!isLoading && !isError && flags && flags.length > 0 && (
            <>
              <HeadRow>
                <span>Flag</span>
                <span>Audience</span>
                <span>Effective</span>
                <span>Updated</span>
                <span />
              </HeadRow>
              {flags.map(flag => <FlagRow key={flag.name} flag={flag} />)}
              <Foot>
                <span className="k">Propagation</span>
                <span>
                  Changes apply across the instance within <code>~5 s</code> — this page shows the
                  saved value immediately; other open tabs and the sidebar can lag a moment behind.
                </span>
              </Foot>
            </>
          )}

          {!isLoading && !isError && flags && flags.length === 0 && (
            <EmptyState>
              <span className="ic">—</span>
              <h3>No feature flags are registered.</h3>
              <span>
                Flags come from the code-level registry at build time — this build ships none.
                They are switched here, never created here.
              </span>
            </EmptyState>
          )}
        </Panel>
      </PageContainer>
    </DashboardLayout>
  );
}
