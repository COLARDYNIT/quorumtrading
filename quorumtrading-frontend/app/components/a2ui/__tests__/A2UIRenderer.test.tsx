import { render, screen } from '@testing-library/react';
import { ThemeProvider } from 'styled-components';
import { terminalTheme } from '@/app/styles/theme';
import { A2UIRenderer } from '../A2UIRenderer';
import { registerComponent, getComponent } from '@/app/lib/a2ui/catalog';

const renderWithTheme = (component: React.ReactElement) => {
  return render(
    <ThemeProvider theme={terminalTheme}>
      {component}
    </ThemeProvider>
  );
};

// Register a simple test component
function TestWidget(props: Record<string, unknown>) {
  return <div data-testid="test-widget">{String(props.label ?? '')}</div>;
}

beforeAll(() => {
  registerComponent('TestWidget', TestWidget as never);
});

describe('A2UIRenderer', () => {
  it('returns null for invalid JSON payload', () => {
    const { container } = renderWithTheme(
      <A2UIRenderer payload="not-valid-json" />
    );
    expect(container.firstChild).toBeNull();
  });

  it('returns null for empty string payload', () => {
    const { container } = renderWithTheme(
      <A2UIRenderer payload="" />
    );
    expect(container.firstChild).toBeNull();
  });

  it('renders a registered component', () => {
    const payload = JSON.stringify({
      surfaceUpdate: {
        components: [
          {
            id: 'w1',
            type: 'TestWidget',
            childIds: [],
            properties: { label: { literalString: 'Hello A2UI' } },
          },
        ],
      },
    });

    renderWithTheme(<A2UIRenderer payload={payload} />);
    expect(screen.getByTestId('test-widget')).toHaveTextContent('Hello A2UI');
  });

  it('renders fallback for unknown component type', () => {
    const payload = JSON.stringify({
      surfaceUpdate: {
        components: [
          {
            id: 'u1',
            type: 'NonExistentWidget',
            childIds: [],
            properties: { foo: { literalString: 'bar' } },
          },
        ],
      },
    });

    renderWithTheme(<A2UIRenderer payload={payload} />);
    expect(screen.getByText(/Unknown: NonExistentWidget/)).toBeInTheDocument();
  });

  it('renders nested component tree', () => {
    // Register a layout-like component that renders children
    function TestRow(props: Record<string, unknown> & { children?: React.ReactNode }) {
      return <div data-testid="test-row">{props.children}</div>;
    }
    registerComponent('TestRow', TestRow as never);

    const payload = JSON.stringify({
      surfaceUpdate: {
        components: [
          {
            id: 'row1',
            type: 'TestRow',
            childIds: ['w1', 'w2'],
            properties: {},
          },
          {
            id: 'w1',
            type: 'TestWidget',
            childIds: [],
            properties: { label: { literalString: 'Child 1' } },
          },
          {
            id: 'w2',
            type: 'TestWidget',
            childIds: [],
            properties: { label: { literalString: 'Child 2' } },
          },
        ],
      },
    });

    renderWithTheme(<A2UIRenderer payload={payload} />);
    expect(screen.getByTestId('test-row')).toBeInTheDocument();
    expect(screen.getByText('Child 1')).toBeInTheDocument();
    expect(screen.getByText('Child 2')).toBeInTheDocument();
  });

  it('renders multiple root components', () => {
    const payload = JSON.stringify({
      surfaceUpdate: {
        components: [
          {
            id: 'w1',
            type: 'TestWidget',
            childIds: [],
            properties: { label: { literalString: 'Root 1' } },
          },
          {
            id: 'w2',
            type: 'TestWidget',
            childIds: [],
            properties: { label: { literalString: 'Root 2' } },
          },
        ],
      },
    });

    renderWithTheme(<A2UIRenderer payload={payload} />);
    expect(screen.getByText('Root 1')).toBeInTheDocument();
    expect(screen.getByText('Root 2')).toBeInTheDocument();
  });
});
