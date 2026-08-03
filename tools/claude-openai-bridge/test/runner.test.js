import test from 'node:test';
import assert from 'node:assert/strict';

import { loadConfig } from '../src/config.js';
import { ConcurrencyLimiter, detectUnknownOption, runCli } from '../src/runner.js';
import { CompletionService } from '../src/completions.js';
import { fakeSpawn, silentLogger, successfulRun } from './helpers.js';

const config = loadConfig({});

function serviceWith(spawnFn, overrides = {}) {
  const merged = { ...config, ...overrides };
  return new CompletionService({
    config: merged,
    limiter: new ConcurrencyLimiter({
      maxConcurrency: merged.maxConcurrency,
      maxQueueDepth: merged.maxQueueDepth,
    }),
    spawnFn,
    logger: silentLogger(),
  });
}

const REQUEST = { model: 'sonnet', messages: [{ role: 'user', content: 'go' }] };

// ---------------------------------------------------------------- spawning

test('the prompt is delivered on stdin, not as an argv argument', () => {
  // Prompts run to many kilobytes; argv has a hard size limit and would leak the prompt into `ps`.
  const spawnFn = fakeSpawn({ stdout: successfulRun({ text: 'ok' }) });
  return runCli({ bin: 'claude', args: ['-p'], stdin: 'the prompt', timeoutMs: 1000, spawnFn }).then(() => {
    assert.equal(spawnFn.calls[0].stdin, 'the prompt');
    assert.ok(!spawnFn.calls[0].args.includes('the prompt'));
  });
});

test('the CLI runs outside the repo so it cannot auto-discover this project CLAUDE.md', async () => {
  const spawnFn = fakeSpawn({ stdout: successfulRun() });
  await runCli({ bin: 'claude', args: [], stdin: 'x', timeoutMs: 1000, spawnFn });
  const { cwd } = spawnFn.calls[0].options;
  assert.ok(cwd && cwd.length > 0);
  assert.ok(!cwd.includes('quorumtrading'), `expected a neutral cwd, got ${cwd}`);
});

test('a run that exceeds the timeout is terminated and reported as 504', async () => {
  const spawnFn = fakeSpawn({ hang: true });
  const error = await runCli({ bin: 'claude', args: [], stdin: 'x', timeoutMs: 20, spawnFn }).catch((e) => e);
  assert.equal(error.status, 504);
  assert.match(error.message, /timeout/i);
  assert.ok(spawnFn.calls[0].killed, 'the child must be killed on timeout');
  assert.equal(spawnFn.calls[0].child.signalsReceived[0], 'SIGTERM');
});

test('the default timeout is not shorter than the backend allows for this engine', () => {
  // The backend permits 300s in the %local profile; a shorter bridge timeout would convert a slow
  // but successful answer into an error the backend cannot tell from a real failure.
  assert.ok(config.timeoutMs >= 360_000, `expected >= 360000ms, got ${config.timeoutMs}`);
});

test('a client disconnect SIGTERMs the child instead of letting it run on', async () => {
  const controller = new AbortController();
  const spawnFn = fakeSpawn({ hang: true });
  const promise = runCli({
    bin: 'claude',
    args: [],
    stdin: 'x',
    timeoutMs: 60_000,
    signal: controller.signal,
    spawnFn,
  });
  setImmediate(() => controller.abort());
  const error = await promise.catch((e) => e);
  assert.equal(error.status, 499);
  assert.ok(spawnFn.calls[0].killed);
  assert.equal(spawnFn.calls[0].child.signalsReceived[0], 'SIGTERM');
});

test('a spawn failure names the binary so a missing CLI is obvious', async () => {
  const spawnFn = () => {
    throw new Error('ENOENT');
  };
  const error = await runCli({ bin: 'claude', args: [], stdin: 'x', timeoutMs: 100, spawnFn }).catch((e) => e);
  assert.match(error.message, /claude/);
});

// ---------------------------------------------------------------- concurrency

test('concurrency is capped and the excess is queued, not run in parallel', async () => {
  let peak = 0;
  let active = 0;
  const limiter = new ConcurrencyLimiter({ maxConcurrency: 2, maxQueueDepth: 10 });

  const task = () =>
    limiter.run(async () => {
      active += 1;
      peak = Math.max(peak, active);
      await new Promise((resolve) => setTimeout(resolve, 15));
      active -= 1;
    });

  await Promise.all([task(), task(), task(), task(), task()]);
  assert.equal(peak, 2, `expected at most 2 concurrent runs, saw ${peak}`);
  assert.equal(limiter.active, 0);
  assert.equal(limiter.queued, 0);
});

test('each queued request still runs exactly one CLI process, in order', async () => {
  const spawnFn = fakeSpawn({ stdout: successfulRun({ text: 'ok' }), delayMs: 5 });
  const service = serviceWith(spawnFn, { maxConcurrency: 1 });
  const results = await Promise.all([1, 2, 3].map(() => service.complete(REQUEST)));
  assert.equal(spawnFn.calls.length, 3);
  results.forEach((run) => assert.equal(run.content, 'ok'));
});

test('a full waiting room sheds load with 503 rather than queueing without bound', async () => {
  const limiter = new ConcurrencyLimiter({ maxConcurrency: 1, maxQueueDepth: 1 });
  const release = [];
  const hold = () => limiter.run(() => new Promise((resolve) => release.push(resolve)));

  const first = hold(); // takes the slot
  await new Promise((resolve) => setImmediate(resolve));
  const second = hold(); // fills the waiting room
  const third = hold().catch((e) => e); // rejected

  const error = await third;
  assert.equal(error.status, 503);
  assert.match(error.message, /saturated/i);

  release.forEach((fn) => fn());
  await first;
  release.forEach((fn) => fn());
  await second;
});

// ---------------------------------------------------------------- CLI version drift

test('detectUnknownOption spots a flag the installed CLI rejected', () => {
  assert.equal(detectUnknownOption("error: unknown option '--max-turns'"), '--max-turns');
  assert.equal(detectUnknownOption('some other failure'), null);
});

test('an optional flag rejected by the CLI is dropped and the request retried once', async () => {
  // Verified against the real CLI: unknown flags are rejected during argv parsing, before any API
  // call, so this retry costs no tokens.
  const spawnFn = fakeSpawn((bin, args, index) =>
    index === 0
      ? { stdout: '', stderr: "error: unknown option '--max-turns'", exitCode: 1 }
      : { stdout: successfulRun({ text: 'recovered' }), exitCode: 0 },
  );
  const service = serviceWith(spawnFn);

  const run = await service.complete(REQUEST);
  assert.equal(run.content, 'recovered');
  assert.equal(spawnFn.calls.length, 2);
  assert.ok(spawnFn.calls[0].args.includes('--max-turns'));
  assert.ok(!spawnFn.calls[1].args.includes('--max-turns'), 'the rejected flag must be dropped on retry');

  // And it stays dropped, so the cost is one wasted invocation per process, not per request.
  await service.complete(REQUEST);
  assert.ok(!spawnFn.calls[2].args.includes('--max-turns'));
});

test('a rejected flag that is NOT optional fails loudly instead of looping', async () => {
  const spawnFn = fakeSpawn({ stdout: '', stderr: "error: unknown option '--system-prompt'", exitCode: 1 });
  const service = serviceWith(spawnFn);
  const error = await service.complete(REQUEST).catch((e) => e);
  assert.match(error.message, /not compatible with this bridge/);
  assert.equal(spawnFn.calls.length, 1);
});

test('a CLI failure surfaces as an error and never as content', async () => {
  const spawnFn = fakeSpawn({ stdout: '', stderr: 'Invalid API key', exitCode: 1 });
  const service = serviceWith(spawnFn);
  const error = await service.complete(REQUEST).catch((e) => e);
  assert.ok(error instanceof Error);
  assert.match(error.message, /Invalid API key/);
});
