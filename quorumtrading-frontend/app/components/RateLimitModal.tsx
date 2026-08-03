'use client';

import styled, { keyframes } from 'styled-components';
import { useEffect, useState, createContext, useContext, useCallback, ReactNode } from 'react';

// ===== Context for rate limit state =====

interface RateLimitContextType {
  isRateLimited: boolean;
  retryAfterSeconds: number;
}

const RateLimitContext = createContext<RateLimitContextType | undefined>(undefined);

export function useRateLimit() {
  const context = useContext(RateLimitContext);
  if (!context) {
    throw new Error('useRateLimit must be used within RateLimitProvider');
  }
  return context;
}

// Store reference for non-React code (like apiFetch)
let globalShowRateLimit: ((retryAfter: number) => void) | null = null;

/**
 * Trigger rate limit modal from anywhere (including non-React code).
 * Called by apiFetch when a 429 is detected.
 *
 * @param retryAfterSeconds Seconds until the rate limit resets (from Retry-After header)
 */
export function triggerRateLimit(retryAfterSeconds: number = 60): void {
  if (typeof window === 'undefined') return;
  if (globalShowRateLimit) {
    globalShowRateLimit(retryAfterSeconds);
  }
}

export function RateLimitProvider({ children }: { children: ReactNode }) {
  const [isRateLimited, setIsRateLimited] = useState(false);
  const [retryAfterSeconds, setRetryAfterSeconds] = useState(60);

  const showRateLimit = useCallback((retryAfter: number) => {
    // Don't re-trigger if already showing
    if (isRateLimited) return;
    setRetryAfterSeconds(retryAfter);
    setIsRateLimited(true);
  }, [isRateLimited]);

  const dismiss = useCallback(() => {
    setIsRateLimited(false);
  }, []);

  // Register global reference on mount
  useEffect(() => {
    globalShowRateLimit = showRateLimit;
    return () => {
      globalShowRateLimit = null;
    };
  }, [showRateLimit]);

  return (
    <RateLimitContext.Provider value={{ isRateLimited, retryAfterSeconds }}>
      {children}
      {isRateLimited && (
        <RateLimitModal retryAfterSeconds={retryAfterSeconds} onDismiss={dismiss} />
      )}
    </RateLimitContext.Provider>
  );
}

// ===== Modal Component =====

const fadeIn = keyframes`
  from { opacity: 0; }
  to { opacity: 1; }
`;

const slideDown = keyframes`
  from {
    opacity: 0;
    transform: translate(-50%, -60%);
  }
  to {
    opacity: 1;
    transform: translate(-50%, -50%);
  }
`;

const Overlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.85);
  z-index: 9999;
  animation: ${fadeIn} 0.2s ease-out;
`;

const ModalContainer = styled.div`
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  background: ${({ theme }) => theme.colors.surface};
  border: 2px solid ${({ theme }) => theme.colors.warning};
  border-radius: 8px;
  padding: 2rem;
  max-width: 420px;
  width: 90%;
  z-index: 10000;
  animation: ${slideDown} 0.3s ease-out;
  box-shadow: 0 0 30px ${({ theme }) => theme.colors.warning}40;
`;

const IconWrapper = styled.div`
  text-align: center;
  margin-bottom: 1rem;

  svg {
    width: 48px;
    height: 48px;
    stroke: ${({ theme }) => theme.colors.warning};
    stroke-width: 1.5;
    fill: none;
  }
`;

const Title = styled.h2`
  color: ${({ theme }) => theme.colors.warning};
  font-family: ${({ theme }) => theme.typography.fontFamily.mono};
  font-size: 1.25rem;
  text-align: center;
  margin: 0 0 1rem 0;
`;

const Message = styled.p`
  color: ${({ theme }) => theme.colors.textMuted};
  font-family: ${({ theme }) => theme.typography.fontFamily.mono};
  font-size: 0.9rem;
  text-align: center;
  margin: 0 0 1.5rem 0;
  line-height: 1.5;
`;

const CountdownText = styled.p`
  color: ${({ theme }) => theme.colors.foreground};
  font-family: ${({ theme }) => theme.typography.fontFamily.mono};
  font-size: 0.85rem;
  text-align: center;
  margin: 0 0 1rem 0;
`;

const Button = styled.button`
  width: 100%;
  padding: 0.75rem 1.5rem;
  background: ${({ theme }) => theme.colors.warning};
  color: ${({ theme }) => theme.colors.background};
  border: none;
  border-radius: 4px;
  font-family: ${({ theme }) => theme.typography.fontFamily.mono};
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    filter: brightness(1.15);
    transform: translateY(-1px);
  }

  &:active {
    transform: translateY(0);
  }
`;

interface RateLimitModalProps {
  retryAfterSeconds: number;
  onDismiss: () => void;
}

function RateLimitModal({ retryAfterSeconds, onDismiss }: RateLimitModalProps) {
  const [countdown, setCountdown] = useState(retryAfterSeconds);

  useEffect(() => {
    const timer = setInterval(() => {
      setCountdown((prev) => {
        if (prev <= 1) {
          clearInterval(timer);
          onDismiss();
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, [onDismiss]);

  return (
    <>
      <Overlay onClick={onDismiss} />
      <ModalContainer role="alertdialog" aria-modal="true" aria-labelledby="rate-limit-title">
        <IconWrapper>
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <circle cx="12" cy="12" r="10" />
            <polyline points="12 6 12 12 16 14" />
          </svg>
        </IconWrapper>
        <Title id="rate-limit-title">Too Many Requests</Title>
        <Message>
          You&apos;ve exceeded the API rate limit. Please wait a moment before trying again.
        </Message>
        <CountdownText>
          Retry available in {countdown} second{countdown !== 1 ? 's' : ''}
        </CountdownText>
        <Button onClick={onDismiss}>
          Dismiss
        </Button>
      </ModalContainer>
    </>
  );
}

export default RateLimitModal;
