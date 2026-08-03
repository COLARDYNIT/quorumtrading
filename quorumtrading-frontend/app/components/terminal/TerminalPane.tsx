'use client';

import styled from 'styled-components';
import { scanlines, boxGlow, screenCurvature } from '@/app/styles/theme/effects';
import { TerminalText } from './TerminalText';

interface TerminalPaneProps {
  title?: React.ReactNode; // Can be string or JSX for complex titles with badges
  children: React.ReactNode;
  scrollable?: boolean;
  actions?: React.ReactNode;
  className?: string;
  'data-testid'?: string;
}

const PaneContainer = styled.div.withConfig({
  shouldForwardProp: (prop) => prop !== 'scrollable',
})<{ scrollable?: boolean }>`
  display: flex;
  flex-direction: column;
  height: 100%;
  background-color: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.md};
  overflow: hidden;

  ${screenCurvature}
  ${boxGlow}
  ${scanlines}
`;

const PaneHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  border-bottom: 1px solid ${p => p.theme.colors.border};
  background-color: ${p => p.theme.colors.primary}11; // 7% opacity
`;

const PaneTitle = styled(TerminalText)`
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  text-transform: uppercase;
  letter-spacing: ${p => p.theme.typography.letterSpacing.wide};
`;

const PaneActions = styled.div`
  display: flex;
  gap: ${p => p.theme.spacing.sm};
`;

const PaneContent = styled.div.withConfig({
  shouldForwardProp: (prop) => prop !== 'scrollable',
})<{ scrollable?: boolean }>`
  flex: 1;
  padding: ${p => p.theme.spacing.md};
  overflow-y: ${p => p.scrollable ? 'auto' : 'visible'};
  overflow-x: hidden;

  /* Custom scrollbar */
  &::-webkit-scrollbar {
    width: 8px;
  }

  &::-webkit-scrollbar-track {
    background: ${p => p.theme.colors.background};
  }

  &::-webkit-scrollbar-thumb {
    background: ${p => p.theme.colors.secondary};
    border: 1px solid ${p => p.theme.colors.primary};

    &:hover {
      background: ${p => p.theme.colors.primary};
    }
  }
`;

export function TerminalPane({
  title,
  children,
  scrollable = false,
  actions,
  className,
  'data-testid': dataTestId,
}: TerminalPaneProps) {
  return (
    <PaneContainer scrollable={scrollable} className={className} data-testid={dataTestId}>
      {(title || actions) && (
        <PaneHeader>
          {title && (
            typeof title === 'string' ? (
              <PaneTitle size="sm" weight="bold">
                {title}
              </PaneTitle>
            ) : (
              <div style={{ display: 'flex', alignItems: 'center' }}>
                {title}
              </div>
            )
          )}
          {actions && <PaneActions>{actions}</PaneActions>}
        </PaneHeader>
      )}
      <PaneContent scrollable={scrollable}>
        {children}
      </PaneContent>
    </PaneContainer>
  );
}
