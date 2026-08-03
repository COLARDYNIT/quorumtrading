/**
 * The HTTP surface: the subset of the OpenAI API that `LocalHttpLLMAdapter` actually calls.
 */

import http from 'node:http';
import crypto from 'node:crypto';
import { buildCatalog } from './catalog.js';
import { BridgeError, toChatCompletion, toErrorBody } from './protocol.js';

const MAX_BODY_BYTES = 10 * 1024 * 1024;

function sendJson(res, status, payload) {
  const body = JSON.stringify(payload);
  res.writeHead(status, {
    'Content-Type': 'application/json',
    'Content-Length': Buffer.byteLength(body),
  });
  res.end(body);
}

/** Constant-time comparison so the shared secret cannot be probed a byte at a time. */
export function secretMatches(expected, presented) {
  if (typeof expected !== 'string' || typeof presented !== 'string') return false;
  const a = Buffer.from(expected, 'utf8');
  const b = Buffer.from(presented, 'utf8');
  if (a.length !== b.length) return false;
  return crypto.timingSafeEqual(a, b);
}

export function extractBearer(headers) {
  const raw = headers.authorization || headers.Authorization;
  if (typeof raw !== 'string') return null;
  const match = /^Bearer\s+(.+)$/i.exec(raw.trim());
  return match ? match[1].trim() : null;
}

/** Whether this request carries the shared secret, when one is configured. */
export function isAuthorized(config, headers) {
  if (!config.apiKey) return true;
  return secretMatches(config.apiKey, extractBearer(headers) || '');
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on('data', (chunk) => {
      size += chunk.length;
      if (size > MAX_BODY_BYTES) {
        reject(new BridgeError('Request body too large', { status: 413, type: 'invalid_request_error' }));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    req.on('error', reject);
  });
}

export function createServer({ config, service, logger = console, now = () => Date.now() }) {
  const catalog = buildCatalog(config.models);

  const handler = async (req, res) => {
    const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
    const path = url.pathname.replace(/\/+$/, '') || '/';

    // Liveness for humans; deliberately unauthenticated and CLI-free.
    if (req.method === 'GET' && path === '/healthz') {
      sendJson(res, 200, { status: 'ok' });
      return;
    }

    if (!isAuthorized(config, req.headers)) {
      sendJson(res, 401, {
        error: {
          message: 'Missing or invalid Authorization bearer token.',
          type: 'invalid_request_error',
          param: null,
          code: 'invalid_api_key',
        },
      });
      return;
    }

    if (req.method === 'GET' && path === '/v1/models') {
      // Static, so it answers inside the consumer's 3s availability probe without touching the CLI.
      sendJson(res, 200, catalog);
      return;
    }

    if (req.method === 'POST' && path === '/v1/chat/completions') {
      await handleCompletion(req, res, { config, service, logger, now });
      return;
    }

    // Notably includes Ollama's native POST /api/show, which the consumer probes for context
    // windows. A 404 is the honest answer: the CLI does not expose a per-model context length
    // before a call, and the consumer caches this 404 and reports the field as unknown. Serving an
    // invented number would put a guess in a field that reads as a measurement.
    sendJson(res, 404, {
      error: {
        message: `No such endpoint: ${req.method} ${path}`,
        type: 'invalid_request_error',
        param: null,
        code: 'not_found',
      },
    });
  };

  const server = http.createServer((req, res) => {
    handler(req, res).catch((error) => {
      logger.error(`[bridge] unhandled error: ${error && error.stack ? error.stack : error}`);
      if (!res.headersSent) {
        sendJson(res, 500, toErrorBody(error));
      } else {
        res.end();
      }
    });
  });

  return server;
}

async function handleCompletion(req, res, { config, service, logger, now }) {
  let request;
  try {
    const raw = await readBody(req);
    request = JSON.parse(raw);
  } catch (error) {
    const status = error instanceof BridgeError ? error.status : 400;
    sendJson(res, status, toErrorBody(error instanceof BridgeError ? error : new BridgeError('Malformed JSON body', {
      status: 400,
      type: 'invalid_request_error',
    })));
    return;
  }

  if (request && request.stream) {
    sendJson(
      res,
      400,
      toErrorBody(
        new BridgeError('Streaming is not supported by this bridge; send `stream: false`.', {
          status: 400,
          type: 'invalid_request_error',
        }),
      ),
    );
    return;
  }

  // If the caller hangs up, stop paying for the answer: the CLI child gets SIGTERM.
  const controller = new AbortController();
  const onClose = () => {
    if (!res.writableEnded) controller.abort();
  };
  res.on('close', onClose);

  const started = now();
  try {
    const run = await service.complete(request, { signal: controller.signal });
    const body = toChatCompletion(run, {
      id: `chatcmpl-${crypto.randomUUID()}`,
      created: Math.floor(now() / 1000),
    });
    if (config.verbose) {
      logger.log(
        `[bridge] ${run.model} answered in ${now() - started}ms ` +
          `(${run.usage ? `${run.usage.prompt_tokens}+${run.usage.completion_tokens} tokens` : 'usage unreported'})`,
      );
    }
    if (!res.writableEnded) sendJson(res, 200, body);
  } catch (error) {
    const status = error instanceof BridgeError ? error.status : 500;
    // 499 is the client-disconnected sentinel: there is nobody left to answer.
    if (status === 499) {
      logger.warn('[bridge] client disconnected; CLI terminated');
      if (!res.writableEnded) res.destroy();
      return;
    }
    logger.error(`[bridge] completion failed: ${error.message}`);
    if (!res.writableEnded) sendJson(res, status, toErrorBody(error));
  } finally {
    res.off('close', onClose);
  }
}
