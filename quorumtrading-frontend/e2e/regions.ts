import type { Page, Locator } from '@playwright/test';

/**
 * Region helpers for e2e selectors.
 *
 * `DashboardLayout` mounts the sidebar twice on purpose — a desktop one hidden below 968px and
 * a mobile drawer hidden above it. Both live in the DOM at all times, so a page-wide locator
 * like `page.getByRole('link', { name: 'Strategies' })` resolves to several elements and trips
 * Playwright's strict mode. Nav labels also collide with page headings ("Benchmarks" is both a
 * sidebar link and an <h1>).
 *
 * Always scope to a region. These helpers are the sanctioned way to do it.
 */

/** The desktop sidebar — the one a 1280px-wide test viewport actually shows. */
export function sidebar(page: Page): Locator {
  return page.getByTestId('sidebar-desktop');
}

/** Page content, excluding chrome. Use for headings and anything the page itself renders. */
export function main(page: Page): Locator {
  return page.getByTestId('main-content');
}

/**
 * Click a sidebar link, expanding its collapsible section first when one is given.
 *
 * The nav groups links under expandable section buttons (STRATEGIES, SIMULATIONS, BENCHMARKS…).
 * A collapsed section's header button overlaps its own sub-links, so clicking the link directly
 * times out on "intercepts pointer events" even though the link reports as visible.
 */
export async function navigateVia(
  page: Page,
  item: string | RegExp,
  section?: string | RegExp,
): Promise<void> {
  const sb = sidebar(page);
  if (section) {
    const header = sb.getByRole('button', { name: section }).first();
    if (await header.count()) {
      await header.click();
    }
  }
  await sb.getByRole('link', { name: item }).first().click();
}
