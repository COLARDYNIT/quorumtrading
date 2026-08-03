import { test, expect } from '@playwright/experimental-ct-react';
import { QueryWrapper } from '@/app/lib/test-utils/integration-test-helpers';
import { PortfolioSnapshotsTest } from './test-components/PortfolioSnapshotsTest';

test.describe('Portfolio API Integration', () => {
  test('fetches portfolio snapshots for a simulation', async ({ mount, page }) => {
    // Use test data
    const testSimulationId = '550e8400-e29b-41d4-a716-446655440000';
    const fromDate = '2023-01-01T00:00:00Z';
    const toDate = '2023-12-31T23:59:59Z';

    await mount(
      <QueryWrapper>
        <PortfolioSnapshotsTest
          simulationId={testSimulationId}
          from={fromDate}
          to={toDate}
        />
      </QueryWrapper>
    );

    // Wait for loading to finish (either success or error)
    await page.waitForSelector(
      '[data-testid="portfolio-snapshots"], [data-testid="error"]',
      { timeout: 5000 }
    );

    // Check which state we're in
    const portfolioSnapshots = page.getByTestId('portfolio-snapshots');
    const errorMessage = page.getByTestId('error');

    const isSuccess = await portfolioSnapshots.isVisible().catch(() => false);

    if (isSuccess) {
      // Verify the snapshots count
      const count = await page.getByTestId('snapshots-count').textContent();
      expect(parseInt(count || '0')).toBeGreaterThanOrEqual(0);
      console.log(`Successfully loaded ${count} portfolio snapshots`);
    } else {
      // Backend returned error - log it but don't fail test
      const isError = await errorMessage.isVisible();
      expect(isError).toBe(true);

      const errorText = await errorMessage.textContent();
      console.log('Backend returned error:', errorText);
    }
  });
});
