/**
 * Startup self-check.
 *
 * The CLI emits a `system`/`init` event on stdout *before* it contacts the API, and that event
 * states which tools are enabled, which model the alias resolved to, and where the credentials came
 * from. Reading it and then terminating the process costs no tokens and no money, which is what
 * makes it usable as a boot check.
 *
 * What it defends against: the CLI documents `--bare` as a mode where "Anthropic auth is strictly
 * ANTHROPIC_API_KEY or apiKeyHelper (OAuth and keychain are never read)", and that mode is slated to
 * become the `-p` default. If that lands — or if an ANTHROPIC_API_KEY leaks into the environment —
 * this bridge would quietly stop using the subscription it exists to use, and start billing an API
 * key instead. `apiKeySource` makes that visible, so the bridge fails loudly at startup rather than
 * drifting.
 */

import os from 'node:os';
import { spawn as nodeSpawn } from 'node:child_process';
import { assertNoForbiddenFlags } from './protocol.js';

export class PreflightError extends Error {
  constructor(message) {
    super(message);
    this.name = 'PreflightError';
  }
}

/** Decide whether an observed init event is acceptable. Pure, so the rules are testable. */
export function evaluateInitEvent(init, config) {
  if (!init || init.type !== 'system' || init.subtype !== 'init') {
    throw new PreflightError(
      'The claude CLI did not emit a system/init event. This bridge needs `--output-format stream-json`; ' +
        'the installed CLI appears to behave differently.',
    );
  }

  if (Array.isArray(init.tools) && init.tools.length > 0) {
    throw new PreflightError(
      `The claude CLI still has ${init.tools.length} tool(s) enabled despite \`--tools ""\` ` +
        `(${init.tools.slice(0, 5).join(', ')}). Refusing to start: a completion endpoint must not have ` +
        'filesystem or shell access.',
    );
  }

  if (config.requireSubscriptionAuth && init.apiKeySource !== 'none') {
    throw new PreflightError(
      `The claude CLI reports apiKeySource="${init.apiKeySource}", not "none". That means it is NOT using ` +
        'your subscription login — it has found an API key (check ANTHROPIC_API_KEY in the environment). ' +
        'Set CLAUDE_BRIDGE_REQUIRE_SUBSCRIPTION=false if billing an API key is what you actually want.',
    );
  }

  return {
    cliVersion: init.claude_code_version || 'unknown',
    resolvedModel: init.model || null,
    apiKeySource: init.apiKeySource,
    toolCount: Array.isArray(init.tools) ? init.tools.length : 0,
  };
}

/**
 * Start the CLI, read the first line of stdout, kill it.
 *
 * Terminating before the init event is consumed is the whole trick: no request reaches the API, so
 * this is free to run on every boot.
 */
export function readInitEvent({ bin, args, cwd, spawnFn = nodeSpawn, timeoutMs = 30_000 }) {
  assertNoForbiddenFlags(args);
  return new Promise((resolve, reject) => {
    let child;
    try {
      child = spawnFn(bin, args, { cwd: cwd || os.tmpdir(), stdio: ['pipe', 'pipe', 'pipe'] });
    } catch (error) {
      reject(new PreflightError(`Could not start '${bin}': ${error.message}. Is the Claude Code CLI installed?`));
      return;
    }

    let buffer = '';
    let stderr = '';
    let settled = false;

    const finish = (fn, value) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      try {
        child.kill('SIGTERM');
      } catch {
        /* already gone */
      }
      fn(value);
    };

    const timer = setTimeout(
      () =>
        finish(
          reject,
          new PreflightError(`Timed out after ${timeoutMs}ms waiting for the claude CLI to start.`),
        ),
      timeoutMs,
    );
    if (timer.unref) timer.unref();

    child.stdout.setEncoding('utf8');
    child.stderr.setEncoding('utf8');
    child.stdout.on('data', (chunk) => {
      buffer += chunk;
      const newline = buffer.indexOf('\n');
      if (newline === -1) return;
      const line = buffer.slice(0, newline).trim();
      if (!line) return;
      try {
        finish(resolve, JSON.parse(line));
      } catch (error) {
        finish(reject, new PreflightError(`First line from the claude CLI was not JSON: ${error.message}`));
      }
    });
    child.stderr.on('data', (chunk) => {
      stderr += chunk;
    });
    child.on('error', (error) =>
      finish(reject, new PreflightError(`Could not run '${bin}': ${error.message}. Is the Claude Code CLI installed?`)),
    );
    child.on('close', () => {
      if (settled) return;
      const detail = stderr.trim() || 'no output';
      finish(reject, new PreflightError(`The claude CLI exited before emitting an init event (${detail}).`));
    });

    if (child.stdin) {
      child.stdin.on('error', () => {});
      // A prompt is written so the CLI proceeds past startup, but the process is killed as soon as
      // the init event arrives — before it is ever sent to the API.
      child.stdin.end('preflight', 'utf8');
    }
  });
}

export function preflightArgs(config) {
  return [
    '-p',
    '--output-format',
    'stream-json',
    '--verbose',
    '--tools',
    '',
    '--strict-mcp-config',
    '--no-session-persistence',
    '--model',
    config.models[0],
    '--system-prompt',
    'preflight',
  ];
}

export async function preflight(config, { spawnFn, logger = console } = {}) {
  const init = await readInitEvent({
    bin: config.claudeBin,
    args: preflightArgs(config),
    cwd: config.cwd,
    spawnFn,
  });
  const summary = evaluateInitEvent(init, config);
  logger.log(
    `[bridge] preflight OK — claude ${summary.cliVersion}, auth=${summary.apiKeySource} (subscription), ` +
      `tools disabled, '${config.models[0]}' resolves to ${summary.resolvedModel}`,
  );
  return summary;
}
