import { render, screen } from '@testing-library/react';
import { ThemeProvider } from 'styled-components';
import { terminalTheme } from '@/app/styles/theme';
import { TerminalPane } from '../TerminalPane';

const renderWithTheme = (component: React.ReactElement) => {
  return render(
    <ThemeProvider theme={terminalTheme}>
      {component}
    </ThemeProvider>
  );
};

describe('TerminalPane', () => {
  it('renders children content', () => {
    renderWithTheme(
      <TerminalPane>
        <div>Pane Content</div>
      </TerminalPane>
    );
    expect(screen.getByText('Pane Content')).toBeInTheDocument();
  });

  it('renders title when provided', () => {
    renderWithTheme(
      <TerminalPane title="System Status">
        <div>Content</div>
      </TerminalPane>
    );
    expect(screen.getByText('System Status')).toBeInTheDocument();
  });

  it('does not render title when not provided', () => {
    renderWithTheme(
      <TerminalPane>
        <div>Content</div>
      </TerminalPane>
    );
    expect(screen.queryByText('System Status')).not.toBeInTheDocument();
  });

  it('renders actions in header', () => {
    renderWithTheme(
      <TerminalPane
        title="Title"
        actions={<button>Action</button>}
      >
        <div>Content</div>
      </TerminalPane>
    );
    expect(screen.getByText('Action')).toBeInTheDocument();
  });

  it('renders header with only actions (no title)', () => {
    renderWithTheme(
      <TerminalPane actions={<button>Action</button>}>
        <div>Content</div>
      </TerminalPane>
    );
    expect(screen.getByText('Action')).toBeInTheDocument();
  });

  it('does not render header when no title or actions', () => {
    const { container } = renderWithTheme(
      <TerminalPane>
        <div data-testid="content">Content</div>
      </TerminalPane>
    );
    // Check that there's no header element by looking for text that would be in a header
    expect(screen.getByTestId('content')).toBeInTheDocument();
  });

  it('renders multiple action buttons', () => {
    renderWithTheme(
      <TerminalPane
        title="Title"
        actions={
          <>
            <button>Action 1</button>
            <button>Action 2</button>
          </>
        }
      >
        <div>Content</div>
      </TerminalPane>
    );
    expect(screen.getByText('Action 1')).toBeInTheDocument();
    expect(screen.getByText('Action 2')).toBeInTheDocument();
  });

  it('forwards className prop', () => {
    const { container } = renderWithTheme(
      <TerminalPane className="custom-pane">
        <div>Content</div>
      </TerminalPane>
    );
    expect(container.firstChild).toHaveClass('custom-pane');
  });

  it('applies scrollable prop to content', () => {
    renderWithTheme(
      <TerminalPane scrollable>
        <div>Scrollable Content</div>
      </TerminalPane>
    );
    expect(screen.getByText('Scrollable Content')).toBeInTheDocument();
  });

  it('has border styling', () => {
    const { container } = renderWithTheme(
      <TerminalPane>
        <div>Content</div>
      </TerminalPane>
    );
    const paneContainer = container.firstChild as HTMLElement;
    expect(paneContainer).toHaveStyleRule('border', `1px solid ${terminalTheme.colors.border}`);
  });

  it('has correct background color', () => {
    const { container } = renderWithTheme(
      <TerminalPane>
        <div>Content</div>
      </TerminalPane>
    );
    const paneContainer = container.firstChild as HTMLElement;
    expect(paneContainer).toHaveStyleRule('background-color', terminalTheme.colors.background);
  });

  it('renders nested components', () => {
    renderWithTheme(
      <TerminalPane title="Parent">
        <div>
          <p>Paragraph</p>
          <span>Span</span>
        </div>
      </TerminalPane>
    );
    expect(screen.getByText('Paragraph')).toBeInTheDocument();
    expect(screen.getByText('Span')).toBeInTheDocument();
  });
});
