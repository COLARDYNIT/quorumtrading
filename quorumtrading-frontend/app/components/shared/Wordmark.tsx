'use client';

import Link from 'next/link';
import styled from 'styled-components';
import type { ReactNode } from 'react';

/**
 * Canonical QuorumTrading brand mark — three colored dots (brand green, info
 * cyan, amber) connected by dim spokes. Theme-aware via CSS variables
 * declared in GlobalStyles, so it works on dark + light backgrounds without
 * swapping assets.
 *
 * Use `BrandMark` when you just need the glyph; use `Wordmark` for the full
 * lockup (glyph + "QuorumTrading" with accent on "Trading").
 */

interface BrandMarkProps {
  /** Square size in px. Default 22 matches the auth screens. */
  size?: number;
  className?: string;
  'aria-hidden'?: boolean;
}

const MarkSvg = styled.svg<{ $size: number }>`
  width: ${p => p.$size}px;
  height: ${p => p.$size}px;
  display: inline-block;
  flex-shrink: 0;
`;

export function BrandMark({ size = 22, className, 'aria-hidden': ariaHidden = true }: BrandMarkProps) {
  return (
    <MarkSvg
      $size={size}
      viewBox="0 0 20 20"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.4"
      className={className}
      aria-hidden={ariaHidden}
    >
      <circle cx="10" cy="4" r="2.2" fill="var(--qt-brand)" stroke="none" />
      <circle cx="4" cy="15" r="2.2" fill="var(--qt-info)" stroke="none" />
      <circle cx="16" cy="15" r="2.2" fill="var(--qt-amber)" stroke="none" />
      <path
        d="M10 6.2 L5.6 13M10 6.2 L14.4 13M6.2 15 L13.8 15"
        stroke="var(--qt-fg-dimmed)"
        strokeWidth="0.8"
        strokeLinecap="round"
      />
    </MarkSvg>
  );
}

interface WordmarkProps {
  /** Link target. Defaults to "/". Pass `null` to render without a link wrapper. */
  href?: string | null;
  /** Glyph size in px. Default 22. */
  markSize?: number;
  /** Wordmark font size in px. Default 20. */
  fontSize?: number;
  /** Gap between glyph and text in px. Default 10. */
  gap?: number;
  /** Extra trailing content rendered after "QuorumTrading" (e.g., " · colardyn-it.be"). */
  trailing?: ReactNode;
  className?: string;
  'aria-label'?: string;
}

const rootStyles = `
  display: inline-flex;
  align-items: center;
  text-decoration: none;
  letter-spacing: -0.01em;

  & .accent { color: var(--qt-brand); }
  & .trailing {
    color: var(--qt-fg-dimmed);
    font-weight: 400;
    font-size: 0.78em;
    margin-left: 6px;
  }
`;

const RootSpan = styled.span<{ $gap: number; $fontSize: number }>`
  ${rootStyles}
  gap: ${p => p.$gap}px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: ${p => p.$fontSize}px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.theme.colors.fgStrong};

  & .trailing { font-family: ${p => p.theme.typography.fontFamily.mono}; }
`;

const RootLink = styled(Link)<{ $gap: number; $fontSize: number }>`
  ${rootStyles}
  gap: ${p => p.$gap}px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: ${p => p.$fontSize}px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.theme.colors.fgStrong};

  & .trailing { font-family: ${p => p.theme.typography.fontFamily.mono}; }

  &:hover { color: ${p => p.theme.colors.fgStrong}; }
`;

export function Wordmark({
  href = '/',
  markSize = 22,
  fontSize = 20,
  gap = 10,
  trailing,
  className,
  'aria-label': ariaLabel = 'QuorumTrading home',
}: WordmarkProps = {}) {
  const content = (
    <>
      <BrandMark size={markSize} />
      <span>
        Quorum<span className="accent">Trading</span>
        {trailing && <span className="trailing">{trailing}</span>}
      </span>
    </>
  );

  if (href == null) {
    return (
      <RootSpan $gap={gap} $fontSize={fontSize} className={className} aria-label={ariaLabel}>
        {content}
      </RootSpan>
    );
  }

  return (
    <RootLink href={href} $gap={gap} $fontSize={fontSize} className={className} aria-label={ariaLabel}>
      {content}
    </RootLink>
  );
}
