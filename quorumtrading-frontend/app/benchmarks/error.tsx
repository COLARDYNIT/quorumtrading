'use client';

import React from 'react';
import styled from 'styled-components';

const ErrorContainer = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background-color: ${p => p.theme.colors.background};
  color: ${p => p.theme.colors.error};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  padding: ${p => p.theme.spacing.lg};
  text-align: center;
  gap: ${p => p.theme.spacing.md};
`;

const ErrorTitle = styled.h1`
  font-size: ${p => p.theme.typography.fontSize.xl};
  color: ${p => p.theme.colors.info};
  margin: 0;
`;

const ErrorMessage = styled.div`
  font-size: ${p => p.theme.typography.fontSize.base};
  color: ${p => p.theme.colors.error};
  max-width: 600px;
`;

const RetryButton = styled.button`
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.lg};
  background-color: transparent;
  border: 1px solid ${p => p.theme.colors.info};
  color: ${p => p.theme.colors.info};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background-color: ${p => p.theme.colors.info};
    color: ${p => p.theme.colors.background};
  }
`;

export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <ErrorContainer>
      <ErrorTitle>Benchmark Error</ErrorTitle>
      <ErrorMessage>{error.message}</ErrorMessage>
      <RetryButton onClick={reset}>Try Again</RetryButton>
    </ErrorContainer>
  );
}
