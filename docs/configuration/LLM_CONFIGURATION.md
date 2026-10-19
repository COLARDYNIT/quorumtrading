# LLM Configuration Guide

## LocalLLM Adapter Configuration

The LocalLLM adapter supports multiple CLI-based LLM tools with intelligent fallback and exponential backoff.

### Fallback Configuration

```properties
# Order of tools to try (comma-separated, left-to-right priority)
# Shipped default — codex and gemini are skipped as too slow
quorumtrading.llm.local.tool.fallback=claude,qwen

# Custom override example (supported tools: claude, qwen, codex, gemini, opencode, dummy)
# quorumtrading.llm.local.tool.fallback=qwen,claude,codex,gemini

# Per-agent tool override (optional). The key segment must exactly match the
# agent name carried in LLMConfig.model — lowercase role names, e.g.
# technical, fundamental, sentiment, risk
quorumtrading.agents.technical.llm-tool=qwen
quorumtrading.agents.sentiment.llm-tool=claude
```

### Failure Cache Configuration

```properties
# Values configured in application.properties
# (code-level fallback defaults in ToolFailureCache are 60 / 1440)

# Initial backoff period after first failure (minutes)
quorumtrading.llm.local.cache.initial-backoff-minutes=30

# Maximum backoff period cap (minutes, 12 hours)
quorumtrading.llm.local.cache.max-backoff-minutes=720
```

## How Fallback Works

1. System tries requested tool first (per-agent or default)
2. On `CLIExecutionException`, records failure and tries next tool in fallback list
3. Short-circuits on first successful execution
4. Failed tools are temporarily blacklisted with exponential backoff (30m → 1h → 2h → ... → 12h max)
5. Blacklisted tools are skipped entirely during their backoff period

### Example Failure Progression

With the shipped chain `claude,qwen`:

```
T+0m:    claude fails → blacklist 30m → try qwen → success
T+15m:   claude blacklisted (15m left) → skip to qwen
T+45m:   claude available → fails again → blacklist 60m (until T+105m)
T+70m:   claude blacklisted (35m left) → qwen fails → blacklist 30m → chain exhausted → failure
```

## The opencode engine

`opencode` is the one local CLI that natively speaks `provider/model`, so an OpenRouter-style id —
including the ones this platform ships as defaults — can be handed to it unchanged. Every other CLI
here takes a private alias vocabulary.

It is **opt-in and changes no shipped default**. `claude` remains `default-tool` and the chain
remains `claude,qwen`; naming opencode is the only way to run it:

```properties
# Either make it the default…
quorumtrading.llm.local.default-tool=opencode
# …or add it to the chain
quorumtrading.llm.local.tool.fallback=opencode,claude,qwen
```

### Settings

```properties
# Directory opencode runs in. Blank → a scratch dir under the system temp dir. Never the JVM's cwd.
quorumtrading.llm.local.opencode.working-dir=${QT_LLM_OPENCODE_DIR:}

# Agent persona (--agent). Blank → flag omitted → opencode's own default agent 'build'.
quorumtrading.llm.local.opencode.agent=${QT_LLM_OPENCODE_AGENT:}

# Pass --auto (auto-approve permissions). OFF by default. See the warning below.
quorumtrading.llm.local.opencode.auto-approve=${QT_LLM_OPENCODE_AUTO_APPROVE:false}
```

### Why the working directory is pinned

opencode discovers project context from wherever it starts. Left to inherit the backend's working
directory it reads whatever repository the JVM was launched from and prepends it to every trading
decision. Measured on opencode 1.18.10, same model, same nine-word prompt, only the directory
differing:

| Working directory | Input tokens |
|---|---:|
| repository root (inherited cwd) | 13,800 |
| empty scratch dir (`--dir`) | 6,230 |

That is **7,570 input tokens of unrelated repository context on every call** — paid per agent, per
trading day, for the whole of a backtest. Running inside a git repository additionally made opencode
take a **git snapshot of the working tree**, which is a side effect on your checkout that an LLM
call has no business having. Both `--dir` and the child process's own cwd are pinned.

### Setting a trading persona (`--agent`)

opencode's default agent is `build` — a **coding assistant** with tool permissions. That is the
wrong persona for a trading decision, and it is why `--agent` exists here.

This platform will **not** write into your home directory, so you must create the agent yourself.
Create `~/.config/opencode/agent/quorum-trader.md`:

```markdown
---
description: Trading analyst for QuorumTrading. Answers from the prompt only.
mode: primary
tools:
  write: false
  edit: false
  bash: false
  read: false
---

You are a trading analyst. Answer only from the information in the prompt.
Never inspect the filesystem, never run commands, never browse the web.
Reply with exactly the JSON object the prompt asks for and nothing else.
```

Then point the backend at it:

```properties
quorumtrading.llm.local.opencode.agent=quorum-trader
```

Verify with `opencode agent list`. Naming an agent that does not exist fails **every** call, which
is why the property is unset by default rather than defaulted to a name we hope you created.

### `--auto` is off, deliberately

`--auto` auto-approves any permission not explicitly denied; opencode's own help calls it
"dangerous!". It is **not** required for unattended use: verified on 1.18.10, one-shot unattended
calls complete normally without it once the child's stdin is closed. The hangs it was assumed to
prevent were a stdin bug, not permission prompts.

Leave it off unless you have a specific reason. Turning it on lets the model execute tools
unattended, and opencode loads **MCP servers from your global `~/.config/opencode/opencode.json`** —
which on a developer machine can include this platform's own MCP server. Auto-approving a trading
agent's tool calls against your own live instance is not a default anyone should inherit.

### What opencode does and does not report

| Field | Behaviour |
|---|---|
| Response text | Taken from the `text` events of the JSON stream |
| Token counts | **Real** — opencode reports `input`/`output`/`reasoning`; recorded as measured |
| Cost | Taken from the stream's `cost` field |
| Answering model | **Not recorded.** Verified: a full successful run contains the string `model` zero times |

opencode never names the model that answered. Rather than echo the id that was *requested* — which
would launder a guess into a stored fact — the answering model is left unset. The only place it is
recorded is the persisted session (`opencode export <sessionID>` reports `providerID` + `modelID`),
which costs a second process spawn per call and is deliberately not done.

An `error` event, or output with no assistant text, raises an error. It never becomes a response —
a trading decision derived from an invented answer is worse than no decision.

### Model catalogue

opencode advertises no catalogue to this platform. Which `provider/model` pairs work depends on the
providers *you* configured in opencode, which the backend cannot see. Run `opencode models` to list
them. A guessed list would let you pin an id the tool rejects, failing mid-backtest rather than at
save time.

## Local HTTP Server Configuration

`LocalHttpLLMAdapter` talks to any OpenAI-compatible server you run yourself — Ollama, LM Studio,
llama.cpp, vLLM. Unlike the CLI adapter above it reaches its engine over HTTP, so it works from
inside the Docker container via `host.docker.internal`.

```properties
quorumtrading.llm.localhttp.enabled=${QT_LLM_LOCALHTTP_ENABLED:false}
quorumtrading.llm.localhttp.base-url=${QT_LLM_LOCALHTTP_URL:http://host.docker.internal:11434/v1}
quorumtrading.llm.localhttp.model=${QT_LLM_LOCALHTTP_MODEL:}
quorumtrading.llm.localhttp.api-key=${QT_LLM_LOCALHTTP_API_KEY:not-configured}
```

Leave `model` empty and the adapter takes the server's first advertised model. `api-key` is sent as
`Authorization: Bearer …`; a blank value or the literal `not-configured` sends no header at all,
which is what a keyless Ollama wants.

That HTTP boundary is also how a **CLI** becomes usable under Docker. The container has no `claude`
binary and a subprocess cannot cross the container boundary — but HTTP can, so a bridge running on
the host closes the gap:
[`tools/claude-openai-bridge`](../../tools/claude-openai-bridge/README.md) exposes the local `claude`
CLI on `:8088` as an OpenAI-compatible endpoint, and the backend then treats it exactly like Ollama
(`QT_LLM_LOCALHTTP_URL=http://host.docker.internal:8088/v1`, `QT_LLM_LOCALHTTP_MODEL=sonnet`). Note
that this adapter hard-codes free local inference, so those calls are **recorded as $0** while
actually drawing on a Claude subscription. It is personal/self-host tooling and must never become a
hosted-edition auth path; see its README for the full limitations.

### CLI engines are host-only — the Docker backend cannot spawn them

Everything under `quorumtrading.llm.local.*` — `claude`, `qwen`, `codex`, `gemini` **and
`opencode`** — works by starting a child process. The backend container has none of those binaries
on its PATH, and a subprocess cannot cross the container boundary. Selecting a CLI engine in the
Docker stack does not fall back to anything; it simply fails to find the tool. **CLI engines require
a natively-run backend.**

The only way a CLI engine reaches a containerised backend is over HTTP, which is exactly what
[`tools/claude-openai-bridge`](../../tools/claude-openai-bridge/README.md) does for `claude`.

**There is no opencode bridge, and none is planned here.** If one is ever wanted, do not copy the
claude bridge's approach of wrapping the one-shot CLI: opencode already ships `opencode serve`, a
headless server (see `opencode serve --help` and `opencode attach`). Building on that is strictly
better than spawning `opencode run` per request — it avoids per-call process startup, and it avoids
the entire class of stdin/pipe defect that one-shot spawning invites.

## OpenRouter Configuration

### Rate Limiting

The adapter handles OpenRouter capacity problems with model-level fallback, not in-request retries:

- HTTP 429 (rate limit), 402 (spend limit), and 503 (service unavailable) raise `RateLimitException`
- The failing model is recorded in `ModelRateLimitCache` with exponential backoff
- The adapter then advances to the next model in the fallback chain

```properties
# Per-model blacklist backoff (30 min initial, 12 h max)
quorumtrading.llm.openrouter.cache.initial-backoff-minutes=30
quorumtrading.llm.openrouter.cache.max-backoff-minutes=720
```

### Configuration

```bash
# Required for orchestration
export OPENROUTER_API_KEY="sk-or-v1-..."
```

Model selection works in two layers:

- `ModelResolutionService` resolves the model per user and agent role at runtime
- The OpenRouter chain default is `quorumtrading.llm.openrouter.model.fallback` in `application.properties` (comma-separated, left-to-right priority) — see that property for the current model list

Avoid `:free` tier models — the config notes they have a history of 100% parse failures.

### Temperature is ignored on the Claude 5.5 models

The Anthropic 5.5 slugs on OpenRouter do not list `temperature` or `top_p` in
`supported_parameters`. `OpenRouterAdapter` and `LocalHttpLLMAdapter` still send `temperature`;
OpenRouter drops unsupported parameters because we do not set `provider.require_parameters`, so the
request does not fail. It does mean every temperature setting (for example
`quorumtrading.picker.llm.temperature`) has no effect when the resolved model is a 5.5 Claude
model. Other providers in the chain still honour it, which is why the adapters keep sending it.

## LLM Provider Abstraction

- `LLMPort` is the output port; its primary method is `queryWithUsage(prompt, config)` (`query(prompt, config)` is a backward-compatibility convenience default)
- `LLMRouterAdapter` implements the port and routes to the concrete providers (`OpenRouterAdapter`, `LocalLLMAdapter`, `LocalHttpLLMAdapter`) discovered via CDI, ordered by `quorumtrading.llm.router.provider-order`, with circuit breakers and per-provider metrics
