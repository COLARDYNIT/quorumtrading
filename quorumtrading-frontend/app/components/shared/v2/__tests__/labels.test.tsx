import 'jest-styled-components';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider } from 'styled-components';
import { terminalTheme } from '@/app/styles/theme';
import { StatusPill } from '../StatusPill';
import { Tag } from '../Tag';
import { Badge } from '../Badge';
import { Switch } from '../Switch';
import { RowAction, TextAction } from '../RowAction';
import { ActionPill } from '../ActionPill';

/**
 * Guards for the DS v0.4.0 label overhaul.
 *
 * These assert the *decisions*, not the pixels — each one is a rule the Aug 2026 census was
 * called to enforce, and each is a rule a future edit could quietly break without any page
 * looking obviously wrong.
 */

function mount(ui: React.ReactElement) {
  return render(<ThemeProvider theme={terminalTheme}>{ui}</ThemeProvider>);
}

const MONO = 'IBM Plex Mono';
const RADIUS = '2px';

describe('A2 — one radius, one voice', () => {
  it('gives the status pill the label radius, not a round pill', () => {
    mount(<StatusPill kind="live">Live</StatusPill>);
    // The round-pill exception is retired; 9999px here would be the regression.
    expect(screen.getByText('Live')).toHaveStyleRule('border-radius', RADIUS);
  });

  it('renders pills and tags in uppercase mono', () => {
    const { unmount } = mount(<StatusPill kind="done">Done</StatusPill>);
    expect(screen.getByText('Done')).toHaveStyleRule('text-transform', 'uppercase');
    expect(screen.getByText('Done')).toHaveStyleRule('font-family', expect.stringContaining(MONO));
    unmount();

    mount(<Tag>plan</Tag>);
    expect(screen.getByText('plan')).toHaveStyleRule('text-transform', 'uppercase');
    expect(screen.getByText('plan')).toHaveStyleRule('font-family', expect.stringContaining(MONO));
  });

  it('never case-transforms identifier tags', () => {
    // Uppercasing an identifier makes it wrong to copy and hides the stored casing.
    mount(<Tag id>an@colardyn-it.be</Tag>);
    expect(screen.getByText('an@colardyn-it.be')).toHaveStyleRule('text-transform', 'none');
  });
});

describe('two tint stops — color is the only per-kind property', () => {
  it('derives border and fill from currentColor rather than a per-kind literal', () => {
    mount(<StatusPill kind="failed">Failed</StatusPill>);
    const pill = screen.getByText('Failed');
    expect(pill).toHaveStyleRule('border', expect.stringContaining('color-mix'));
    expect(pill).toHaveStyleRule('background', expect.stringContaining('currentColor'));
  });

  it('changes only the color between two different kinds', () => {
    const { unmount } = mount(<StatusPill kind="live">A</StatusPill>);
    const liveBg = screen.getByText('A');
    const sharedBackground = liveBg.className && getComputedStyle(liveBg).background;
    unmount();

    mount(<StatusPill kind="paper">B</StatusPill>);
    // Both kinds resolve their fill through the same currentColor mix, so the declared
    // background rule is identical — only `color` differs.
    expect(screen.getByText('B')).toHaveStyleRule('background', expect.stringContaining('currentColor'));
    expect(typeof sharedBackground).toBe('string');
  });
});

describe('motion axis — the dot animates only while work happens', () => {
  // styled-components hashes the keyframes name, so the timing is what identifies the pulse.
  const PULSE = expect.stringContaining('1.4s ease-in-out infinite');
  const DOT = { modifier: '& > .dot' };

  it('animates the dot for in-flight kinds', () => {
    mount(<StatusPill kind="simulating">Simulating</StatusPill>);
    expect(screen.getByText('Simulating')).toHaveStyleRule('animation', PULSE, DOT);
  });

  it('leaves the dot still for settled kinds', () => {
    mount(<StatusPill kind="completed">Completed</StatusPill>);
    expect(screen.getByText('Completed')).not.toHaveStyleRule('animation', PULSE, DOT);
  });

  it('lets a call site opt a live pill out of the pulse', () => {
    // A live run that is not currently progressing must stop claiming that it is.
    mount(
      <StatusPill kind="live" still>
        Live
      </StatusPill>
    );
    expect(screen.getByText('Live')).not.toHaveStyleRule('animation', PULSE, DOT);
  });
});

describe('solid fill is reserved for real money', () => {
  it('fills a live money pill with the brand', () => {
    mount(
      <StatusPill kind="live" money>
        Live
      </StatusPill>
    );
    expect(screen.getByText('Live')).toHaveStyleRule('background', terminalTheme.colors.brand);
  });

  it('leaves an ordinary live pill outlined', () => {
    mount(<StatusPill kind="live">Live</StatusPill>);
    expect(screen.getByText('Live')).toHaveStyleRule('background', expect.stringContaining('color-mix'));
  });

  it('keeps paper-toned live visually separate from funded live', () => {
    mount(
      <StatusPill kind="live" paperToned>
        Live
      </StatusPill>
    );
    expect(screen.getByText('Live')).toHaveStyleRule('opacity', '0.85', { modifier: '& > .dot' });
  });
});

describe('SKIP keeps its dashed border', () => {
  it('renders SKIP as "skip", not as a hold', () => {
    // Regression guard: SKIP rows once rendered as amber HOLD pills.
    mount(<ActionPill action="skip" />);
    expect(screen.getByText('skip')).toBeInTheDocument();
    expect(screen.queryByText('hold')).not.toBeInTheDocument();
  });

  it('dashes the border so a non-decision cannot read as a decision', () => {
    mount(<ActionPill action="skip" />);
    expect(screen.getByText('skip')).toHaveStyleRule('border-style', 'dashed');
  });

  it('leaves a real decision solid-bordered', () => {
    mount(<ActionPill action="buy" />);
    expect(screen.getByText('buy')).not.toHaveStyleRule('border-style', 'dashed');
  });
});

describe('badge, switch and row actions', () => {
  it('renders a count badge', () => {
    mount(<Badge>12 uses</Badge>);
    expect(screen.getByText('12 uses')).toHaveStyleRule('border-radius', RADIUS);
  });

  it('exposes the switch as a real switch control', () => {
    // The census found two hand-rolled <span> switches that were neither keyboard-operable
    // nor announced; the atom must not repeat that.
    mount(<Switch checked={false} onChange={() => {}} label="Email alerts" />);
    const sw = screen.getByRole('switch', { name: 'Email alerts' });
    expect(sw).toHaveAttribute('aria-checked', 'false');
  });

  it('toggles on click', async () => {
    const onChange = jest.fn();
    mount(<Switch checked={false} onChange={onChange} label="Email alerts" />);
    await userEvent.click(screen.getByRole('switch', { name: 'Email alerts' }));
    expect(onChange).toHaveBeenCalledWith(true);
  });

  it('ships the verb marker CSS-side so callers pass no icon', () => {
    mount(<RowAction onClick={() => {}}>Open</RowAction>);
    expect(screen.getByRole('button', { name: 'Open' })).toHaveStyleRule('content', "'›'", {
      modifier: '&::before',
    });
  });

  it('fires the row action', async () => {
    const onClick = jest.fn();
    mount(<RowAction onClick={onClick}>Open</RowAction>);
    await userEvent.click(screen.getByRole('button', { name: 'Open' }));
    expect(onClick).toHaveBeenCalled();
  });

  it('renders the boxless tertiary action without a box', () => {
    mount(<TextAction onClick={() => {}}>Clear</TextAction>);
    expect(screen.getByRole('button', { name: 'Clear' })).toHaveStyleRule('border', 'none');
  });
});
