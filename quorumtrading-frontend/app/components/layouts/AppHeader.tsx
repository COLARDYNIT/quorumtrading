'use client';

import styled from 'styled-components';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { TerminalButton } from '../terminal/TerminalButton';
import { Wordmark } from '@/app/components/shared/Wordmark';
import { useAuth } from '@/app/hooks/useAuth';
import { useThemeMode } from '@/app/hooks/useThemeMode';
import { NotificationBell } from './NotificationBell';

const Header = styled.header`
  position: sticky;
  top: 0;
  z-index: 1100;
  background: ${p => p.theme.colors.background}ee;
  backdrop-filter: blur(10px);
  border-bottom: 1px solid ${p => p.theme.colors.border};
`;

const HeaderContent = styled.div`
  max-width: 1440px;
  margin: 0 auto;
  padding: ${p => p.theme.spacing.md} ${p => p.theme.spacing.xl};
  display: flex;
  justify-content: space-between;
  align-items: center;

  @media (max-width: 768px) {
    padding: ${p => p.theme.spacing.md} ${p => p.theme.spacing.md};
  }
`;

const HeaderWordmark = styled(Wordmark)`
  /* Shrink the canonical lockup on smaller viewports so it shares space
   * with the hamburger / theme toggle without crowding. */
  font-size: 18px;

  @media (max-width: 768px) {
    font-size: 16px;
    gap: 8px;
    & svg { width: 20px; height: 20px; }
  }

  @media (max-width: 480px) {
    font-size: 14px;
    gap: 6px;
    & svg { width: 18px; height: 18px; }
  }
`;

const RightSection = styled.div`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.md};

  @media (max-width: 480px) {
    gap: ${p => p.theme.spacing.sm};
  }
`;

const LeftSection = styled.div`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.sm};
`;

const HamburgerButton = styled.button`
  display: none;
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px;
  color: ${p => p.theme.colors.textMuted};
  transition: color 0.15s ease;

  &:hover {
    color: ${p => p.theme.colors.terminalGreen};
  }

  @media (max-width: 968px) {
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 4px;
  }
`;

const HamburgerLine = styled.span`
  display: block;
  width: 20px;
  height: 2px;
  background: currentColor;
  border-radius: 1px;
`;

const DashboardLink = styled(Link)`
  @media (max-width: 480px) {
    display: none;
  }
`;

const ThemeToggle = styled.button`
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  background: none;
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 50%;
  color: ${p => p.theme.colors.textMuted};
  cursor: pointer;
  transition: all 0.15s ease;
  flex-shrink: 0;
  margin-left: ${p => p.theme.spacing.xs};

  svg {
    width: 16px;
    height: 16px;
    transition: transform 0.2s ease;
  }

  &:hover {
    color: ${p => p.theme.colors.terminalAmber};
    border-color: ${p => p.theme.colors.terminalAmber};

    svg {
      transform: rotate(15deg);
    }
  }
`;

const WelcomeMessage = styled.span`
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};

  strong {
    color: ${p => p.theme.colors.terminalGreen};
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
  }

  @media (max-width: 640px) {
    display: none;
  }
`;

interface AppHeaderProps {
  onMenuToggle?: () => void;
}

export function AppHeader({ onMenuToggle }: AppHeaderProps) {
  const router = useRouter();
  const { user, isAuthenticated, logout } = useAuth();
  const { choice, toggle: toggleTheme } = useThemeMode();

  const handleLogout = async () => {
    await logout();
    router.push('/');
  };

  return (
    <Header>
      <HeaderContent>
        <LeftSection>
          {onMenuToggle && (
            <HamburgerButton onClick={onMenuToggle} aria-label="Toggle menu">
              <HamburgerLine />
              <HamburgerLine />
              <HamburgerLine />
            </HamburgerButton>
          )}
          <HeaderWordmark href="/" />
        </LeftSection>

        <RightSection>
          {/* Show authenticated UI only when confirmed logged in */}
          {isAuthenticated && user ? (
            <>
              <WelcomeMessage>
                Welcome back, <strong>{user.displayName?.split(' ')[0] || user.email}</strong>
              </WelcomeMessage>
              <DashboardLink href="/dashboard">
                <TerminalButton variant="ghost" size="sm">
                  Dashboard
                </TerminalButton>
              </DashboardLink>
              <NotificationBell enabled={isAuthenticated} />
              <TerminalButton variant="secondary" size="sm" onClick={handleLogout}>
                Logout
              </TerminalButton>
            </>
          ) : (
            /* Show sign-in buttons immediately (don't wait for auth check) */
            <>
              <Link href="/auth/signin">
                <TerminalButton variant="ghost" size="sm">
                  Sign In
                </TerminalButton>
              </Link>
              <Link href="/auth/signup">
                <TerminalButton variant="primary" size="sm">
                  Get Started
                </TerminalButton>
              </Link>
            </>
          )}
          <ThemeToggle
            onClick={toggleTheme}
            aria-label={`Theme: ${choice} (click to change)`}
            title={`Theme: ${choice} (click to change)`}
          >
            {choice === 'dark' ? (
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
              </svg>
            ) : choice === 'light' ? (
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="12" cy="12" r="5" />
                <line x1="12" y1="1" x2="12" y2="3" />
                <line x1="12" y1="21" x2="12" y2="23" />
                <line x1="4.22" y1="4.22" x2="5.64" y2="5.64" />
                <line x1="18.36" y1="18.36" x2="19.78" y2="19.78" />
                <line x1="1" y1="12" x2="3" y2="12" />
                <line x1="21" y1="12" x2="23" y2="12" />
                <line x1="4.22" y1="19.78" x2="5.64" y2="18.36" />
                <line x1="18.36" y1="5.64" x2="19.78" y2="4.22" />
              </svg>
            ) : (
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <rect x="2" y="3" width="20" height="14" rx="2" ry="2" />
                <line x1="8" y1="21" x2="16" y2="21" />
                <line x1="12" y1="17" x2="12" y2="21" />
              </svg>
            )}
          </ThemeToggle>
        </RightSection>
      </HeaderContent>
    </Header>
  );
}
