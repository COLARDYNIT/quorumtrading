# claude-openai-bridge

An OpenAI-compatible HTTP endpoint in front of the locally-installed `claude` CLI, so the
dockerized QuorumTrading backend can use **your Claude subscription** as an LLM engine.

The backend already knows how to talk to a local OpenAI-compatible server — that is
`LocalHttpLLMAdapter`, the `local-http` provider, normally pointed at Ollama. But the container has
no `claude` binary and no way to reach your subscription. This bridge fills that gap: it listens on
the host, speaks the two endpoints the adapter actually calls, and answers each one by shelling out
to `claude -p`.

It is **host tooling**. It is not part of the Maven reactor or the Next.js app, nothing in either
build compiles, lints or type-checks it, and it has zero runtime dependencies — just Node and the
`claude` CLI you already have.

## Why not an off-the-shelf wrapper

Community options were surveyed and rejected. The two actively-maintained ones never invoke the CLI
at all: they lift the OAuth token out of your Claude Code credentials, impersonate the Claude Code
client against `api.anthropic.com`, and defeat TLS fingerprinting to look like the real thing. That
is a different and much less defensible thing than running the tool you already have. The one
project that does use the official `claude -p` path is dormant, ships no LICENSE, silently drops
system prompts, fabricates prose when the CLI returns nothing, and reports token counts as
`len / 4`. Writing our own turned out to be small, because `claude -p --output-format stream-json`
already reports the real model, the real token counts, and an explicit error flag — so an honest
bridge is mostly just *not throwing that information away*.

## Requirements

- Node **>= 20** (developed and verified on v24.12.0)
- Claude Code CLI, logged in with your subscription — verified against **2.1.220**
- No npm install step. There are no dependencies.

## Run

```bash
cd tools/claude-openai-bridge
npm start          # listens on http://127.0.0.1:8088/v1
npm test           # 69 unit tests, no CLI required
```

On startup the bridge runs a **preflight** against the real CLI and refuses to serve if anything
looks wrong:

```
[bridge] preflight OK — claude 2.1.220, auth=none (subscription), tools disabled, 'sonnet' resolves to claude-sonnet-5
```

The preflight is free. The CLI emits its `system`/`init` event *before* it contacts the API, so the
bridge reads that one line and kills the process — no tokens, no money. It asserts that tools are
disabled and that `apiKeySource` is `none`, i.e. the CLI is using your subscription login rather
than an `ANTHROPIC_API_KEY` it found in the environment.

## Wiring it into QuorumTrading

The backend property is `quorumtrading.llm.localhttp.base-url`. Point it at this bridge instead of
Ollama. **Note the `/v1` suffix** — the adapter appends `/chat/completions` and `/models` to it.

**Docker stack** (the container reaches the host through `host.docker.internal`, which
`docker-compose.local.yml` already maps):

```bash
QT_LLM_LOCALHTTP_ENABLED=true
QT_LLM_LOCALHTTP_URL=http://host.docker.internal:8088/v1
QT_LLM_LOCALHTTP_MODEL=sonnet
```

**Native dev backend** (`./mvnw quarkus:dev`, same machine as the bridge):

```bash
QT_LLM_LOCALHTTP_ENABLED=true
QT_LLM_LOCALHTTP_URL=http://localhost:8088/v1
QT_LLM_LOCALHTTP_MODEL=sonnet
```

### About `QT_LLM_LOCALHTTP_MODEL`

It is **not** read inside the adapter's request path. It resolves in `SystemModelDefaultsProvider`
as this engine's *system default*, and then arrives at the adapter as an ordinary requested model
through the normal preference chain — so a strategy pin or a per-agent preference still wins over
it. Set it to one of the ids this bridge advertises: `sonnet`, `haiku`, `opus`.

If you leave it empty, the adapter calls `firstAdvertisedModel()` and takes **`data[0]`** from
`/v1/models`. That is why the catalogue is ordered `sonnet, haiku, opus` and not alphabetically:
sonnet is a sane default for agent traffic, and opus is last so it can never become the accidental
default for an unpinned agent and quietly drain your quota. Reorder with `CLAUDE_BRIDGE_MODELS` if
you disagree, but reorder deliberately.

Note that the id you *pin* is a CLI alias, while the model *reported back* is the resolved name the
CLI actually used (`sonnet` → `claude-sonnet-5`). That mismatch is intentional; see below.

## What it exposes

| Endpoint | Behaviour |
|---|---|
| `POST /v1/chat/completions` | Non-streaming. One CLI process per request. |
| `GET /v1/models` | Static list, answers instantly. The adapter probes it with a 3s timeout and treats a non-200 as "provider is down". |
| `GET /healthz` | Liveness. Unauthenticated, never touches the CLI. |
| everything else, incl. `POST /api/show` | `404`. See below. |

`/api/show` is Ollama's *native* endpoint, which the adapter probes for a per-model context window.
The bridge returns 404 rather than an invented number. The CLI does report a real `contextWindow`,
but only in the usage block *after* a call has completed, and the adapter caches the first 404
permanently — so there is no honest way to serve it up front. The adapter already reads a 404 as
"unknown", which is exactly the truth. A plausible-looking constant in a field that reads as a
measurement would be worse than nothing.

## How an OpenAI request maps onto the CLI

| OpenAI field | CLI |
|---|---|
| `messages[role=system]` | `--system-prompt` (joined). Replace semantics — verified: a system prompt of *"you are a lemon"* produced `lemon`, not a coding-assistant answer. This is what stops your trading agents from behaving like Claude Code. |
| `messages[role=user/assistant]` | the stdin prompt. A single user turn is passed **verbatim**, with no added framing; genuine multi-turn conversations get `User:` / `Assistant:` labels. |
| `model` | `--model`. Aliases (`sonnet`) and namespaced ids (`anthropic/claude-sonnet-5` → `claude-sonnet-5`) both work. |
| `reasoning_effort` | `--effort`, for the levels the CLI documents (`low`/`medium`/`high`/`xhigh`/`max`; `minimal`→`low`). Anything else is dropped rather than passed through, because an invalid value makes the CLI exit non-zero. |
| `temperature`, `max_tokens` | **ignored** — the CLI exposes no equivalent. They are accepted and discarded rather than faked. |
| `stream: true` | rejected with a 400. This bridge is non-streaming; the adapter never asks for streaming. |

Fixed flags on every invocation: `-p --output-format stream-json --verbose --tools "" --max-turns 1
--strict-mcp-config --no-session-persistence`, run with a cwd outside this repo.

`--tools ""` means the model gets no Bash, no Edit, no Read. This is a completion endpoint, not an
agent with shell access. `--strict-mcp-config` keeps your MCP servers out of it, and running outside
the repo stops the CLI auto-discovering this project's `CLAUDE.md` into every agent prompt.

**`--bare` is never passed, and there is a test asserting it.** The CLI documents it as a mode where
"Anthropic auth is strictly `ANTHROPIC_API_KEY` or `apiKeyHelper` — OAuth and keychain are never
read", which would bypass the subscription this bridge exists to use. It is also slated to become
the `-p` default in a future release; the preflight's `apiKeySource` check is what turns that from
a silent behaviour change into a loud startup failure.

### Verified CLI JSON mapping

Observed on CLI 2.1.220 on this machine (real output, not assumed):

| Bridge output | Source in the CLI's JSON |
|---|---|
| `choices[0].message.content` | `result` on the final `type:"result"` event |
| `model` | `message.model` on the last `type:"assistant"` event |
| `usage.prompt_tokens` | `usage.input_tokens` **+** `cache_creation_input_tokens` **+** `cache_read_input_tokens` |
| `usage.completion_tokens` | `usage.output_tokens` |
| `finish_reason` | `stop_reason` (`max_tokens` → `length`) |
| error | non-zero exit, or `is_error: true`, or a blank `result` |

Two of these are less obvious than they look.

**The model comes from the `assistant` event, not from `modelUsage`.** A single `--model sonnet`
request was observed producing *two* `modelUsage` keys — `claude-sonnet-5` alongside an ancillary
`claude-haiku-4-5-20251001` call — so `modelUsage` cannot identify the answering model on its own.
This is also why the bridge uses `stream-json` rather than the plain `json` output format the CLI
offers: `stream-json` carries the identical final `result` event *plus* per-message `assistant`
events that name the model that produced the text. The requested model is **never** a fallback. The
adapter records this field as the model that answered, so echoing the request back would launder a
guess into a stored fact.

**Prompt tokens are summed across the cache components.** One observed call reported
`input_tokens: 9` with `cache_creation_input_tokens: 7075` and `cache_read_input_tokens: 17536`.
All three are genuinely counted prompt tokens — they differ only in billing — and reporting the 9
alone would understate the call by three orders of magnitude *and* trip the adapter's own
plausibility guard, which rejects any count below one token per 20 prompt characters. OpenAI's
`prompt_tokens` likewise includes cached tokens.

When the CLI reports nothing usable, `usage` is **omitted entirely**. This bridge never estimates
token counts.

### Errors are errors

A non-zero exit, `is_error: true`, or an empty response all become an OpenAI-shaped error body with
a 5xx status. An empty answer is **never** turned into a message. Synthesising prose to fill a blank
result is the exact failure mode of the wrapper we rejected, and ADR-0004 (*watchdogs pause on
evidence, never fabricate completion*) forbids it here too. `test/protocol.test.js` has a test named
`ANTI-FABRICATION` that fails if anyone reintroduces it.

## Configuration

All optional; defaults suit the local stack.

| Variable | Default | Meaning |
|---|---|---|
| `CLAUDE_BRIDGE_HOST` | `127.0.0.1` | Bind address. See the security note. |
| `CLAUDE_BRIDGE_PORT` | `8088` | Listen port. |
| `CLAUDE_BRIDGE_API_KEY` | *(unset)* | Shared secret. When set, every request must send `Authorization: Bearer <it>`. |
| `CLAUDE_BRIDGE_MODELS` | `sonnet,haiku,opus` | Catalogue, in order. **First entry is the default for unpinned agents.** |
| `CLAUDE_BRIDGE_MAX_CONCURRENCY` | `2` | Concurrent CLI processes. |
| `CLAUDE_BRIDGE_MAX_QUEUE` | `32` | Waiting room; beyond it requests get 503. |
| `CLAUDE_BRIDGE_TIMEOUT_MS` | `360000` | Ceiling on one call. Keep it above the backend's own timeout. |
| `CLAUDE_BRIDGE_SYSTEM_PROMPT` | neutral one-liner | Used only when the caller sends no system message. |
| `CLAUDE_BRIDGE_CWD` | OS temp dir | Working directory for the CLI. Keep it outside any repo. |
| `CLAUDE_BRIDGE_CLAUDE_BIN` | `claude` | Path to the CLI. |
| `CLAUDE_BRIDGE_REQUIRE_SUBSCRIPTION` | `true` | Refuse to start unless `apiKeySource` is `none`. |
| `CLAUDE_BRIDGE_SKIP_PREFLIGHT` | `false` | Escape hatch. Unsupported. |
| `CLAUDE_BRIDGE_VERBOSE` | `false` | Per-request logging, including rate-limit events. |

## Security

**The bridge has no authentication by default and spends real money.** Anyone who can reach the
port can run prompts on your subscription.

It binds to `127.0.0.1` for that reason. That is enough for a native-dev backend on the same
machine, and it is *also* enough for Docker on macOS and Windows: `host.docker.internal` resolves to
a host-side address the loopback listener accepts.

If you do need to widen it — a Linux Docker setup, or another machine — then set **both**:

```bash
CLAUDE_BRIDGE_HOST=0.0.0.0
CLAUDE_BRIDGE_API_KEY=$(openssl rand -hex 32)
```

and give the backend the same secret via `QT_LLM_LOCALHTTP_API_KEY`, which is exactly the value the
adapter sends as `Authorization: Bearer …`. Binding to `0.0.0.0` without a key logs a warning at
startup, on purpose. Note that a wrong or missing key makes `/v1/models` return 401, which the
adapter reports as "provider is down" rather than as an auth error — so check the bridge log if the
engine disappears.

## Rate limits and concurrency

**This shares your subscription with the Claude Code you are typing into.** A backtest is not a
handful of calls: every trading day fires several agent consensus calls, so a month-long backtest
can issue hundreds. If you exhaust the limit, your interactive session degrades too.

Concurrency defaults to **2** for that reason. Raising it does not buy throughput against a
per-account limit — it just reaches the limit sooner and in a burst. Requests beyond the cap queue;
beyond the queue they get a 503, which is a deliberate load-shed rather than an unbounded backlog
that would blow past every caller's timeout anyway. Run with `CLAUDE_BRIDGE_VERBOSE=true` to see
rate-limit events the CLI reports.

Also worth knowing: the bridge always sends a `--system-prompt`, even when the caller supplies none.
Without it the CLI loads its full coding-assistant persona plus auto-discovered `CLAUDE.md`, which
was measured at roughly **24k extra prompt tokens per call** on this machine. Replacing it is both
the correct behaviour for a trading agent and dramatically cheaper.

## LIMITATIONS

Read this part.

- **This is for your own local instance only.** It must **not** be shipped, deployed, or offered as
  an authentication path for the hosted edition. It works by spending the credentials of whoever is
  logged into the CLI on the host — there is one identity, it is yours, and nothing here
  authenticates or isolates a caller beyond an optional shared secret. Multi-tenant use would mean
  every user's traffic billed to your personal subscription.
- Check that your usage is consistent with the Claude Consumer Terms and Usage Policy for your
  subscription. Automated backtests are a materially different traffic pattern from interactive
  coding.
- `temperature` and `max_tokens` are silently ignored — the CLI has no equivalent. If a strategy
  depends on a specific temperature, this engine cannot honour it.
- No streaming, no tool calls, no function calling, no vision, no embeddings. Text in, text out.
- Per-model context windows are reported as unknown (see `/api/show` above).
- One OS process per request. It is heavier and slower to start than an HTTP API call, which is
  another reason the concurrency cap is low.
- Cost is reported to the backend as **$0**, because `LocalHttpLLMAdapter` hard-codes free local
  inference. The CLI does report a real `total_cost_usd` per call, and it is *not* zero — your
  simulation cost figures will read as free when they are not. Use `CLAUDE_BRIDGE_VERBOSE=true` and
  the CLI's own `/usage` if you need the real number.

## CLI version assumptions

Verified against **2.1.220**. Things that could break on an upgrade:

- **`--max-turns` is not in `--help`** on 2.1.220, but is accepted. It may be renamed or removed. If
  the CLI rejects it, the bridge detects `unknown option`, drops the flag, and retries once — which
  costs nothing, because argv is validated before any API call. It logs a warning when this happens.
  The same fallback covers `--effort`.
- A flag the bridge genuinely needs (`--system-prompt`, `--output-format`, …) being rejected is a
  hard failure with an explicit "not compatible with this bridge" message, rather than a silent
  degradation.
- If `-p` ever starts defaulting to `--bare` behaviour, `apiKeySource` stops being `none` and the
  preflight fails at startup with an explanation. That is the intended outcome.
- The response shape is read defensively: unparseable stdout lines are ignored rather than treated
  as content, and a missing `result` event is an error.
