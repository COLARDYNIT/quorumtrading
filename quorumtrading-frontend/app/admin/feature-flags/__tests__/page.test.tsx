import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ThemeProvider } from 'styled-components';
import { terminalTheme } from '@/app/styles/theme';
import FeatureFlagsAdminPage from '../page';
import * as hooks from '@/app/lib/api/hooks';
import * as authHooks from '@/app/hooks/useAuth';
import type { FeatureFlag } from '@/app/lib/types/features';

jest.mock('@/app/lib/api/hooks');
jest.mock('@/app/hooks/useAuth');
jest.mock('@/app/components/layouts/DashboardLayout', () => ({
  // The real layout drags in the sidebar, auth context and router; none of that is what these
  // tests are about.
  DashboardLayout: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

const mockReplace = jest.fn();
jest.mock('next/navigation', () => ({
  useRouter: () => ({ replace: mockReplace, push: jest.fn(), prefetch: jest.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/admin/feature-flags',
}));

const renderPage = () =>
  render(
    <ThemeProvider theme={terminalTheme}>
      <FeatureFlagsAdminPage />
    </ThemeProvider>
  );

function flag(overrides: Partial<FeatureFlag> = {}): FeatureFlag {
  return {
    name: 'challenges',
    description: 'Public trading challenges and their leaderboards',
    enabled: true,
    strategies: [{ name: 'default', parameters: {} }],
    updatedAt: '2026-07-31T10:00:00Z',
    updatedBy: null,
    ...overrides,
  };
}

const mutate = jest.fn();

function setUp(options: {
  role?: 'ADMIN' | 'USER';
  edition?: 'saas' | 'standalone';
  flags?: FeatureFlag[];
  isLoading?: boolean;
  isError?: boolean;
} = {}) {
  const { role = 'ADMIN', edition = 'saas', flags = [flag()] } = options;

  (authHooks.useAuth as jest.Mock).mockReturnValue({
    isAuthenticated: true,
    isLoading: false,
    user: { email: 'someone@example.com', role },
  });
  (hooks.useInstanceInfo as jest.Mock).mockReturnValue({
    data: { edition, brokers: ['ALPACA'], automaticInvestmentsEnabled: false },
  });
  (hooks.useFeatureFlags as jest.Mock).mockReturnValue({
    data: options.isLoading || options.isError ? undefined : flags,
    isLoading: Boolean(options.isLoading),
    isError: Boolean(options.isError),
    error: options.isError ? new Error('nope') : undefined,
  });
  (hooks.useUpdateFeatureFlag as jest.Mock).mockReturnValue({
    mutate,
    reset: jest.fn(),
    isPending: false,
    isError: false,
    error: undefined,
  });
}

describe('feature flags admin page', () => {
  beforeEach(() => jest.clearAllMocks());

  it('lists each flag with its current state', () => {
    setUp({
      flags: [
        flag(),
        flag({ name: 'market-research', description: 'LLM-driven research', enabled: false, strategies: [] }),
      ],
    });

    renderPage();

    expect(screen.getByText('challenges')).toBeInTheDocument();
    expect(screen.getByText('market-research')).toBeInTheDocument();
    expect(screen.getByText('Enabled')).toBeInTheDocument();
    expect(screen.getByText('Disabled')).toBeInTheDocument();
  });

  it('describes who an enabled flag currently applies to', () => {
    setUp({ flags: [flag()] });

    renderPage();

    expect(screen.getByText(/on for everyone/)).toBeInTheDocument();
  });

  it('shows a targeted flag as reaching named people, not everyone', () => {
    // The point of the redesign: "Enabled" and "everyone has it" are different claims. A flag on
    // for two people must not be glanceable as fully on, so the reach pill counts them and the
    // addresses are visible in the row without opening anything.
    setUp({
      flags: [
        flag({
          strategies: [{
            name: 'userWithId',
            parameters: { userIds: 'beta@example.com,gamma@example.com' },
          }],
        }),
      ],
    });

    renderPage();

    expect(screen.getByText('Enabled')).toBeInTheDocument();
    expect(screen.getByText(/on for 2 people/)).toBeInTheDocument();
    expect(screen.queryByText(/on for everyone/)).not.toBeInTheDocument();
    expect(screen.getByText('beta@example.com')).toBeInTheDocument();
    expect(screen.getByText('gamma@example.com')).toBeInTheDocument();
  });

  it('counts a single targeted person in the singular', () => {
    setUp({
      flags: [flag({
        strategies: [{ name: 'userWithId', parameters: { userIds: 'beta@example.com' } }],
      })],
    });

    renderPage();

    expect(screen.getByText(/on for 1 person/)).toBeInTheDocument();
  });

  it('keeps a disabled flag\u2019s audience visible, marked as kept', () => {
    // The backend stores the strategy on a disabled flag, so hiding it would misrepresent what
    // re-enabling does.
    setUp({
      flags: [flag({
        enabled: false,
        strategies: [{ name: 'userWithId', parameters: { userIds: 'beta@example.com' } }],
      })],
    });

    renderPage();

    expect(screen.getByText('beta@example.com')).toBeInTheDocument();
    expect(screen.getByText('kept')).toBeInTheDocument();
    expect(screen.getByText(/off for everyone/)).toBeInTheDocument();
  });

  it('says plainly when a flag has only ever been seeded', () => {
    setUp({ flags: [flag({ updatedBy: null })] });

    renderPage();

    expect(screen.getByText(/seeded · never edited/)).toBeInTheDocument();
  });

  it('toggling sends the opposite enabled value', () => {
    setUp({ flags: [flag({ enabled: true })] });

    renderPage();
    fireEvent.click(screen.getByText('Enabled'));

    expect(mutate).toHaveBeenCalledWith(
      { name: 'challenges', request: { enabled: false, enabledForEmails: undefined } },
      expect.any(Object)
    );
  });

  it('saving an audience sends the addresses as a list, trimmed', () => {
    setUp({ flags: [flag()] });

    renderPage();
    fireEvent.click(screen.getByRole('button', { name: 'Audience' }));
    const input = screen.getByLabelText(/Restrict challenges/);
    fireEvent.change(input, { target: { value: ' a@example.com , b@example.com ' } });
    fireEvent.click(screen.getByText('Save audience'));

    expect(mutate).toHaveBeenCalledWith(
      { name: 'challenges', request: { enabled: true, enabledForEmails: ['a@example.com', 'b@example.com'] } },
      expect.any(Object)
    );
  });

  it('clearing the audience sends undefined, meaning everyone', () => {
    setUp({
      flags: [
        flag({ strategies: [{ name: 'userWithId', parameters: { userIds: 'beta@example.com' } }] }),
      ],
    });

    renderPage();
    fireEvent.click(screen.getByRole('button', { name: 'Audience' }));
    fireEvent.change(screen.getByLabelText(/Restrict challenges/), { target: { value: '  ' } });
    fireEvent.click(screen.getByText('Save audience'));

    expect(mutate).toHaveBeenCalledWith(
      { name: 'challenges', request: { enabled: true, enabledForEmails: undefined } },
      expect.any(Object)
    );
  });

  it('cannot save an audience that has not changed', () => {
    setUp({ flags: [flag()] });

    renderPage();
    fireEvent.click(screen.getByRole('button', { name: 'Audience' }));

    expect(screen.getByText('Save audience')).toBeDisabled();
  });

  it('sends a plain user away rather than showing them the flags', async () => {
    setUp({ role: 'USER' });

    renderPage();

    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/dashboard'));
  });

  it('admits a standalone operator because they hold the ADMIN role, not by exception', async () => {
    // The self-host bootstrap promotes its owner to ADMIN, so this page needs no per-edition
    // carve-out. It used to have one, and the sidebar did not — which hid the screen from the very
    // user the carve-out existed to admit. A plain USER is turned away on every edition.
    setUp({ role: 'ADMIN', edition: 'standalone' });
    renderPage();
    expect(mockReplace).not.toHaveBeenCalled();
    expect(screen.getByText('challenges')).toBeInTheDocument();

    jest.clearAllMocks();
    setUp({ role: 'USER', edition: 'standalone' });
    renderPage();
    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/dashboard'));
  });

  it('reports a load failure instead of rendering an empty list', () => {
    setUp({ isError: true });

    renderPage();

    expect(screen.getByText(/Could not load feature flags/)).toBeInTheDocument();
  });

  it('shows a loading state while the flags are in flight', () => {
    setUp({ isLoading: true });

    renderPage();

    expect(screen.getByText('Loading…')).toBeInTheDocument();
  });

  it('says so when no flags are registered', () => {
    setUp({ flags: [] });

    renderPage();

    expect(screen.getByText('No feature flags are registered.')).toBeInTheDocument();
  });
});
