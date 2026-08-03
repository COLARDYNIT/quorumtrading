'use client';

import { useState } from 'react';
import Link from 'next/link';
import styled from 'styled-components';
import { Panel } from '@/app/components/shared/v2';
import type { ChallengeResponse } from '@/app/lib/types/challenge';

interface Props {
  yamlContent: string;
  linkedChallenges: ChallengeResponse[];
  strategyId: string;
}

type Tab = 'yaml' | 'linked';

const TabRow = styled.div`
  display: flex;
  gap: 6px;
  align-items: center;
  flex-wrap: wrap;
`;

const TabPill = styled.button<{ $on: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 12px;
  padding: 3px 9px;
  border-radius: 2px;
  border: 1px solid ${p => p.$on ? p.theme.colors.fgMuted : p.theme.colors.border};
  color: ${p => p.$on ? p.theme.colors.fgStrong : p.theme.colors.fgMuted};
  background: ${p => p.$on ? 'rgba(255, 255, 255, 0.04)' : 'transparent'};
  cursor: pointer;
  transition: color 120ms ease, border-color 120ms ease, background 120ms ease;

  &:hover {
    color: ${p => p.theme.colors.foreground};
    border-color: ${p => p.theme.colors.fgMuted};
  }
`;

const CopyButton = styled.button`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 2px;
  border: 1px solid ${p => p.theme.colors.border};
  color: ${p => p.theme.colors.fgMuted};
  background: transparent;
  cursor: pointer;
  transition: color 120ms ease, border-color 120ms ease;

  &:hover {
    color: ${p => p.theme.colors.foreground};
    border-color: ${p => p.theme.colors.fgMuted};
  }
`;

const YamlBlock = styled.pre`
  margin: 0;
  padding: 16px 20px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13px;
  line-height: 1.6;
  color: ${p => p.theme.colors.foreground};
  background: ${p => p.theme.colors.background};
  border: none;
  border-radius: 0;
  overflow: auto;
  max-height: 560px;
  white-space: pre;
  scrollbar-gutter: stable;

  /* Single-column — the panel now lives in the 2fr left column of the v2
     layout, which is narrower than the v1 full-width grid that motivated the
     two-column rule. Keep it linear so the YAML reads top-to-bottom. */

  /* GlobalStyles paints a hover-tint background + border on every <code> /
     <pre>; that produces a per-line band when <code> sits inside a multi-
     column <pre>. Reset both so the YAML reads as a flat block. */
  code {
    background: transparent;
    border: none;
    border-radius: 0;
    padding: 0;
    color: inherit;
    font-family: inherit;
    font-size: inherit;
  }
`;

const LinkedList = styled.div`
  display: flex;
  flex-direction: column;
`;

const LinkedRow = styled(Link)`
  display: grid;
  grid-template-columns: 110px 1fr auto;
  gap: 12px;
  align-items: center;
  padding: 10px 14px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  font-size: 13px;
  text-decoration: none;
  color: inherit;

  &:last-child { border-bottom: none; }
  &:hover { background: rgba(255, 255, 255, 0.02); }

  .kind {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 9.5px;
    letter-spacing: 0.06em;
    text-transform: uppercase;
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
    padding: 2px 7px;
    border-radius: 2px;
    color: ${p => p.theme.colors.terminalAmber};
    border: 1px solid rgba(255, 176, 0, 0.4);
    background: rgba(255, 176, 0, 0.06);
    display: inline-block;
    width: fit-content;
  }
  .name { color: ${p => p.theme.colors.foreground}; }
  .meta {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 12px;
    color: ${p => p.theme.colors.fgDimmed};
    white-space: nowrap;
  }
`;

const EmptyMsg = styled.div`
  padding: 28px 16px;
  text-align: center;
  color: ${p => p.theme.colors.fgMuted};
  font-size: 13px;
`;

/**
 * Full-width Definition panel — switches between the raw YAML source and the
 * list of challenges currently bound to this strategy. Per chat feedback, the
 * panel intentionally spans the full grid width: the YAML is the canonical
 * artifact and benefits from a two-column layout when there's room for it.
 */
export function DefinitionPanel({ yamlContent, linkedChallenges, strategyId }: Props) {
  const [tab, setTab] = useState<Tab>('yaml');
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(yamlContent);
      setCopied(true);
      setTimeout(() => setCopied(false), 1400);
    } catch {
      // Clipboard rejected (insecure context, etc) — silently no-op.
    }
  };

  const tabs: Array<{ key: Tab; label: string; count?: number }> = [
    { key: 'yaml', label: 'Markdown' },
    { key: 'linked', label: 'Linked', count: linkedChallenges.length },
  ];

  const subtitle = tab === 'yaml'
    ? 'what the council reads'
    : `${linkedChallenges.length} linked challenge${linkedChallenges.length === 1 ? '' : 's'}`;

  return (
    <Panel
      title="strategy.md"
      count={subtitle}
      noBodyPadding
      headerActions={
        <TabRow>
          {tabs.map(t => (
            <TabPill
              key={t.key}
              type="button"
              $on={tab === t.key}
              onClick={() => setTab(t.key)}
            >
              {t.label}{t.count != null ? ` · ${t.count}` : ''}
            </TabPill>
          ))}
          <span style={{ width: 4 }} />
          {tab === 'yaml' && (
            <CopyButton type="button" onClick={handleCopy}>
              {copied ? 'Copied' : 'Copy'}
            </CopyButton>
          )}
        </TabRow>
      }
    >
      {tab === 'yaml' && (
        <YamlBlock><code>{yamlContent}</code></YamlBlock>
      )}
      {tab === 'linked' && (
        linkedChallenges.length === 0 ? (
          <EmptyMsg>No challenges link to this strategy yet.</EmptyMsg>
        ) : (
          <LinkedList>
            {linkedChallenges.map(c => (
              <LinkedRow key={c.id} href={`/challenges/${c.slug}?joinWith=${strategyId}`}>
                <span className="kind">challenge</span>
                <span className="name">{c.name} <span style={{ opacity: 0.6 }}>· {c.ticker}</span></span>
                <span className="meta">{c.status.toLowerCase()} · {c.participantCount} entr{c.participantCount === 1 ? 'y' : 'ies'}</span>
              </LinkedRow>
            ))}
          </LinkedList>
        )
      )}
    </Panel>
  );
}
