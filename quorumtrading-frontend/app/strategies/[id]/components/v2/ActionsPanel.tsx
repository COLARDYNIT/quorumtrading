'use client';

import Link from 'next/link';
import styled from 'styled-components';
import { Panel } from '@/app/components/shared/v2';

interface Props {
  strategyId: string;
  publicVisible: boolean;
  canTogglePublish: boolean;
  hasSims: boolean;
  togglePending: boolean;
  deletePending: boolean;
  onTogglePublish: () => void;
  onDelete: () => void;
  isAdmin?: boolean;
}

/**
 * Right-rail Actions panel. Collects scattered actions (Edit / Publish toggle /
 * Delete / Backfill) into one place so the TopBar is free for navigation.
 * Duplicate-as-new and Compare-versions intentionally absent — no backend
 * endpoints exist for those yet, so we don't fabricate the affordance.
 */
export function ActionsPanel({
  strategyId, publicVisible, canTogglePublish, hasSims,
  togglePending, deletePending,
  onTogglePublish, onDelete, isAdmin,
}: Props) {
  const deleteLabel = hasSims ? 'Delete strategy…' : 'Delete strategy';
  return (
    <Panel title="Actions">
      <List>
        <Item as={Link} href={`/strategies/${strategyId}/edit`}>
          <span className="g">↗</span>Open in editor
        </Item>
        {isAdmin && (
          <Item as={Link} href={`/admin/data-availability?strategyId=${strategyId}`}>
            <span className="g">↓</span>Backfill historical data
          </Item>
        )}
        {canTogglePublish && (
          <ItemButton type="button" onClick={onTogglePublish} disabled={togglePending}>
            <span className="g">{publicVisible ? '↧' : '↥'}</span>
            {togglePending
              ? 'Updating…'
              : publicVisible ? 'Unpublish from library' : 'Publish to library'}
          </ItemButton>
        )}
        <ItemButton type="button" $danger onClick={onDelete} disabled={deletePending}>
          <span className="g">×</span>
          {deletePending ? 'Deleting…' : deleteLabel}
        </ItemButton>
      </List>
    </Panel>
  );
}

const List = styled.div`
  display: flex;
  flex-direction: column;
  padding: 4px;
`;

const itemMixin = `
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 2px;
  font-size: 13px;
  text-decoration: none;
  cursor: pointer;
  background: transparent;
  border: none;
  text-align: left;
  width: 100%;
  transition: background 120ms ease, color 120ms ease;
`;

const Item = styled.a`
  ${itemMixin}
  color: ${p => p.theme.colors.foreground};
  font-family: ${p => p.theme.typography.fontFamily.sans};

  & > .g {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    color: ${p => p.theme.colors.fgMuted};
    width: 14px;
    display: inline-block;
    text-align: center;
  }
  &:hover { background: rgba(255, 255, 255, 0.04); color: ${p => p.theme.colors.fgStrong}; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: -2px; }
`;

const ItemButton = styled.button<{ $danger?: boolean }>`
  ${itemMixin}
  font-family: ${p => p.theme.typography.fontFamily.sans};
  color: ${p => p.$danger ? p.theme.colors.negative : p.theme.colors.foreground};

  & > .g {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    color: ${p => p.$danger ? p.theme.colors.negative : p.theme.colors.fgMuted};
    width: 14px;
    display: inline-block;
    text-align: center;
  }
  &:hover {
    background: ${p => p.$danger ? 'rgba(255, 51, 102, 0.06)' : 'rgba(255, 255, 255, 0.04)'};
    color: ${p => p.$danger ? p.theme.colors.negative : p.theme.colors.fgStrong};
  }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: -2px; }
`;
