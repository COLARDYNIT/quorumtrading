import { render, screen } from '@testing-library/react';
import { ThemeProvider } from 'styled-components';
import { terminalTheme } from '@/app/styles/theme';
import { ExecutionHealthBanner } from '../ExecutionHealthBanner';
import type { SimulationHealth } from '@/app/lib/types/api';

jest.mock('@/app/lib/api/hooks', () => ({
  useSimulationHealth: jest.fn(),
}));

// eslint-disable-next-line @typescript-eslint/no-require-imports
const { useSimulationHealth } = require('@/app/lib/api/hooks');

function health(overrides: Partial<SimulationHealth> = {}): SimulationHealth {
  return {
    status: 'SIMULATING',
    verdict: 'HEALTHY',
    detail: 'Recent rounds are producing decisions.',
    lastDecisionAt: '2026-08-01T10:00:00Z',
    lastRoundAt: '2026-08-01T10:00:00Z',
    recentRows: 10,
    recentSkippedRows: 0,
    consecutiveSkippedRows: 0,
    totalEvaluations: 10,
    totalSkippedEvaluations: 0,
    topSkipReasons: [],
    externalPauseReason: null,
    failureReason: null,
    ...overrides,
  };
}

const renderBanner = (h: SimulationHealth | undefined, status: SimulationHealth['status'] = 'SIMULATING') => {
  useSimulationHealth.mockReturnValue({ data: h });
  return render(
    <ThemeProvider theme={terminalTheme}>
      <ExecutionHealthBanner simulationId="sim-1" status={status} />
    </ThemeProvider>
  );
};

describe('ExecutionHealthBanner', () => {
  it('renders nothing for a healthy run', () => {
    renderBanner(health());

    expect(screen.queryByTestId('execution-health-banner')).not.toBeInTheDocument();
  });

  it('explains a stalled run with the dominant skip reason', () => {
    // The exact scenario this banner exists for: every round skips on a slow local
    // engine, and the page used to show four empty panels saying "nothing yet".
    renderBanner(health({
      verdict: 'STALLED',
      consecutiveSkippedRows: 12,
      recentSkippedRows: 12,
      recentRows: 12,
      lastDecisionAt: null,
      topSkipReasons: [{
        reason: 'Consensus pipeline for AAPL did not complete within 360s (slow or failed LLM engine)',
        count: 12,
      }],
    }));

    const banner = screen.getByTestId('execution-health-banner');
    expect(banner).toHaveAttribute('data-verdict', 'STALLED');
    expect(screen.getByText(/12 consecutive skipped/i)).toBeInTheDocument();
    expect(screen.getByText(/slow or failed LLM engine/)).toBeInTheDocument();
  });

  it('names the engine when the system paused for LLM failure', () => {
    renderBanner(health({
      verdict: 'PAUSED_EXTERNAL',
      externalPauseReason: 'LLM_FAILURE_DETECTED',
    }));

    expect(screen.getByText(/LLM engine stopped answering/i)).toBeInTheDocument();
  });

  it('still explains the budget pause the old banner covered', () => {
    renderBanner(health({
      verdict: 'PAUSED_EXTERNAL',
      externalPauseReason: 'BUDGET_DEPLETED',
    }));

    expect(screen.getByText(/daily AI budget exceeded/i)).toBeInTheDocument();
  });

  it('explains a data-gap pause — previously the circuit breaker paused silently', () => {
    renderBanner(health({
      verdict: 'PAUSED_EXTERNAL',
      externalPauseReason: 'DATA_GAP_DETECTED',
    }));

    expect(screen.getByText(/no-market-data skips/i)).toBeInTheDocument();
  });

  it('renders nothing on terminal statuses even if health is present', () => {
    renderBanner(health({ status: 'COMPLETED', verdict: 'COMPLETED' }), 'COMPLETED');

    expect(screen.queryByTestId('execution-health-banner')).not.toBeInTheDocument();
  });
});
