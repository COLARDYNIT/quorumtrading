import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: 'html',
  globalSetup: './playwright.global-setup.ts',
  use: {
    baseURL: 'http://localhost:3000',
    trace: 'on-first-retry',
  },
  projects: [
    // Setup project for authentication
    {
      name: 'setup',
      testMatch: /.*\.setup\.ts/,
    },
    // Public tests - no authentication required
    {
      name: 'public',
      testMatch: /landing\.spec\.ts/,
      use: { ...devices['Desktop Chrome'] },
    },
    // Authenticated tests - depends on setup
    {
      name: 'authenticated',
      testMatch: /dashboard\.spec\.ts/,
      dependencies: ['setup'],
      use: {
        ...devices['Desktop Chrome'],
        storageState: '.playwright/.auth/user.json',
      },
    },
    // Market Research tests
    {
      name: 'research',
      testMatch: /research\.spec\.ts/,
      dependencies: ['setup'],
      use: {
        ...devices['Desktop Chrome'],
        storageState: '.playwright/.auth/user.json',
      },
    },
    // Strategy tests
    {
      name: 'strategy',
      testMatch: /strategy\.spec\.ts/,
      dependencies: ['setup'],
      use: {
        ...devices['Desktop Chrome'],
        storageState: '.playwright/.auth/user.json',
      },
    },
    // Backtest tests
    {
      name: 'backtest',
      testMatch: /backtest\.spec\.ts/,
      dependencies: ['setup'],
      use: {
        ...devices['Desktop Chrome'],
        storageState: '.playwright/.auth/user.json',
      },
    },
    // Core loop: login → create strategy → run backtest → see results.
    // Deterministic + free: requires the backend running with QUARKUS_PROFILE=e2e
    // (dummy LLM provider + seeded BTC market data). See e2e/core-loop.spec.ts.
    {
      name: 'core-loop',
      testMatch: /core-loop\.spec\.ts/,
      dependencies: ['setup'],
      use: {
        ...devices['Desktop Chrome'],
        storageState: '.playwright/.auth/user.json',
      },
    },
    // Settings tests
    {
      name: 'settings',
      testMatch: /settings\.spec\.ts/,
      dependencies: ['setup'],
      use: {
        ...devices['Desktop Chrome'],
        storageState: '.playwright/.auth/user.json',
      },
    },
  ],
  webServer: {
    command: 'rm -rf .next/dev/lock && npm run dev',
    url: 'http://localhost:3000',
    reuseExistingServer: !process.env.CI,
    timeout: 120000,
  },
});
