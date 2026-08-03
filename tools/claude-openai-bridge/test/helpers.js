/**
 * Test doubles. No test in this suite ever launches a real `claude` process.
 */

import { EventEmitter } from 'node:events';
import { PassThrough } from 'node:stream';

/**
 * A fake `spawn` that replays a scripted CLI run.
 *
 * @param {object|function} script  either a fixed outcome, or a function of (bin, args, callIndex)
 *                                  returning one — the function form lets a test model a CLI that
 *                                  rejects a flag on the first call and succeeds on the second.
 */
export function fakeSpawn(script) {
  const calls = [];

  const spawnFn = (bin, args, options) => {
    const index = calls.length;
    const outcome = typeof script === 'function' ? script(bin, args, index) : script;
    const { stdout = '', stderr = '', exitCode = 0, closeSignal = null, hang = false, delayMs = 0 } = outcome || {};

    const child = new EventEmitter();
    child.stdout = new PassThrough();
    child.stderr = new PassThrough();
    child.stdin = new PassThrough();
    child.killed = false;
    child.signalsReceived = [];

    const call = { bin, args, options, child, stdin: '', killed: false };
    calls.push(call);

    child.stdin.on('data', (chunk) => {
      call.stdin += chunk.toString('utf8');
    });

    let finished = false;
    const finish = (code, signal) => {
      if (finished) return;
      finished = true;
      if (stdout) child.stdout.write(stdout);
      if (stderr) child.stderr.write(stderr);
      child.stdout.end();
      child.stderr.end();
      setImmediate(() => child.emit('close', code, signal));
    };

    child.kill = (signal) => {
      child.killed = true;
      call.killed = true;
      child.signalsReceived.push(signal);
      // A real CLI exits when signalled; emulate that so the runner's close handler fires.
      finish(null, signal || 'SIGTERM');
      return true;
    };

    if (!hang) {
      setTimeout(() => finish(exitCode, closeSignal), delayMs);
    }

    return child;
  };

  spawnFn.calls = calls;
  return spawnFn;
}

/** Serialise events as the NDJSON the CLI writes with `--output-format stream-json`. */
export function ndjson(...events) {
  return events.map((e) => JSON.stringify(e)).join('\n') + '\n';
}

/**
 * The `system`/init event, shaped exactly as CLI 2.1.220 emitted it on this machine.
 */
export function initEvent(overrides = {}) {
  return {
    type: 'system',
    subtype: 'init',
    cwd: '/private/tmp',
    session_id: 'd6b334d2-44ba-4c0a-89ba-fb23cb14b3ef',
    tools: [],
    mcp_servers: [],
    model: 'claude-sonnet-5',
    permissionMode: 'default',
    apiKeySource: 'none',
    claude_code_version: '2.1.220',
    output_style: 'default',
    ...overrides,
  };
}

/** An `assistant` event; `message.model` is the authoritative answering model. */
export function assistantEvent(model, overrides = {}) {
  return {
    type: 'assistant',
    message: { model, stop_reason: null, usage: { input_tokens: 546, output_tokens: 1 }, ...overrides },
  };
}

/**
 * The final `result` event. Field names and nesting are copied from a real observed run:
 * `{"is_error":false,...,"usage":{"input_tokens":9,"cache_creation_input_tokens":7075,
 *   "cache_read_input_tokens":17536,"output_tokens":69},"modelUsage":{...},"result":"OK",...}`
 */
export function resultEvent(overrides = {}) {
  return {
    type: 'result',
    subtype: 'success',
    is_error: false,
    stop_reason: 'end_turn',
    num_turns: 1,
    session_id: 'f96f0d93',
    total_cost_usd: 0.00236,
    usage: {
      input_tokens: 571,
      cache_creation_input_tokens: 0,
      cache_read_input_tokens: 0,
      output_tokens: 5,
    },
    modelUsage: {
      'claude-sonnet-5': {
        inputTokens: 571,
        outputTokens: 5,
        contextWindow: 1000000,
        canonicalModel: 'claude-sonnet-5',
      },
    },
    api_error_status: null,
    result: 'OK',
    ...overrides,
  };
}

/** A complete, successful stream-json run. */
export function successfulRun({ model = 'claude-sonnet-5', text = 'OK', usage, modelUsage } = {}) {
  const result = resultEvent({ result: text });
  if (usage !== undefined) result.usage = usage;
  if (modelUsage !== undefined) result.modelUsage = modelUsage;
  return ndjson(initEvent(), assistantEvent(model), result);
}

export function silentLogger() {
  return { log() {}, warn() {}, error() {} };
}
