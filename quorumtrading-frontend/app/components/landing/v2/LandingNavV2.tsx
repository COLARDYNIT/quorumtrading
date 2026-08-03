'use client';

import Link from 'next/link';
import styled from 'styled-components';
import { useAuth } from '@/app/hooks/useAuth';
import { Wordmark } from '@/app/components/shared/Wordmark';

const Bar = styled.div`
  position: sticky;
  top: 0;
  z-index: 100;
  height: 60px;
  display: flex;
  align-items: center;
  background: rgba(10, 14, 20, 0.85);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
`;

const Inner = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  width: 100%;
  max-width: 1440px;
  margin: 0 auto;
  padding: 0 32px;

  @media (max-width: 768px) {
    padding: 0 20px;
    gap: 12px;
  }
`;

const Brand = styled(Wordmark)`
  letter-spacing: 0.01em;
`;

const Links = styled.nav`
  display: flex;
  gap: 6px;
  align-items: center;

  & > a {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 14px;
    color: ${p => p.theme.colors.fgMuted};
    padding: 6px 12px;
    border-radius: 2px;
    text-decoration: none;
    transition: color 120ms ease, background 120ms ease;
  }
  & > a:hover { color: ${p => p.theme.colors.foreground}; background: rgba(255, 255, 255, 0.03); }

  @media (max-width: 900px) {
    display: none;
  }
`;

const Actions = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
`;

const CtaGhost = styled(Link)`
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 2px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 14px;
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  color: ${p => p.theme.colors.foreground};
  border: 1px solid ${p => p.theme.colors.border};
  background: transparent;
  text-decoration: none;
  transition: border-color 120ms ease;

  &:hover { border-color: ${p => p.theme.colors.fgMuted}; }
`;

const CtaSolid = styled(Link)`
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 2px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 14px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.theme.colors.fgInverse};
  background: ${p => p.theme.colors.brand};
  border: 1px solid ${p => p.theme.colors.brand};
  text-decoration: none;
  transition: background 120ms ease, border-color 120ms ease;

  &:hover { background: ${p => p.theme.colors.brandHover}; border-color: ${p => p.theme.colors.brandHover}; }
`;

const Userpip = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px 4px 4px;
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 9999px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13px;
  color: ${p => p.theme.colors.fgMuted};
  background: ${p => p.theme.colors.surface};

  & > .avatar {
    width: 22px; height: 22px;
    border-radius: 50%;
    background: ${p => p.theme.colors.brandTint};
    color: ${p => p.theme.colors.brand};
    display: inline-flex; align-items: center; justify-content: center;
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 12px;
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
    text-transform: uppercase;
  }
  & > .name { color: ${p => p.theme.colors.foreground}; font-weight: ${p => p.theme.typography.fontWeight.medium}; }

  @media (max-width: 640px) {
    & > .name { display: none; }
  }
`;

export function LandingNavV2() {
  const { user } = useAuth();
  const signedIn = !!user;
  const initials = (user?.displayName ?? user?.email ?? 'qt').slice(0, 2);

  return (
    <Bar>
      <Inner>
        <Brand href="/" markSize={20} fontSize={15} />
        <Links>
          <a href="#problem">Why</a>
          <a href="#council">Council</a>
          <Link href="/strategies/public">Strategies</Link>
          <a href="#evidence">Backtests</a>
          <a href="#trust">Go live</a>
        </Links>
        <Actions>
          {signedIn ? (
            <>
              <Userpip>
                <span className="avatar">{initials}</span>
                <span className="name">{user?.displayName ?? user?.email}</span>
              </Userpip>
              <CtaSolid href="/dashboard">→ Control room</CtaSolid>
            </>
          ) : (
            <>
              <CtaGhost href="/auth/signin">Sign in</CtaGhost>
              <CtaSolid href="/auth/signin">Start free</CtaSolid>
            </>
          )}
        </Actions>
      </Inner>
    </Bar>
  );
}
