import test from 'node:test';
import assert from 'node:assert/strict';

import { loadConfig } from '../src/config.js';
import { buildCatalog } from '../src/catalog.js';
import { ConcurrencyLimiter } from '../src/runner.js';
import { CompletionService } from '../src/completions.js';
import { createServer, extractBearer, isAuthorized, secretMatches } from '../src/server.js';
import { fakeSpawn, silentLogger, successfulRun } from './helpers.js';

/** Boot the bridge on an ephemeral port with a scripted CLI behind it. */
async function withServer(script, envOverrides, fn) {
  const config = loadConfig(envOverrides || {});
  const spawnFn = fakeSpawn(script);
  const service = new CompletionService({
    config,
    limiter: new ConcurrencyLimiter(config),
    spawnFn,
    logger: silentLogger(),
  });
  const server = createServer({ config, service, logger: silentLogger() });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    return await fn({ base, spawnFn, config });
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
}

const OK = { stdout: successfulRun({ model: 'claude-sonnet-5', text: 'BUY' }) };

// ---------------------------------------------------------------- /v1/models

test('/v1/models lists sonnet first so it, not opus, is the default for unpinned agents', () => {
  // The consumer's firstAdvertisedModel() takes data[0] whenever nothing is pinned.
  const catalog = buildCatalog(loadConfig({}).models);
  assert.equal(catalog.data[0].id, 'sonnet');
  assert.ok(catalog.data.some((m) => m.id === 'opus'));
  assert.notEqual(catalog.data[0].id, 'opus');
  assert.equal(catalog.data[catalog.data.length - 1].id, 'opus');
});

test('/v1/models answers OpenAI-shaped JSON without touching the CLI', async () => {
  await withServer(OK, {}, async ({ base, spawnFn }) => {
    const response = await fetch(`${base}/v1/models`);
    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.object, 'list');
    assert.ok(Array.isArray(body.data));
    assert.equal(body.data[0].id, 'sonnet');
    body.data.forEach((entry) => {
      assert.equal(entry.object, 'model');
      assert.equal(typeof entry.id, 'string');
    });
    // The consumer probes this with a 3s timeout, so it must be static.
    assert.equal(spawnFn.calls.length, 0, '/v1/models must not spawn the CLI');
  });
});

test('the model catalogue is configurable, and order is preserved verbatim', () => {
  const catalog = buildCatalog(loadConfig({ CLAUDE_BRIDGE_MODELS: 'haiku, sonnet ,opus' }).models);
  assert.deepEqual(catalog.data.map((m) => m.id), ['haiku', 'sonnet', 'opus']);
});

// ---------------------------------------------------------------- /v1/chat/completions

test('a completion returns content, an honest model, and real usage', async () => {
  await withServer(OK, {}, async ({ base }) => {
    const response = await fetch(`${base}/v1/chat/completions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ model: 'sonnet', messages: [{ role: 'user', content: 'Analyse AAPL' }] }),
    });
    assert.equal(response.status, 200);
    const body = await response.json();

    assert.equal(body.choices[0].message.content, 'BUY');
    assert.equal(body.model, 'claude-sonnet-5'); // resolved by the CLI, not the requested alias
    assert.equal(body.usage.prompt_tokens, 571);
    assert.equal(body.usage.completion_tokens, 5);
    assert.equal(body.object, 'chat.completion');
  });
});

test('an empty CLI answer becomes a 5xx error with no content field', async () => {
  await withServer({ stdout: successfulRun({ text: '' }) }, {}, async ({ base }) => {
    const response = await fetch(`${base}/v1/chat/completions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ messages: [{ role: 'user', content: 'x' }] }),
    });
    assert.ok(response.status >= 500, `expected 5xx, got ${response.status}`);
    const body = await response.json();
    assert.ok(body.error, 'the response must be an OpenAI-shaped error');
    assert.ok(!body.choices, 'an error must not carry choices');
    assert.match(body.error.message, /empty response/i);
  });
});

test('streaming is refused explicitly rather than answered non-streaming', async () => {
  await withServer(OK, {}, async ({ base }) => {
    const response = await fetch(`${base}/v1/chat/completions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ stream: true, messages: [{ role: 'user', content: 'x' }] }),
    });
    assert.equal(response.status, 400);
  });
});

test('a malformed body is a 400, not a 500', async () => {
  await withServer(OK, {}, async ({ base }) => {
    const response = await fetch(`${base}/v1/chat/completions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: 'not json',
    });
    assert.equal(response.status, 400);
    assert.ok((await response.json()).error);
  });
});

// ---------------------------------------------------------------- /api/show

test('Ollama’s native /api/show 404s instead of inventing a context window', async () => {
  // The consumer probes this for context length and caches a 404 as "unknown", which is the honest
  // answer: the CLI does not report a context window before a call has been made.
  await withServer(OK, {}, async ({ base }) => {
    const response = await fetch(`${base}/api/show`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ model: 'sonnet' }),
    });
    assert.equal(response.status, 404);
  });
});

test('/healthz answers without the CLI or a token', async () => {
  await withServer(OK, { CLAUDE_BRIDGE_API_KEY: 'secret' }, async ({ base }) => {
    const response = await fetch(`${base}/healthz`);
    assert.equal(response.status, 200);
  });
});

// ---------------------------------------------------------------- auth

test('secretMatches is length-safe and exact', () => {
  assert.equal(secretMatches('abc', 'abc'), true);
  assert.equal(secretMatches('abc', 'abd'), false);
  assert.equal(secretMatches('abc', 'ab'), false);
  assert.equal(secretMatches('abc', ''), false);
});

test('extractBearer parses the header the consumer sends', () => {
  assert.equal(extractBearer({ authorization: 'Bearer sk-test' }), 'sk-test');
  assert.equal(extractBearer({ authorization: 'bearer sk-test' }), 'sk-test');
  assert.equal(extractBearer({}), null);
});

test('with no key configured every request is allowed', () => {
  assert.equal(isAuthorized(loadConfig({}), {}), true);
});

test('with a key configured, requests without the right bearer token are rejected', async () => {
  await withServer(OK, { CLAUDE_BRIDGE_API_KEY: 'sk-local' }, async ({ base, spawnFn }) => {
    const post = (headers) =>
      fetch(`${base}/v1/chat/completions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...headers },
        body: JSON.stringify({ messages: [{ role: 'user', content: 'x' }] }),
      });

    assert.equal((await post({})).status, 401);
    assert.equal((await post({ Authorization: 'Bearer wrong' })).status, 401);
    assert.equal(spawnFn.calls.length, 0, 'an unauthorized request must never spawn the CLI');

    assert.equal((await post({ Authorization: 'Bearer sk-local' })).status, 200);
  });
});

test('the model list is protected too, so a bad key reads as "provider down"', async () => {
  await withServer(OK, { CLAUDE_BRIDGE_API_KEY: 'sk-local' }, async ({ base }) => {
    assert.equal((await fetch(`${base}/v1/models`)).status, 401);
    const ok = await fetch(`${base}/v1/models`, { headers: { Authorization: 'Bearer sk-local' } });
    assert.equal(ok.status, 200);
  });
});

// ---------------------------------------------------------------- defaults

test('the bridge binds to loopback by default, and the port matches the documented wiring', () => {
  const config = loadConfig({});
  assert.equal(config.host, '127.0.0.1');
  assert.equal(config.port, 8088);
  assert.equal(loadConfig({ CLAUDE_BRIDGE_HOST: '0.0.0.0' }).host, '0.0.0.0');
});
