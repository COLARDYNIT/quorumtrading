'use client';

import React from 'react';
import Link from 'next/link';
import styled from 'styled-components';
import { fadeInUp } from '@/app/styles/animations';

const Container = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background-color: ${p => p.theme.colors.background};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  padding: ${p => p.theme.spacing.lg};
  text-align: center;
  gap: ${p => p.theme.spacing.md};
  animation: ${fadeInUp} 0.5s ease-out both;
`;

const ErrorCode = styled.h1`
  font-size: 80px;
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  color: ${p => p.theme.colors.terminalPink};
  margin: 0;
  line-height: 1;
  text-shadow: 0 0 2px rgba(255, 51, 102, 0.4);
  letter-spacing: ${p => p.theme.typography.letterSpacing.wide};
`;

const Title = styled.h2`
  font-size: ${p => p.theme.typography.fontSize['2xl']};
  color: ${p => p.theme.colors.info};
  margin: 0;
  text-shadow: 0 0 2px rgba(0, 217, 255, 0.4);
`;

const Message = styled.p`
  font-size: ${p => p.theme.typography.fontSize.base};
  color: ${p => p.theme.colors.textMuted};
  max-width: 480px;
  margin: 0;
  line-height: ${p => p.theme.typography.lineHeight.relaxed};
`;

const Prompt = styled.span`
  color: ${p => p.theme.colors.terminalGreen};
  font-size: ${p => p.theme.typography.fontSize.sm};
`;

const HomeLink = styled(Link)`
  display: inline-block;
  margin-top: ${p => p.theme.spacing.sm};
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.lg};
  background-color: transparent;
  border: 1px solid ${p => p.theme.colors.info};
  color: ${p => p.theme.colors.info};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  text-decoration: none;
  cursor: pointer;
  transition: all 0.2s;
  border-radius: ${p => p.theme.borderRadius.sm};

  &:hover {
    background-color: ${p => p.theme.colors.info};
    color: ${p => p.theme.colors.background};
  }
`;

export default function NotFound() {
  return (
    <Container>
      <ErrorCode>404</ErrorCode>
      <Title>Page Not Found</Title>
      <Message>
        The requested route does not exist. It may have been moved or the URL
        may be incorrect.
      </Message>
      <Prompt>&gt; redirecting to dashboard...</Prompt>
      <HomeLink href="/dashboard">Return to Dashboard</HomeLink>
    </Container>
  );
}
