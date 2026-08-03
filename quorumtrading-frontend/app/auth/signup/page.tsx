'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import styled from 'styled-components';
import { useAuth } from '@/app/hooks/useAuth';
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
  TermsRow,
  glyphFor,
} from '../_chrome';

const ProvidersFallback = styled.div`
  text-align: center;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgMuted};
  padding: 8px 0;
`;

export default function SignUpPage() {
  const router = useRouter();
  const { providers, providersLoading, login, isAuthenticated, isLoading } = useAuth();
  const [termsAccepted, setTermsAccepted] = useState(false);

  useEffect(() => {
    if (!isLoading && isAuthenticated) {
      router.push('/dashboard');
    }
  }, [isAuthenticated, isLoading, router]);

  if (isLoading) {
    return (
      <AuthStage>
        <Wordmark />
        <AuthPanel>
          <ProvidersFallback>Loading…</ProvidersFallback>
        </AuthPanel>
      </AuthStage>
    );
  }

  return (
    <AuthStage>
      <Wordmark />
      <AuthPanel>
        <TitleRow>
          <h1>Create account</h1>
          <div className="desc">
            Backtests are free. Connect a broker only when you&apos;re ready to go live.
          </div>
        </TitleRow>

        <TermsRow>
          <input
            type="checkbox"
            checked={termsAccepted}
            onChange={e => setTermsAccepted(e.target.checked)}
          />
          <span>
            I agree to the <Link href="/terms">Terms of Service</Link> and{' '}
            <Link href="/privacy">Privacy Policy</Link>.
          </span>
        </TermsRow>

        <Divider>continue with</Divider>

        {providersLoading ? (
          <ProvidersFallback>Loading providers…</ProvidersFallback>
        ) : providers.length === 0 ? (
          <Banner $kind="error">No authentication providers available.</Banner>
        ) : (
          <OAuthList>
            {providers.map(provider => (
              <OAuthButton
                key={provider.id}
                type="button"
                onClick={() => login(provider.id)}
                disabled={!termsAccepted}
                title={termsAccepted ? undefined : 'Accept the terms first'}
              >
                <span className="gly">{glyphFor(provider)}</span>
                Sign up with {provider.name}
              </OAuthButton>
            ))}
          </OAuthList>
        )}

        <Foot>
          Already have an account? <Link href="/auth/signin">Sign in</Link>.
        </Foot>
      </AuthPanel>
      <Legal>
        Account creation grants you the free tier — backtests on mock data, unlimited strategies, no broker required. By continuing you accept the <Link href="/terms">Terms</Link> and <Link href="/privacy">Privacy policy</Link>.
      </Legal>
    </AuthStage>
  );
}
