'use client';

import { useState, useEffect } from 'react';
import styled from 'styled-components';
import { fadeInUp } from '@/app/styles/animations';

const CONSENT_KEY = 'qt-cookie-consent';

const Banner = styled.div`
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: ${p => p.theme.zIndex.notification};
  background: ${p => p.theme.colors.surface};
  border-top: 1px solid ${p => p.theme.colors.border};
  padding: ${p => p.theme.spacing.md} ${p => p.theme.spacing.lg};
  display: flex;
  align-items: center;
  justify-content: center;
  gap: ${p => p.theme.spacing.lg};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  animation: ${fadeInUp} 0.3s ease-out both;
  box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.4);

  @media (max-width: 640px) {
    flex-direction: column;
    gap: ${p => p.theme.spacing.sm};
    text-align: center;
    padding: ${p => p.theme.spacing.md};
  }
`;

const Text = styled.span`
  line-height: ${p => p.theme.typography.lineHeight.normal};
`;

const PolicyLink = styled.a`
  color: ${p => p.theme.colors.terminalBlue};
  text-decoration: none;

  &:hover {
    text-decoration: underline;
  }
`;

const AcceptButton = styled.button`
  background: transparent;
  color: ${p => p.theme.colors.terminalGreen};
  border: 1px solid ${p => p.theme.colors.terminalGreen};
  border-radius: ${p => p.theme.borderRadius.sm};
  padding: ${p => p.theme.spacing.xs} ${p => p.theme.spacing.md};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  cursor: pointer;
  white-space: nowrap;
  transition: background ${p => p.theme.transition.fast}, color ${p => p.theme.transition.fast};

  &:hover {
    background: ${p => p.theme.colors.terminalGreen};
    color: ${p => p.theme.colors.background};
  }
`;

export function CookieConsent() {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const consent = localStorage.getItem(CONSENT_KEY);
    if (!consent) {
      setVisible(true);
    }
  }, []);

  const handleAccept = () => {
    localStorage.setItem(CONSENT_KEY, 'accepted');
    setVisible(false);
  };

  if (!visible) return null;

  return (
    <Banner>
      <Text>
        This site uses essential cookies for session authentication only.{' '}
        <PolicyLink href="/privacy">Privacy Policy</PolicyLink>
      </Text>
      <AcceptButton onClick={handleAccept}>Accept</AcceptButton>
    </Banner>
  );
}
