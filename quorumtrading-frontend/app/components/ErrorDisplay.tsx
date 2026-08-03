'use client';

import styled from 'styled-components';
import { ProblemDetail, getValidationErrors } from '../lib/types/error';

interface ErrorDisplayProps {
  error: Error | ProblemDetail | unknown;
  onRetry?: () => void;
  className?: string;
}

const ErrorContainer = styled.div`
  background: ${p => p.theme.colors.surfaceDark};
  border: 1px solid ${p => p.theme.colors.error};
  border-radius: ${p => p.theme.borderRadius.md};
  padding: ${p => p.theme.spacing.md};
  margin: ${p => p.theme.spacing.sm} 0;
  font-family: ${p => p.theme.typography.fontFamily.mono};
`;

const ErrorHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: ${p => p.theme.spacing.sm};
`;

const ErrorTitle = styled.div`
  color: ${p => p.theme.colors.error};
  font-size: ${p => p.theme.typography.fontSize.lg};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  text-transform: uppercase;
`;

const ErrorStatus = styled.div`
  color: ${p => p.theme.colors.textDimmed};
  font-size: ${p => p.theme.typography.fontSize.sm};
`;

const ErrorDetail = styled.div`
  color: ${p => p.theme.colors.textLight};
  margin-bottom: ${p => p.theme.spacing.sm};
  line-height: ${p => p.theme.typography.lineHeight.relaxed};
`;

const ErrorValidation = styled.div`
  margin-top: ${p => p.theme.spacing.sm};
  padding-top: ${p => p.theme.spacing.sm};
  border-top: 1px solid ${p => p.theme.colors.borderSubtle};
`;

const ValidationTitle = styled.div`
  color: ${p => p.theme.colors.warning};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  margin-bottom: ${p => p.theme.spacing.sm};
`;

const ValidationList = styled.ul`
  list-style: none;
  padding: 0;
  margin: 0;
`;

const ValidationItem = styled.li`
  color: ${p => p.theme.colors.textLight};
  margin: ${p => p.theme.spacing.xs} 0;
  padding-left: ${p => p.theme.spacing.sm};

  &::before {
    content: '\u2192 ';
    color: ${p => p.theme.colors.warning};
    margin-right: ${p => p.theme.spacing.xs};
  }
`;

const ValidationField = styled.span`
  color: ${p => p.theme.colors.info};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
`;

const ValidationMessage = styled.span`
  color: ${p => p.theme.colors.warning};
`;

const RejectedValue = styled.span`
  color: ${p => p.theme.colors.textDimmed};
  font-style: italic;
`;

const ErrorTimestamp = styled.div`
  color: ${p => p.theme.colors.textSubtle};
  font-size: ${p => p.theme.typography.fontSize.xs};
  margin-top: ${p => p.theme.spacing.sm};
  padding-top: ${p => p.theme.spacing.sm};
  border-top: 1px solid ${p => p.theme.colors.borderSubtle};
`;

const RetryButton = styled.button`
  background: ${p => p.theme.colors.error};
  color: ${p => p.theme.colors.textWhite};
  border: none;
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  margin-top: ${p => p.theme.spacing.sm};
  cursor: pointer;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  text-transform: uppercase;
  font-size: ${p => p.theme.typography.fontSize.sm};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  border-radius: ${p => p.theme.borderRadius.sm};
  transition: background ${p => p.theme.transition.fast};

  &:hover {
    background: ${p => p.theme.colors.error}cc;
  }

  &:active {
    background: ${p => p.theme.colors.error}99;
  }
`;

export function ErrorDisplay({ error, onRetry, className = '' }: ErrorDisplayProps) {
  const problemDetail =
    typeof error === 'object' &&
    error !== null &&
    'status' in error
      ? (error as ProblemDetail)
      : null;

  const validationErrors = problemDetail ? getValidationErrors(problemDetail) : [];

  return (
    <ErrorContainer className={className}>
      <ErrorHeader>
        <ErrorTitle>
          {problemDetail?.title || 'Error'}
        </ErrorTitle>
        {problemDetail?.status && (
          <ErrorStatus>
            Status: {problemDetail.status}
          </ErrorStatus>
        )}
      </ErrorHeader>

      {problemDetail?.detail && (
        <ErrorDetail>
          {problemDetail.detail}
        </ErrorDetail>
      )}

      {validationErrors.length > 0 && (
        <ErrorValidation>
          <ValidationTitle>Validation Errors:</ValidationTitle>
          <ValidationList>
            {validationErrors.map((err, index) => (
              <ValidationItem key={index}>
                <ValidationField>{err.field}:</ValidationField>{' '}
                <ValidationMessage>{err.message}</ValidationMessage>
                {err.rejectedValue !== undefined && err.rejectedValue !== null && (
                  <RejectedValue>
                    {' '}(rejected: &quot;{String(err.rejectedValue)}&quot;)
                  </RejectedValue>
                )}
              </ValidationItem>
            ))}
          </ValidationList>
        </ErrorValidation>
      )}

      {problemDetail?.timestamp && (
        <ErrorTimestamp>
          Time: {new Date(problemDetail.timestamp).toLocaleString()}
        </ErrorTimestamp>
      )}

      {onRetry && (
        <RetryButton onClick={onRetry}>
          Retry
        </RetryButton>
      )}
    </ErrorContainer>
  );
}

/**
 * Compact error display for inline usage
 */
const InlineErrorContainer = styled.div`
  display: inline-flex;
  align-items: center;
  gap: ${p => p.theme.spacing.sm};
  color: ${p => p.theme.colors.error};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.base};
`;

const InlineErrorIcon = styled.span`
  font-size: ${p => p.theme.typography.fontSize.lg};
`;

const InlineErrorMessage = styled.span`
  line-height: ${p => p.theme.typography.lineHeight.normal};
`;

export function InlineError({ error }: { error: unknown }) {
  const problemDetail =
    typeof error === 'object' &&
    error !== null &&
    'status' in error
      ? (error as ProblemDetail)
      : null;

  const message = problemDetail?.detail || problemDetail?.title || String(error);

  return (
    <InlineErrorContainer>
      <InlineErrorIcon>⚠</InlineErrorIcon>
      <InlineErrorMessage>{message}</InlineErrorMessage>
    </InlineErrorContainer>
  );
}
