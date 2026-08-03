import { test, expect } from '@playwright/test';
import { main } from './regions';

/**
 * Settings E2E Tests
 *
 * These tests verify the settings pages functionality including:
 * - Main settings page with navigation cards
 * - LLM Usage page with statistics
 * - Budget page
 * - Broker accounts page
 *
 * REQUIREMENTS:
 * 1. Backend running at localhost:8085
 * 2. Valid session authentication
 *
 * Run with: npx playwright test --project=settings
 */

test.describe('Settings', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/settings');
    // Wait for the page content to fully load (SectionTitle uses h2)
    await page.getByTestId('main-content').getByRole('heading', { name: /Settings/i }).first()
      .waitFor({ timeout: 10000 });
  });

  test('should display settings page with title', async ({ page }) => {
    await expect(main(page).getByRole('heading', { name: /Settings/i }).first()).toBeVisible();
  });

  test('should display AI Budget settings card', async ({ page }) => {
    await expect(page.locator('main').getByText('AI Budget')).toBeVisible();
  });

  test('should display Broker Accounts settings card', async ({ page }) => {
    await expect(page.locator('main').getByText('Broker Accounts')).toBeVisible();
  });

  test('should display AI Usage settings card', async ({ page }) => {
    await expect(page.locator('main h3').getByText('AI Usage')).toBeVisible();
  });

  test('should navigate to budget page and show title', async ({ page }) => {
    await main(page).getByRole('link', { name: /AI Budget/i }).first().click();
    await page.waitForURL('**/settings/budget');
    await expect(main(page).getByRole('heading', { name: /AI Budget/i }).first()).toBeVisible();
  });

  test('should navigate to broker accounts page and show title', async ({ page }) => {
    await main(page).getByRole('link', { name: /Broker Accounts/i }).first().click();
    await page.waitForURL('**/settings/broker-accounts');
    await expect(main(page).getByRole('heading', { name: /Broker Accounts/i }).first()).toBeVisible();
  });

  test('should navigate to usage page and show title', async ({ page }) => {
    await main(page).getByRole('link', { name: /AI Usage/i }).first().click();
    await page.waitForURL('**/settings/usage');
    await expect(main(page).getByRole('heading', { name: /LLM Usage/i }).first()).toBeVisible();
  });

  test('usage page should display description text', async ({ page }) => {
    await main(page).getByRole('link', { name: /AI Usage/i }).first().click();
    await page.waitForURL('**/settings/usage');
    // Verify both title and description are visible
    await expect(main(page).getByRole('heading', { name: /LLM Usage/i }).first()).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/Track your AI usage costs/i)).toBeVisible();
  });
});
