import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider } from 'styled-components';
import { terminalTheme } from '@/app/styles/theme';
import { TerminalButton } from '../TerminalButton';

const renderWithTheme = (component: React.ReactElement) => {
  return render(
    <ThemeProvider theme={terminalTheme}>
      {component}
    </ThemeProvider>
  );
};

describe('TerminalButton', () => {
  it('renders button text', () => {
    renderWithTheme(<TerminalButton>Click Me</TerminalButton>);
    expect(screen.getByText('Click Me')).toBeInTheDocument();
  });

  it('calls onClick when clicked', async () => {
    const handleClick = jest.fn();
    const user = userEvent.setup();

    renderWithTheme(<TerminalButton onClick={handleClick}>Click</TerminalButton>);

    await user.click(screen.getByText('Click'));
    expect(handleClick).toHaveBeenCalledTimes(1);
  });

  it('does not call onClick when disabled', async () => {
    const handleClick = jest.fn();
    const user = userEvent.setup();

    renderWithTheme(
      <TerminalButton onClick={handleClick} disabled>
        Disabled
      </TerminalButton>
    );

    await user.click(screen.getByText('Disabled'));
    expect(handleClick).not.toHaveBeenCalled();
  });

  it('applies primary variant styles', () => {
    renderWithTheme(<TerminalButton variant="primary">Primary</TerminalButton>);
    const button = screen.getByText('Primary');
    expect(button).toHaveStyleRule('border-color', terminalTheme.colors.primary);
    expect(button).toHaveStyleRule('color', terminalTheme.colors.primary);
  });

  it('applies secondary variant styles', () => {
    renderWithTheme(<TerminalButton variant="secondary">Secondary</TerminalButton>);
    const button = screen.getByText('Secondary');
    expect(button).toHaveStyleRule('border-color', terminalTheme.colors.secondary);
  });

  it('applies danger variant styles', () => {
    renderWithTheme(<TerminalButton variant="danger">Danger</TerminalButton>);
    const button = screen.getByText('Danger');
    expect(button).toHaveStyleRule('border-color', terminalTheme.colors.error);
    expect(button).toHaveStyleRule('color', terminalTheme.colors.error);
  });

  it('applies ghost variant styles', () => {
    renderWithTheme(<TerminalButton variant="ghost">Ghost</TerminalButton>);
    const button = screen.getByText('Ghost');
    expect(button).toHaveStyleRule('border-color', 'transparent');
    expect(button).toHaveStyleRule('background-color', 'transparent');
  });

  it('applies small size styles', () => {
    renderWithTheme(<TerminalButton size="sm">Small</TerminalButton>);
    const button = screen.getByText('Small');
    expect(button).toHaveStyleRule('font-size', terminalTheme.typography.fontSize.sm);
  });

  it('applies medium size styles by default', () => {
    renderWithTheme(<TerminalButton>Medium</TerminalButton>);
    const button = screen.getByText('Medium');
    expect(button).toHaveStyleRule('font-size', terminalTheme.typography.fontSize.base);
  });

  it('applies large size styles', () => {
    renderWithTheme(<TerminalButton size="lg">Large</TerminalButton>);
    const button = screen.getByText('Large');
    expect(button).toHaveStyleRule('font-size', terminalTheme.typography.fontSize.lg);
  });

  it('has uppercase text transform', () => {
    renderWithTheme(<TerminalButton>Button</TerminalButton>);
    expect(screen.getByText('Button')).toHaveStyleRule('text-transform', 'uppercase');
  });

  it('has correct letter spacing', () => {
    renderWithTheme(<TerminalButton>Button</TerminalButton>);
    expect(screen.getByText('Button')).toHaveStyleRule('letter-spacing', terminalTheme.typography.letterSpacing.wide);
  });

  it('forwards className prop', () => {
    renderWithTheme(<TerminalButton className="custom">Button</TerminalButton>);
    expect(screen.getByText('Button')).toHaveClass('custom');
  });

  it('supports different button types', () => {
    renderWithTheme(<TerminalButton type="submit">Submit</TerminalButton>);
    expect(screen.getByText('Submit')).toHaveAttribute('type', 'submit');
  });

  it('has button type by default', () => {
    renderWithTheme(<TerminalButton>Button</TerminalButton>);
    expect(screen.getByText('Button')).toHaveAttribute('type', 'button');
  });

  it('applies disabled cursor when disabled', () => {
    renderWithTheme(<TerminalButton disabled>Disabled</TerminalButton>);
    expect(screen.getByText('Disabled')).toHaveStyleRule('cursor', 'not-allowed');
  });

  it('applies pointer cursor when not disabled', () => {
    renderWithTheme(<TerminalButton>Enabled</TerminalButton>);
    expect(screen.getByText('Enabled')).toHaveStyleRule('cursor', 'pointer');
  });

  it('uses monospace font', () => {
    renderWithTheme(<TerminalButton>Button</TerminalButton>);
    // CSS minifies spaces after commas
    const expectedFont = terminalTheme.typography.fontFamily.mono.replace(/, /g, ',');
    expect(screen.getByText('Button')).toHaveStyleRule('font-family', expectedFont);
  });
});
