/**
 * Configuration, read once from the environment.
 *
 * Every value has a default that works for the QuorumTrading local stack; nothing here reaches out
 * to the network or the filesystem.
 */

/** Aliases the installed CLI accepts for `--model`, in deliberate order (see catalog.js). */
const DEFAULT_MODELS = 'sonnet,haiku,opus';

/**
 * Neutral system prompt used when the caller sends no system message.
 *
 * Passing `--system-prompt` is not optional-by-accident: without it the CLI loads its full Claude
 * Code coding-assistant persona plus auto-discovered CLAUDE.md, which (measured on this machine)
 * added ~24k prompt tokens per call and told the model it was a coding assistant. Neither is right
 * for a trading agent. This default replaces that persona with as little as possible.
 */
const DEFAULT_SYSTEM_PROMPT = 'You are a helpful assistant. Answer the user directly and concisely.';

function num(raw, fallback) {
  if (raw === undefined || raw === null || String(raw).trim() === '') return fallback;
  const parsed = Number(raw);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function bool(raw, fallback) {
  if (raw === undefined || raw === null || String(raw).trim() === '') return fallback;
  return ['1', 'true', 'yes', 'on'].includes(String(raw).trim().toLowerCase());
}

export function loadConfig(env = process.env) {
  return {
    host: env.CLAUDE_BRIDGE_HOST || '127.0.0.1',
    port: num(env.CLAUDE_BRIDGE_PORT, 8088),

    /** Shared secret. When set, every request must carry `Authorization: Bearer <it>`. */
    apiKey: env.CLAUDE_BRIDGE_API_KEY || null,

    claudeBin: env.CLAUDE_BRIDGE_CLAUDE_BIN || 'claude',

    /**
     * Working directory for the spawned CLI. Deliberately NOT the repo: the CLI auto-discovers
     * CLAUDE.md from its cwd, and a trading agent must not inherit this project's instructions.
     */
    cwd: env.CLAUDE_BRIDGE_CWD || null, // null => os.tmpdir() at spawn time

    /**
     * Hard ceiling on one CLI call. The backend's own timeout for this engine is 120s by default
     * and 300s in the `%local` profile, so the bridge must never be the shorter one — otherwise it
     * turns a slow-but-fine answer into a bridge error the backend cannot distinguish from a real
     * failure.
     */
    timeoutMs: num(env.CLAUDE_BRIDGE_TIMEOUT_MS, 360_000),

    /** Concurrent CLI processes. Small by design: these share the user's subscription limits. */
    maxConcurrency: Math.max(1, num(env.CLAUDE_BRIDGE_MAX_CONCURRENCY, 2)),

    /** Requests allowed to wait for a slot before the bridge sheds load with 503. */
    maxQueueDepth: Math.max(0, num(env.CLAUDE_BRIDGE_MAX_QUEUE, 32)),

    models: String(env.CLAUDE_BRIDGE_MODELS || DEFAULT_MODELS)
      .split(',')
      .map((m) => m.trim())
      .filter(Boolean),

    defaultSystemPrompt: env.CLAUDE_BRIDGE_SYSTEM_PROMPT || DEFAULT_SYSTEM_PROMPT,

    /**
     * Refuse to start unless the CLI reports `apiKeySource: "none"`, i.e. it is authenticating
     * with the subscription login rather than an ANTHROPIC_API_KEY. This is the guard against the
     * documented drift risk: `--bare` (which the CLI says never reads OAuth or the keychain) is
     * slated to become the `-p` default. If that lands, this check fails loudly at startup instead
     * of silently billing an API key.
     */
    requireSubscriptionAuth: bool(env.CLAUDE_BRIDGE_REQUIRE_SUBSCRIPTION, true),

    /** Skip the startup preflight entirely. Escape hatch; documented as unsupported. */
    skipPreflight: bool(env.CLAUDE_BRIDGE_SKIP_PREFLIGHT, false),

    verbose: bool(env.CLAUDE_BRIDGE_VERBOSE, false),
  };
}

export { DEFAULT_MODELS, DEFAULT_SYSTEM_PROMPT };
