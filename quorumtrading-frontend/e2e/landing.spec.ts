import { test, expect } from '@playwright/test';

/**
 * Landing Page E2E Tests
 *
 * Verifies the public marketing landing page (the v2 "you can read the receipts"
 * redesign). No authentication or backend is required — these assert the static
 * rendered chrome + marketing copy, so they pass with the API offline.
 */
test.describe('Landing Page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('should display the hero heading', async ({ page }) => {
    const hero = page.getByRole('heading', { name: /the receipts/i }).first();
    await expect(hero).toBeVisible();
  });

  test('should display the primary navigation', async ({ page }) => {
    for (const label of ['Why', 'Council', 'Strategies', 'Backtests']) {
      await expect(
        page.getByRole('link', { name: label, exact: true }).first()
      ).toBeVisible();
    }
  });

  test('should display the auth call-to-action', async ({ page }) => {
    await expect(page.getByText('Sign in', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('Start free', { exact: true }).first()).toBeVisible();
  });

  test('should display The Council section', async ({ page }) => {
    const heading = page.getByRole('heading', { name: /Three specialists/i });
    await heading.scrollIntoViewIfNeeded();
    await expect(heading).toBeVisible();
  });

  test('should display the backtest section', async ({ page }) => {
    const heading = page.getByRole('heading', { name: /Years of receipts/i });
    await heading.scrollIntoViewIfNeeded();
    await expect(heading).toBeVisible();
  });

  test('should display the closing call-to-action', async ({ page }) => {
    const heading = page.getByRole('heading', {
      name: /Open the council on your next trade/i,
    });
    await heading.scrollIntoViewIfNeeded();
    await expect(heading).toBeVisible();
  });
});
