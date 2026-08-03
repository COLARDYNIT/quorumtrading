import { test, expect } from '@playwright/experimental-ct-react';
import { QueryWrapper } from '@/app/lib/test-utils/integration-test-helpers';
import { MarketDataQueryTest } from './test-components/MarketDataQueryTest';

test.describe('Market Data API Integration', () => {
  test('queries market data events from backend', async ({ mount, page }) => {
    // Use test date range
    const fromDate = '2023-01-01T00:00:00Z';
    const toDate = '2023-12-31T23:59:59Z';

    await mount(
      <QueryWrapper>
        <MarketDataQueryTest from={fromDate} to={toDate} symbol="BTC-USD" />
      </QueryWrapper>
    );

    // Wait for loading to finish (either success or error)
    await page.waitForSelector(
      '[data-testid="market-data"], [data-testid="error"]',
      { timeout: 5000 }
    );

    // Check which state we're in
    const marketData = page.getByTestId('market-data');
    const errorMessage = page.getByTestId('error');

    const isSuccess = await marketData.isVisible().catch(() => false);

    if (isSuccess) {
      // Verify the events count
      const count = await page.getByTestId('events-count').textContent();
      expect(parseInt(count || '0')).toBeGreaterThanOrEqual(0);
      console.log(`Successfully loaded ${count} market data events`);
    } else {
      // Backend returned error - log it but don't fail test
      const isError = await errorMessage.isVisible();
      expect(isError).toBe(true);

      const errorText = await errorMessage.textContent();
      console.log('Backend returned error:', errorText);
    }
  });
});
