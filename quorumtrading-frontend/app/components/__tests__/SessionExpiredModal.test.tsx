import { render, screen, act } from '@testing-library/react';
import { ThemeProvider } from 'styled-components';
import { terminalTheme } from '@/app/styles/theme';
import { SessionExpiredProvider, triggerSessionExpired } from '../SessionExpiredModal';
import * as autoSignIn from '@/app/hooks/useStandaloneAutoSignIn';

jest.mock('@/app/hooks/useStandaloneAutoSignIn');

/**
 * The destination of the redirect is deliberately not asserted here. jsdom marks `location`
 * and `location.href` unforgeable — both are non-configurable, so they can be neither replaced
 * nor spied on, and assigning `href` no-ops instead of navigating. What is left observable is
 * the part that actually regressed: whether a padlock and a countdown appear in front of a
 * sign-in that needs no announcing, and whether the return URL survives the trip.
 */

function mockAutoSignIn(enabled: boolean, resolved = true) {
  (autoSignIn.useStandaloneAutoSignIn as jest.Mock).mockReturnValue({ enabled, resolved });
}

const renderProvider = () =>
  render(
    <ThemeProvider theme={terminalTheme}>
      <SessionExpiredProvider>
        <div>app</div>
      </SessionExpiredProvider>
    </ThemeProvider>
  );

describe('SessionExpiredProvider', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    sessionStorage.clear();
    // Not an auth page: triggerSessionExpired() ignores /auth/* and the index by design.
    window.history.replaceState({}, '', '/dashboard');
  });

  /**
   * A standalone instance signs itself back in the moment the signin page loads, so the padlock,
   * the apology and the ten-second countdown announce a problem that fixes itself before the
   * countdown ends.
   */
  it('shows no warning when the instance signs itself back in', () => {
    mockAutoSignIn(true);

    renderProvider();
    act(() => triggerSessionExpired());

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(screen.queryByText(/session expired/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/redirecting to login/i)).not.toBeInTheDocument();
  });

  /** Skipping the warning is only an improvement if it still puts the user back where they were. */
  it('still saves the return URL when it skips the warning', () => {
    window.history.replaceState({}, '', '/simulations/42?tab=decisions');
    mockAutoSignIn(true);

    renderProvider();
    act(() => triggerSessionExpired());

    expect(sessionStorage.getItem('auth_redirect')).toBe('/simulations/42?tab=decisions');
  });

  it('warns and counts down on an instance that will actually ask for credentials', () => {
    mockAutoSignIn(false);

    renderProvider();
    act(() => triggerSessionExpired());

    expect(screen.getByRole('alertdialog')).toBeInTheDocument();
    expect(screen.getByText(/redirecting to login in 10 seconds/i)).toBeInTheDocument();
  });

  /**
   * Rendering the modal before the probes answer and then redirecting once they do would put
   * the scare back, just briefly — which is the whole thing this change removes.
   */
  it('shows nothing while it does not yet know what kind of instance this is', () => {
    mockAutoSignIn(false, false);

    renderProvider();
    act(() => triggerSessionExpired());

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });
});
