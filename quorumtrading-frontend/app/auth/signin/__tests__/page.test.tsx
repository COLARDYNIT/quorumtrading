import { render, screen, waitFor } from '@testing-library/react';
import { ThemeProvider } from 'styled-components';
import { terminalTheme } from '@/app/styles/theme';
import SignInPage from '../page';
import * as hooks from '@/app/lib/api/hooks';
import * as authHooks from '@/app/hooks/useAuth';
import { SIGNIN_PICKER_FLAG } from '@/app/lib/api/auth';

jest.mock('@/app/lib/api/hooks');
jest.mock('@/app/hooks/useAuth');
jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn() }),
}));

const renderPage = () =>
  render(
    <ThemeProvider theme={terminalTheme}>
      <SignInPage />
    </ThemeProvider>
  );

function mockAuth(overrides: Partial<Record<string, unknown>> = {}) {
  const devLogin = jest.fn();
  const futLogin = jest.fn();
  (authHooks.useAuth as jest.Mock).mockReturnValue({
    providers: [],
    providersLoading: false,
    login: jest.fn(),
    devLogin,
    futLogin,
    devLoginEnabled: true,
    isAuthenticated: false,
    isLoading: false,
    ...overrides,
  });
  return { devLogin, futLogin };
}

function mockEdition(edition: 'saas' | 'standalone', isLoading = false) {
  (hooks.useInstanceInfo as jest.Mock).mockReturnValue({
    data: isLoading ? undefined : { edition, brokers: ['ALPACA'], automaticInvestmentsEnabled: false },
    isLoading,
  });
}

describe('SignInPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    sessionStorage.clear();
    window.history.replaceState({}, '', '/auth/signin');
  });

  /**
   * Standalone is single-user: the only meaningful identity is the local owner, so the
   * page must enter directly instead of asking the user to pick between two developer
   * bypasses ("Dev User" / "FUT User") — the confusion this test pins down.
   */
  it('standalone edition auto-enters as the local owner instead of showing a picker', async () => {
    mockEdition('standalone');
    const { devLogin } = mockAuth();

    renderPage();

    await waitFor(() => expect(devLogin).toHaveBeenCalledTimes(1));
    expect(screen.getByText(/entering your local instance/i)).toBeInTheDocument();
    expect(screen.queryByText(/dev user/i)).not.toBeInTheDocument();
  });

  it('standalone with ?picker=1 shows a manual chooser with honest labels', () => {
    window.history.replaceState({}, '', '/auth/signin?picker=1');
    mockEdition('standalone');
    const { devLogin } = mockAuth();

    renderPage();

    expect(devLogin).not.toHaveBeenCalled();
    expect(screen.getByText(/continue as local owner/i)).toBeInTheDocument();
    expect(screen.getByText(/fresh test account/i)).toBeInTheDocument();
    // Developer jargon and the pointless signup link stay off the standalone page.
    expect(screen.queryByText(/dev user/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/create one/i)).not.toBeInTheDocument();
  });

  /**
   * logout() leaves a one-shot flag behind; without honouring it, signing out of a
   * standalone instance would bounce straight back in and logout would mean nothing.
   */
  it('shows the picker once after an explicit logout, and clears the flag', () => {
    sessionStorage.setItem(SIGNIN_PICKER_FLAG, '1');
    mockEdition('standalone');
    const { devLogin } = mockAuth();

    renderPage();

    expect(devLogin).not.toHaveBeenCalled();
    expect(screen.getByText(/continue as local owner/i)).toBeInTheDocument();
    expect(sessionStorage.getItem(SIGNIN_PICKER_FLAG)).toBeNull();
  });

  it('saas edition keeps the developer picker and never auto-enters', () => {
    mockEdition('saas');
    const { devLogin } = mockAuth();

    renderPage();

    expect(devLogin).not.toHaveBeenCalled();
    expect(screen.getByText(/continue as dev user/i)).toBeInTheDocument();
    expect(screen.getByText(/continue as fut user/i)).toBeInTheDocument();
  });

  it('does not auto-enter while the instance probe is still in flight', () => {
    mockEdition('standalone', true);
    const { devLogin } = mockAuth();

    renderPage();

    expect(devLogin).not.toHaveBeenCalled();
    expect(screen.getByText(/loading providers/i)).toBeInTheDocument();
  });

  it('does not auto-enter when already authenticated', () => {
    mockEdition('standalone');
    const { devLogin } = mockAuth({ isAuthenticated: true });

    renderPage();

    expect(devLogin).not.toHaveBeenCalled();
  });

  /**
   * `?expired=true` is a redirect, not a request for the picker — the expiry path navigates
   * raw and never goes through logout(), so it leaves no one-shot flag behind and must not
   * suppress auto-enter. What it must also not do is narrate: an instance that signs itself
   * back in has nothing to apologise for, and "Session expired" over a panel that is already
   * entering reads as a failure the user has to act on.
   */
  it('auto-enters after a session expiry without narrating the expiry', async () => {
    window.history.replaceState({}, '', '/auth/signin?expired=true');
    mockEdition('standalone');
    const { devLogin } = mockAuth();

    renderPage();

    await waitFor(() => expect(devLogin).toHaveBeenCalledTimes(1));
    expect(screen.getByText(/entering your local instance/i)).toBeInTheDocument();
    expect(screen.queryByText(/continue as local owner/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/session expired/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/your session expired/i)).not.toBeInTheDocument();
    expect(screen.getByRole('heading')).toHaveTextContent(/sign in/i);
  });

  it('still explains the expiry when the picker is what the user gets', () => {
    window.history.replaceState({}, '', '/auth/signin?expired=true');
    mockEdition('saas');
    mockAuth();

    renderPage();

    expect(screen.getByRole('heading')).toHaveTextContent(/session expired/i);
    expect(screen.getByText(/your session expired\. sign in again to continue\./i)).toBeInTheDocument();
  });

  it('respects configured OIDC providers over the standalone bypass', () => {
    mockEdition('standalone');
    const { devLogin } = mockAuth({
      providers: [{ id: 'google', name: 'Google', icon: 'google' }],
    });

    renderPage();

    expect(devLogin).not.toHaveBeenCalled();
    expect(screen.getByText(/continue with google/i)).toBeInTheDocument();
  });
});
