import { test, expect } from '@playwright/test';
import { main } from './regions';

test.describe('Benchmarks', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/benchmarks');
  });

  test('should display benchmarks page', async ({ page }) => {
    await expect(main(page).getByRole('heading', { name: /Benchmarks/i }).first()).toBeVisible();
  });

  test('should show benchmark tabs', async ({ page }) => {
    // Should have filter tabs for All, Live, Backtest
    await expect(page.getByRole('button', { name: /All/ })).toBeVisible();
    await expect(page.getByRole('button', { name: /Live/ })).toBeVisible();
    await expect(page.getByRole('button', { name: /Backtest/ })).toBeVisible();
  });

  test('should show benchmark list or empty state after loading', async ({ page }) => {
    // Wait for loading to complete - either "Loading benchmarks..." disappears or shows real content
    await page.waitForTimeout(2000); // Allow API call to complete

    const hasBenchmarks = await page.locator('a[href*="/benchmarks/"]:not([href$="/new"])').count() > 0;
    const hasEmptyState = await page.getByText(/no benchmarks|create your first/i).count() > 0;
    const hasEmptyTabs = await page.getByRole('button', { name: /\(0\)/ }).count() > 0;

    expect(hasBenchmarks || hasEmptyState || hasEmptyTabs).toBeTruthy();
  });

  test('should navigate to new benchmark page', async ({ page }) => {
    // Wait for page to load fully
    await page.waitForTimeout(1000);

    // Both the header control and the empty-state control are links pointing at /benchmarks/new;
    // match on href so relabelling the copy cannot break this again.
    await main(page).locator('a[href="/benchmarks/new"]').first().click();

    await page.waitForURL(/\/benchmarks\/new/);

    // Should show benchmark creation form
    await expect(main(page).getByRole('heading').first()).toBeVisible();
  });

  test('should show benchmark details when clicking a benchmark', async ({ page }) => {
    // This used to `test.skip()` when the list was empty. The e2e database is cleaned before every
    // run, so it was empty unless another project happened to have created something first — the
    // test either skipped silently or passed by luck of the parallel schedule, and never once
    // asserted on a benchmark detail page. It now creates its own.
    const benchmarkId = await createBenchmark(page);

    await page.goto('/benchmarks');
    await main(page).locator(`a[href*="/benchmarks/${benchmarkId}"]`).first().click();
    await page.waitForURL(new RegExp(`/benchmarks/${benchmarkId}`));

    await expect(main(page).getByText(/status|performance|return|trades|portfolio/i).first())
      .toBeVisible();
  });
});

/**
 * Creates a benchmark straight through the REST API and returns its id.
 *
 * Two strategies, deliberately: the create endpoint returns a null `benchmarkId` for a single
 * strategy (that path produces a standalone simulation instead, which is what core-loop covers).
 * `page.request` carries the storageState session cookie, so this is authenticated the same way
 * the UI is, and it skips the multi-step wizard core-loop already exercises.
 */
async function createBenchmark(page: import('@playwright/test').Page): Promise<string> {
  const api = 'http://localhost:8085/api/v1';
  const runId = Date.now().toString(36);

  const strategyIds: string[] = [];
  for (const ticker of ['BTC', 'ETH']) {
    const response = await page.request.post(`${api}/strategies`, {
      data: {
        name: `E2E Benchmark ${ticker} ${runId}`,
        markdownContent: [
          '---',
          'version: v1.0',
          'consensus_method: MAJORITY',
          'backtest_schedule:',
          '  interval: 1w',
          'crypto_tickers:',
          `  - ${ticker}`,
          '---',
          '',
          `# ${ticker} benchmark entry`,
          '',
          '## Rules',
          '1. Hold.',
        ].join('\n'),
      },
    });
    expect(response.ok(), `strategy create failed: ${response.status()}`).toBeTruthy();
    strategyIds.push((await response.json()).id);
  }

  const start = new Date(Date.now() - 12 * 24 * 60 * 60 * 1000).toISOString();
  const end = new Date(Date.now() - 1 * 24 * 60 * 60 * 1000).toISOString();
  const response = await page.request.post(`${api}/benchmarks`, {
    data: { strategyIds, startDate: start, endDate: end, name: `E2E Benchmark ${runId}` },
  });
  expect(response.ok(), `benchmark create failed: ${response.status()}`).toBeTruthy();

  const { benchmarkId } = await response.json();
  expect(benchmarkId, 'two strategies must yield a real benchmark, not a standalone simulation')
    .toBeTruthy();
  return benchmarkId;
}
