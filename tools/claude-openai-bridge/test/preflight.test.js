import test from 'node:test';
import assert from 'node:assert/strict';

import { loadConfig } from '../src/config.js';
import { FORBIDDEN_FLAGS } from '../src/protocol.js';
import { evaluateInitEvent, preflight, preflightArgs, readInitEvent } from '../src/preflight.js';
import { fakeSpawn, initEvent, ndjson, resultEvent, silentLogger } from './helpers.js';

const config = loadConfig({});

test('preflight args never include a forbidden flag', () => {
  const args = preflightArgs(config);
  FORBIDDEN_FLAGS.forEach((flag) => assert.ok(!args.includes(flag), `${flag} must never be passed`));
  assert.equal(args[args.indexOf('--tools') + 1], '');
});

test('a healthy init event yields the CLI version and the resolved model', () => {
  const summary = evaluateInitEvent(initEvent(), config);
  assert.equal(summary.cliVersion, '2.1.220');
  assert.equal(summary.resolvedModel, 'claude-sonnet-5');
  assert.equal(summary.toolCount, 0);
});

test('startup fails if the CLI is not using subscription auth', () => {
  // Guards the documented drift risk: --bare is slated to become the -p default, and it reads
  // ANTHROPIC_API_KEY instead of the subscription. Silent drift would bill an API key.
  assert.throws(
    () => evaluateInitEvent(initEvent({ apiKeySource: 'ANTHROPIC_API_KEY' }), config),
    /NOT using your subscription login/,
  );
});

test('the subscription requirement can be waived deliberately', () => {
  const relaxed = loadConfig({ CLAUDE_BRIDGE_REQUIRE_SUBSCRIPTION: 'false' });
  const summary = evaluateInitEvent(initEvent({ apiKeySource: 'ANTHROPIC_API_KEY' }), relaxed);
  assert.equal(summary.apiKeySource, 'ANTHROPIC_API_KEY');
});

test('startup fails if tools are still enabled', () => {
  assert.throws(
    () => evaluateInitEvent(initEvent({ tools: ['Bash', 'Edit'] }), config),
    /must not have filesystem or shell access/,
  );
});

test('startup fails if the CLI does not emit an init event at all', () => {
  assert.throws(() => evaluateInitEvent(resultEvent(), config), /did not emit a system\/init event/);
});

test('the preflight reads one line and kills the CLI before it can call the API', async () => {
  // This is what makes a boot check free: the init event is emitted before any request is sent.
  const spawnFn = fakeSpawn({ stdout: ndjson(initEvent(), resultEvent()), delayMs: 0 });
  const summary = await preflight(config, { spawnFn, logger: silentLogger() });
  assert.equal(summary.cliVersion, '2.1.220');
  assert.ok(spawnFn.calls[0].killed, 'the preflight child must be terminated');
});

test('a missing CLI binary produces an actionable message', async () => {
  const spawnFn = () => {
    throw new Error('spawn claude ENOENT');
  };
  const error = await readInitEvent({ bin: 'claude', args: [], spawnFn }).catch((e) => e);
  assert.match(error.message, /Is the Claude Code CLI installed\?/);
});

test('a CLI that exits without an init event is reported, not ignored', async () => {
  const spawnFn = fakeSpawn({ stdout: '', stderr: 'not logged in', exitCode: 1 });
  const error = await readInitEvent({ bin: 'claude', args: [], spawnFn }).catch((e) => e);
  assert.match(error.message, /not logged in/);
});
