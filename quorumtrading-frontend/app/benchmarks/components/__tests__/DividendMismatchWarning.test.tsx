import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from 'styled-components';
import { terminalTheme } from '@/app/styles/theme';
import { DividendMismatchWarning } from '../DividendMismatchWarning';
import * as hooks from '@/app/lib/api/hooks';
import type { SimulationResponse, StrategyResponse, DividendInfoResponse } from '@/app/lib/types/api';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: false },
  },
});

const renderWithProviders = (component: React.ReactElement) => {
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={terminalTheme}>
        {component}
      </ThemeProvider>
    </QueryClientProvider>
  );
};

jest.mock('@/app/lib/api/hooks');

function mockSimulation(overrides: Partial<SimulationResponse> = {}): SimulationResponse {
  return {
    id: 'sim-1',
    strategyId: 'strat-1',
    startDate: '2025-01-01T00:00:00Z',
    endDate: '2025-06-01T00:00:00Z',
    status: 'COMPLETED',
    createdAt: '2025-01-01T00:00:00Z',
    ...overrides,
  };
}

function mockStrategy(overrides: Partial<StrategyResponse> = {}): StrategyResponse {
  return {
    id: 'strat-1',
    name: 'Test Strategy',
    version: 1,
    markdownContent: `---
stock_tickers:
  - AAPL
  - MSFT
---
# Strategy`,
    createdAt: '2025-01-01T00:00:00Z',
    publicVisible: false,
    ...overrides,
  };
}

describe('DividendMismatchWarning', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('renders nothing when no dividend data available', () => {
    (hooks.useStrategies as jest.Mock).mockReturnValue({ data: undefined });
    (hooks.useDividendInfo as jest.Mock).mockReturnValue({ data: undefined });

    const { container } = renderWithProviders(
      <DividendMismatchWarning simulations={[mockSimulation()]} />
    );

    expect(container.firstChild).toBeNull();
  });

  it('renders nothing when all strategies have same dividend profile (all pay dividends)', () => {
    const strategies = [
      mockStrategy({ id: 'strat-1', markdownContent: '---\nstock_tickers:\n  - AAPL\n---\n# S1' }),
      mockStrategy({ id: 'strat-2', name: 'Strategy 2', markdownContent: '---\nstock_tickers:\n  - MSFT\n---\n# S2' }),
    ];

    (hooks.useStrategies as jest.Mock).mockReturnValue({ data: strategies });
    (hooks.useDividendInfo as jest.Mock).mockReturnValue({
      data: {
        items: [
          { ticker: 'AAPL', paysDividends: true, annualYieldEstimate: 0.99, dividendCountLastYear: 4, assetType: 'STOCK' },
          { ticker: 'MSFT', paysDividends: true, annualYieldEstimate: 3.32, dividendCountLastYear: 4, assetType: 'STOCK' },
        ],
      } as DividendInfoResponse,
    });

    const { container } = renderWithProviders(
      <DividendMismatchWarning
        simulations={[
          mockSimulation({ id: 'sim-1', strategyId: 'strat-1' }),
          mockSimulation({ id: 'sim-2', strategyId: 'strat-2' }),
        ]}
      />
    );

    expect(container.firstChild).toBeNull();
  });

  it('renders warning when strategies have mixed dividend profiles', () => {
    const strategies = [
      mockStrategy({ id: 'strat-1', name: 'Income Strategy', markdownContent: '---\nstock_tickers:\n  - AAPL\n  - JNJ\n---\n# S1' }),
      mockStrategy({ id: 'strat-2', name: 'Growth Strategy', markdownContent: '---\nstock_tickers:\n  - TSLA\n  - NVDA\n---\n# S2' }),
    ];

    (hooks.useStrategies as jest.Mock).mockReturnValue({ data: strategies });
    (hooks.useDividendInfo as jest.Mock).mockReturnValue({
      data: {
        items: [
          { ticker: 'AAPL', paysDividends: true, annualYieldEstimate: 0.99, dividendCountLastYear: 4, assetType: 'STOCK' },
          { ticker: 'JNJ', paysDividends: true, annualYieldEstimate: 4.76, dividendCountLastYear: 4, assetType: 'STOCK' },
          { ticker: 'TSLA', paysDividends: false, annualYieldEstimate: null, dividendCountLastYear: 0, assetType: 'STOCK' },
          { ticker: 'NVDA', paysDividends: false, annualYieldEstimate: null, dividendCountLastYear: 0, assetType: 'STOCK' },
        ],
      } as DividendInfoResponse,
    });

    renderWithProviders(
      <DividendMismatchWarning
        simulations={[
          mockSimulation({ id: 'sim-1', strategyId: 'strat-1' }),
          mockSimulation({ id: 'sim-2', strategyId: 'strat-2' }),
        ]}
      />
    );

    expect(screen.getByText('Dividend Comparison Notice')).toBeInTheDocument();
    expect(screen.getByText(/Income Strategy/)).toBeInTheDocument();
    expect(screen.getByText(/Growth Strategy/)).toBeInTheDocument();
    expect(screen.getByText(/price-only/)).toBeInTheDocument();
  });

  it('renders warning for overlay dividend mismatch', () => {
    const strategies = [
      mockStrategy({ id: 'strat-1', name: 'My Strategy', markdownContent: '---\nstock_tickers:\n  - TSLA\n---\n# S1' }),
    ];

    (hooks.useStrategies as jest.Mock).mockReturnValue({ data: strategies });
    (hooks.useDividendInfo as jest.Mock).mockReturnValue({
      data: {
        items: [
          { ticker: 'TSLA', paysDividends: false, annualYieldEstimate: null, dividendCountLastYear: 0, assetType: 'STOCK' },
          { ticker: 'SPY', paysDividends: true, annualYieldEstimate: 6.76, dividendCountLastYear: 4, assetType: 'STOCK' },
        ],
      } as DividendInfoResponse,
    });

    renderWithProviders(
      <DividendMismatchWarning
        simulations={[mockSimulation({ id: 'sim-1', strategyId: 'strat-1' })]}
        overlaySymbols={['SPY']}
      />
    );

    expect(screen.getByText('Dividend Comparison Notice')).toBeInTheDocument();
    expect(screen.getByText(/SPY/)).toBeInTheDocument();
    expect(screen.getByText(/overlay/)).toBeInTheDocument();
  });

  it('renders nothing when no simulations provided', () => {
    (hooks.useStrategies as jest.Mock).mockReturnValue({ data: [] });
    (hooks.useDividendInfo as jest.Mock).mockReturnValue({ data: undefined });

    const { container } = renderWithProviders(
      <DividendMismatchWarning simulations={[]} />
    );

    expect(container.firstChild).toBeNull();
  });
});
