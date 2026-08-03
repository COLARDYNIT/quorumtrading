import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ThemeProvider } from 'styled-components';
import { terminalTheme } from '@/app/styles/theme';
import { LocalHome } from '../LocalHome';
import * as hooks from '@/app/lib/api/hooks';
import * as authHooks from '@/app/hooks/useAuth';
import { apiClient, AuthSessionExpiredError } from '@/app/lib/api/client';
import type { InstanceInfo } from '@/app/lib/types/instance';
import type { LlmProviderStatus } from '@/app/lib/types/llmProvider';

jest.mock('@/app/lib/api/hooks');
jest.mock('@/app/hooks/useAuth');

const renderWithTheme = (component: React.ReactElement) =>
  render(<ThemeProvider theme={terminalTheme}>{component}</ThemeProvider>);

function mockInstance(overrides: Partial<InstanceInfo> = {}): InstanceInfo {
  return {
    edition: 'standalone',
    brokers: ['ALPACA', 'SAXO'],
    automaticInvestmentsEnabled: false,
    ...overrides,
  };
}

function mockLlmStatus(selected: boolean): LlmProviderStatus {
  return {
    selected: selected ? 'LOCAL' : null,
    configuredAt: selected ? '2026-06-11T00:00:00Z' : null,
    providers: [
      { id: 'LOCAL', label: 'Local (claude CLI)', available: true, selected, detail: '' },
      { id: 'OPENROUTER', label: 'OpenRouter', available: false, selected: false, detail: '' },
    ],
  };
}

function mockAnonymous() {
  (authHooks.useAuth as jest.Mock).mockReturnValue({
    isAuthenticated: false,
    isLoading: false,
    user: null,
  });
}

function mockSession() {
  (authHooks.useAuth as jest.Mock).mockReturnValue({
    isAuthenticated: true,
    isLoading: false,
    user: { email: 'local@quorumtrading.local', name: 'Local' },
  });
  (hooks.useStrategies as jest.Mock).mockReturnValue({ data: [{ id: 's1' }, { id: 's2' }] });
  (hooks.useSimulations as jest.Mock).mockReturnValue({ data: [{ id: 'sim1' }] });
  (hooks.useBrokerAccounts as jest.Mock).mockReturnValue({
    data: [{ id: 'b1', brokerType: 'SAXO_SIM' }],
  });
}

describe('LocalHome', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockAnonymous();
  });

  it('renders the fresh-boot state: enter CTA, both broker regions, LLM not selected', () => {
    (hooks.useInstanceInfo as jest.Mock).mockReturnValue({ data: mockInstance() });
    (hooks.useLlmProviderStatus as jest.Mock).mockReturnValue({ data: mockLlmStatus(false) });

    renderWithTheme(<LocalHome />);

    expect(screen.getByText(/Enter QuorumTrading/)).toBeInTheDocument();
    expect(screen.getByText('QuorumTrading is running on this machine.')).toBeInTheDocument();
    expect(screen.getByText('EU — Saxo')).toBeInTheDocument();
    expect(screen.getByText('US — Alpaca')).toBeInTheDocument();
    expect(screen.getByText('Not selected')).toBeInTheDocument();
    expect(screen.getByText('Choose engine')).toBeInTheDocument();
    // Sysline shows offered regions while nothing else is known
    expect(screen.getByText('EU · US')).toBeInTheDocument();
    // No session → no workspace strip
    expect(screen.queryByText('Workspace')).not.toBeInTheDocument();
    // MCP snippet ships the literal placeholder
    expect(screen.getByText('<token from boot banner>')).toBeInTheDocument();
  });

  it('hides the EU broker row on a US-only instance (absent, not greyed)', () => {
    (hooks.useInstanceInfo as jest.Mock).mockReturnValue({
      data: mockInstance({ brokers: ['ALPACA'] }),
    });
    (hooks.useLlmProviderStatus as jest.Mock).mockReturnValue({ data: mockLlmStatus(false) });

    renderWithTheme(<LocalHome />);

    expect(screen.queryByText('EU — Saxo')).not.toBeInTheDocument();
    expect(screen.getByText('US — Alpaca')).toBeInTheDocument();
    expect(screen.getByText('US')).toBeInTheDocument();
  });

  it('shows the active engine state when an LLM is selected', () => {
    (hooks.useInstanceInfo as jest.Mock).mockReturnValue({ data: mockInstance() });
    (hooks.useLlmProviderStatus as jest.Mock).mockReturnValue({ data: mockLlmStatus(true) });

    renderWithTheme(<LocalHome />);

    expect(screen.getByText('Active')).toBeInTheDocument();
    expect(screen.getByText('Change engine')).toBeInTheDocument();
    expect(screen.getByText(/no usage cost/)).toBeInTheDocument();
    // Sysline swaps the 4th cell to the engine
    expect(screen.getByText('LOCAL')).toBeInTheDocument();
  });

  it('renders the workspace strip and connected broker state with a session', () => {
    mockSession();
    (hooks.useInstanceInfo as jest.Mock).mockReturnValue({ data: mockInstance() });
    (hooks.useLlmProviderStatus as jest.Mock).mockReturnValue({ data: mockLlmStatus(true) });

    renderWithTheme(<LocalHome />);

    expect(screen.getByText('Workspace')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument(); // strategies
    // 'Strategies' appears in both the KPI label and the footer nav
    expect(screen.getAllByText('Strategies').length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText('EU connected')).toBeInTheDocument();
    expect(screen.getByText('Linked via Saxo consent flow.')).toBeInTheDocument();
    expect(screen.getByText('Session')).toBeInTheDocument();
  });

  it('dev-logins silently and retries once when Connect (Saxo) hits a 401', async () => {
    (hooks.useInstanceInfo as jest.Mock).mockReturnValue({ data: mockInstance() });
    (hooks.useLlmProviderStatus as jest.Mock).mockReturnValue({ data: mockLlmStatus(true) });

    const startMock = jest
      .spyOn(apiClient.brokerAccounts, 'saxoOnboardStart')
      // The api client maps 401 responses to AuthSessionExpiredError — what production throws.
      .mockRejectedValueOnce(new AuthSessionExpiredError())
      .mockResolvedValueOnce({ authorizationUrl: 'https://sim.example/authorize?x=1' });
    const fetchMock = jest.fn().mockResolvedValue({ ok: true });
    global.fetch = fetchMock as unknown as typeof fetch;

    renderWithTheme(<LocalHome />);
    fireEvent.click(screen.getByRole('button', { name: 'Connect' }));

    // 401 → silent dev-login → one retry (jsdom can't intercept the final location.href
    // assignment, so assert the two calls that produce it instead).
    await waitFor(() => expect(startMock).toHaveBeenCalledTimes(2));
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/auth/dev-login'),
      expect.objectContaining({ credentials: 'include', redirect: 'manual' })
    );
  });
});
