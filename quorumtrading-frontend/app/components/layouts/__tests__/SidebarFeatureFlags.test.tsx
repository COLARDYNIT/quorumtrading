import { render, screen } from '@testing-library/react';
import { ThemeProvider } from 'styled-components';
import { terminalTheme } from '@/app/styles/theme';
import { Sidebar } from '../Sidebar';
import * as hooks from '@/app/lib/api/hooks';
import * as authHooks from '@/app/hooks/useAuth';
import type { FeatureName } from '@/app/lib/types/features';

jest.mock('@/app/lib/api/hooks');
jest.mock('@/app/hooks/useAuth');

jest.mock('next/navigation', () => ({
  useRouter: () => ({ replace: jest.fn(), push: jest.fn(), prefetch: jest.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/dashboard',
}));

/**
 * Navigation hiding for switched-off features.
 *
 * <p>Worth its own test because it is the one place where the flags are deliberately *not* an
 * enforcement mechanism: the backend refuses a disabled feature regardless, and typing the URL is
 * expected to still reach the page. What the sidebar owes the user is not showing them a door that
 * only leads to a 403.</p>
 */
const renderSidebar = () =>
  render(
    <ThemeProvider theme={terminalTheme}>
      <Sidebar />
    </ThemeProvider>
  );

function setUp(options: {
  off?: FeatureName[];
  role?: 'ADMIN' | 'USER';
  edition?: 'saas' | 'standalone';
} = {}) {
  const { off = [], role = 'USER', edition = 'saas' } = options;

  (authHooks.useAuth as jest.Mock).mockReturnValue({
    isAuthenticated: true,
    isLoading: false,
    user: { email: 'someone@example.com', role },
  });
  (hooks.useInstanceInfo as jest.Mock).mockReturnValue({
    data: { edition, brokers: ['ALPACA'], automaticInvestmentsEnabled: false },
  });
  (hooks.useFeatureEnabled as jest.Mock).mockImplementation(
    (feature: FeatureName) => !off.includes(feature)
  );
}

describe('Sidebar feature-flag navigation', () => {
  beforeEach(() => jest.clearAllMocks());

  it('shows both flagged entries when the features are on', () => {
    setUp();

    renderSidebar();

    expect(screen.getByText('Challenges')).toBeInTheDocument();
    expect(screen.getByText('Research')).toBeInTheDocument();
  });

  it('hides Challenges when its flag is off', () => {
    setUp({ off: ['challenges'] });

    renderSidebar();

    expect(screen.queryByText('Challenges')).not.toBeInTheDocument();
    expect(screen.getByText('Research')).toBeInTheDocument();
  });

  it('hides Research when its flag is off', () => {
    setUp({ off: ['market-research'] });

    renderSidebar();

    expect(screen.queryByText('Research')).not.toBeInTheDocument();
    expect(screen.getByText('Challenges')).toBeInTheDocument();
  });

  it('hides both when both flags are off, and leaves the rest of the nav alone', () => {
    setUp({ off: ['challenges', 'market-research'] });

    renderSidebar();

    expect(screen.queryByText('Challenges')).not.toBeInTheDocument();
    expect(screen.queryByText('Research')).not.toBeInTheDocument();
    // "Strategies" is both a section and a subroute label, hence getAllByText.
    expect(screen.getAllByText('Strategies').length).toBeGreaterThan(0);
    expect(screen.getByText('Simulations')).toBeInTheDocument();
    expect(screen.getByText('Benchmarks')).toBeInTheDocument();
  });

  it('offers an administrator the feature-flag screen', () => {
    setUp({ role: 'ADMIN' });

    renderSidebar();

    expect(screen.getByText('Feature Flags')).toBeInTheDocument();
  });

  it('hides the admin New Challenge shortcut when challenges are switched off', () => {
    // Otherwise an administrator is offered a form whose POST the gate would refuse.
    setUp({ role: 'ADMIN', off: ['challenges'] });

    renderSidebar();

    expect(screen.getByText('Feature Flags')).toBeInTheDocument();
    expect(screen.queryByText('New Challenge')).not.toBeInTheDocument();
  });

  it('does not offer the flag screen to a plain user', () => {
    setUp({ role: 'USER' });

    renderSidebar();

    expect(screen.queryByText('Feature Flags')).not.toBeInTheDocument();
  });

  it('still hides the hosted-only assistant on a standalone instance', () => {
    // The edition check is separate from the flags and must survive alongside them.
    setUp({ edition: 'standalone' });

    renderSidebar();

    expect(screen.queryByText('AI Assistant')).not.toBeInTheDocument();
    expect(screen.getByText('Research')).toBeInTheDocument();
  });
});
