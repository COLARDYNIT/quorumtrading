import { test, expect } from '@playwright/test';
import { main } from './regions';

test.describe('Strategies', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/strategies');
  });

  test('should display strategies list page', async ({ page }) => {
    // The heading is "Trading Strategies"
    await expect(main(page).getByRole('heading', { name: /Trading Strategies|Strategies/i })).toBeVisible();
  });

  test('should show strategy tabs or empty state', async ({ page }) => {
    // Wait for the page to load - either shows strategies or tabs with (0) count
    const hasStrategies = await page.locator('a[href*="/strategies/"]:not([href$="/new"])').count() > 0;
    const hasEmptyTabs = await page.getByRole('button', { name: /\(0\)/ }).count() > 0;
    const hasNewButton = await page.getByRole('button', { name: /\+ New Strategy/ }).count() > 0;

    expect(hasStrategies || hasEmptyTabs || hasNewButton).toBeTruthy();
  });

  test('should navigate to strategy details when clicking a strategy', async ({ page }) => {
    // Check if there are any strategies by looking for links to strategy details
    const strategyLinks = page.locator('a[href*="/strategies/"]:not([href$="/new"])');

    if (await strategyLinks.count() > 0) {
      await strategyLinks.first().click();
      await page.waitForURL(/\/strategies\/.+/);

      // Should show strategy details - look for common strategy page elements
      await expect(page.locator('text=/overview|configuration|entry|exit|position|backtest/i').first()).toBeVisible();
    } else {
      // Skip if no strategies exist
      test.skip();
    }
  });
});
