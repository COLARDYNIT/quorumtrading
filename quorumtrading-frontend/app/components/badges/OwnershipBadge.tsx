'use client';

import styled from 'styled-components';

type OwnershipType = 'mine' | 'shared' | 'public';

interface OwnershipBadgeProps {
  type: OwnershipType;
}

const Badge = styled.span<{ $type: OwnershipType }>`
  display: inline-flex;
  align-items: center;
  padding: 0.125rem 0.5rem;
  border-radius: 2px;
  font-size: 0.75rem;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.5px;

  ${({ $type }) => {
    switch ($type) {
      case 'mine':
        return `
          background: var(--cds-support-success);
          color: var(--cds-text-on-color);
        `;
      case 'shared':
        return `
          background: var(--cds-support-info);
          color: var(--cds-text-on-color);
        `;
      case 'public':
        return `
          background: var(--cds-layer-02);
          color: var(--cds-text-secondary);
          border: 1px solid var(--cds-border-subtle-00);
        `;
    }
  }}
`;

const labels: Record<OwnershipType, string> = {
  mine: 'Yours',
  shared: 'Shared',
  public: 'Public',
};

export function OwnershipBadge({ type }: OwnershipBadgeProps) {
  return <Badge $type={type}>{labels[type]}</Badge>;
}
