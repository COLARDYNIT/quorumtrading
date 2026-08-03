'use client';

import Link from 'next/link';
import styled from 'styled-components';

interface Props {
  newSimHref: string;
  backfillHref?: string;
  /** Highlight the banner (brand-green border + tint) — used when the strategy has no sims yet. */
  highlighted?: boolean;
}

/**
 * "+ Promote to a sim" dashed-border CTA banner. Sits between strategy.md and
 * the Simulations list so the call-to-action is where the eye already is when
 * scanning runs.
 */
export function PromoteCta({ newSimHref, backfillHref, highlighted }: Props) {
  return (
    <Banner $highlighted={!!highlighted}>
      <Copy>
        <strong>
          {highlighted ? 'Ready to run? Promote to a sim.' : 'Promote to a sim →'}
        </strong>
        <span>
          {highlighted
            ? 'A simulation is one execution against a fixed date range. Mock costs nothing and runs in seconds.'
            : 'Mock, Backtest, or Alpaca paper. The strategy doesn’t change; only the broker does.'}
        </span>
      </Copy>
      <Actions>
        {backfillHref && (
          <GhostLink href={backfillHref}>Backfill data</GhostLink>
        )}
        <SolidLink href={newSimHref}>
          <span>+</span>New simulation
        </SolidLink>
      </Actions>
    </Banner>
  );
}

const Banner = styled.div<{ $highlighted: boolean }>`
  border: 1px dashed ${p => p.$highlighted ? p.theme.colors.brand : p.theme.colors.border};
  background: ${p => p.$highlighted ? 'rgba(0, 255, 65, 0.03)' : 'transparent'};
  border-radius: 4px;
  padding: 14px 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  transition: border-color 120ms ease, background 120ms ease;

  &:hover { border-color: ${p => p.theme.colors.brand}; }

  @media (max-width: 768px) {
    flex-direction: column;
    align-items: flex-start;
  }
`;

const Copy = styled.div`
  flex: 1;
  min-width: 0;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13.5px;
  color: ${p => p.theme.colors.fgMuted};
  line-height: 1.5;

  & > strong {
    display: block;
    color: ${p => p.theme.colors.fgStrong};
    font-weight: ${p => p.theme.typography.fontWeight.medium};
    margin-bottom: 2px;
  }
`;

const Actions = styled.div`
  display: inline-flex;
  gap: 8px;
  flex-shrink: 0;
`;

const SolidLink = styled(Link)`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 13px;
  border-radius: 2px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.theme.colors.fgInverse};
  background: ${p => p.theme.colors.brand};
  border: 1px solid ${p => p.theme.colors.brand};
  text-decoration: none;

  &:hover { background: ${p => p.theme.colors.brandHover}; border-color: ${p => p.theme.colors.brandHover}; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
`;

const GhostLink = styled(Link)`
  display: inline-flex;
  align-items: center;
  padding: 7px 13px;
  border-radius: 2px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13px;
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  color: ${p => p.theme.colors.foreground};
  background: transparent;
  border: 1px solid ${p => p.theme.colors.border};
  text-decoration: none;

  &:hover { background: rgba(255, 255, 255, 0.04); border-color: ${p => p.theme.colors.fgMuted}; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
`;
