/**
 * Spawning the CLI: concurrency capping, queueing, timeouts and cancellation.
 *
 * The spawn function is injected so every rule in here is testable without launching a real
 * process.
 */

import { spawn as nodeSpawn } from 'node:child_process';
import os from 'node:os';
import { BridgeError } from './protocol.js';

/**
 * A counting semaphore with a bounded waiting room.
 *
 * Each request is a whole CLI process talking to a subscription the user is also using
 * interactively, so unbounded fan-out is not a throughput win — it is a way to exhaust a rate limit
 * shared with the human at the keyboard. When the waiting room is full the bridge sheds load rather
 * than growing an unbounded queue that would blow past every caller's timeout anyway.
 */
export class ConcurrencyLimiter {
  constructor({ maxConcurrency = 2, maxQueueDepth = 32 } = {}) {
    this.maxConcurrency = Math.max(1, maxConcurrency);
    this.maxQueueDepth = Math.max(0, maxQueueDepth);
    this.active = 0;
    this.queue = [];
  }

  get queued() {
    return this.queue.length;
  }

  acquire() {
    if (this.active < this.maxConcurrency) {
      this.active += 1;
      return Promise.resolve();
    }
    if (this.queue.length >= this.maxQueueDepth) {
      return Promise.reject(
        new BridgeError(
          `Bridge is saturated: ${this.active} in flight, ${this.queue.length} queued. Retry shortly.`,
          { status: 503, type: 'rate_limit_error', code: 'bridge_saturated' },
        ),
      );
    }
    return new Promise((resolve) => {
      this.queue.push(resolve);
    });
  }

  release() {
    const next = this.queue.shift();
    if (next) {
      next(); // hands the slot straight over; `active` stays as-is
      return;
    }
    this.active = Math.max(0, this.active - 1);
  }

  async run(task) {
    await this.acquire();
    try {
      return await task();
    } finally {
      this.release();
    }
  }
}

/** True when the CLI rejected a flag we passed, e.g. `error: unknown option '--max-turns'`. */
export function detectUnknownOption(stderr, stdout = '') {
  const match = /unknown option ['"`]?(--[a-zA-Z0-9-]+)/.exec(`${stderr}\n${stdout}`);
  return match ? match[1] : null;
}

/**
 * Run the CLI once.
 *
 * Resolves with the raw outcome even when the exit code is non-zero — deciding what a failure means
 * belongs to `interpretRun`, not here.
 */
export function runCli({
  bin,
  args,
  stdin,
  cwd,
  timeoutMs,
  signal,
  spawnFn = nodeSpawn,
  env = process.env,
}) {
  return new Promise((resolve, reject) => {
    let child;
    try {
      child = spawnFn(bin, args, {
        cwd: cwd || os.tmpdir(),
        env,
        stdio: ['pipe', 'pipe', 'pipe'],
      });
    } catch (error) {
      reject(new BridgeError(`Could not start '${bin}': ${error.message}`, { status: 500, code: 'spawn_failed' }));
      return;
    }

    let stdout = '';
    let stderr = '';
    let settled = false;
    let timedOut = false;
    let aborted = false;
    let killTimer = null;

    const cleanup = () => {
      clearTimeout(timeoutTimer);
      if (killTimer) clearTimeout(killTimer);
      if (signal && onAbort) signal.removeEventListener('abort', onAbort);
    };

    /** SIGTERM first so the CLI can wind down, SIGKILL only if it ignores us. */
    const terminate = () => {
      try {
        child.kill('SIGTERM');
      } catch {
        /* already gone */
      }
      killTimer = setTimeout(() => {
        try {
          child.kill('SIGKILL');
        } catch {
          /* already gone */
        }
      }, 5_000);
      if (killTimer.unref) killTimer.unref();
    };

    const timeoutTimer = setTimeout(() => {
      timedOut = true;
      terminate();
    }, timeoutMs);
    if (timeoutTimer.unref) timeoutTimer.unref();

    const onAbort = signal
      ? () => {
          aborted = true;
          terminate();
        }
      : null;
    if (signal) {
      if (signal.aborted) {
        // Client already gone; never start work for nobody.
        terminate();
        aborted = true;
      } else {
        signal.addEventListener('abort', onAbort, { once: true });
      }
    }

    child.stdout.setEncoding('utf8');
    child.stderr.setEncoding('utf8');
    child.stdout.on('data', (chunk) => {
      stdout += chunk;
    });
    child.stderr.on('data', (chunk) => {
      stderr += chunk;
    });

    child.on('error', (error) => {
      if (settled) return;
      settled = true;
      cleanup();
      reject(new BridgeError(`claude CLI failed to run: ${error.message}`, { status: 500, code: 'spawn_failed' }));
    });

    child.on('close', (code, closeSignal) => {
      if (settled) return;
      settled = true;
      cleanup();
      if (timedOut) {
        reject(
          new BridgeError(`claude CLI exceeded the ${timeoutMs}ms bridge timeout`, {
            status: 504,
            code: 'timeout',
          }),
        );
        return;
      }
      if (aborted) {
        reject(new BridgeError('Client disconnected; CLI process terminated', { status: 499, code: 'client_closed' }));
        return;
      }
      resolve({ stdout, stderr, exitCode: code === null ? -1 : code, signal: closeSignal || null });
    });

    if (child.stdin) {
      child.stdin.on('error', () => {
        /* the child may exit before we finish writing; the close handler reports the real reason */
      });
      child.stdin.end(stdin, 'utf8');
    }
  });
}
