#!/usr/bin/env node
/**
 * Entry point: preflight the CLI, then serve.
 */

import { loadConfig } from './src/config.js';
import { ConcurrencyLimiter } from './src/runner.js';
import { CompletionService } from './src/completions.js';
import { createServer } from './src/server.js';
import { preflight } from './src/preflight.js';

async function main() {
  const config = loadConfig();

  if (config.skipPreflight) {
    console.warn('[bridge] CLAUDE_BRIDGE_SKIP_PREFLIGHT is set — starting without verifying the CLI.');
  } else {
    try {
      await preflight(config);
    } catch (error) {
      console.error(`[bridge] preflight FAILED: ${error.message}`);
      process.exit(1);
    }
  }

  const limiter = new ConcurrencyLimiter({
    maxConcurrency: config.maxConcurrency,
    maxQueueDepth: config.maxQueueDepth,
  });
  const service = new CompletionService({ config, limiter });
  const server = createServer({ config, service });

  server.listen(config.port, config.host, () => {
    console.log(`[bridge] listening on http://${config.host}:${config.port}/v1`);
    console.log(`[bridge] models (first is the default for unpinned agents): ${config.models.join(', ')}`);
    console.log(
      `[bridge] max ${config.maxConcurrency} concurrent CLI call(s), ${config.timeoutMs}ms timeout, ` +
        `auth ${config.apiKey ? 'REQUIRED' : 'disabled'}`,
    );
    if (config.host === '0.0.0.0' && !config.apiKey) {
      console.warn(
        '[bridge] WARNING: bound to 0.0.0.0 with no CLAUDE_BRIDGE_API_KEY. Anyone who can reach this ' +
          'port can spend your Claude subscription. Set a key or bind to 127.0.0.1.',
      );
    }
  });

  const shutdown = (signal) => {
    console.log(`[bridge] ${signal} received, shutting down`);
    server.close(() => process.exit(0));
    setTimeout(() => process.exit(0), 5_000).unref();
  };
  process.on('SIGINT', () => shutdown('SIGINT'));
  process.on('SIGTERM', () => shutdown('SIGTERM'));
}

main().catch((error) => {
  console.error(`[bridge] fatal: ${error && error.stack ? error.stack : error}`);
  process.exit(1);
});
