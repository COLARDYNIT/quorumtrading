'use client';

import styled from 'styled-components';
import { hueColor, labelCompact, tagBase, tagIdentifier, type LabelHue } from '@/app/styles/labels';

interface TagProps {
  children: React.ReactNode;
  /** Categorical grouping colour. Defaults to `dim` — colour only when it earns its place. */
  hue?: LabelHue;
  /**
   * Identifier content — emails, symbols, key prefixes.
   *
   * Renders verbatim instead of uppercased. An uppercased identifier is wrong to copy and
   * hides whether the casing shown is the casing stored.
   */
  id?: boolean;
  /** 10px variant for dense ledgers. */
  compact?: boolean;
  className?: string;
  title?: string;
}

const Chip = styled.span<{ $hue: LabelHue; $id: boolean; $compact: boolean }>`
  ${tagBase}
  color: ${p => hueColor[p.$hue](p.theme.colors)};
  ${p => p.$id && tagIdentifier}
  ${p => p.$compact && labelCompact}
`;

/**
 * Categorical metadata — the DS v0.4.0 `tag` species.
 *
 * This replaces the pile of one-off chip components the Aug 2026 census found (notif-type,
 * scope-chip, aud-chip, act-chip, pub-chip, derived-tag, symchip and friends). If a label
 * describes *what something is* rather than *what a process is doing*, it is a Tag; if it
 * describes process state, it is a StatusPill.
 */
export function Tag({ children, hue = 'dim', id = false, compact = false, className, title }: TagProps) {
  return (
    <Chip $hue={hue} $id={id} $compact={compact} className={className} title={title}>
      {children}
    </Chip>
  );
}
