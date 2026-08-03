'use client';

import styled, { keyframes } from 'styled-components';
import { useEffect, useState, createContext, useContext, useCallback, ReactNode } from 'react';
import { useStandaloneAutoSignIn } from '../hooks/useStandaloneAutoSignIn';

/** Where an expired session goes. `expired=true` is what makes the signin page say so. */
const SIGNIN_EXPIRED_URL = '/auth/signin?expired=true';

// ===== Context for session expired state =====

interface SessionExpiredContextType {
  isSessionExpired: boolean;
  setSessionExpired: () => void;
  resetSessionExpired: () => void;
}

const SessionExpiredContext = createContext<SessionExpiredContextType | undefined>(undefined);

export function useSessionExpired() {
  const context = useContext(SessionExpiredContext);
  if (!context) {
    throw new Error('useSessionExpired must be used within SessionExpiredProvider');
  }
  return context;
}

// Store reference for non-React code (like apiFetch)
let globalSetSessionExpired: (() => void) | null = null;

/**
 * Trigger session expired modal from anywhere (including non-React code).
 * This is called by apiFetch when a 401 is detected.
 */
export function triggerSessionExpired(): void {
  if (typeof window === 'undefined') return;

  // Skip if on auth pages
  const currentPath = window.location.pathname;
  const isAuthPage = currentPath.startsWith('/auth/') || currentPath === '/';
  if (isAuthPage) return;

  // Save current location for post-login redirect
  sessionStorage.setItem('auth_redirect', currentPath + window.location.search);

  // Trigger the modal via the global reference
  if (globalSetSessionExpired) {
    globalSetSessionExpired();
  } else {
    // Fallback: direct redirect if context not available
    window.location.href = SIGNIN_EXPIRED_URL;
  }
}

export function SessionExpiredProvider({ children }: { children: ReactNode }) {
  const [isSessionExpired, setIsSessionExpired] = useState(false);

  const setSessionExpired = useCallback(() => {
    setIsSessionExpired(true);
  }, []);

  const resetSessionExpired = useCallback(() => {
    setIsSessionExpired(false);
  }, []);

  // Register global reference on mount
  useEffect(() => {
    globalSetSessionExpired = setSessionExpired;
    return () => {
      globalSetSessionExpired = null;
    };
  }, [setSessionExpired]);

  return (
    <SessionExpiredContext.Provider value={{ isSessionExpired, setSessionExpired, resetSessionExpired }}>
      {children}
      {isSessionExpired && <ExpiredSessionHandoff />}
    </SessionExpiredContext.Provider>
  );
}

/**
 * What an expired session gets, once we know what kind of instance this is.
 *
 * On a standalone instance the signin page enters as the local owner the moment it loads, so a
 * padlock, an apology and a ten-second countdown announce a problem that fixes itself before the
 * countdown ends — a scare in place of a blink. Redirect straight there instead. The return URL
 * was already saved by `triggerSessionExpired()` above, so the user lands back where they were.
 *
 * Nothing changes anywhere else: an instance that will actually ask for credentials still gets
 * the warning and the countdown.
 *
 * Mounted only while a session is expired, which keeps the instance probe off the public pages
 * that never needed it. Rendering nothing until `resolved` is deliberate: showing the modal
 * first and redirecting once the probe lands would put the scare back, just briefly.
 */
function ExpiredSessionHandoff() {
  const { resolved, enabled: signsItselfBackIn } = useStandaloneAutoSignIn();

  useEffect(() => {
    if (signsItselfBackIn) {
      window.location.href = SIGNIN_EXPIRED_URL;
    }
  }, [signsItselfBackIn]);

  if (!resolved || signsItselfBackIn) return null;
  return <SessionExpiredModal />;
}

// ===== Modal Component =====

const fadeIn = keyframes`
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
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
  max-width: 400px;
  width: 90%;
  z-index: 10000;
  animation: ${slideDown} 0.3s ease-out;
  box-shadow: 0 0 30px ${({ theme }) => theme.colors.warning}40;
`;

const IconWrapper = styled.div`
  text-align: center;
  font-size: 3rem;
  margin-bottom: 1rem;
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
  color: ${({ theme }) => theme.colors.textMuted};
  font-family: ${({ theme }) => theme.typography.fontFamily.mono};
  font-size: 0.8rem;
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

function SessionExpiredModal() {
  const [countdown, setCountdown] = useState(10);

  useEffect(() => {
    const timer = setInterval(() => {
      setCountdown((prev) => {
        if (prev <= 1) {
          clearInterval(timer);
          window.location.href = SIGNIN_EXPIRED_URL;
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, []);

  const handleLogin = () => {
    window.location.href = SIGNIN_EXPIRED_URL;
  };

  return (
    <>
      <Overlay onClick={handleLogin} />
      <ModalContainer role="alertdialog" aria-modal="true" aria-labelledby="session-expired-title">
        <IconWrapper>🔒</IconWrapper>
        <Title id="session-expired-title">Session Expired</Title>
        <Message>
          Your session has expired. Please log in again to continue using QuorumTrading.
        </Message>
        <CountdownText>
          Redirecting to login in {countdown} second{countdown !== 1 ? 's' : ''}...
        </CountdownText>
        <Button onClick={handleLogin}>
          Log In Now
        </Button>
      </ModalContainer>
    </>
  );
}

export default SessionExpiredModal;
