import { render, screen } from '@testing-library/react';
import { ThemeProvider } from 'styled-components';
import { terminalTheme } from '@/app/styles/theme';
import { TerminalBadge } from '../TerminalBadge';

const renderWithTheme = (component: React.ReactElement) => {
  return render(
    <ThemeProvider theme={terminalTheme}>
      {component}
    </ThemeProvider>
  );
};

describe('TerminalBadge', () => {
  it('renders badge text', () => {
    renderWithTheme(<TerminalBadge>Running</TerminalBadge>);
    expect(screen.getByText('Running')).toBeInTheDocument();
  });

  it('applies success variant color', () => {
    renderWithTheme(<TerminalBadge variant="success">Success</TerminalBadge>);
    const badge = screen.getByText('Success');
    expect(badge).toHaveStyleRule('border-color', terminalTheme.colors.success);
    expect(badge).toHaveStyleRule('color', terminalTheme.colors.success);
  });

  it('applies warning variant color', () => {
    renderWithTheme(<TerminalBadge variant="warning">Warning</TerminalBadge>);
    const badge = screen.getByText('Warning');
    expect(badge).toHaveStyleRule('border-color', terminalTheme.colors.warning);
    expect(badge).toHaveStyleRule('color', terminalTheme.colors.warning);
  });

  it('applies error variant color', () => {
    renderWithTheme(<TerminalBadge variant="error">Error</TerminalBadge>);
    const badge = screen.getByText('Error');
    expect(badge).toHaveStyleRule('border-color', terminalTheme.colors.error);
    expect(badge).toHaveStyleRule('color', terminalTheme.colors.error);
  });

  it('applies info variant color', () => {
    renderWithTheme(<TerminalBadge variant="info">Info</TerminalBadge>);
    const badge = screen.getByText('Info');
    expect(badge).toHaveStyleRule('border-color', terminalTheme.colors.info);
    expect(badge).toHaveStyleRule('color', terminalTheme.colors.info);
  });

  it('applies neutral variant by default', () => {
    renderWithTheme(<TerminalBadge>Neutral</TerminalBadge>);
    const badge = screen.getByText('Neutral');
    expect(badge).toHaveStyleRule('border-color', terminalTheme.colors.textMuted);
    expect(badge).toHaveStyleRule('color', terminalTheme.colors.textMuted);
  });

  it('has uppercase text transform', () => {
    renderWithTheme(<TerminalBadge>Badge</TerminalBadge>);
    expect(screen.getByText('Badge')).toHaveStyleRule('text-transform', 'uppercase');
  });

  it('has correct letter spacing', () => {
    renderWithTheme(<TerminalBadge>Badge</TerminalBadge>);
    expect(screen.getByText('Badge')).toHaveStyleRule('letter-spacing', terminalTheme.typography.letterSpacing.wide);
  });

  it('uses monospace font', () => {
    renderWithTheme(<TerminalBadge>Badge</TerminalBadge>);
    // CSS minifies spaces after commas
    const expectedFont = terminalTheme.typography.fontFamily.mono.replace(/, /g, ',');
    expect(screen.getByText('Badge')).toHaveStyleRule('font-family', expectedFont);
  });

  it('has xs font size', () => {
    renderWithTheme(<TerminalBadge>Badge</TerminalBadge>);
    expect(screen.getByText('Badge')).toHaveStyleRule('font-size', terminalTheme.typography.fontSize.xs);
  });

  it('has semibold font weight', () => {
    renderWithTheme(<TerminalBadge>Badge</TerminalBadge>);
    expect(screen.getByText('Badge')).toHaveStyleRule('font-weight', terminalTheme.typography.fontWeight.semibold.toString());
  });

  it('forwards className prop', () => {
    renderWithTheme(<TerminalBadge className="custom">Badge</TerminalBadge>);
    expect(screen.getByText('Badge')).toHaveClass('custom');
  });

  it('displays inline block', () => {
    renderWithTheme(<TerminalBadge>Badge</TerminalBadge>);
    expect(screen.getByText('Badge')).toHaveStyleRule('display', 'inline-block');
  });

  it('has correct padding', () => {
    renderWithTheme(<TerminalBadge>Badge</TerminalBadge>);
    expect(screen.getByText('Badge')).toHaveStyleRule(
      'padding',
      `${terminalTheme.spacing.xs} ${terminalTheme.spacing.sm}`
    );
  });

  it('has border radius', () => {
    renderWithTheme(<TerminalBadge>Badge</TerminalBadge>);
    expect(screen.getByText('Badge')).toHaveStyleRule('border-radius', terminalTheme.borderRadius.sm);
  });

  it('renders with multiple variants', () => {
    renderWithTheme(
      <>
        <TerminalBadge variant="success">Success</TerminalBadge>
        <TerminalBadge variant="error">Error</TerminalBadge>
        <TerminalBadge variant="warning">Warning</TerminalBadge>
      </>
    );
    expect(screen.getByText('Success')).toBeInTheDocument();
    expect(screen.getByText('Error')).toBeInTheDocument();
    expect(screen.getByText('Warning')).toBeInTheDocument();
  });
});
