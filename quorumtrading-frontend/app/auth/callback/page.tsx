'use client';

import { Suspense, useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import styled, { keyframes, css } from 'styled-components';
import { useAuth } from '@/app/hooks/useAuth';
import {
  AuthStage,
  Wordmark,
  AuthPanel,
  TitleRow,
  Spinner,
  Banner,
  OAuthButton,
} from '../_chrome';

const FUN_MESSAGES = [
  'Hallucinating a sudden price drop…',
  'Resisting urge to buy high, sell low…',
  'Convincing the sentiment agent to chill…',
  'Pretending to understand the market…',
  'Arguing with the sentiment agent again…',
  'Promising not to panic sell this time…',
  'Asking the technical agent what "RSI" means…',
  'Blaming yesterday on Mercury retrograde…',
  'Manifesting green candles…',
  'Training on r/wallstreetbets… wait, no…',
  'Updating "days since panic sold" counter…',
  "Reminding AI that FOMO isn't a strategy…",
  'Generating excuses for the next dip…',
  'Checking if stonks only go up…',
  'Consulting magic 8-ball for market direction…',
];

type AuthState = 'loading' | 'success' | 'redirecting' | 'error';

const MIN_LOADING_TIME_MS = 2500;

function getRandomMessageIndex(currentIndex: number): number {
  let newIndex;
  do {
    newIndex = Math.floor(Math.random() * FUN_MESSAGES.length);
  } while (newIndex === currentIndex && FUN_MESSAGES.length > 1);
  return newIndex;
}

function getFirstName(displayName: string | undefined): string {
  if (!displayName) return 'there';
  return displayName.split(' ')[0];
}

function CallbackContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { refreshUser, user } = useAuth();
  const [authState, setAuthState] = useState<AuthState>('loading');
  const [error, setError] = useState<string | null>(null);
  const [messageIndex, setMessageIndex] = useState(0);
  const [userName, setUserName] = useState<string>('');
  const [loadingStartTime] = useState(() => Date.now());
  const [minTimeElapsed, setMinTimeElapsed] = useState(false);

  useEffect(() => {
    const elapsed = Date.now() - loadingStartTime;
    const remaining = Math.max(0, MIN_LOADING_TIME_MS - elapsed);
    const timer = setTimeout(() => setMinTimeElapsed(true), remaining);
    return () => clearTimeout(timer);
  }, [loadingStartTime]);

  useEffect(() => {
    if (authState !== 'loading') return;
    setMessageIndex(Math.floor(Math.random() * FUN_MESSAGES.length));
    const interval = setInterval(() => {
      setMessageIndex(prev => getRandomMessageIndex(prev));
    }, 4000);
    return () => clearInterval(interval);
  }, [authState]);

  useEffect(() => {
    const handleCallback = async () => {
      const success = searchParams.get('success');
      const errorParam = searchParams.get('error');

      if (errorParam) {
        setError(errorParam);
        setAuthState('error');
        return;
      }

      if (success === 'true') {
        const MAX_ATTEMPTS = 4;
        const RETRY_DELAY_MS = 1500;
        for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
          try {
            const userData = await refreshUser();
            if (userData) return;
          } catch {
            /* retry */
          }
          if (attempt < MAX_ATTEMPTS) {
            await new Promise(r => setTimeout(r, RETRY_DELAY_MS));
          }
        }
        setError('Could not verify login. Please try again.');
        setAuthState('error');
      } else {
        setError('Authentication failed. Please try again.');
        setAuthState('error');
      }
    };
    handleCallback();
  }, [searchParams, refreshUser]);

  useEffect(() => {
    if (user && minTimeElapsed && authState === 'loading') {
      setUserName(getFirstName(user.displayName ?? undefined));
      setAuthState('success');
    }
  }, [user, minTimeElapsed, authState]);

  useEffect(() => {
    if (authState === 'success') {
      const t = setTimeout(() => setAuthState('redirecting'), 800);
      return () => clearTimeout(t);
    }
  }, [authState]);

  useEffect(() => {
    if (authState === 'redirecting') {
      const t = setTimeout(() => {
        const savedRedirect = sessionStorage.getItem('auth_redirect');
        if (savedRedirect) {
          sessionStorage.removeItem('auth_redirect');
          window.location.href = savedRedirect;
        } else {
          window.location.href = '/dashboard';
        }
      }, 400);
      return () => clearTimeout(t);
    }
  }, [authState]);

  if (authState === 'error') {
    return (
      <AuthStage>
        <Wordmark />
        <AuthPanel>
          <TitleRow>
            <h1>Sign-in didn&apos;t complete</h1>
            <div className="desc">We couldn&apos;t verify the OIDC handshake.</div>
          </TitleRow>
          {error && <Banner $kind="error">{error}</Banner>}
          <OAuthButton type="button" onClick={() => router.push('/auth/signin')}>
            Try again
          </OAuthButton>
        </AuthPanel>
      </AuthStage>
    );
  }

  if (authState === 'success' || authState === 'redirecting') {
    return (
      <Stage $fadeOut={authState === 'redirecting'}>
        <Wordmark />
        <AuthPanel>
          <SuccessBlock>
            <SuccessIcon>
              <Checkmark viewBox="0 0 24 24">
                <polyline points="4,12 10,18 20,6" />
              </Checkmark>
            </SuccessIcon>
            <SuccessText>
              <h1>Welcome back, {userName}.</h1>
              <div className="desc">Routing you to the dashboard.</div>
            </SuccessText>
          </SuccessBlock>
        </AuthPanel>
      </Stage>
    );
  }

  return (
    <AuthStage>
      <Wordmark />
      <AuthPanel style={{ alignItems: 'center' }}>
        <TitleRow style={{ alignItems: 'center', textAlign: 'center' }}>
          <h1>Signing you in…</h1>
          <div className="desc">Verifying credentials with your provider.</div>
        </TitleRow>
        <SpinnerWrap>
          <Spinner />
        </SpinnerWrap>
        <FunLine key={messageIndex}>{FUN_MESSAGES[messageIndex]}</FunLine>
      </AuthPanel>
    </AuthStage>
  );
}

export default function CallbackPage() {
  return (
    <Suspense
      fallback={
        <AuthStage>
          <Wordmark />
          <AuthPanel style={{ alignItems: 'center' }}>
            <SpinnerWrap><Spinner /></SpinnerWrap>
          </AuthPanel>
        </AuthStage>
      }
    >
      <CallbackContent />
    </Suspense>
  );
}

// ─── styled ──────────────────────────────────────────────────────────

const fadeInOut = keyframes`
  0% { opacity: 0; }
  15% { opacity: 1; }
  85% { opacity: 1; }
  100% { opacity: 0; }
`;

const fadeOut = keyframes`
  to { opacity: 0; }
`;

const scaleIn = keyframes`
  0% { transform: scale(0.85); opacity: 0; }
  60% { transform: scale(1.03); opacity: 1; }
  100% { transform: scale(1); opacity: 1; }
`;

const checkmarkDraw = keyframes`
  0% { stroke-dashoffset: 24; }
  100% { stroke-dashoffset: 0; }
`;

const Stage = styled(AuthStage)<{ $fadeOut?: boolean }>`
  ${p => p.$fadeOut && css`
    animation: ${fadeOut} 0.4s ease-out forwards;
  `}
`;

const SpinnerWrap = styled.div`
  display: flex;
  justify-content: center;
  padding: 6px 0;
`;

const FunLine = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
  text-align: center;
  animation: ${fadeInOut} 4s ease-in-out;
  min-height: 18px;
`;

const SuccessBlock = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 4px 0;
  animation: ${scaleIn} 0.4s ease-out;
`;

const SuccessIcon = styled.div`
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: rgba(0, 255, 65, 0.08);
  border: 1.5px solid ${p => p.theme.colors.brand};
  display: flex;
  align-items: center;
  justify-content: center;
`;

const Checkmark = styled.svg`
  width: 28px;
  height: 28px;
  stroke: ${p => p.theme.colors.brand};
  stroke-width: 2.5;
  stroke-linecap: round;
  stroke-linejoin: round;
  fill: none;
  stroke-dasharray: 24;
  stroke-dashoffset: 24;
  animation: ${checkmarkDraw} 0.4s ease-out 0.2s forwards;
`;

const SuccessText = styled(TitleRow)`
  align-items: center;
  text-align: center;
`;
