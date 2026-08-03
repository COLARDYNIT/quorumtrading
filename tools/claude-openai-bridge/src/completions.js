/**
 * One chat completion: build argv, run the CLI, interpret what came back.
 */

import { buildArgs, interpretRun, BridgeError } from './protocol.js';
import { runCli, detectUnknownOption } from './runner.js';

/**
 * Flags this bridge would like to pass but can live without.
 *
 * `--max-turns` is accepted by CLI 2.1.220 but is *not* in `--help`, so it may be renamed or
 * removed without notice. Rather than pin the bridge to one CLI build, a rejected flag is detected
 * and dropped for the rest of the process lifetime. This costs nothing: the CLI validates argv
 * before it makes any API call, so a rejected flag burns no tokens.
 */
const OPTIONAL_FLAGS = {
  '--max-turns': 'maxTurns',
  '--effort': 'effort',
};

export class CompletionService {
  constructor({ config, limiter, spawnFn, logger = console }) {
    this.config = config;
    this.limiter = limiter;
    this.spawnFn = spawnFn;
    this.logger = logger;
    /** Flags proved unsupported by the installed CLI; starts optimistic. */
    this.capabilities = { maxTurns: true, effort: true };
  }

  async complete(request, { signal } = {}) {
    return this.limiter.run(() => this.#attempt(request, { signal, allowRetry: true }));
  }

  async #attempt(request, { signal, allowRetry }) {
    const { args, stdin } = buildArgs(request, this.config, this.capabilities);

    const outcome = await runCli({
      bin: this.config.claudeBin,
      args,
      stdin,
      cwd: this.config.cwd,
      timeoutMs: this.config.timeoutMs,
      signal,
      spawnFn: this.spawnFn,
    });

    // Did the CLI reject one of our optional flags? Drop it and try once more.
    if (outcome.exitCode !== 0) {
      const rejected = detectUnknownOption(outcome.stderr, outcome.stdout);
      const capability = rejected ? OPTIONAL_FLAGS[rejected] : null;
      if (capability && this.capabilities[capability] && allowRetry) {
        this.logger.warn(
          `[bridge] installed claude CLI rejected ${rejected}; continuing without it for the rest of this process.`,
        );
        this.capabilities[capability] = false;
        return this.#attempt(request, { signal, allowRetry: false });
      }
      if (rejected && !capability) {
        throw new BridgeError(
          `claude CLI rejected a required flag (${rejected}). The installed CLI is not compatible with this bridge.`,
          { status: 500, type: 'bridge_configuration_error', code: 'incompatible_cli' },
        );
      }
    }

    const run = interpretRun(outcome);
    if (run.rateLimit && this.config.verbose) {
      this.logger.warn(`[bridge] rate limit info: ${JSON.stringify(run.rateLimit)}`);
    }
    return run;
  }
}
