import { test, expect } from '@playwright/experimental-ct-react';
import { QueryWrapper } from '@/app/lib/test-utils/integration-test-helpers';

test.describe('Playwright Setup Verification', () => {
  test('backend is accessible', async ({ request }) => {
    const response = await request.get('http://localhost:8080/q/health');

    expect(response.status()).toBe(200);
    expect(response.ok()).toBeTruthy();
  });

  test('frontend dev server is accessible', async ({ page }) => {
    await page.goto('http://localhost:3000');
    expect(page.url()).toContain('localhost:3000');
  });

  test('can mount a simple React component', async ({ mount }) => {
    const TestComponent = () => <div data-testid="test">Hello Playwright CT</div>;

    const component = await mount(<TestComponent />);

    await expect(component.getByTestId('test')).toBeVisible();
    await expect(component.getByTestId('test')).toHaveText('Hello Playwright CT');
  });

  test('can mount component with QueryWrapper', async ({ mount }) => {
    const TestComponent = () => <div data-testid="query-test">With Query Provider</div>;

    const component = await mount(
      <QueryWrapper>
        <TestComponent />
      </QueryWrapper>
    );

    await expect(component.getByTestId('query-test')).toBeVisible();
    await expect(component.getByTestId('query-test')).toHaveText('With Query Provider');
  });
});
