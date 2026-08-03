import { test, expect } from '@playwright/test';

/**
 * Core loop e2e: login → create strategy → run a backtest → see results.
 *
 * Deterministic and free:
 * - Backend must run with QUARKUS_PROFILE=e2e — that profile enables the dummy LLM provider
 *   (canned JSON decisions: BUY when flat, HOLD once holding) and seeds 16 days of BTC price
 *   bars + news at startup (E2eMarketDataSeeder), so no external API and no LLM spend.
 * - The strategy wizard defaults (MAJORITY consensus, 1w crypto backtest interval) plus a
 *   12d-ago → 1d-ago backtest window give a short, deterministic run: the executor's first
 *   evaluation is at the start date itself (SimulationExecutor starts the loop at startTime),
 *   then every interval after it. The end date must be in the past in every timezone — the
 *   wizard sends the local clock-face as UTC — or the end-simulation scheduler can't complete
 *   the run. The window also has to sit inside the 16 days of seeded BTC data.
 * - After the evaluation the engine transitions the caught-up backtest SIMULATING → LIVE;
 *   the end-simulation scheduler (10s under %e2e) then flips it LIVE → COMPLETED.
 *
 * Run locally:
 *   cd quorumtrading-backend && QUARKUS_PROFILE=e2e ./mvnw quarkus:dev   # port 8085
 *   cd quorumtrading-frontend && npx playwright test --project=core-loop
 *
 * Optional real-model smoke (local only, never CI — needs a secret):
 *   OPENROUTER_API_KEY=sk-or-... QUARKUS_PROFILE=e2e java \
 *     -Dquorumtrading.llm.local.enabled=false \
 *     -Dquorumtrading.llm.router.provider-order=openrouter \
 *     -jar quorumtrading-bootstrap/target/quarkus-app/quarkus-run.jar
 *   REAL_LLM=1 npx playwright test --project=core-loop
 *   (~$0.0003/run with the configured Mistral fallback model; the BUY assertion relaxes to
 *   "some consensus decision exists" because a real model is non-deterministic.)
 */

const REAL_LLM = process.env.REAL_LLM === '1';

// Unique suffix so re-runs against a still-running backend never collide on names
const runId = Date.now().toString(36);
const STRATEGY_NAME = `E2E Core Loop Strategy ${runId}`;
const SIMULATION_NAME = `E2E Core Loop Backtest ${runId}`;

// Shared across the serial tests (same worker process)
let strategyUrl = '';
let simulationUrl = '';

test.describe.configure({ mode: 'serial' });

test.describe('core loop: strategy → backtest → results', () => {
  test('create a BTC strategy through the wizard', async ({ page }) => {
    await page.goto('/strategies/new');

    // --- Step 1/4: Assets ---
    await page.getByPlaceholder('Auto-generated if left empty').fill(STRATEGY_NAME);
    await page.getByRole('button', { name: /24\/7 trading/i }).click();
    // Popular-suggestion chips are a local constant — no server dependency
    await page.getByRole('button', { name: 'BTC', exact: true }).click();
    await page.getByRole('button', { name: /Continue/ }).click();

    // --- Step 2/4: Decision method (default Majority Vote — no orchestrator LLM call) ---
    await expect(page.getByText('Decision method')).toBeVisible();
    await page.getByRole('button', { name: /Continue/ }).click();

    // --- Step 3/4: Instructions ---
    await page
      .getByPlaceholder(/Describe your trading strategy/)
      .fill('Buy BTC when no position is held. Hold once a position exists. Keep it simple.');
    await page.getByRole('button', { name: /Continue/ }).click();

    // --- Step 4/4: Review + create ---
    await expect(page.getByText('Step 4 of 4')).toBeVisible();
    await page.getByRole('button', { name: 'Create Strategy' }).click();

    // Lands on the strategy detail page showing the new strategy
    await page.waitForURL(/\/strategies\/[0-9a-f-]{36}/, { timeout: 20_000 });
    await expect(page.getByText(STRATEGY_NAME).first()).toBeVisible({ timeout: 15_000 });
    strategyUrl = page.url();
  });

  test('launch a backtest simulation for the strategy', async ({ page }) => {
    expect(strategyUrl, 'strategy test must have created a strategy').toBeTruthy();
    await page.goto(strategyUrl);

    // "Promote to a sim" CTA → /benchmarks/new?strategyId=… ("Launch simulation" wizard)
    await page.getByRole('link', { name: /New simulation/i }).first().click();
    await page.waitForURL(/\/benchmarks\/new\?strategyId=/, { timeout: 15_000 });
    await expect(page.getByText('Launch simulation')).toBeVisible();

    // Mode defaults to Backtest. Switch the period selector to a custom range:
    // 12d-ago → 1d-ago (> the strategy's 1w backtest interval → one evaluation) inside
    // the 16-day seeded BTC data range, with the end date safely in the past even after
    // the wizard's local-clock-face → "Z" conversion.
    await page.getByRole('button', { name: /Custom Range/i }).click();
    const pad = (n: number) => String(n).padStart(2, '0');
    const clockFace = (d: Date) =>
      `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:00`;
    const start = new Date(Date.now() - 12 * 24 * 60 * 60 * 1000);
    const end = new Date(Date.now() - 1 * 24 * 60 * 60 * 1000);
    await page.getByLabel('Start Date').fill(clockFace(start));
    await page.getByLabel('End Date').fill(clockFace(end));

    await page.getByLabel('Benchmark Name').fill(SIMULATION_NAME);
    await page.getByRole('button', { name: 'Create Benchmark' }).click();

    // Single-strategy launch navigates straight to the new simulation's detail page
    await page.waitForURL(/\/simulations\/[0-9a-f-]{36}/, { timeout: 20_000 });
    simulationUrl = page.url();
  });

  test('backtest completes and shows a BUY decision with real results', async ({ page }) => {
    // The poll below is allowed 90s, but Playwright's per-test default is 30s — so the test was
    // killed at 30s and the remaining 60s of polling never happened. Any diagnosis based on
    // "it polled for 90s and never completed" was reading a run that stopped a third of the way in.
    test.setTimeout(150_000);
    expect(simulationUrl, 'launch test must have created a simulation').toBeTruthy();
    await page.goto(simulationUrl);

    // Scope to the page content — the sidebar nav also contains the word "Completed"
    // (Challenges → Completed), which must never satisfy the status assertion.
    const main = page.locator('main');

    // One dummy-LLM evaluation completes in seconds, then: SIMULATING → LIVE (caught up)
    // → COMPLETED via the 10s end-simulation scheduler. The 30s pickup scheduler is the
    // worst-case retry if the data-ready event loses its race. Reload-poll up to 90s.
    await expect(async () => {
      await page.reload();
      await expect(main.getByText('Completed', { exact: true }).first()).toBeVisible({ timeout: 3_000 });
    }).toPass({ timeout: 90_000, intervals: [3_000] });

    if (REAL_LLM) {
      // Non-deterministic model: just require that a consensus decision was recorded
      await expect(main.getByText(/^(buy|sell|hold)$/i).first()).toBeVisible({ timeout: 15_000 });
    } else {
      // Deterministic dummy LLM: the single evaluation is a BUY of BTC
      await expect(main.getByText(/^buy$/i).first()).toBeVisible({ timeout: 15_000 });

      // The BUY opened a real BTC position (holdings table row + non-zero equity)
      await expect(main.getByText('Holdings & cash')).toBeVisible();
      await expect(main.getByText('Total equity')).toBeVisible();
      await expect(main.getByText('BTC').first()).toBeVisible();

      // All three agents voted (agent council shows per-agent activity)
      await expect(main.getByText('Agent council')).toBeVisible();
      await expect(main.getByText('Technical').first()).toBeVisible();
      await expect(main.getByText('Fundamental').first()).toBeVisible();
      await expect(main.getByText('Sentiment').first()).toBeVisible();
    }
  });
});
