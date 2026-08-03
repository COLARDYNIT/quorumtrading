'use client';

import styled, { keyframes } from 'styled-components';
import type { AuthProvider } from '@/app/lib/api/auth';

// Re-export the canonical brand lockup so existing auth pages keep importing
// `Wordmark` from `_chrome` without churn.
export { Wordmark } from '@/app/components/shared/Wordmark';

/**
 * Backend ships AuthProvider.icon as the provider id ("google", "github")
 * rather than a single-char glyph. Render the leading uppercase letter;
 * if the icon happens to be a real 1-char glyph (emoji, symbol), let it
 * through unchanged.
 */
export function glyphFor(provider: AuthProvider): string {
  const icon = provider.icon || provider.name;
  if (!icon) return '·';
  if ([...icon].length === 1) return icon;
  return icon.charAt(0).toUpperCase();
}

/**
 * Shared chrome for /auth/signin, /auth/signup, /auth/callback.
 * Centered single-panel layout on a dim background — drops the terminal
 * cursor, no green-glow text-shadows, neutral foreground.
 */

export const AuthStage = styled.div`
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 18px;
  padding: 24px;
  background: ${p => p.theme.colors.background};
`;

export const AuthPanel = styled.div`
  width: 100%;
  max-width: 380px;
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 4px;
  padding: 26px 26px 22px;
  display: flex;
  flex-direction: column;
  gap: 18px;
`;

export const TitleRow = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;

  h1 {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 19px;
    color: ${p => p.theme.colors.fgStrong};
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
    letter-spacing: -0.005em;
    margin: 0;
  }
  .desc {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 13.5px;
    color: ${p => p.theme.colors.fgMuted};
    line-height: 1.5;
  }
`;

export const OAuthList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
`;

export const OAuthButton = styled.button<{ $primary?: boolean }>`
  display: flex;
  align-items: center;
  gap: 10px;
  justify-content: center;
  padding: 9px 12px;
  border: 1px solid ${p => p.$primary ? p.theme.colors.brand : p.theme.colors.border};
  border-radius: 2px;
  background: ${p => p.$primary ? 'rgba(0, 255, 65, 0.06)' : 'transparent'};
  color: ${p => p.$primary ? p.theme.colors.brand : p.theme.colors.foreground};
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 14px;
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  cursor: pointer;
  transition: ${p => p.theme.transition.fast};

  &:hover:not(:disabled) {
    border-color: ${p => p.theme.colors.fgMuted};
    color: ${p => p.theme.colors.fgStrong};
  }
  &:disabled {
    opacity: 0.45;
    cursor: not-allowed;
  }

  .gly {
    width: 16px;
    height: 16px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 15px;
    color: ${p => p.$primary ? p.theme.colors.brand : p.theme.colors.fgMuted};
  }
`;

export const Divider = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
  color: ${p => p.theme.colors.fgDimmed};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  letter-spacing: 0.1em;
  text-transform: uppercase;

  &::before,
  &::after {
    content: '';
    flex: 1;
    height: 1px;
    background: ${p => p.theme.colors.borderSubtle};
  }
`;

export const Foot = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13px;
  color: ${p => p.theme.colors.fgMuted};
  text-align: center;

  a {
    color: ${p => p.theme.colors.info};
    text-decoration: none;
  }
  a:hover { text-decoration: underline; }
`;

export const Legal = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  color: ${p => p.theme.colors.fgDimmed};
  text-align: center;
  max-width: 360px;
  line-height: 1.55;

  a {
    color: ${p => p.theme.colors.info};
    text-decoration: none;
  }
  a:hover { text-decoration: underline; }
`;

export const Banner = styled.div<{ $kind?: 'warning' | 'error' }>`
  padding: 9px 12px;
  border: 1px solid ${p => p.$kind === 'error' ? p.theme.colors.error : p.theme.colors.terminalAmber};
  border-radius: 2px;
  background: ${p => p.$kind === 'error'
    ? 'rgba(255, 51, 102, 0.06)'
    : 'rgba(255, 176, 0, 0.06)'};
  color: ${p => p.$kind === 'error' ? p.theme.colors.error : p.theme.colors.terminalAmber};
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 12.5px;
  line-height: 1.45;
`;

const spin = keyframes`
  to { transform: rotate(360deg); }
`;

export const Spinner = styled.div`
  width: 28px;
  height: 28px;
  border: 2px solid ${p => p.theme.colors.border};
  border-top-color: ${p => p.theme.colors.brand};
  border-radius: 50%;
  animation: ${spin} 0.7s linear infinite;
`;

export const MonoLine = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  color: ${p => p.theme.colors.fgDimmed};
  text-align: center;
`;

export const TermsRow = styled.label`
  display: flex;
  align-items: flex-start;
  gap: 9px;
  cursor: pointer;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 12.5px;
  color: ${p => p.theme.colors.fgMuted};
  line-height: 1.45;
  user-select: none;

  input {
    margin-top: 2px;
    accent-color: ${p => p.theme.colors.brand};
  }
  a {
    color: ${p => p.theme.colors.info};
    text-decoration: none;
  }
  a:hover { text-decoration: underline; }
`;
