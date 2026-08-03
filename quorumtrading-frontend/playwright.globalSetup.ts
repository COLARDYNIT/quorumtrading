import { chromium, FullConfig } from '@playwright/test';

async function globalSetup(config: FullConfig) {
  console.log('🔍 Checking backend availability at http://localhost:8085...');

  const browser = await chromium.launch();
  const page = await browser.newPage();

  try {
    // Check backend health endpoint
    const response = await page.goto('http://localhost:8085/q/health', {
      timeout: 5000,
      waitUntil: 'domcontentloaded'
    });

    if (!response || response.status() !== 200) {
      await browser.close();
      throw new Error(
        '\n\n' +
        '❌ Backend not running or unhealthy!\n\n' +
        'Integration tests require backend at http://localhost:8085\n\n' +
        'Start backend with:\n' +
        '  cd ../quorumtrading-backend\n' +
        '  ./mvnw quarkus:dev\n\n' +
        'Then re-run tests.\n'
      );
    }

    console.log('✅ Backend health check passed (http://localhost:8085)');
  } catch (error) {
    await browser.close();

    if (error instanceof Error && error.message.includes('net::ERR_CONNECTION_REFUSED')) {
      throw new Error(
        '\n\n' +
        '❌ Cannot connect to backend at http://localhost:8085\n\n' +
        'Start backend with:\n' +
        '  cd ../quorumtrading-backend\n' +
        '  ./mvnw quarkus:dev\n\n' +
        'Then re-run tests.\n'
      );
    }

    throw error;
  }

  await browser.close();
}

export default globalSetup;
