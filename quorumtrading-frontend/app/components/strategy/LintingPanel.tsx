'use client';

import { useMemo } from 'react';
import styled from 'styled-components';
import { TerminalText } from '../terminal/TerminalText';
import { boxGlow } from '@/app/styles/theme/effects';
import type { LintDiagnostic } from '@/app/lib/types/schema';

interface LintingPanelProps {
  diagnostics: LintDiagnostic[];
  isLoading?: boolean;
  onDiagnosticClick?: (diagnostic: LintDiagnostic) => void;
}

const PanelContainer = styled.div`
  display: flex;
  flex-direction: column;
  background-color: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.md};
  overflow: hidden;
  ${boxGlow}
`;

const PanelHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  border-bottom: 1px solid ${p => p.theme.colors.border};
  background-color: ${p => p.theme.colors.primary}11;
`;

const HeaderTitle = styled(TerminalText)`
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  text-transform: uppercase;
  letter-spacing: ${p => p.theme.typography.letterSpacing.wide};
`;

const CountBadges = styled.div`
  display: flex;
  gap: ${p => p.theme.spacing.sm};
`;

const CountBadge = styled.span<{ $type: 'error' | 'warning' | 'info' }>`
  display: inline-flex;
  align-items: center;
  gap: ${p => p.theme.spacing.xs};
  padding: 2px ${p => p.theme.spacing.sm};
  border-radius: ${p => p.theme.borderRadius.sm};
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  background-color: ${p => {
    switch (p.$type) {
      case 'error': return p.theme.colors.error + '20';
      case 'warning': return p.theme.colors.warning + '20';
      case 'info': return p.theme.colors.primary + '20';
    }
  }};
  color: ${p => {
    switch (p.$type) {
      case 'error': return p.theme.colors.error;
      case 'warning': return p.theme.colors.warning;
      case 'info': return p.theme.colors.primary;
    }
  }};
`;

const DiagnosticsList = styled.div`
  max-height: 200px;
  overflow-y: auto;

  /* Custom scrollbar */
  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-track {
    background: ${p => p.theme.colors.background};
  }
  &::-webkit-scrollbar-thumb {
    background: ${p => p.theme.colors.primary}40;
    border-radius: 3px;
    &:hover {
      background: ${p => p.theme.colors.primary}60;
    }
  }
`;

const DiagnosticItem = styled.button<{ $severity: 'error' | 'warning' | 'info' }>`
  display: flex;
  align-items: flex-start;
  gap: ${p => p.theme.spacing.sm};
  width: 100%;
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  border: none;
  border-bottom: 1px solid ${p => p.theme.colors.border}40;
  background: transparent;
  text-align: left;
  cursor: pointer;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  transition: background-color 0.15s ease;

  &:last-child {
    border-bottom: none;
  }

  &:hover {
    background-color: ${p => p.theme.colors.hover};
  }

  &:focus {
    outline: none;
    background-color: ${p => p.theme.colors.hover};
  }
`;

const SeverityIcon = styled.span<{ $severity: 'error' | 'warning' | 'info' }>`
  flex-shrink: 0;
  font-size: ${p => p.theme.typography.fontSize.sm};
  line-height: 1.4;
  color: ${p => {
    switch (p.$severity) {
      case 'error': return p.theme.colors.error;
      case 'warning': return p.theme.colors.warning;
      case 'info': return p.theme.colors.primary;
    }
  }};
`;

const DiagnosticContent = styled.div`
  flex: 1;
  min-width: 0;
`;

const DiagnosticMessage = styled.div`
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.text};
  line-height: 1.4;
  word-break: break-word;
`;

const DiagnosticLocation = styled.div`
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  margin-top: 2px;
`;

const EmptyState = styled.div`
  padding: ${p => p.theme.spacing.lg};
  text-align: center;
`;

const SuccessIcon = styled.span`
  display: block;
  font-size: 24px;
  margin-bottom: ${p => p.theme.spacing.sm};
`;

const LoadingState = styled.div`
  padding: ${p => p.theme.spacing.md};
  text-align: center;
`;

const LoadingDot = styled.span`
  display: inline-block;
  width: 8px;
  height: 8px;
  margin: 0 2px;
  background-color: ${p => p.theme.colors.primary};
  border-radius: 50%;
  animation: pulse 1.4s ease-in-out infinite both;

  &:nth-child(1) { animation-delay: 0s; }
  &:nth-child(2) { animation-delay: 0.16s; }
  &:nth-child(3) { animation-delay: 0.32s; }

  @keyframes pulse {
    0%, 80%, 100% { transform: scale(0); opacity: 0.5; }
    40% { transform: scale(1); opacity: 1; }
  }
`;

function getSeverityIcon(severity: 'error' | 'warning' | 'info'): string {
  switch (severity) {
    case 'error': return '×';
    case 'warning': return '⚠';
    case 'info': return 'ℹ';
  }
}

export function LintingPanel({
  diagnostics,
  isLoading = false,
  onDiagnosticClick,
}: LintingPanelProps) {
  const counts = useMemo(() => {
    const result = { error: 0, warning: 0, info: 0 };
    diagnostics.forEach(d => {
      result[d.severity]++;
    });
    return result;
  }, [diagnostics]);

  const hasIssues = diagnostics.length > 0;

  return (
    <PanelContainer>
      <PanelHeader>
        <HeaderTitle size="sm">Problems</HeaderTitle>
        {!isLoading && (
          <CountBadges>
            {counts.error > 0 && (
              <CountBadge $type="error">
                {counts.error} error{counts.error !== 1 ? 's' : ''}
              </CountBadge>
            )}
            {counts.warning > 0 && (
              <CountBadge $type="warning">
                {counts.warning} warning{counts.warning !== 1 ? 's' : ''}
              </CountBadge>
            )}
            {counts.info > 0 && (
              <CountBadge $type="info">
                {counts.info} info
              </CountBadge>
            )}
            {!hasIssues && (
              <CountBadge $type="info">
                0 issues
              </CountBadge>
            )}
          </CountBadges>
        )}
      </PanelHeader>

      {isLoading ? (
        <LoadingState>
          <LoadingDot />
          <LoadingDot />
          <LoadingDot />
        </LoadingState>
      ) : hasIssues ? (
        <DiagnosticsList>
          {diagnostics.map((diagnostic, index) => (
            <DiagnosticItem
              key={`${diagnostic.line}-${diagnostic.code}-${index}`}
              $severity={diagnostic.severity}
              onClick={() => onDiagnosticClick?.(diagnostic)}
              type="button"
            >
              <SeverityIcon $severity={diagnostic.severity}>
                {getSeverityIcon(diagnostic.severity)}
              </SeverityIcon>
              <DiagnosticContent>
                <DiagnosticMessage>{diagnostic.message}</DiagnosticMessage>
                <DiagnosticLocation>
                  Line {diagnostic.line}
                  {diagnostic.code && ` [${diagnostic.code}]`}
                </DiagnosticLocation>
              </DiagnosticContent>
            </DiagnosticItem>
          ))}
        </DiagnosticsList>
      ) : (
        <EmptyState>
          <SuccessIcon>✓</SuccessIcon>
          <TerminalText variant="success" size="sm">
            No problems detected
          </TerminalText>
        </EmptyState>
      )}
    </PanelContainer>
  );
}
