/**
 * Playwright Global Setup
 * Runs once before all E2E tests to ensure a clean test environment.
 *
 * Cleanup is a best-effort convenience for local re-runs against a still-running backend.
 * It must never be load-bearing: under the %e2e profile the backend already runs Flyway
 * clean-at-start, so every backend boot guarantees a clean database.
 */

import { exec } from 'child_process';
import { promisify } from 'util';

const execAsync = promisify(exec);

async function globalSetup() {
  console.log('\n🧹 Cleaning up test database before E2E tests...\n');

  // %e2e backend profile uses quorumtrading_test on localhost:55432
  const dbHost = process.env.DB_HOST || 'localhost';
  const dbPort = process.env.DB_PORT || '55432';
  const dbName = process.env.DB_NAME || 'quorumtrading_test';
  const dbUser = process.env.DB_USER || 'quorumtrading';
  const dbPassword = process.env.DB_PASSWORD || 'dev-password';
  const connectionUrl = `postgresql://${dbUser}:${dbPassword}@${dbHost}:${dbPort}/${dbName}`;

  try {
    // TCP psql (works both locally and with CI service containers)
    await execAsync(`psql "${connectionUrl}" -c "SELECT 1" -q`);
  } catch {
    console.log('⚠️  Database not reachable via psql - skipping cleanup\n');
    return;
  }

  try {
    // Tables in correct order (children before parents due to foreign keys).
    // NOTE: keep this list in sync with the actual schema — a missing table
    // (e.g. the dropped market_events or strategy_shares) aborts the whole
    // single -c batch and rolls back every DELETE.
    const cleanupSQL = `
      DELETE FROM consensus_decisions;
      DELETE FROM llm_decisions;
      DELETE FROM portfolio_snapshots;
      DELETE FROM simulation_contributions;
      DELETE FROM simulation_portfolios;
      DELETE FROM research_agent_decisions;
      DELETE FROM research_executions;
      DELETE FROM benchmarks;
      DELETE FROM investment_allocations;
      DELETE FROM investment_plans;
      DELETE FROM simulations;
      DELETE FROM scheduled_simulations;
      DELETE FROM orchestrator_strategies;
      DELETE FROM market_research;
      DELETE FROM backfill_jobs;
      DELETE FROM notifications;
    `;

    const { stdout, stderr } = await execAsync(
      `psql "${connectionUrl}" -c "${cleanupSQL.replace(/\n/g, ' ')}"`
    );

    if (stderr && stderr.includes('ERROR')) {
      console.error('❌ Database cleanup error:', stderr);
    } else {
      console.log('✅ Database cleaned up successfully');
      if (stdout) console.log(stdout);
    }
    console.log('');

  } catch (error) {
    console.error('❌ Error during database cleanup:', error);
    console.log('⚠️  Continuing with tests (database may not be clean)\n');
  }
}

export default globalSetup;
