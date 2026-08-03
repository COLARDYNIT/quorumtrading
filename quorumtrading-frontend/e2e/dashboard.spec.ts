import { test, expect, Page } from '@playwright/test';
import { sidebar, main, navigateVia } from './regions';

/**
 * Dashboard E2E Tests
 *
 * These tests verify the authenticated dashboard functionality.
 *
 * REQUIREMENTS:
 * 1. Backend running at localhost:8085
 * 2. Valid session authentication
 *
 * Run with: npx playwright test --project=authenticated
 *
 * Tests will be skipped if authentication fails (redirected to landing page).
 */

// Helper function that navigates and returns whether we're authenticated
async function navigateToDashboard(page: Page): Promise<boolean> {
  await page.goto('/dashboard');
  // Wait a moment for any redirects
  await page.waitForTimeout(500);
  const url = page.url();
  return url.includes('/dashboard');
}

test.describe('Dashboard Page', () => {
  test.use({ storageState: '.playwright/.auth/user.json' });

  test('should display connection status indicator', async ({ page }) => {
    const isAuthenticated = await navigateToDashboard(page);
    if (!isAuthenticated) {
      test.skip();
      return;
    }

    // Check for the "Connected" status text in the sidebar
    await expect(sidebar(page).getByText('Connected')).toBeVisible();
  });

  test('should display sidebar with QuorumTrading logo', async ({ page }) => {
    const isAuthenticated = await navigateToDashboard(page);
    if (!isAuthenticated) {
      test.skip();
      return;
    }

    // The logo is a link with text "> QuorumTrading"
    const logo = page.getByRole('link', { name: /QuorumTrading/i });
    await expect(logo).toBeVisible();
  });

  test('should display sidebar navigation items', async ({ page }) => {
    const isAuthenticated = await navigateToDashboard(page);
    if (!isAuthenticated) {
      test.skip();
      return;
    }

    // Navigation links are in a navigation element
    const nav = sidebar(page);
    await expect(nav.getByRole('link', { name: 'Overview' })).toBeVisible();
    await expect(nav.getByRole('link', { name: 'Research' })).toBeVisible();
    await expect(nav.getByRole('link', { name: 'Strategies' })).toBeVisible();
  });

  test('should display getting started or active simulations', async ({ page }) => {
    const isAuthenticated = await navigateToDashboard(page);
    if (!isAuthenticated) {
      test.skip();
      return;
    }

    // For new users: Getting Started wizard; For existing users: Active Simulations
    const gettingStarted = main(page).getByText(/spin up your first strategy|active simulations|no live simulations/i);
    await expect(gettingStarted.first()).toBeVisible();
  });

  test('should display welcome message', async ({ page }) => {
    const isAuthenticated = await navigateToDashboard(page);
    if (!isAuthenticated) {
      test.skip();
      return;
    }

    // Should show welcome message with user name
    await expect(page.getByText(/Welcome back|Welcome,|Hey/i).first()).toBeVisible();
  });

  test('should display research sidebar link', async ({ page }) => {
    const isAuthenticated = await navigateToDashboard(page);
    if (!isAuthenticated) {
      test.skip();
      return;
    }

    await expect(sidebar(page).getByRole('link', { name: 'Research' })).toBeVisible();
  });

  test('should display strategy sidebar link', async ({ page }) => {
    const isAuthenticated = await navigateToDashboard(page);
    if (!isAuthenticated) {
      test.skip();
      return;
    }

    await expect(sidebar(page).getByRole('link', { name: 'Strategies' })).toBeVisible();
  });

  test('should display sessions menu', async ({ page }) => {
    const isAuthenticated = await navigateToDashboard(page);
    if (!isAuthenticated) {
      test.skip();
      return;
    }

    // The section is a collapsible button; "Sessions" was renamed to SIMULATIONS.
    await expect(sidebar(page).getByRole('button', { name: /SIMULATIONS/i })).toBeVisible();
  });
});

test.describe('Dashboard Navigation', () => {
  test.use({ storageState: '.playwright/.auth/user.json' });

  test('should navigate to strategies page', async ({ page }) => {
    const isAuthenticated = await navigateToDashboard(page);
    if (!isAuthenticated) {
      test.skip();
      return;
    }

    await navigateVia(page, 'Strategies', /STRATEGIES/i);
    await expect(page).toHaveURL(/\/strategies/);
  });

  test('should navigate to research page', async ({ page }) => {
    const isAuthenticated = await navigateToDashboard(page);
    if (!isAuthenticated) {
      test.skip();
      return;
    }

    await navigateVia(page, 'Research');
    await expect(page).toHaveURL(/\/research/);
  });

  test('should navigate to sessions', async ({ page }) => {
    const isAuthenticated = await navigateToDashboard(page);
    if (!isAuthenticated) {
      test.skip();
      return;
    }

    // Click Benchmarks to expand, then click Live
    await sidebar(page).getByRole('button', { name: /BENCHMARKS/i }).first().click();
    // "Live" exists under both SIMULATIONS and BENCHMARKS; match on href, not label.
    await sidebar(page).locator('a[href*="/benchmarks"]').first().click();
    await expect(page).toHaveURL(/\/benchmarks/);
  });

  test('should navigate home from logo', async ({ page }) => {
    const isAuthenticated = await navigateToDashboard(page);
    if (!isAuthenticated) {
      test.skip();
      return;
    }

    // First navigate somewhere else
    await navigateVia(page, 'Strategies', /STRATEGIES/i);
    await expect(page).toHaveURL(/\/strategies/);

    // Then click logo to go home
    await page.getByRole('link', { name: /QuorumTrading/i }).click();
    await expect(page).toHaveURL('/');
  });
});
