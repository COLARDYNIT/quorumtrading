import { render, screen } from '@testing-library/react';
import { ThemeProvider } from 'styled-components';
import { terminalTheme } from '@/app/styles/theme';
import { AtAGlancePanel } from '../AtAGlancePanel';
import type { StrategyPerformanceResponse } from '@/app/lib/types/api';

const renderPanel = (performance: StrategyPerformanceResponse | undefined) =>
  render(
    <ThemeProvider theme={terminalTheme}>
      <AtAGlancePanel
        performance={performance}
        liveCount={0}
        completedCount={performance?.completedSimulations ?? 0}
        failedCount={0}
        publicVisible={false}
        canTogglePublish={false}
      />
    </ThemeProvider>
  );

function perf(overrides: Partial<StrategyPerformanceResponse> = {}): StrategyPerformanceResponse {
  return {
    totalSimulations: 3,
    completedSimulations: 3,
    avgReturn: 4.5,
    winRate: 66,
    avgSharpeRatio: 1.25,
    bestReturn: 9.5,
    worstReturn: -2.5,
    recentTrend: [1.5, -0.5, 4.5],
    emptyRuns: 0,
    ...overrides,
  };
}

/** The value cell that follows a given row label, so a row's own em-dash can't be confused
 *  with any other row's. */
function valueForLabel(label: string): string {
  return screen.getByText(label).parentElement?.lastElementChild?.textContent ?? '';
}

describe('AtAGlancePanel Sharpe cell', () => {
  it('renders a computed Sharpe ratio to two decimals', () => {
    renderPanel(perf({ avgSharpeRatio: 1.25 }));

    expect(valueForLabel('Sharpe (avg)')).toBe('1.25');
  });

  it('renders an em-dash, not 0.00, when the backend could not compute the Sharpe ratio', () => {
    // The backend sends null for a backtest with too few daily snapshots to take a standard
    // deviation. It used to send 0, which rendered as "0.00" and read as a measured
    // risk-neutral result — a fabricated number on a panel of real ones.
    renderPanel(perf({ avgSharpeRatio: null }));

    expect(valueForLabel('Sharpe (avg)')).toBe('—');
    expect(screen.queryByText('0.00')).not.toBeInTheDocument();
  });

  it('still renders the other metrics when only the Sharpe ratio is unavailable', () => {
    // Guards against "fix it by hiding the row": null Sharpe must not blank the panel.
    renderPanel(perf({ avgSharpeRatio: null, winRate: 66, bestReturn: 9.5 }));

    expect(valueForLabel('Sharpe (avg)')).toBe('—');
    expect(valueForLabel('Best run')).toBe('+9.50%');
  });

  it('renders an em-dash when there is no performance data at all', () => {
    renderPanel(undefined);

    expect(valueForLabel('Sharpe (avg)')).toBe('—');
  });
});
