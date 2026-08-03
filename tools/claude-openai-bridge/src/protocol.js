/**
 * Pure translation between the OpenAI chat-completions shape and the `claude` CLI.
 *
 * Everything here is a function of its arguments — no spawning, no clock, no I/O — so the mapping
 * rules and, more importantly, the refusal-to-fabricate rules are unit-testable without a CLI.
 */

/**
 * Flags this bridge must never pass.
 *
 * `--bare` sets `CLAUDE_CODE_SIMPLE=1` and, in the CLI's own words, "Anthropic auth is strictly
 * ANTHROPIC_API_KEY or apiKeyHelper via --settings (OAuth and keychain are never read)". Passing it
 * would defeat the entire purpose of this bridge, which is to use the user's *subscription*.
 */
export const FORBIDDEN_FLAGS = ['--bare'];

export class BridgeError extends Error {
  constructor(message, { status = 502, type = 'claude_cli_error', code = null } = {}) {
    super(message);
    this.name = 'BridgeError';
    this.status = status;
    this.type = type;
    this.code = code;
  }
}

/** OpenAI content can be a string or an array of typed parts. Accept both, invent nothing. */
export function extractText(content) {
  if (content === null || content === undefined) return '';
  if (typeof content === 'string') return content;
  if (Array.isArray(content)) {
    return content
      .map((part) => {
        if (typeof part === 'string') return part;
        if (part && typeof part === 'object' && typeof part.text === 'string') return part.text;
        return '';
      })
      .filter(Boolean)
      .join('\n');
  }
  throw new BridgeError('Unsupported message content type', {
    status: 400,
    type: 'invalid_request_error',
  });
}

/**
 * Split OpenAI `messages` into the system prompt and the conversational turns.
 *
 * System messages are pulled out and joined; they become `--system-prompt`, which the CLI applies
 * with *replace* semantics (verified: a system prompt of "you are a lemon" produced "lemon" rather
 * than a coding-assistant answer). That replacement is the point — a trading agent must not inherit
 * the Claude Code persona.
 */
export function splitMessages(messages) {
  if (!Array.isArray(messages) || messages.length === 0) {
    throw new BridgeError('`messages` must be a non-empty array', {
      status: 400,
      type: 'invalid_request_error',
    });
  }

  const systemParts = [];
  const turns = [];

  for (const message of messages) {
    if (!message || typeof message !== 'object') {
      throw new BridgeError('Each entry in `messages` must be an object', {
        status: 400,
        type: 'invalid_request_error',
      });
    }
    const role = message.role;
    const text = extractText(message.content);

    if (role === 'system' || role === 'developer') {
      if (text.trim()) systemParts.push(text);
    } else if (role === 'user' || role === 'assistant') {
      turns.push({ role, text });
    } else {
      throw new BridgeError(`Unsupported message role: ${JSON.stringify(role)}`, {
        status: 400,
        type: 'invalid_request_error',
      });
    }
  }

  if (turns.length === 0) {
    throw new BridgeError('`messages` must contain at least one user or assistant turn', {
      status: 400,
      type: 'invalid_request_error',
    });
  }

  return { systemPrompt: systemParts.join('\n\n'), turns };
}

/**
 * Render conversational turns into the single prompt the CLI reads from stdin.
 *
 * The common case for this bridge's consumer is exactly one user turn, and that is passed through
 * *verbatim* — no headers, no framing. Anything else would silently edit a carefully built agent
 * prompt. Only genuine multi-turn conversations get role labels, because there the structure has to
 * survive the flattening somehow.
 */
export function renderTranscript(turns) {
  if (turns.length === 1 && turns[0].role === 'user') {
    return turns[0].text;
  }
  const body = turns.map((t) => `${t.role === 'user' ? 'User' : 'Assistant'}: ${t.text}`).join('\n\n');
  return `${body}\n\nAssistant:`;
}

/** Effort levels the CLI documents for `--effort`. */
const EFFORT_LEVELS = new Set(['low', 'medium', 'high', 'xhigh', 'max']);

/**
 * Map an OpenAI `reasoning_effort` onto the CLI's `--effort`.
 * Unknown values are dropped rather than passed through — an invalid value makes the CLI exit
 * non-zero, turning a cosmetic hint into a failed trading decision.
 */
export function mapEffort(reasoningEffort) {
  if (typeof reasoningEffort !== 'string') return null;
  const normalized = reasoningEffort.trim().toLowerCase();
  if (EFFORT_LEVELS.has(normalized)) return normalized;
  if (normalized === 'minimal' || normalized === 'none') return 'low';
  return null;
}

/**
 * Strip a namespace from a model id so callers can send hosted-style ids.
 * `anthropic/claude-sonnet-4` -> `claude-sonnet-4`. The CLI accepts both aliases and full names.
 */
export function normalizeModelId(model) {
  if (typeof model !== 'string') return null;
  const trimmed = model.trim();
  if (!trimmed) return null;
  const slash = trimmed.lastIndexOf('/');
  return slash === -1 ? trimmed : trimmed.slice(slash + 1);
}

/**
 * Build the argv for one CLI invocation.
 *
 * @param {object} request       parsed OpenAI request body
 * @param {object} config        bridge config (default system prompt, fallback model)
 * @param {object} capabilities  flags this CLI build is known to accept; a flag disproved at
 *                               runtime is dropped here rather than retried forever
 */
export function buildArgs(request, config, capabilities = {}) {
  const { maxTurns = true, effort = true } = capabilities;
  const { systemPrompt, turns } = splitMessages(request.messages);

  const args = [
    '-p',
    '--output-format',
    'stream-json',
    // stream-json is a strict superset of `--output-format json`: it carries the identical final
    // `result` event AND per-message `assistant` events that name the model that actually answered.
    // With plain json the only model signal is `modelUsage`, which was observed to contain two
    // entries for a single request (an ancillary haiku call alongside the real sonnet answer) — so
    // it cannot identify the answering model on its own.
    '--verbose',
    // No tools. This is an LLM completion endpoint, not an agent with filesystem and shell access.
    // Verified via the CLI's own init event, which reported `tools: []`.
    '--tools',
    '',
    // Ignore the user's configured MCP servers: a completion must not acquire side effects, and
    // loading them costs startup latency on every call.
    '--strict-mcp-config',
    // Do not litter the session store with one file per agent decision.
    '--no-session-persistence',
  ];

  if (maxTurns) args.push('--max-turns', '1');

  const model = normalizeModelId(request.model) || config.models[0];
  if (model) args.push('--model', model);

  // Always send a system prompt. An empty one would leave the CLI's default coding-assistant
  // persona (plus auto-discovered CLAUDE.md) in place — measured at ~24k extra prompt tokens.
  args.push('--system-prompt', systemPrompt.trim() ? systemPrompt : config.defaultSystemPrompt);

  if (effort) {
    const level = mapEffort(request.reasoning_effort);
    if (level) args.push('--effort', level);
  }

  assertNoForbiddenFlags(args);
  return { args, stdin: renderTranscript(turns), model };
}

export function assertNoForbiddenFlags(args) {
  for (const forbidden of FORBIDDEN_FLAGS) {
    if (args.includes(forbidden)) {
      throw new BridgeError(
        `Refusing to invoke the CLI with ${forbidden}: it bypasses subscription login.`,
        { status: 500, type: 'bridge_configuration_error' },
      );
    }
  }
}

/** Parse NDJSON, tolerating blank lines and partial trailing output. */
export function parseNdjson(text) {
  const events = [];
  for (const line of String(text).split('\n')) {
    const trimmed = line.trim();
    if (!trimmed) continue;
    try {
      events.push(JSON.parse(trimmed));
    } catch {
      // A non-JSON line is CLI noise, not an answer. Ignored deliberately — never treated as content.
    }
  }
  return events;
}

/**
 * Turn the CLI's real prompt-token accounting into a single OpenAI `prompt_tokens`.
 *
 * All three components are genuinely counted prompt tokens; they differ only in how they are
 * billed. Reporting `input_tokens` alone understates a cached call by orders of magnitude —
 * observed 9 against 7,075 cache-creation + 17,536 cache-read for one request — and would trip the
 * consumer's own plausibility guard, which rejects any count below one token per 20 prompt
 * characters. OpenAI's `prompt_tokens` likewise counts cached tokens.
 *
 * @returns a usage object, or null when the CLI reported nothing usable. Null means the field is
 *          omitted entirely; this bridge never estimates token counts.
 */
export function normalizeUsage(rawUsage) {
  if (!rawUsage || typeof rawUsage !== 'object') return null;

  const int = (value) => (typeof value === 'number' && Number.isFinite(value) && value >= 0 ? Math.round(value) : 0);

  const promptTokens =
    int(rawUsage.input_tokens) +
    int(rawUsage.cache_creation_input_tokens) +
    int(rawUsage.cache_read_input_tokens);
  const completionTokens = int(rawUsage.output_tokens);

  if (promptTokens === 0 && completionTokens === 0) return null;

  return {
    prompt_tokens: promptTokens,
    completion_tokens: completionTokens,
    total_tokens: promptTokens + completionTokens,
  };
}

/**
 * Work out which model actually answered, using only what the CLI reported.
 *
 * Order of authority:
 *   1. the last `assistant` event's `message.model` — the model that produced the text;
 *   2. the single key of `modelUsage`, when there is exactly one (unambiguous);
 *   3. the `system`/init event's resolved `model`.
 *
 * The requested model is never a fallback. The consumer records this field as the model that
 * answered, so echoing the request back would launder a guess into a stored fact.
 */
export function resolveAnsweringModel({ assistantModels, result, systemInit }) {
  for (let i = assistantModels.length - 1; i >= 0; i -= 1) {
    const candidate = assistantModels[i];
    if (typeof candidate === 'string' && candidate.trim()) return candidate.trim();
  }
  const usageKeys = result && result.modelUsage ? Object.keys(result.modelUsage) : [];
  if (usageKeys.length === 1 && usageKeys[0].trim()) return usageKeys[0].trim();
  if (systemInit && typeof systemInit.model === 'string' && systemInit.model.trim()) {
    return systemInit.model.trim();
  }
  return null;
}

/**
 * Interpret one completed CLI run.
 *
 * Throws — never returns text — when the CLI failed or produced nothing. An empty answer is an
 * error, not a message: synthesising prose to fill a blank result is the exact failure mode this
 * bridge exists to avoid, and ADR-0004 ("watchdogs pause on evidence, never fabricate") forbids it.
 */
export function interpretRun({ stdout, stderr, exitCode, signal }) {
  const events = parseNdjson(stdout);
  const systemInit = events.find((e) => e.type === 'system' && e.subtype === 'init') || null;
  const assistantModels = events
    .filter((e) => e.type === 'assistant' && e.message)
    .map((e) => e.message.model);
  const result = events.find((e) => e.type === 'result') || null;
  const rateLimit = events.filter((e) => e.type === 'rate_limit_event').pop() || null;

  if (signal) {
    throw new BridgeError(`claude CLI terminated by signal ${signal}`, { status: 504, code: 'terminated' });
  }

  if (!result) {
    const detail = (stderr || '').trim() || `exit code ${exitCode}`;
    throw new BridgeError(`claude CLI produced no result event (${detail})`, {
      status: 502,
      code: 'no_result',
    });
  }

  if (exitCode !== 0 || result.is_error === true) {
    const detail =
      (typeof result.result === 'string' && result.result.trim()) ||
      (stderr || '').trim() ||
      result.subtype ||
      `exit code ${exitCode}`;
    throw new BridgeError(`claude CLI reported an error: ${detail}`, {
      status: 502,
      code: result.api_error_status ? String(result.api_error_status) : 'cli_error',
    });
  }

  const content = typeof result.result === 'string' ? result.result : '';
  if (!content.trim()) {
    // Do NOT invent a message here. See the class comment.
    throw new BridgeError(
      'claude CLI returned an empty response; refusing to synthesise content for it.',
      { status: 502, code: 'empty_response' },
    );
  }

  const model = resolveAnsweringModel({ assistantModels, result, systemInit });
  if (!model) {
    throw new BridgeError(
      'claude CLI did not report which model answered; refusing to guess.',
      { status: 502, code: 'unknown_model' },
    );
  }

  return {
    content,
    model,
    usage: normalizeUsage(result.usage),
    stopReason: typeof result.stop_reason === 'string' ? result.stop_reason : null,
    rateLimit: rateLimit ? rateLimit.rate_limit_info || null : null,
    systemInit,
  };
}

/** OpenAI `stop_reason` vocabulary, mapped from the CLI's. */
export function mapFinishReason(stopReason) {
  if (stopReason === 'max_tokens') return 'length';
  if (stopReason === 'tool_use') return 'tool_calls';
  return 'stop';
}

/** Shape one interpreted run as an OpenAI chat completion. */
export function toChatCompletion(run, { id, created }) {
  const body = {
    id,
    object: 'chat.completion',
    created,
    // The model the CLI actually used — see resolveAnsweringModel.
    model: run.model,
    choices: [
      {
        index: 0,
        message: { role: 'assistant', content: run.content },
        finish_reason: mapFinishReason(run.stopReason),
      },
    ],
  };
  // Omitted entirely rather than guessed when the CLI reported nothing usable.
  if (run.usage) body.usage = run.usage;
  return body;
}

export function toErrorBody(error) {
  const bridgeError = error instanceof BridgeError ? error : null;
  return {
    error: {
      message: error && error.message ? error.message : 'Unknown bridge error',
      type: bridgeError ? bridgeError.type : 'internal_error',
      param: null,
      code: bridgeError ? bridgeError.code : null,
    },
  };
}
