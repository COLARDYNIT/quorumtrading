import test from 'node:test';
import assert from 'node:assert/strict';

import { loadConfig } from '../src/config.js';
import {
  BridgeError,
  buildArgs,
  extractText,
  interpretRun,
  mapEffort,
  normalizeModelId,
  normalizeUsage,
  renderTranscript,
  resolveAnsweringModel,
  splitMessages,
  toChatCompletion,
} from '../src/protocol.js';
import { assistantEvent, initEvent, ndjson, resultEvent, successfulRun } from './helpers.js';

const config = loadConfig({});

function argValue(args, flag) {
  const index = args.indexOf(flag);
  return index === -1 ? undefined : args[index + 1];
}

/** `assert.throws` returns undefined, so capture the error when its fields need asserting. */
function catchError(fn) {
  try {
    fn();
  } catch (error) {
    return error;
  }
  return null;
}

// ---------------------------------------------------------------- message mapping

test('system messages become --system-prompt, not part of the stdin prompt', () => {
  const { args, stdin } = buildArgs(
    {
      model: 'sonnet',
      messages: [
        { role: 'system', content: 'You are a fundamental analyst.' },
        { role: 'user', content: 'Analyse AAPL.' },
      ],
    },
    config,
  );

  assert.equal(argValue(args, '--system-prompt'), 'You are a fundamental analyst.');
  assert.equal(stdin, 'Analyse AAPL.');
});

test('multiple system messages are joined, in order', () => {
  const { args } = buildArgs(
    {
      messages: [
        { role: 'system', content: 'Rule one.' },
        { role: 'system', content: 'Rule two.' },
        { role: 'user', content: 'go' },
      ],
    },
    config,
  );
  assert.equal(argValue(args, '--system-prompt'), 'Rule one.\n\nRule two.');
});

test('a request with no system message still replaces the CLI persona with a neutral default', () => {
  // Without --system-prompt the CLI keeps its coding-assistant persona and auto-discovered
  // CLAUDE.md. A trading agent must not inherit either.
  const { args } = buildArgs({ messages: [{ role: 'user', content: 'go' }] }, config);
  const systemPrompt = argValue(args, '--system-prompt');
  assert.ok(systemPrompt && systemPrompt.length > 0);
  assert.equal(systemPrompt, config.defaultSystemPrompt);
});

test('a single user turn is passed through verbatim, with no added framing', () => {
  const prompt = 'Line one\n\nLine two: {"json": true}';
  const { stdin } = buildArgs({ messages: [{ role: 'user', content: prompt }] }, config);
  assert.equal(stdin, prompt);
});

test('multi-turn conversations are rendered as a labelled transcript', () => {
  const stdin = renderTranscript([
    { role: 'user', text: 'Hi' },
    { role: 'assistant', text: 'Hello' },
    { role: 'user', text: 'Buy?' },
  ]);
  assert.equal(stdin, 'User: Hi\n\nAssistant: Hello\n\nUser: Buy?\n\nAssistant:');
});

test('array-style content parts are flattened', () => {
  assert.equal(extractText([{ type: 'text', text: 'a' }, { type: 'text', text: 'b' }]), 'a\nb');
});

test('an unknown role is rejected rather than silently dropped', () => {
  assert.throws(() => splitMessages([{ role: 'tool', content: 'x' }]), /Unsupported message role/);
});

test('empty messages are rejected', () => {
  assert.throws(() => splitMessages([]), /non-empty array/);
});

// ---------------------------------------------------------------- flags

test('the CLI is always invoked non-interactively with stream-json and no tools', () => {
  const { args } = buildArgs({ messages: [{ role: 'user', content: 'go' }] }, config);
  assert.ok(args.includes('-p'));
  assert.equal(argValue(args, '--output-format'), 'stream-json');
  assert.ok(args.includes('--verbose'));
  assert.equal(argValue(args, '--tools'), '');
  assert.ok(args.includes('--strict-mcp-config'));
  assert.ok(args.includes('--no-session-persistence'));
});

test('--max-turns 1 is passed when the CLI supports it', () => {
  const { args } = buildArgs({ messages: [{ role: 'user', content: 'go' }] }, config);
  assert.equal(argValue(args, '--max-turns'), '1');
});

test('--max-turns is dropped when the installed CLI is known not to accept it', () => {
  const { args } = buildArgs({ messages: [{ role: 'user', content: 'go' }] }, config, { maxTurns: false });
  assert.ok(!args.includes('--max-turns'));
});

test('--bare is never passed, because it bypasses subscription login', () => {
  // The CLI documents --bare as "Anthropic auth is strictly ANTHROPIC_API_KEY ... OAuth and
  // keychain are never read", which would defeat the whole point of this bridge.
  const { args } = buildArgs({ messages: [{ role: 'user', content: 'go' }] }, config);
  assert.ok(!args.includes('--bare'));
});

test('model aliases and namespaced ids both reach --model', () => {
  assert.equal(
    argValue(buildArgs({ model: 'haiku', messages: [{ role: 'user', content: 'x' }] }, config).args, '--model'),
    'haiku',
  );
  assert.equal(
    argValue(
      buildArgs({ model: 'anthropic/claude-sonnet-5', messages: [{ role: 'user', content: 'x' }] }, config).args,
      '--model',
    ),
    'claude-sonnet-5',
  );
});

test('an unpinned request falls back to the first catalogue entry, which is sonnet', () => {
  const { args } = buildArgs({ messages: [{ role: 'user', content: 'x' }] }, config);
  assert.equal(argValue(args, '--model'), 'sonnet');
});

test('normalizeModelId strips a namespace and rejects blanks', () => {
  assert.equal(normalizeModelId('  opus  '), 'opus');
  assert.equal(normalizeModelId(''), null);
  assert.equal(normalizeModelId(null), null);
});

test('reasoning_effort maps onto --effort only for values the CLI documents', () => {
  assert.equal(mapEffort('high'), 'high');
  assert.equal(mapEffort('XHIGH'), 'xhigh');
  assert.equal(mapEffort('minimal'), 'low');
  // An invalid value makes the CLI exit non-zero, so it is dropped rather than passed through.
  assert.equal(mapEffort('turbo'), null);
  assert.equal(mapEffort(undefined), null);

  const { args } = buildArgs(
    { messages: [{ role: 'user', content: 'x' }], reasoning_effort: 'turbo' },
    config,
  );
  assert.ok(!args.includes('--effort'));
});

test('--effort is omitted entirely when the installed CLI lacks the flag', () => {
  const { args } = buildArgs(
    { messages: [{ role: 'user', content: 'x' }], reasoning_effort: 'high' },
    config,
    { effort: false },
  );
  assert.ok(!args.includes('--effort'));
});

// ---------------------------------------------------------------- honest model echo

test('the reported model comes from the CLI assistant event, never from the request', () => {
  const run = interpretRun({
    stdout: successfulRun({ model: 'claude-sonnet-5', text: 'hello' }),
    stderr: '',
    exitCode: 0,
    signal: null,
  });
  assert.equal(run.model, 'claude-sonnet-5');

  const body = toChatCompletion(run, { id: 'chatcmpl-1', created: 1 });
  // The caller asked for the alias "sonnet"; what is recorded is the resolved model that answered.
  assert.equal(body.model, 'claude-sonnet-5');
  assert.notEqual(body.model, 'sonnet');
});

test('when modelUsage names several models, the assistant event decides which one answered', () => {
  // Observed for real: a single sonnet request also produced an ancillary haiku call, so modelUsage
  // had two keys. Picking by token count or by "first key" would report the wrong model.
  const stdout = ndjson(
    initEvent(),
    assistantEvent('claude-sonnet-5'),
    resultEvent({
      result: 'lemon',
      modelUsage: {
        'claude-haiku-4-5-20251001': { inputTokens: 525, outputTokens: 11 },
        'claude-sonnet-5': { inputTokens: 571, outputTokens: 5 },
      },
    }),
  );
  const run = interpretRun({ stdout, stderr: '', exitCode: 0, signal: null });
  assert.equal(run.model, 'claude-sonnet-5');
});

test('model resolution falls back through modelUsage then init, but never to the request', () => {
  assert.equal(
    resolveAnsweringModel({ assistantModels: [], result: { modelUsage: { 'claude-opus-4': {} } }, systemInit: null }),
    'claude-opus-4',
  );
  assert.equal(
    resolveAnsweringModel({ assistantModels: [], result: { modelUsage: {} }, systemInit: initEvent() }),
    'claude-sonnet-5',
  );
  assert.equal(resolveAnsweringModel({ assistantModels: [], result: null, systemInit: null }), null);
});

test('a run that never names a model is an error, not a guess', () => {
  const stdout = ndjson(resultEvent({ result: 'text', modelUsage: {} }));
  assert.throws(() => interpretRun({ stdout, stderr: '', exitCode: 0, signal: null }), /refusing to guess/);
});

// ---------------------------------------------------------------- usage

test('prompt_tokens sums the real cached and uncached prompt components', () => {
  // Observed: input_tokens 9 alongside cache_creation 7075 and cache_read 17536. Reporting 9 would
  // understate the call by three orders of magnitude and trip the consumer's plausibility guard.
  const usage = normalizeUsage({
    input_tokens: 9,
    cache_creation_input_tokens: 7075,
    cache_read_input_tokens: 17536,
    output_tokens: 69,
  });
  assert.deepEqual(usage, { prompt_tokens: 24620, completion_tokens: 69, total_tokens: 24689 });
});

test('usage is omitted rather than estimated when the CLI reports nothing', () => {
  assert.equal(normalizeUsage(undefined), null);
  assert.equal(normalizeUsage({}), null);
  assert.equal(normalizeUsage({ input_tokens: 0, output_tokens: 0 }), null);

  const run = interpretRun({
    stdout: successfulRun({ text: 'hi', usage: {} }),
    stderr: '',
    exitCode: 0,
    signal: null,
  });
  const body = toChatCompletion(run, { id: 'x', created: 1 });
  assert.ok(!('usage' in body), 'usage must be absent, not zero-filled or estimated');
});

test('a reported usage block is passed through as counts, never derived from text length', () => {
  const text = 'x'.repeat(4000);
  const run = interpretRun({
    stdout: successfulRun({ text, usage: { input_tokens: 1200, output_tokens: 900 } }),
    stderr: '',
    exitCode: 0,
    signal: null,
  });
  assert.deepEqual(run.usage, { prompt_tokens: 1200, completion_tokens: 900, total_tokens: 2100 });
  // len/4 would be 1000 — the estimate the surveyed project shipped. Prove we did not do that.
  assert.notEqual(run.usage.completion_tokens, Math.round(text.length / 4));
});

// ---------------------------------------------------------------- failure is never content

test('ANTI-FABRICATION: an empty CLI response raises an error and never invents content', () => {
  // This is the regression guard. The surveyed off-the-shelf wrapper filled empty responses with
  // generated prose; ADR-0004 forbids exactly that. If someone reintroduces synthesis here, this
  // test fails.
  for (const emptyish of ['', '   ', '\n\n']) {
    let thrown = null;
    let returned = null;
    try {
      returned = interpretRun({
        stdout: successfulRun({ text: emptyish }),
        stderr: '',
        exitCode: 0,
        signal: null,
      });
    } catch (error) {
      thrown = error;
    }
    assert.equal(returned, null, `an empty CLI result must not yield a completion (got ${JSON.stringify(returned)})`);
    assert.ok(thrown instanceof BridgeError, 'an empty response must raise a BridgeError');
    assert.match(thrown.message, /empty response/i);
    assert.ok(thrown.status >= 500, 'an empty response must surface as a server-side failure');
  }
});

test('is_error from the CLI becomes a 5xx error carrying the CLI message', () => {
  const stdout = ndjson(
    initEvent(),
    resultEvent({ is_error: true, subtype: 'error_during_execution', result: 'Credit balance too low' }),
  );
  const error = catchError(() => interpretRun({ stdout, stderr: '', exitCode: 0, signal: null }));
  assert.ok(error instanceof BridgeError);
  assert.match(error.message, /Credit balance too low/);
  assert.equal(error.status, 502);
});

test('a non-zero exit is an error even when a result event is present', () => {
  const stdout = ndjson(initEvent(), resultEvent({ result: 'partial' }));
  assert.throws(() => interpretRun({ stdout, stderr: 'boom', exitCode: 1, signal: null }), /reported an error/);
});

test('missing result event is an error, and CLI stderr is surfaced', () => {
  assert.throws(
    () => interpretRun({ stdout: ndjson(initEvent()), stderr: 'Invalid API key', exitCode: 1, signal: null }),
    /Invalid API key/,
  );
});

test('a killed process is a timeout-class error, not an empty answer', () => {
  const error = catchError(() => interpretRun({ stdout: '', stderr: '', exitCode: null, signal: 'SIGTERM' }));
  assert.ok(error instanceof BridgeError);
  assert.equal(error.status, 504);
});

test('non-JSON noise on stdout is ignored and never treated as content', () => {
  const stdout = `Warning: something\n${successfulRun({ text: 'real answer' })}`;
  const run = interpretRun({ stdout, stderr: '', exitCode: 0, signal: null });
  assert.equal(run.content, 'real answer');
});

// ---------------------------------------------------------------- response shape

test('the response is OpenAI-shaped where the consumer reads it', () => {
  const run = interpretRun({ stdout: successfulRun({ text: 'BUY' }), stderr: '', exitCode: 0, signal: null });
  const body = toChatCompletion(run, { id: 'chatcmpl-abc', created: 1700 });

  assert.equal(body.object, 'chat.completion');
  assert.equal(body.choices[0].message.content, 'BUY');
  assert.equal(body.choices[0].message.role, 'assistant');
  assert.equal(body.choices[0].finish_reason, 'stop');
  assert.equal(typeof body.model, 'string');
});

test('a max_tokens stop maps to the OpenAI "length" finish reason', () => {
  const stdout = ndjson(initEvent(), assistantEvent('claude-sonnet-5'), resultEvent({ stop_reason: 'max_tokens' }));
  const run = interpretRun({ stdout, stderr: '', exitCode: 0, signal: null });
  assert.equal(toChatCompletion(run, { id: 'x', created: 1 }).choices[0].finish_reason, 'length');
});
