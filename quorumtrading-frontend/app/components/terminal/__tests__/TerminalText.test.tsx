import { render, screen } from '@testing-library/react';
import { ThemeProvider } from 'styled-components';
import { terminalTheme } from '@/app/styles/theme';
import { TerminalText } from '../TerminalText';

// Helper to render with theme
const renderWithTheme = (component: React.ReactElement) => {
  return render(
    <ThemeProvider theme={terminalTheme}>
      {component}
    </ThemeProvider>
  );
};

describe('TerminalText', () => {
  it('renders text content', () => {
    renderWithTheme(<TerminalText>Hello World</TerminalText>);
    expect(screen.getByText('Hello World')).toBeInTheDocument();
  });

  it('applies primary variant color by default', () => {
    renderWithTheme(<TerminalText>Primary Text</TerminalText>);
    const element = screen.getByText('Primary Text');
    expect(element).toHaveStyleRule('color', terminalTheme.colors.textPrimary);
  });

  it('applies success variant color', () => {
    renderWithTheme(<TerminalText variant="success">Success</TerminalText>);
    const element = screen.getByText('Success');
    expect(element).toHaveStyleRule('color', terminalTheme.colors.success);
  });

  it('applies warning variant color', () => {
    renderWithTheme(<TerminalText variant="warning">Warning</TerminalText>);
    const element = screen.getByText('Warning');
    expect(element).toHaveStyleRule('color', terminalTheme.colors.warning);
  });

  it('applies error variant color', () => {
    renderWithTheme(<TerminalText variant="error">Error</TerminalText>);
    const element = screen.getByText('Error');
    expect(element).toHaveStyleRule('color', terminalTheme.colors.error);
  });

  it('applies info variant color', () => {
    renderWithTheme(<TerminalText variant="info">Info</TerminalText>);
    const element = screen.getByText('Info');
    expect(element).toHaveStyleRule('color', terminalTheme.colors.info);
  });

  it('applies secondary variant color', () => {
    renderWithTheme(<TerminalText variant="secondary">Secondary</TerminalText>);
    const element = screen.getByText('Secondary');
    expect(element).toHaveStyleRule('color', terminalTheme.colors.textSecondary);
  });

  it('applies muted variant color', () => {
    renderWithTheme(<TerminalText variant="muted">Muted</TerminalText>);
    const element = screen.getByText('Muted');
    expect(element).toHaveStyleRule('color', terminalTheme.colors.textMuted);
  });

  it('applies correct font size', () => {
    renderWithTheme(<TerminalText size="lg">Large Text</TerminalText>);
    const element = screen.getByText('Large Text');
    expect(element).toHaveStyleRule('font-size', terminalTheme.typography.fontSize.lg);
  });

  it('applies xs font size', () => {
    renderWithTheme(<TerminalText size="xs">Extra Small</TerminalText>);
    const element = screen.getByText('Extra Small');
    expect(element).toHaveStyleRule('font-size', terminalTheme.typography.fontSize.xs);
  });

  it('applies correct font weight', () => {
    renderWithTheme(<TerminalText weight="bold">Bold Text</TerminalText>);
    const element = screen.getByText('Bold Text');
    expect(element).toHaveStyleRule('font-weight', terminalTheme.typography.fontWeight.bold.toString());
  });

  it('applies medium font weight', () => {
    renderWithTheme(<TerminalText weight="medium">Medium Text</TerminalText>);
    const element = screen.getByText('Medium Text');
    expect(element).toHaveStyleRule('font-weight', terminalTheme.typography.fontWeight.medium.toString());
  });

  it('uses monospace font by default', () => {
    renderWithTheme(<TerminalText>Mono</TerminalText>);
    const element = screen.getByText('Mono');
    // CSS minifies spaces after commas
    const expectedFont = terminalTheme.typography.fontFamily.mono.replace(/, /g, ',');
    expect(element).toHaveStyleRule('font-family', expectedFont);
  });

  it('uses inherit font when mono is false', () => {
    renderWithTheme(<TerminalText mono={false}>Non-Mono</TerminalText>);
    const element = screen.getByText('Non-Mono');
    expect(element).toHaveStyleRule('font-family', 'inherit');
  });

  it('forwards className prop', () => {
    renderWithTheme(<TerminalText className="custom-class">Text</TerminalText>);
    expect(screen.getByText('Text')).toHaveClass('custom-class');
  });

  it('applies custom style prop', () => {
    renderWithTheme(<TerminalText style={{ marginTop: '10px' }}>Styled Text</TerminalText>);
    const element = screen.getByText('Styled Text');
    expect(element).toHaveStyle({ marginTop: '10px' });
  });
});
