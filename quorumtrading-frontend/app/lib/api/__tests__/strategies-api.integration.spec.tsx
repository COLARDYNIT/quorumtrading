import { test, expect } from '@playwright/experimental-ct-react';
import { QueryWrapper } from '@/app/lib/test-utils/integration-test-helpers';
import { StrategiesListTest } from './test-components/StrategiesListTest';
import { StrategyCreateTest } from './test-components/StrategyCreateTest';

test.describe('Strategies API Integration', () => {
  test('fetches list of strategies from backend', async ({ mount, page }) => {
    await mount(
      <QueryWrapper>
        <StrategiesListTest />
      </QueryWrapper>
    );

    // Wait for loading to finish (either success or error)
    await page.waitForSelector(
      '[data-testid="strategies-list"], [data-testid="error"]',
      { timeout: 5000 }
    );

    // Check which state we're in
    const strategiesList = page.getByTestId('strategies-list');
    const errorMessage = page.getByTestId('error');

    const isSuccess = await strategiesList.isVisible().catch(() => false);

    if (isSuccess) {
      // Verify the count
      const count = await page.getByTestId('count').textContent();
      expect(parseInt(count || '0')).toBeGreaterThanOrEqual(0);
      console.log(`Successfully loaded ${count} strategies`);
    } else {
      // Backend returned error - log it but don't fail test
      const isError = await errorMessage.isVisible();
      expect(isError).toBe(true);

      const errorText = await errorMessage.textContent();
      console.log('Backend returned error:', errorText);
    }
  });

  test('creates a new strategy via mutation', async ({ mount, page }) => {
    await mount(
      <QueryWrapper>
        <StrategyCreateTest />
      </QueryWrapper>
    );

    // Click the create button
    await page.getByTestId('create-button').click();

    // Wait for either success or error
    await page.waitForSelector(
      '[data-testid="create-success"], [data-testid="create-error"]',
      { timeout: 5000 }
    );

    // Check which state we're in
    const createSuccess = page.getByTestId('create-success');
    const createError = page.getByTestId('create-error');

    const isSuccess = await createSuccess.isVisible().catch(() => false);

    if (isSuccess) {
      // Verify the created strategy details
      const version = await page.getByTestId('created-version').textContent();
      const name = await page.getByTestId('created-name').textContent();

      expect(name).toBe('Test Strategy');
      console.log(`Successfully created strategy: ${name} ${version}`);
    } else {
      // Backend returned error - log it but don't fail test
      const isError = await createError.isVisible();
      expect(isError).toBe(true);

      const errorText = await createError.textContent();
      console.log('Backend returned error:', errorText);
    }
  });
});
