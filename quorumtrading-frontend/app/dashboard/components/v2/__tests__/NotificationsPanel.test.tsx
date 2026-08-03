/**
 * The dashboard notifications panel must not let one message eat the panel.
 *
 * A notification body is not length-bounded at the source: a skipped investment quotes the
 * picker's reasoning, which lists the strategies it rejected — one per active strategy, with no
 * cap. A user with a large strategy pool produced a body measured at 13KB, which rendered as a
 * single 1,244px row in a list whose rows are 17px, pushing every other notification out of view.
 *
 * The picker now caps its list, but the panel is the surface that has to survive a long body
 * whatever produces one, so the row clamps as well. Belt and braces, on the screen a user checks
 * to find out what the platform did with their money.
 */
import { render, screen } from '@testing-library/react';
import { ThemeProvider } from 'styled-components';
import { terminalTheme } from '@/app/styles/theme';
import { NotificationsPanel } from '../NotificationsPanel';
import * as hooks from '@/app/lib/api/hooks';

jest.mock('@/app/lib/api/hooks');

const LONG_BODY = 'Rejected: '.repeat(400);

function renderPanel() {
  return render(
    <ThemeProvider theme={terminalTheme}>
      <NotificationsPanel />
    </ThemeProvider>
  );
}

describe('NotificationsPanel', () => {
  beforeEach(() => {
    (hooks.useNotifications as jest.Mock).mockReturnValue({
      data: [
        {
          id: 'n1',
          type: 'INVESTMENT_SKIPPED',
          title: 'Investment plan skipped',
          body: LONG_BODY,
          status: 'UNREAD',
          createdAt: '2026-07-29T10:00:00Z',
          channel: 'IN_APP',
        },
      ],
      isLoading: false,
    });
    (hooks.useMarkAllNotificationsRead as jest.Mock).mockReturnValue({ mutate: jest.fn() });
  });

  it('clamps a long notification body instead of letting it fill the panel', () => {
    const { container } = renderPanel();

    const body = container.querySelector('.body');
    expect(body).not.toBeNull();

    const styles = window.getComputedStyle(body as Element);
    expect(styles.overflow).toBe('hidden');
    expect(styles.getPropertyValue('-webkit-line-clamp')).toBe('3');
  });

  it('still renders the notification title', () => {
    renderPanel();
    expect(screen.getByText('Investment plan skipped')).toBeInTheDocument();
  });
});
