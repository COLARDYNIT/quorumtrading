# QuorumTrading

**Multi-LLM trading consensus, self-hosted on your own machine.**

QuorumTrading runs a council of AI agents — Technical, Fundamental and Sentiment — that analyze a
market situation independently and then vote on a trading decision. You write a strategy in plain
markdown, backtest it against historical data (every agent's full reasoning is preserved, so you
can scrub through time and see *why* each call was made), and only then decide whether to let it
trade paper or real money through your own broker account.

<p align="center">
  <img src="docs/promo.gif" alt="Demo: two commands to boot the stack, then a Claude conversation over MCP — three strategy philosophies race through the 2021 GameStop squeeze, the winner beats buy & hold by 90 points, and the winning run opens in the browser with every agent vote recorded." width="800">
</p>

The whole platform is **agent-native**: it ships an [MCP](https://modelcontextprotocol.io) server
that exposes everything — strategies, backtests, results — so you can drive it all from
Claude Code or any MCP client, without touching the web UI. Point it at a local LLM you already
run — Ollama, LM Studio, or the `claude` CLI — and the AI consensus itself costs you no API key;
the local edition even auto-detects one at boot.

> ⚠️ QuorumTrading is **not financial advice** and is provided **as is**. Trading involves risk
> of loss. See [`NOTICE`](NOTICE).

## What you get

- **Consensus, not a single model** — agents vote (`MAJORITY`) or an orchestrator model weighs
  their arguments (`ORCHESTRATOR_OVERRIDE`). Every vote, every argument, every dissent is stored.
- **Markdown strategies** — trading rules are a markdown file with YAML frontmatter: tickers,
  schedule, consensus method, and free-form instructions the agents actually follow.
- **Backtesting with time-travel** — replay months of market history in minutes, then step
  through any decision point and read the full agent transcript.
- **Recurring investment plans** — DCA-style monthly plans where a picker chooses which of
  your strategies receives the contribution (off by default; promote it deliberately). See
  [LOCAL_SETUP.md § Recurring investment plans](LOCAL_SETUP.md#7-recurring-investment-plans-dca).
- **Benchmarks** — put a finished run next to a buy-and-hold curve for the same window and the same
  starting capital, and see whether the council actually earned its keep. See
  [LOCAL_SETUP.md § Compare against buy-and-hold](LOCAL_SETUP.md#8-compare-against-buy-and-hold).
- **Crypto works out of the box, keyless** — **crypto** (e.g. BTC, ETH) backfills and backtests with
  **zero API keys**: built-in feeds query Kraken and Coinbase public candles and keep whichever
  returns the more complete history. So you
  can experiment end-to-end on crypto the moment the stack is up. **Stocks are different** — there's
  no keyless stock feed, so stock backfill/backtesting needs your own market-data key. That does
  *not* have to be a brokerage account: a **Tiingo** token (plain email signup) is enough to backfill
  and backtest stocks, and a free **Alpaca** paper key covers data *and* later paper/live trading.
- **Your broker, your keys** — 🇺🇸 US: **Alpaca** (API keys — trading *and* market data; a free paper
  key covers stock market data). 🇪🇺 EU: **Saxo** (browser OAuth) — ⚠️ **experimental and largely
  untested; expect rough edges**. Backtests never place real orders (they run on a built-in mock
  broker); prices come from a market-data provider, not the mock broker — keyless for crypto, your
  own key for stocks. See [LOCAL_SETUP.md § Get price data](LOCAL_SETUP.md#3-get-price-data).
- **Your LLM engine, your choice** — a free local engine (Ollama / LM Studio / any OpenAI-compatible
  server, or your own `claude` CLI) or OpenRouter with your own key. Auto-detected at boot,
  switchable at runtime, no restart.
- **Single-user by design** — the self-hosted edition has no sign-up, no OAuth dance, no
  tenant logic. Your data stays in your own PostgreSQL.
- **Feature flags you control** — some product areas ship switched off (challenges and market
  research are both off on a self-host). Flags live in your own database, and on a self-hosted
  instance you are the administrator, so you can switch any of them on from
  **Admin → Feature Flags** without touching configuration. See
  [Feature flags](#feature-flags) below.

## Quick start (self-host)

All you need is Docker:

```bash
git clone https://github.com/COLARDYNIT/quorumtrading.git
cd quorumtrading
docker compose -f docker-compose.local.yml up --build
```

That one command builds and boots everything:

- **Web UI** → <http://localhost:3000> — the standalone edition greets you with a local home page
  (status + next steps), not a marketing site. There is no sign-up, but the app pages still need a
  browser session: open <http://localhost:8085/api/v1/auth/dev-login> once and you are in as the
  single local user. (The session lasts two hours; open that link again when it lapses.)
- **Backend + API + MCP server** → <http://localhost:8085>. On first boot the backend prints a
  banner with your personal MCP token and the one-liner to wire up Claude Code:

```
claude mcp add --transport http quorumtrading http://localhost:8085/mcp \
  --header "Authorization: Bearer qt_..."
```

Missed the banner, or started the stack detached? The token is retrievable any time:

```bash
docker compose -f docker-compose.local.yml logs backend | grep -A10 "MCP ready"
# or read it straight from the container:
docker exec qt-local-backend cat /work/.qt-local/mcp-token
```

From there, ask your agent to backfill price data, draft a strategy and backtest it — or do the
same in the browser. Try prompts like *"backfill 18 months of BTC and show me the indicators"* or
*"create a weekly BTC momentum strategy and backtest it over the last 6 months with $10k"* — more
in [LOCAL_SETUP.md § What to ask your agent](LOCAL_SETUP.md#what-to-ask-your-agent), plus the MCP
server's built-in guided prompts (`create_backtest`, `analyze_simulation`, …).

> **Creating a backtest starts it.** There is no draft state to inspect and then approve — the run
> begins as soon as its price data is ready, and each evaluation spends model calls (or, on a free
> engine, minutes). Ask for a cost estimate first; your agent has a tool for it that creates
> nothing. See [LOCAL_SETUP.md § What things cost](LOCAL_SETUP.md#9-what-things-cost).

> **Market research is not part of the self-hosted edition.** Its agents reason by calling platform
> tools mid-analysis, which needs a hosted conversational model; without one they answer from the
> prompt alone and produce confident-looking verdicts they never actually reasoned to. Rather than
> ship that, the research tools refuse here and the feature lives on
> [quorumtrading.com](https://quorumtrading.com). Writing a strategy yourself — or asking your agent
> to draft one — reaches the same place, and the AI trading consensus is unaffected.

> **Which model should drive the MCP server?** This is the model running *your agent*, separate from
> the [LLM engine](#bring-your-own-llm) that powers the trading consensus. All four Claude tiers can
> complete a full backfill → strategy → backtest → analysis cycle unaided. What differs is how much
> they verify:
>
> | Model | Best for |
> |---|---|
> | **Sonnet** | The everyday default. Completes the workflow reliably, cross-checks its own results, and rarely misreads a tool response. |
> | **Opus** | Multi-step investigation — auditing a strategy's decisions, reconciling figures across tools, diagnosing why a backtest behaved oddly. Slowest and most thorough. |
> | **Fable** | Wide exploration when you don't yet know what you're looking for — sweeping many tools in one session and following whatever looks wrong. Uses the most tool calls to get there. |
> | **Haiku** | Single, well-specified operations: one backfill, one indicator lookup, checking a simulation's status. It finishes multi-step work too, but with far less cross-checking, so treat its analytical conclusions as a first draft. |
>
> These are drawn from running the same crypto backtesting flows through each tier and comparing what
> came back. The pattern worth knowing: when an MCP tool did not behave as Haiku expected, it tended
> to abandon the tool and call the REST API directly with a guessed verb and path — then report the
> resulting `405` as a product defect. Sonnet, Opus and Fable stayed on the tools and read the
> backend source when they wanted to confirm something.
>
> Whatever you use, a backtest is a background job: the agent must poll `get_simulation` until the
> status is terminal. Models that skip the polling loop will report on an unfinished run.

> **Before the agents can think:** AI consensus needs an LLM engine, and the stack auto-selects one
> at boot only if it can find one. Already running Ollama or LM Studio? Nothing to do. Otherwise the
> boot log says *"No LLM engine selected yet"* and tells you your options — the quickest for a Claude
> Code user is the host bridge in
> [`tools/claude-openai-bridge`](tools/claude-openai-bridge/README.md), which exposes your existing
> `claude` CLI to the container ([details](#bring-your-own-llm)).

> **Before you can backtest:** the mock broker executes trades but does **not** supply prices.
> **Crypto** (e.g. BTC, ETH) works out of the box — the built-in keyless feed backfills and backtests
> with no keys. **Stocks** need a market-data key first — a free Tiingo token if you only want to
> backtest, or a free Alpaca paper key if you may want to paper-trade later. See
> [LOCAL_SETUP.md § Get price data](LOCAL_SETUP.md#3-get-price-data).

**Agent-only?** Skip the web UI and run the API + MCP server alone:

```bash
docker compose -f docker-compose.local.yml up --build backend
```

Full guide, including choosing your LLM engine and linking a broker:
**[LOCAL_SETUP.md](LOCAL_SETUP.md)**.

## Bring your own LLM

The AI consensus needs one LLM engine. The self-host edition **auto-selects the first available one
at boot** (free options first), so if you already run a local model there's nothing to configure.
Three ways to power it — switch anytime at `http://localhost:8085/api/v1/llm/setup` or via the
`choose_llm_provider` MCP tool:

**1. A local model server — free, keyless, works in Docker (recommended).**
Run [Ollama](https://ollama.com) (`ollama serve` then `ollama pull llama3.2`), LM Studio, llama.cpp,
vLLM, or any OpenAI-compatible server on your host. The stack reaches it automatically over
`host.docker.internal` (default `:11434`), detects it at boot, and uses its first model. Point
elsewhere with `QT_LLM_LOCALHTTP_URL`, or pin a model with `QT_LLM_LOCALHTTP_MODEL`.

**2. Your coding CLI (`claude`, `codex`, `gemini`, `qwen`) — no API key; spends your CLI subscription.**
These run as a subprocess on the machine the backend runs on:
- **Native backend** — run it on your host (`cd quorumtrading-backend && ./mvnw quarkus:dev
  -Dquarkus.profile=local`); it auto-detects `claude` on your `PATH`.
- **Under Docker** — the container can't invoke a host CLI, so run a small bridge on your host that
  exposes the CLI as an OpenAI-compatible endpoint the container reaches like any local server. For
  `claude`, use [`tools/claude-openai-bridge`](tools/claude-openai-bridge/README.md) (Node, no
  dependencies) — it echoes the model that actually answered rather than the alias you asked for,
  reports real token counts, and never invents content when the CLI returns nothing:
  ```bash
  cd tools/claude-openai-bridge && npm start   # serves 127.0.0.1:8088
  # then restart the backend pointed at it:
  QT_LLM_LOCALHTTP_URL=http://host.docker.internal:8088/v1 QT_LLM_LOCALHTTP_MODEL=sonnet \
    docker compose -f docker-compose.local.yml up -d backend
  ```
  For `codex`/`gemini`/`qwen`, [`scripts/local-llm-cli-shim.py`](scripts/local-llm-cli-shim.py)
  (`python3 scripts/local-llm-cli-shim.py`, serves `:11435`) covers several CLIs behind one endpoint.
  Either way, expect roughly **20–40s per agent call** — each one spawns a CLI process. A weekly
  6-month crypto backtest (~90 calls) lands around 20–40 minutes. Keep backtests on a weekly
  `backtest_schedule` while you're exploring; switch to a local model server for faster runs.

  > **Pick the model deliberately.** The bridge advertises `sonnet, haiku, opus` and uses the first
  > unless you pin one with `QT_LLM_LOCALHTTP_MODEL`; the shim defaults to `haiku`
  > (`SHIM_CLAUDE_MODEL=sonnet python3 scripts/local-llm-cli-shim.py`). Haiku is fast and fine for
  > touring the platform; larger models weigh conflicting signals more carefully.
  >
  > **Cost reads as $0 and isn't.** The local-server adapter hard-codes free local inference, so
  > these calls are recorded at zero while genuinely drawing on your subscription — and on the same
  > account as the Claude Code you are typing into.

**3. OpenRouter — hosted models, your API key.** Paste a key on the setup page (stored encrypted in
your own database); usage is billed to your OpenRouter account.

> In the Docker stack the CLI (`LOCAL`) engine shows as **unavailable** — correct, the container has
> no CLI. That is a limit on *subprocesses*, not on the CLI itself: a host bridge (option 2) puts it
> back within reach over HTTP, where it registers as a local server. Use a model server (option 1) if
> you want an engine that is genuinely free as well as keyless.

## Feature flags

Parts of the product are gated behind runtime feature flags, evaluated by the
[Unleash](https://www.getunleash.io/) SDK. There is no Unleash server to run: the SDK is wired to
read its toggle definitions from this application's own `feature_flags` table, so a self-hosted
install has working flags with nothing extra to deploy.

| Flag | Hosted | Self-hosted | Gates |
|---|---|---|---|
| `challenges` | off | off | Public trading challenges and leaderboards |
| `market-research` | on | off | LLM-driven market research, and its hourly scheduler |

A switched-off feature is closed on every surface, and says so:

- the sidebar entry is hidden (typing the URL still reaches the page — the API is what refuses)
- its REST endpoints answer `403` with a body naming the flag to switch on
- its MCP tools disappear from `tools/list`, and refuse with the same flag name if called anyway
- any scheduler behind it stays idle, so nothing keeps running — or spending — unnoticed

Flags are stored in the database, not in configuration. The `quorumtrading.features.*.default`
properties are **seeds**, applied only the first time an instance boots — editing one will not move
a flag that already exists, so a redeploy cannot silently undo an administrator's change.

**Changing a flag** needs an administrator (`users.role = 'ADMIN'`). On a self-hosted standalone
instance your local user is provisioned as an administrator at boot, so you can manage your own box without editing the
database. Use **Admin → Feature Flags** in the web UI, or:

```bash
curl -X PUT http://localhost:8085/api/v1/admin/feature-flags/challenges \
  -H 'Content-Type: application/json' -d '{"enabled":true}'
```

A flag can also be switched on for **named people only** — send
`{"enabled":true,"enabledForEmails":["someone@example.com"]}`. That is Unleash's built-in
`userWithId` strategy, with the caller's email as the identity, and it applies to the web UI, the
REST API and MCP alike.

## How it's built

| | |
|---|---|
| Backend | Quarkus 3.x · Java 25 · PostgreSQL 16 + Flyway · Redis 7 |
| Frontend | Next.js 16 · React 19 · TypeScript · styled-components · TanStack Query |
| Architecture | Hexagonal (ports & adapters), DDD — domain model knows nothing about HTTP, SQL or LLM providers |
| Interfaces | REST (OpenAPI), WebSocket, MCP (streamable HTTP) |
| LLM | OpenRouter (your key), a local OpenAI-compatible server (Ollama / LM Studio), or local CLI engines (`claude`, …) behind one routing port |

```
quorumtrading-backend/
├── quorumtrading-model/           # Domain: entities, value objects, domain services
├── quorumtrading-application/     # Use cases, ports, orchestration, AI agents
├── quorumtrading-adapter-*/       # REST, WebSocket, MCP, persistence, LLM, brokers, market data
└── quorumtrading-bootstrap/       # Quarkus entry point, schedulers, CDI wiring
quorumtrading-frontend/            # Next.js terminal-themed web UI
```

Key reading: [Run modes](docs/RUN_MODES.md) (SaaS / dev / standalone / test — profiles, ports,
databases, flags) · [Hexagonal architecture](CLAUDE.md#architecture) (module layout, layer rules,
testing constraints) · [Domain model](docs/BOUNDED_CONTEXT_CANVAS.md) ·
[Capabilities](docs/CAPABILITIES.md) (honest feature inventory, including what's *not* built).

## Developing

Prerequisites: Java 25, Node 20+, Docker.

```bash
# 1. Infrastructure
docker-compose up -d postgres redis

# 2. Test database (one-time)
psql postgresql://quorumtrading:dev-password@localhost:55432/postgres \
  -c "CREATE DATABASE quorumtrading_test OWNER quorumtrading;"

# 3. Backend with live reload → http://localhost:8085 (Swagger: /q/swagger-ui)
cd quorumtrading-backend && ./mvnw quarkus:dev

# 4. Frontend → http://localhost:3000
cd quorumtrading-frontend && npm install && npm run dev
```

### Tests

```bash
# Backend — full suite (needs the postgres + redis containers running)
cd quorumtrading-backend && ./mvnw test

# Frontend — unit, then production build (the build type-checks test files too)
cd quorumtrading-frontend && npm test && npm run build

# E2E (Playwright)
cd quorumtrading-frontend && npm run test:e2e

# LLM integration tests — real OpenRouter calls, from the repo root
OPENROUTER_API_KEY=sk-or-... ./scripts/run-integration-tests.sh
```

Tests run against a real PostgreSQL (`quorumtrading_test`) — never an in-memory database — so
Flyway migrations are exercised on every run.

## Contributing

Contributions are welcome — read **[CONTRIBUTING.md](CONTRIBUTING.md)** first. The short version:

- Every contribution needs the [Contributor License Agreement](CLA.md) (recorded automatically
  on your first pull request).
- The architecture rules are enforced, not aspirational: domain code carries no JPA/JSON
  annotations, adapters never depend on other adapters, application services are POJOs wired in
  the bootstrap module — ArchUnit tests will fail your build otherwise.
- A change is done when the full backend suite and the frontend build pass.
- Good first contributions: bug reports with reproductions, documentation fixes, new market-data
  or broker adapters behind the existing ports, strategy examples.

## Community

- **Issues & PRs** — right here on GitHub.
- **Reddit** — [r/quorumtrading](https://reddit.com/r/quorumtrading): setups, strategies,
  backtest results from other self-hosters.
- **Hosted edition** — [quorumtrading.com](https://quorumtrading.com), if you'd rather not run
  your own.

## License

QuorumTrading is **dual-licensed**:

- **Open source:** GNU Affero General Public License v3.0 or later — see [`LICENSE`](LICENSE).
  You may self-host it for free; if you run a modified version as a network service you must share
  your changes under the AGPL.
- **Commercial:** for closed-source embedding or hosting without AGPL obligations — see
  [`LICENSING.md`](LICENSING.md).

The **"QuorumTrading" name and logo are trademarks** and are **not** covered by the code license —
see [`TRADEMARK.md`](TRADEMARK.md). Forks and third-party hosted services must use a clearly
distinct name.
