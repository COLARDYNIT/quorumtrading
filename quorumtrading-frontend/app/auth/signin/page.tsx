'use client';

import { useEffect, useState, useMemo, useRef } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import styled from 'styled-components';
import { useAuth } from '@/app/hooks/useAuth';
import { useStandaloneAutoSignIn } from '@/app/hooks/useStandaloneAutoSignIn';
import { useInstanceInfo } from '@/app/lib/api/hooks';
import { SIGNIN_PICKER_FLAG } from '@/app/lib/api/auth';
import {
  AuthStage,
  Wordmark,
  AuthPanel,
  TitleRow,
  OAuthList,
  OAuthButton,
  Divider,
  Foot,
  Legal,
  Banner,
  glyphFor,
} from '../_chrome';

const ProvidersFallback = styled.div`
  text-align: center;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgMuted};
  padding: 8px 0;
`;

export default function SignInPage() {
  const router = useRouter();
  const {
    providers,
    providersLoading,
    login,
    devLogin,
    futLogin,
    devLoginEnabled,
    isAuthenticated,
    isLoading: authLoading,
  } = useAuth();
  const { data: instance, isLoading: instanceLoading } = useInstanceInfo();
  const autoSignIn = useStandaloneAutoSignIn();

  const isDevelopment =
    typeof window !== 'undefined' && window.location.hostname === 'localhost';
  const showDevLogin = devLoginEnabled || isDevelopment;
  const isStandalone = instance?.edition === 'standalone';

  // Read post-hydration in one pass. `pickerRequested` suppresses standalone auto-login
  // when the picker was explicitly asked for: ?picker=1 in the URL, or the one-shot flag
  // logout() leaves behind — without the latter, signing out would bounce straight back
  // in and logout would mean nothing. `checked` keeps auto-enter disarmed until this ran.
  const [urlState, setUrlState] = useState({
    expired: false,
    pickerRequested: false,
    checked: false,
  });
  useEffect(() => {
    if (typeof window === 'undefined') return;
    const params = new URLSearchParams(window.location.search);
    const pickerRequested =
      params.get('picker') === '1' || sessionStorage.getItem(SIGNIN_PICKER_FLAG) === '1';
    sessionStorage.removeItem(SIGNIN_PICKER_FLAG);
    const returnTo = params.get('returnTo');
    if (returnTo && returnTo.startsWith('/')) {
      sessionStorage.setItem('auth_redirect', returnTo);
    }
    setUrlState({
      expired: params.get('expired') === 'true',
      pickerRequested,
      checked: true,
    });
  }, []);
  const isSessionExpired = urlState.expired;

  // `useStandaloneAutoSignIn` answers "does this instance enter on its own?"; the rest is
  // this page's business — whether the picker was asked for, and whether there is anyone to
  // sign in yet. The session-expiry modal reads the same hook, which is what keeps it from
  // announcing a login that is about to happen silently.
  const autoEnter =
    autoSignIn.enabled &&
    urlState.checked &&
    !urlState.pickerRequested &&
    !authLoading &&
    !isAuthenticated;

  // Fire-once guard only; nothing renders from this ref. Once fired, devLogin()
  // replaces window.location, and `autoEnter` stays true until the page unloads,
  // so the "entering…" panel below keys off `autoEnter` alone.
  const autoEnterFired = useRef(false);
  useEffect(() => {
    if (!autoEnter || autoEnterFired.current) return;
    autoEnterFired.current = true;
    devLogin();
  }, [autoEnter, devLogin]);

  const [lastProvider, setLastProvider] = useState<string | null>(null);
  useEffect(() => {
    if (typeof window !== 'undefined') {
      setLastProvider(localStorage.getItem('last_auth_provider'));
    }
  }, []);

  const sortedProviders = useMemo(() => {
    if (!lastProvider || providers.length === 0) {
      return { primary: null, others: providers };
    }
    const primary = providers.find(p => p.id === lastProvider) ?? null;
    const others = providers.filter(p => p.id !== lastProvider);
    return { primary, others };
  }, [providers, lastProvider]);

  const handleLogin = (providerId: string) => {
    localStorage.setItem('last_auth_provider', providerId);
    login(providerId);
  };

  useEffect(() => {
    if (!isAuthenticated) return;
    const savedRedirect = sessionStorage.getItem('auth_redirect');
    if (savedRedirect) sessionStorage.removeItem('auth_redirect');
    const target = savedRedirect && savedRedirect.startsWith('/') ? savedRedirect : '/dashboard';
    router.push(target);
  }, [isAuthenticated, router]);

  const noProvidersAvailable = !providersLoading && providers.length === 0 && !showDevLogin;

  // `?expired=true` is set by the redirect out of an expired session, and it is honest on an
  // instance that is about to ask for credentials. While auto-entering it is not: nothing
  // expired from the user's side, and "Session expired / Sign in again to continue" would sit
  // above a panel that is already signing them back in. Report the entry, not the expiry.
  const showExpiredCopy = isSessionExpired && !autoEnter;

  const bypassButtons = isStandalone ? (
    <>
      <Divider>local</Divider>
      <OAuthButton $primary type="button" onClick={devLogin}>
        <span className="gly">~</span>
        Continue as Local Owner
      </OAuthButton>
      <OAuthButton type="button" onClick={futLogin}>
        <span className="gly">~</span>
        Fresh test account
      </OAuthButton>
    </>
  ) : (
    <>
      <Divider>dev</Divider>
      <OAuthButton type="button" onClick={devLogin}>
        <span className="gly">~</span>
        Continue as Dev User
      </OAuthButton>
      <OAuthButton type="button" onClick={futLogin}>
        <span className="gly">~</span>
        Continue as FUT User (fresh account)
      </OAuthButton>
    </>
  );

  let panelBody;
  if (providersLoading || instanceLoading) {
    panelBody = <ProvidersFallback>Loading providers…</ProvidersFallback>;
  } else if (autoEnter) {
    panelBody = <ProvidersFallback>Entering your local instance…</ProvidersFallback>;
  } else if (noProvidersAvailable) {
    panelBody = (
      <Banner $kind="error">
        Unable to reach the authentication service. Check that the backend is running and try again.
      </Banner>
    );
  } else {
    panelBody = (
      <OAuthList>
        {sortedProviders.primary && (
          <OAuthButton
            $primary
            type="button"
            onClick={() => handleLogin(sortedProviders.primary!.id)}
          >
            <span className="gly">{glyphFor(sortedProviders.primary)}</span>
            Continue with {sortedProviders.primary.name}
          </OAuthButton>
        )}
        {sortedProviders.others.map(provider => (
          <OAuthButton
            key={provider.id}
            type="button"
            onClick={() => handleLogin(provider.id)}
          >
            <span className="gly">{glyphFor(provider)}</span>
            Continue with {provider.name}
          </OAuthButton>
        ))}
        {showDevLogin && bypassButtons}
      </OAuthList>
    );
  }

  return (
    <AuthStage>
      <Wordmark />
      <AuthPanel>
        {showExpiredCopy && (
          <Banner>Your session expired. Sign in again to continue.</Banner>
        )}

        <TitleRow>
          <h1>{showExpiredCopy ? 'Session expired' : 'Sign in'}</h1>
          <div className="desc">
            {isStandalone
              ? 'Single-user local instance.'
              : 'Welcome back. Pick a provider to continue.'}
          </div>
        </TitleRow>

        {panelBody}

        {!isStandalone && (
          <Foot>
            No account yet? <Link href="/auth/signup">Create one</Link>.
          </Foot>
        )}
      </AuthPanel>
      <Legal>
        By signing in you agree to the <Link href="/terms">Terms</Link> and acknowledge the{' '}
        <Link href="/privacy">Privacy policy</Link>. QuorumTrading runs market simulations only — no investment advice is given.
      </Legal>
    </AuthStage>
  );
}

