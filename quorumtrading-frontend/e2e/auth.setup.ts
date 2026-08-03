import { test as setup, expect } from '@playwright/test';
import path from 'path';

const authFile = path.join(__dirname, '../.playwright/.auth/user.json');

/**
 * Authentication Setup for E2E Tests
 *
 * Uses the backend's dev-login endpoint to create a real authenticated session.
 * This endpoint is only available when running with the e2e or dev profile.
 */
setup('authenticate via dev-login', async ({ page, context }) => {
  // Call the dev-login endpoint which creates a real session
  await page.goto('http://localhost:8085/api/v1/auth/dev-login');

  // Should redirect to frontend callback
  await page.waitForURL('**/auth/callback**', { timeout: 10000 });

  // Don't assert on the callback's transient greeting: it renders and then the page redirects,
  // so the check races the redirect and fails intermittently. The authenticated-state checks
  // below ("Control room", then the dashboard) prove the session just as well and are stable.

  // Give the callback page time to trigger redirect
  await page.waitForTimeout(2000);

  // Navigate to any page to verify auth state persists.
  // The authed landing header shows the "Control room" link (no Logout button anymore).
  await page.goto('http://localhost:3000/');
  await expect(page.getByRole('link', { name: /Control room/i })).toBeVisible({ timeout: 15000 });

  // Navigate to dashboard explicitly
  await page.goto('http://localhost:3000/dashboard');

  // Wait for dashboard to load
  await expect(page.getByText(/Dashboard/i).first()).toBeVisible({ timeout: 15000 });

  // Dismiss the first-run modals. WelcomeModal / WelcomeBackModal render a TerminalModal
  // overlay that covers the whole layout and swallows pointer events, so every navigation
  // test times out clicking a perfectly visible sidebar link. They are gated purely on these
  // localStorage keys, and storageState captures localStorage — so setting them here fixes it
  // once for every authenticated project instead of per-spec.
  await page.evaluate(() => {
    localStorage.setItem('quorumtrading:welcome:dismissed', 'true');
    localStorage.setItem('quorumtrading:welcome-back:dismissed', 'true');
    localStorage.setItem('quorumtrading:onboarding:dismissed', 'true');
  });

  // Save the authenticated state (session cookies + the dismissals above)
  await context.storageState({ path: authFile });
});
