import { render, screen } from '@testing-library/react';
import { ThemeProvider } from 'styled-components';
import { terminalTheme } from '@/app/styles/theme';
import { Act6OwnItPanel } from '../Act6OwnItPanel';

const renderPanel = () =>
  render(
    <ThemeProvider theme={terminalTheme}>
      <Act6OwnItPanel />
    </ThemeProvider>
  );

describe('Act6OwnItPanel', () => {
  it('links to the public repository and the self-host guide', () => {
    renderPanel();

    const github = screen.getByRole('link', { name: /view on github/i });
    expect(github).toHaveAttribute('href', 'https://github.com/COLARDYNIT/quorumtrading');

    const guide = screen.getByRole('link', { name: /self-host guide/i });
    expect(guide).toHaveAttribute(
      'href',
      'https://github.com/COLARDYNIT/quorumtrading/blob/main/LOCAL_SETUP.md'
    );
  });

  it('shows the two verbatim self-host commands', () => {
    renderPanel();

    expect(
      screen.getByText('git clone https://github.com/COLARDYNIT/quorumtrading.git')
    ).toBeInTheDocument();
    expect(
      screen.getByText('docker compose -f docker-compose.local.yml up --build')
    ).toBeInTheDocument();
  });

  it('keeps the claims honest: license, keyless crypto, and the hosted-only caveat', () => {
    renderPanel();

    // Dual licensing is stated, never "free for everything"
    expect(screen.getByText(/AGPL-3\.0/, { selector: 'h4' })).toBeInTheDocument();
    // Keyless applies to crypto; stocks explicitly need a key
    expect(screen.getByText(/Stocks need one free market-data key/)).toBeInTheDocument();
    // The one hosted-only feature is disclosed rather than hidden
    expect(screen.getByText(/Market research stays hosted-only/)).toBeInTheDocument();
  });

  it('anchors as #own so the nav link lands here', () => {
    const { container } = renderPanel();
    expect(container.querySelector('#own')).not.toBeNull();
  });
});
