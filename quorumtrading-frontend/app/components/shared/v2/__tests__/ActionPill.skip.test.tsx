import { render, screen } from '@testing-library/react';
import { ThemeProvider } from 'styled-components';
import { terminalTheme } from '@/app/styles/theme';
import { ActionPill } from '../ActionPill';

/**
 * SKIP rows used to render as amber HOLD pills — the pill component had no skip kind, so a
 * run whose engine failed every round looked like a run that kept deciding to hold. The
 * pill's label is what a user scans; it must say what actually happened.
 */
describe('ActionPill skip kind', () => {
  it('renders SKIP as "skip", not as a hold', () => {
    render(
      <ThemeProvider theme={terminalTheme}>
        <ActionPill action="skip" />
      </ThemeProvider>
    );

    expect(screen.getByText('skip')).toBeInTheDocument();
    expect(screen.queryByText('hold')).not.toBeInTheDocument();
  });
});
