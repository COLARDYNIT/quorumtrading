import { test, expect } from '@playwright/test';
import { main } from './regions';

test.describe('Market Research', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/research');
  });

  test('should display market research list page', async ({ page }) => {
    // Check for the main Market Research h1 heading
    await expect(main(page).getByRole('heading', { name: /Research/i }).first()).toBeVisible();
  });

  test('should navigate to create new research page', async ({ page }) => {
    // The create control is a link labelled "+ NEW RESEARCH" (it used to be a button).
    await main(page).getByRole('link', { name: /NEW RESEARCH/i }).first().click();
    await page.waitForURL('**/research/new');
    await expect(main(page).getByRole('heading', { name: /Research/i }).first()).toBeVisible();
  });

  test('should create a new market research', async ({ page }) => {
    // Navigate to create page
    await page.goto('/research/new');

    // Fill in the name field (using placeholder text to locate)
    const nameField = page.getByRole('textbox', { name: /name/i }).or(
      page.locator('input[placeholder*="Weekly"]')
    );
    await nameField.fill('E2E Test Research');

    // Fill description/instructions field - look for the larger textarea
    const instructionsField = page.locator('textarea').first();
    await instructionsField.fill('Find momentum strategies for BTC. Look for RSI and MACD signals.');

    // Wait for the button to be enabled
    await page.waitForTimeout(500); // Allow form state to update

    // Submit the form
    const createButton = page.locator('button:has-text("Create Research")');
    await expect(createButton).toBeEnabled({ timeout: 5000 });
    await createButton.click();

    // Wait for redirect to detail page or list
    await page.waitForURL(/\/research\/.+/, { timeout: 10000 });

    // Verify research was created - look for the name in heading or content
    await expect(page.locator('text=E2E Test Research').first()).toBeVisible({ timeout: 10000 });
  });

  test('should execute market research', async ({ page }) => {
    // First create a research
    await page.goto('/research/new');

    const nameField = page.locator('input[placeholder*="Weekly"]').or(
      page.getByRole('textbox', { name: /name/i })
    );
    await nameField.fill('E2E Execute Test');

    const instructionsField = page.locator('textarea').first();
    await instructionsField.fill('Analyze BTC market trends');

    await page.waitForTimeout(500);

    const createButton = page.locator('button:has-text("Create Research")');
    await expect(createButton).toBeEnabled({ timeout: 5000 });
    await createButton.click();

    // Wait for redirect
    await page.waitForURL(/\/research\/.+/, { timeout: 10000 });

    // Click execute button - can be "Execute Now" or "Run First Execution"
    const executeButton = page.locator('button:has-text("Execute Now"), button:has-text("Run First Execution")');
    await executeButton.first().click();

    // Wait for execution to be added to history - look for an execution entry
    // After execution starts, we should see either status indicators or the execution in history
    await expect(
      page.locator('text=/COMPLETED|IN_PROGRESS|PENDING|FAILED|execution/i').first()
    ).toBeVisible({ timeout: 30000 });
  });
});
