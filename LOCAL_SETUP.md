# Run QuorumTrading locally (self-host edition)

Run the whole platform on your own machine, drive it from Claude Code (or any MCP client), and keep
all data in your own PostgreSQL. Designed for one user — you. This is the **standalone** edition
(`QT_EDITION=standalone`, what `docker-compose.local.yml` boots) — "self-host", "local edition" and
"standalone" all name the same thing.

## Prerequisites

- **Docker** (Docker Desktop or Docker Engine + Compose v2). That's it — no Java or Node needed.
- Optionally a **local LLM**, so AI features run free and keyless (see section 4):
  - a **local model server** — Ollama, LM Studio, llama.cpp, vLLM (OpenAI-compatible). The
    recommended local option under Docker: the stack reaches it on your host automatically. **Or**
  - the **`claude` CLI** (Claude Code) — invoked directly only when the backend runs *natively* (the
    Docker container can't invoke a host CLI), but reachable under Docker by running
    [`tools/claude-openai-bridge`](tools/claude-openai-bridge/README.md) on your host. Uses your
    Claude subscription rather than being free.
  With neither, use OpenRouter with your own API key.

## 1. Start it

```bash
docker compose -f docker-compose.local.yml up --build
```

This builds and boots the whole stack — PostgreSQL, Redis, the backend (REST + MCP server) and the
**web UI**:

- **Web UI** → <http://localhost:3000>
- **Backend + API + MCP** → <http://localhost:8085> (Swagger UI: `/q/swagger-ui`)

(Add `-d` to run detached — later sections use `up -d` for exactly that reason. With `-d`, the
`--wait` flag blocks until the healthchecks pass.)

**Agent-only / headless.** If you drive everything from an MCP agent and don't want the web UI,
name the `backend` service — its `depends_on` still pulls in PostgreSQL and Redis, and the frontend
container is left out:

```bash
docker compose -f docker-compose.local.yml up --build backend
```

The first build downloads dependencies and takes a few minutes (the web UI adds a Next.js
production build on top). The stack is ready when `curl http://localhost:8085/q/health` returns
`"status": "UP"`. On first boot you'll also see
`WARN ... OpenRouter API key is not configured` (until you pick an LLM engine in section 4) and
`WARN ... Reddit API returned status 403` (anonymous Reddit access is blocked) — both are harmless.
The initial data-collection pass also runs before any market-data keys are set — with no keys the
Alpaca and Finnhub adapters skip their fetches with a one-line `WARN ... not configured` each. A
clean zero-key boot logs no ERRORs; if you see one, something is actually wrong.
A handful of framework-level WARNs (`Unrecognized configuration key "quarkus.oidc.*"`, a Redis
MeterRegistry gauge) are likewise benign — OIDC is off in the standalone edition.
When it's ready, the backend prints a banner in the logs:

```
======================================================================
  QuorumTrading (local edition) — MCP ready
======================================================================
  MCP endpoint : http://localhost:8085/mcp
  Token        : qt_xxxxxxxx...
  Wire Claude Code in one command:
    claude mcp add --transport http quorumtrading http://localhost:8085/mcp \
      --header "Authorization: Bearer qt_xxxxxxxx..."
======================================================================
```

If you scrolled past it: `docker compose -f docker-compose.local.yml logs backend | grep -A10 "MCP ready"` —
or read the token straight from the container: `docker exec qt-local-backend cat /work/.qt-local/mcp-token`.

## 2. Connect Claude Code

Run the `claude mcp add ...` line from the banner. Or copy [`.mcp.json.example`](.mcp.json.example)
to `.mcp.json` and paste the token in. Then in Claude Code, the `quorumtrading` MCP tools are
available — ask it to list strategies, run a backtest, etc.

> **Token gotchas:**
> - Wiping the data volume (`docker compose -f docker-compose.local.yml down -v`) generates a
>   **new** token on next boot — your MCP client keeps the old one and every authenticated tool
>   then fails with *"Invalid API token"*. Re-run the `claude mcp add` line from the fresh banner.
> - **Re-adding the server is not enough — restart your MCP client too.** Claude Code loads MCP
>   servers when the session starts, so `claude mcp add` updates `~/.claude.json` but the *running*
>   session keeps talking to the old endpoint with the old token and keeps failing. Exit and
>   relaunch `claude` after re-adding. To confirm the fault is the client rather than the backend,
>   call the endpoint directly with the banner token — if this returns JSON, the server is fine and
>   only your session is stale:
>   ```bash
>   curl -s -X POST http://localhost:8085/mcp \
>     -H "Authorization: Bearer qt_..." -H 'Content-Type: application/json' \
>     -H 'Accept: application/json, text/event-stream' \
>     -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_llm_status","arguments":{}}}'
>   ```
> - Rebuilding or restarting the containers (**without** `-v`) keeps the token — it lives in the
>   `qt_local_secrets` volume, so no re-add is needed.
> - If you also use the **hosted** edition, name the servers distinctly (e.g. `quorumtrading-local`
>   vs `quorumtrading`) and double-check which one your agent actually loaded — similarly named
>   servers make it easy for an agent to silently talk to the wrong backend.

### What to ask your agent

The MCP server ships **guided prompts** (`create_backtest`, `analyze_simulation`,
`compare_strategies`, `setup_live_trading`, `diagnose_simulation`, `explain_indicators`) — pick one
from your MCP client's prompt list for a step-by-step workflow. (`full_research_cycle` and
`analyze_market_research` are listed too, but they drive market research, which this edition does
not offer — see below.)
In Claude Code they show up as slash commands named after the server you registered, e.g.
`/quorumtrading-local:create_backtest` — type `/` and search for `quorumtrading` to see them all.
Or just ask in plain language. The prompts below are grouped by workflow stage; everything in the
**Data**, **Strategy**, **Backtest** and **Analysis** groups works end-to-end on a fresh keyless
install, using crypto.

**Ten-minute first run (crypto, zero keys):**

> *"Backfill two years of BTC daily bars, then write me a weekly Bitcoin strategy that accumulates
> weakness in tranches — buy when RSI drops below 45, add below 32, trim above 62 — keeping 25% cash
> in reserve. Backtest it over 2026 year-to-date with $10k and walk me through what it decided and
> why."*

That single sentence exercises the whole platform. The rest of this section breaks it into pieces
you can ask for individually.

> **Why a mean-reversion strategy for the first run, and not a trend-following one?** Because in
> this window a trend strategy would correctly do *nothing*, which makes a poor first demo. Bitcoin
> closed **below its 200-day SMA on every single trading day of 2026** — and essentially every
> trend-following strategy treats that line as a regime filter. The agents will read the rule you
> wrote, conclude the regime disqualifies entry, and hold cash for the whole window: a completed
> backtest, zero trades, `0.00%` return.
>
> That is a **real result, not a broken install**: over the same window buying and holding BTC
> returned **-27.7%** ($88,738 → $64,144), so a flat 0.00% in cash comfortably beat the market, and
> the reasoning is preserved on every HOLD so you can read exactly why it stood aside. But if you
> want to watch the machinery actually trade on your first run, match the strategy style to the
> regime you are testing, or pick a window where the asset genuinely trended.

**Data**

- *"Backfill 18 months of BTC daily bars, then show me the current indicators."* (crypto — no keys)
- *"What price data do I actually have stored for BTC — how many bars, which dates, which provider?"*
- *"Is SPCX a real ticker? find_ticker says private — backfill a month of bars and tell me if that
  holds up."* (the reliable way to check a recent listing on a local engine)

**Market research — hosted edition only**

Market research is not part of the self-hosted edition; its tools refuse here. Its agents reason by
calling platform tools mid-analysis, which needs a hosted conversational model — without one they
answer from the prompt alone and return confident-looking verdicts they never reasoned to, which is
worse than not offering it. It lives on [quorumtrading.com](https://quorumtrading.com).

Ask your agent to draft the strategy instead — it reaches the same place:

- *"Suggest a mean-reversion strategy for ETH and write it as a QuorumTrading strategy, then
  backtest it over the last 3 months."*

**Strategy**

- *"Create a weekly BTC momentum strategy with a 12% drawdown guard and backtest it over the last
  6 months with $10k."*
- *"Write a strategy for a stock that only listed six weeks ago — no 50- or 200-day averages exist
  yet, so use short-window signals and a large cash reserve."*
- *"Take my BTC strategy and make a more conservative variant: half the position limit, and only
  buy above the 200-day average."*
- *"Explain what each frontmatter field in my strategy actually does to how the agents behave."*

**Backtest**

- *"Backtest my BTC strategy over 2026 year-to-date with $10k and tell me when it's done."*
  (backtests run in the background — your agent won't be pinged; ask it to check on them)
- *"How much AI budget will a 6-month daily backtest cost me before I start it?"*
- *"Run the same strategy over a bull stretch and a drawdown stretch, and compare how it behaved."*

**Analysis**

- *"Why did the backtest sell on March 3rd? Show me each agent's reasoning for that decision."*
- *"Which agents actually voted in that backtest, and which ones abstained — and why?"*
- *"Show me every decision where the agents disagreed, and how the tie was resolved."*
- *"Plot my portfolio value over the backtest and tell me the worst drawdown."*
- *"Compare my strategies by return and win rate, and tell me which one to paper-trade."*
- *"Benchmark my BTC strategy against just buying and holding BTC over the same window."*

**Going live** (needs a broker account — see [section 5](#5-link-a-broker-paper-first))

- *"Check my broker accounts and confirm the credentials still validate."*
- *"Set up my best strategy for paper trading on Alpaca with $5k, and tell me exactly what will
  happen on the first evaluation."*

**Recurring investing** (see [section 7](#7-recurring-investment-plans-dca) — off by default)

- *"Set up a monthly $200 plan that buys into whichever of my crypto strategies looks best."*
- *"Show me every time my plan fired, what it bought, and what it skipped."*

Three expectations worth setting up front:

- **Creating a backtest starts it.** There is no draft state to inspect and then approve — the run
  begins as soon as its price data is ready, and every evaluation spends model calls (or, on a free
  engine, minutes). Ask for a cost estimate *first*: your agent has `estimate_backtest_cost`, which
  takes the same strategy and dates and creates nothing.

- **`find_ticker` can be stale on recent IPOs.** With a local LLM engine there is no web search,
  so `find_ticker` answers from the model's training data and says so in its reply ("answered
  without web search"). A recently listed company can then be wrongly reported as "not publicly
  traded" — SpaceX (SPCX, listed June 2026) is exactly the kind of listing a 2025-era local model
  misses. When in doubt, verify independently: run `backfill_price_data` with the ticker you
  believe is right, then `get_price_coverage` on it — if bars are stored, it's real. Check coverage
  rather than the backfill result: `recordsInserted: 0` with a non-zero `recordsSkipped` means the
  bars were *already stored*, which reads like a negative but is the strongest possible yes.
  Genuinely private companies
  (OpenAI, Stripe) have no ticker; for those, a *listed proxy* (a fund or holding company that
  owns shares) is the closest tradable stand-in.
- **Niche tickers get technical-only consensus.** The Fundamental and Sentiment agents abstain
  when almost no news exists for the ticker in the evaluated period (fewer than 2–3 stored news
  items). Big names have plenty of news; a thinly-covered fund — or any crypto ticker on a keyless
  install — may trade on the Technical agent alone. A Finnhub key (section 3) improves news
  coverage for stocks. The consensus note tells you exactly who sat out and why, and distinguishes
  a deliberate abstention from a malfunction:

  ```
  Majority consensus: HOLD with 1/1 agent votes for BTC
    [FUNDAMENTAL abstained — only 0 of 2 required news events stored for this period;
     SENTIMENT abstained — only 0 of 3 required news/social events stored for this period]
  ```

  An agent that *had* the data but returned nothing instead reads
  `SENTIMENT — no decision returned (timed out before answering)`, which points at the engine rather
  than the data.

The AI-consensus quality depends on the engine you picked in section 4 — a small local model is
fine for touring the platform, but expect noticeably better agent reasoning from larger models.
Concretely, on the same 17-session stock backtest: a ~3B model (Ollama `llama3.2`) finished in about
7 minutes but produced 3 malformed-JSON responses (logged to `llm_parse_failures`, agent recorded as
SKIP) and blew the consensus pipeline timeout twice; a mid-size hosted-class model produced none.
Parse failures are queryable — `get_consensus_with_agents` with `onlyParseFailures=true` — and the
table records both the requested and the actually-served model, so it is worth a look before you
conclude a strategy is bad rather than the engine.

## 3. Get price data

The mock broker executes trades, but it does **not** supply prices. Where those prices come from
depends on the asset:

### Crypto — nothing to do (keyless, out of the box)

**Crypto (e.g. BTC, ETH) needs no setup.** Built-in keyless feeds query Kraken's and Coinbase's
public candle endpoints and keep whichever returns the more complete history — no account, no API
key, and no single point of failure. Just ask your agent to
`backfill_price_data` for a crypto ticker and run a backtest. (Getting-started depth: daily bars,
roughly two years deep. For heavier crypto history or intraday, bring your own broker/data key like
the stock path below.)

### Stocks — bring a key (a free Alpaca paper key or Tiingo token is enough)

There is **no keyless stock feed**, so stock backfill/backtesting needs a market-data provider. Two
free options, both fine — pick by what you intend to do:

| | **Alpaca** (paper key) | **Tiingo** (API token) |
|---|---|---|
| Signup | brokerage account | email signup, no brokerage |
| Covers | market data **and** US paper/live trading | market data only (daily/weekly, no intraday, no crypto) |
| Pick it if | you plan to paper- or live-trade later | you only want to **backtest** stocks |

Either one alone is enough to backfill and backtest a stock. For **Tiingo**: sign up at
<https://www.tiingo.com/>, copy [`.env.local.example`](.env.local.example) to `.env`, set
`TIINGO_API_TOKEN`, and restart with `docker compose -f docker-compose.local.yml up -d`.

The **free Alpaca paper account** route — no funding, no live trading required:

1. Sign up at <https://alpaca.markets/> and, from the dashboard, generate **API keys** (a paper key
   is fine — it covers market data too, and later powers US paper/live trading).
2. Copy [`.env.local.example`](.env.local.example) to `.env` and set `ALPACA_API_KEY` /
   `ALPACA_API_SECRET`.
3. Restart so the container picks them up: `docker compose -f docker-compose.local.yml up -d`.

Extras (all free tiers):

- **Finnhub** (`FINNHUB_API_KEY`) / **Reddit** (`REDDIT_CLIENT_ID` + `REDDIT_CLIENT_SECRET`) —
  company news + sentiment for the Fundamental/Sentiment agents. Optional; prices don't need them.

Without a stock key set, a **stock** `backfill_price_data` fails with *"No price bars returned …
Stocks need a market-data provider — set ALPACA_API_KEY/ALPACA_API_SECRET or TIINGO_API_TOKEN and
restart"* — but **crypto backfills and backtests regardless**, no keys required.

> **Your shell environment counts as configuration.** `docker compose` reads `ALPACA_API_KEY`,
> `TIINGO_API_TOKEN`, `FINNHUB_API_KEY` and `OPENROUTER_API_KEY` from your **shell** as well as from
> `.env`, and **the shell wins**. If any of these is exported from your `~/.zshenv`, `~/.zshrc` or
> `~/.bashrc`, the stack silently picks it up — so stock backfills can appear to work "with no keys"
> on your machine and then fail on a colleague's. Ask the container what it actually got rather than
> guessing:
> ```bash
> docker exec qt-local-backend env | grep -E 'ALPACA|TIINGO|FINNHUB|OPENROUTER'
> ```
> Anything reading `not-configured` is genuinely unset. To reproduce a true zero-key run, unset the
> variables for the command: `env -u TIINGO_API_TOKEN -u FINNHUB_API_KEY docker compose -f
> docker-compose.local.yml up -d --force-recreate backend`.

## 4. Choose your LLM engine

The platform needs one AI engine for consensus. In the local edition it **auto-selects the first
available engine at boot** (free options first), so if you already run a local LLM there's nothing
to do. Three engines, switchable anytime without a restart:

- **Local server** — an OpenAI-compatible server you run yourself: **Ollama** (`ollama serve`, port
  11434), **LM Studio**, llama.cpp, vLLM, … Free, no key. **Works under Docker** — the stack reaches
  it on your host via `host.docker.internal`, no config needed for Ollama's default port. Pin a
  model with `QT_LLM_LOCALHTTP_MODEL`, otherwise the server's first advertised model is used.

  > **Switching between two local engines?** `QT_LLM_LOCALHTTP_URL` is an explicit override — once
  > it is set (in `.env` or your shell) the stack uses it and will *not* fall back to auto-detecting
  > Ollama on `:11434`. If you set it earlier for the CLI shim on `:11435`, installing Ollama
  > appears to do nothing. Point it back (or unset it) and restart:
  > ```bash
  > QT_LLM_LOCALHTTP_URL=http://host.docker.internal:11434/v1 QT_LLM_LOCALHTTP_MODEL=llama3.2 \
  >   docker compose -f docker-compose.local.yml up -d backend
  > ```
  > `get_llm_status` names the endpoint and model actually in use — check it after switching.
- **Local CLI** — the `claude` CLI on your machine (codex/gemini/qwen/**opencode** also work). No key — but the
  backend can invoke it directly only when it runs **natively**
  (`./mvnw quarkus:dev -Dquarkus.profile=local`); the Docker container has no CLI inside it. A
  subprocess cannot cross the container boundary, but HTTP can, so under Docker you put a small
  bridge on the **host** and point the stack at it like any other local server:
  - for `claude`, [`tools/claude-openai-bridge`](tools/claude-openai-bridge/README.md) — `npm start`
    (Node, no dependencies), serves `:8088`. It reports the model that actually answered and real
    token counts, and refuses to start unless the CLI is on your subscription login:
    ```bash
    QT_LLM_LOCALHTTP_URL=http://host.docker.internal:8088/v1 QT_LLM_LOCALHTTP_MODEL=sonnet \
      docker compose -f docker-compose.local.yml up -d backend
    ```
  - for codex/gemini/qwen, [`scripts/local-llm-cli-shim.py`](scripts/local-llm-cli-shim.py)
    (`:11435`), which covers several CLIs behind one endpoint.
  - for **opencode**, there is **no bridge** — it is native-backend only. If you ever want one,
    build it on `opencode serve` (its own headless server) rather than by wrapping `opencode run`
    per request; see [LLM configuration](docs/configuration/LLM_CONFIGURATION.md).

  > **Running opencode?** Two settings are worth knowing before you start. It runs as a *coding
  > assistant* (`build`) unless you create a trading persona and set
  > `quorumtrading.llm.local.opencode.agent`, and it reads project context from its working
  > directory — which the backend now pins to a scratch dir, saving a measured ~7,570 input tokens
  > per call versus inheriting a repository checkout. Both are documented in
  > [LLM configuration](docs/configuration/LLM_CONFIGURATION.md).

  > **This spends your Claude subscription, and the cost shows as $0.** The local-server adapter
  > hard-codes free local inference, so per-simulation cost figures read as free when they are not —
  > the CLI's own `/usage` is the real number. It also shares the same account as the Claude Code you
  > are typing into, and runs 2 CLI processes at a time, so a 3-agent consensus round serialises.
  > Fine for touring the platform; for long backtests prefer a local model server or OpenRouter.

  > **Pick the model deliberately.** The shim runs `claude --model haiku` by default
  > (`SHIM_CLAUDE_MODEL`). Haiku is quick and fine for touring the platform; larger models weigh
  > conflicting signals more carefully. Switch with
  > `SHIM_CLAUDE_MODEL=sonnet python3 scripts/local-llm-cli-shim.py`.
  >
  > **Before blaming the engine, read what the agent was actually given.** When a backtest refuses
  > to trade on a setup your rule should have caught, pull the agent's `indicator_snapshot` via
  > `get_consensus_with_agents` and check the inputs, not just the verdict — the snapshot records
  > the exact indicator values and any "insufficient data" warnings behind the decision. An agent
  > reasoning correctly from a missing or flat input looks identical to an agent ignoring your
  > strategy, and only the snapshot tells the two apart.
- **OpenRouter** — hosted models via your own API key, billed to your OpenRouter account.

To override the auto-selection, choose explicitly on the setup page at
<http://localhost:8085/api/v1/llm/setup>, or ask your agent to call the `choose_llm_provider` MCP
tool (`LOCAL`, `LOCAL_HTTP`, or `OPENROUTER`). An OpenRouter key pasted on the setup page is stored
encrypted in your own database.

> **Note:** `docker compose` reads variables like `OPENROUTER_API_KEY` from your **shell
> environment** as well as from `.env` — the shell wins. If your profile exports an OpenRouter key,
> the stack will show OpenRouter as configured even with no `.env` entry. Auto-selection still
> prefers the free local engines first.

> **Which one under Docker?** Run **Ollama / LM Studio** on your host and it just works
> (auto-detected via `host.docker.internal`). **Every CLI engine — `claude`, `qwen`, `codex`,
> `gemini`, `opencode` — is host-only and stays unavailable in Docker**: the backend starts them as
> child processes, the container has none of those binaries, and a subprocess cannot cross the
> container boundary. Selecting one there does not fall back to anything, it just fails to find the
> tool. Those engines need a native backend run — or a host bridge in front of the CLI, which then
> registers as a local *server* rather than as the CLI engine (see the Local CLI bullet above).
> Everything AI-related also works with **OpenRouter** + a key.

## 5. Link a broker (paper first)

You do **not** need a broker to write strategies, backfill prices or backtest — backtests always run
on a built-in mock broker. You need one to trade, paper or real.

**Credentials go through the web UI, not through your agent.** Open
<http://localhost:3000/settings/broker-accounts> → *Add broker account*. Your agent can read broker
accounts (`list_broker_accounts`, `get_broker_account`, `validate_broker_credentials`) but never
receives the keys.

> **`list_broker_accounts` also returns MOCK accounts; the web UI does not.** Backtests get a mock
> account created for them automatically, so this list can hold dozens of `MOCK` entries all reading
> `status: ACTIVE` while the *Connected brokers* page correctly says **0**. Neither view is wrong —
> they answer different questions. "Do I have a broker that can trade?" means: is there an account
> whose `brokerType` is **not** MOCK. A mock account is also the credential-free way to try a
> recurring investment plan ([section 7](#7-recurring-investment-plans-dca)) without linking
> anything real.

🇺🇸 **Alpaca.** Create a key at the [Alpaca dashboard](https://app.alpaca.markets/) and pick the
matching account type:

| Type | What it does |
|------|--------------|
| `ALPACA_PAPER` | Simulated orders on real market data. **Start here.** A paper key also covers stock market data (section 3). |
| `ALPACA_LIVE` | **Real money.** The add-broker form shows a warning block and a mandatory *"I accept full responsibility"* checkbox; the submit button stays disabled until you tick it. |

🇪🇺 **Saxo.** See [section 6](#6-optional-experimental-link-your-personal-saxo-account) — experimental,
off unless you opt in.

**Which of my runs is trading real money?** Not the ones whose `status` is `LIVE` — that is a
lifecycle state, and a mock-broker backtest that catches up with the present reports it too. Read
the **`realMoney`** field on a simulation: it is true only for an `ALPACA_LIVE` or `SAXO_LIVE`
account. Ask your agent to *"list my simulations and show which ones have realMoney true"*.

Once an account is linked, tell your agent to use it — for a paper run it passes that account's
`brokerAccountId` when creating the simulation.

## 6. (Optional, experimental) Link your personal Saxo account

> ⚠️ **The Saxo (EU) integration is experimental and largely untested.** The OAuth onboarding,
> token-refresh and trading paths exist in the code but have not been exercised end-to-end against a
> real Saxo account. Treat it as work-in-progress: expect rough edges, and don't rely on it for
> anything that matters. Alpaca (US) is the supported broker. If you try Saxo, please file issues.
>
> Also note a hard Saxo constraint: **Saxo's SIM (paper) environment does not serve equity/ETF
> market data — only Forex** — so the SIM path can place orders but can't price stocks/ETFs.
>
> **Linking Saxo does not unlock European equities.** Before you spend an afternoon registering a
> developer app: exchange-qualified tickers (`ASML:XAMS`, `BMW.DE`, `AZN:XLON`) are refused at
> strategy creation regardless of which broker is attached, so there is no strategy you can write
> that would trade a European venue. That refusal is in the platform, not in Saxo. This section is
> for exercising the integration itself; it is not a route to EU market access. The supported way
> to hold a European company today is its US listing — ask `find_ticker` for it, and note that a
> depositary receipt carries a currency component you did not choose. See
> [docs/CAPABILITIES.md § 4](docs/CAPABILITIES.md).

1. Register a **personal OAuth app** at <https://www.developer.saxo/> (start with the **SIM**
   environment). Set its redirect URI to
   `http://localhost:8085/api/v1/broker-accounts/saxo/onboard/callback`.
2. Copy [`.env.local.example`](.env.local.example) to `.env`, set `SAXO_ENABLED=true` and your
   `SAXO_APP_KEY` / `SAXO_APP_SECRET`, and (recommended) a `BROKER_CREDENTIALS_ENCRYPTION_KEY`
   (`openssl rand -hex 32`).
3. Restart: `docker compose -f docker-compose.local.yml up -d`.
4. Begin linking — open the consent URL this returns, approve at Saxo, and you'll land on the
   "account linked" page:
   ```bash
   # Get a session cookie, then start onboarding:
   curl -s -c cookies.txt http://localhost:8085/api/v1/auth/dev-login >/dev/null
   curl -s -b cookies.txt -X POST http://localhost:8085/api/v1/broker-accounts/saxo/onboard/start \
     -H 'Content-Type: application/json' -d '{"environment":"SIM"}'
   ```
   Open the `authorizationUrl` from the response in a browser.

> Saxo SIM is paper trading — no real money. Live trading is not enabled in this edition yet.

## 7. Recurring investment plans (DCA)

A plan contributes a fixed amount on one day each month. Each tick picks one of *your* strategies and
either tops up an existing live simulation or starts a new one. Ask your agent for the
`investment-plans` guide (`get_guide`) for the full reference — the essentials:

> ⚠️ **Automatic execution is off by default on every install.** A plan can read `status: ACTIVE`
> with a future `nextExecutionAt` and still never fire on its own. The field that settles it is
> **`automaticExecutionEnabled`** on the plan (or `automaticInvestmentsEnabled` from
> `GET /api/v1/instance`). While it is false, the only thing that funds a plan is you asking your
> agent to run one tick now. A plan that silently never invests looks exactly like one that works.

To turn the scheduler on, set `QUORUMTRADING_SCHEDULER_INVESTMENTS_ENABLED=true` for the backend
container and restart it. Do that only once you have watched a manual tick do the right thing.

Two things that surprise people:

- **`AI_PICK` runs no AI.** It is the only accepted picker policy, and the default picker simply
  chooses the most-recently-updated active strategy in your pool — which it says plainly in the
  allocation's `pickerReasoning`. `riskTolerance` and `maxStrategiesActive` are stored but not acted
  on. The ticker allow/block lists *are* enforced.
- **Every strategy you own is eligible.** A strategy you create today immediately joins the pool an
  active plan can pick from. Set `allowedTickers` if you want a plan confined to particular assets.

Every tick writes an allocation row whether it funded, skipped or failed — that row is the audit
trail, not the plan. Deleting a plan keeps those rows but does **not** stop the simulations it
created or release the broker cash they reserved; ask your agent to end those runs explicitly.

Email delivery is off by default too, so notification preferences may list an EMAIL channel that
delivers nothing. The in-app inbox always works.

## 8. Compare against buy-and-hold

The question a backtest is really asked — *did the council beat just buying the thing?* — is
answered by benchmark overlays. Ask your agent to *"benchmark my BTC strategy against buying and
holding BTC over the same window"*; it composes your finished simulations into a benchmark and
fetches a buy-and-hold curve for the reference asset over the same window and starting capital.

Challenges (shared leaderboards on one ticker) are **off by default** here — gated by the
`challenges` feature flag ([section 10](#10-feature-flags--switch-product-areas-on-and-off)), not by
the edition, because a leaderboard needs other participants to mean anything. Switch the flag on and
the tools appear; a local install still ships only closed historical challenges and cannot create
new ones, so benchmarks remain the way to compete against yourself.

## 9. What things cost

- **On a free local engine, money is the wrong unit.** Every cost estimate reads `0.0000` no matter
  how large the run. What you pay is **time**: roughly 20-40 seconds per agent call. Ask for
  `totalLLMCalls`, not the dollar figure — a 12-call run is a few minutes, a 180-call run is most of
  an hour.
- **Price before you commit.** `estimate_backtest_cost` creates nothing and respects the strategy's
  `backtest_schedule`. If it reports zero trading days, the run would decide nothing at all.
- **The estimate aims high, not low.** It counts calendar days the exchange may close, so a stock
  backtest usually runs slightly cheaper than quoted. Do not expect an abstention to save you a
  call, though: a confidence-threshold abstention happens *after* the agent has been asked and
  answered, so you pay for it either way.
- **There is a platform spend ceiling**, and it applies to self-hosted installs: it caps
  platform-tracked AI spend (not your provider's own billing). `GET /api/v1/budget` reports the
  limit, current spend and what remains. A free local engine should not draw against it, and
  per-run costs on one do read `$0.00` — but the balance is per account and survives engine changes,
  so a small non-zero `currentSpend` may be left over from before you switched. Nothing attributes
  a spend figure back to the run that caused it, so treat it as a coarse guard, not accounting.
- **`consensus_method` costs money.** Omitting it from a strategy selects `ORCHESTRATOR_OVERRIDE`,
  which adds one model call per evaluation round on top of the agents'. Write `MAJORITY` if you want
  the cheaper path.

## 10. Feature flags — switch product areas on and off

Some parts of the product are gated behind runtime **feature flags**. On a fresh local instance:

| Flag | Local default | What it gates |
|---|---|---|
| `challenges` | **off** | Public trading challenges and their leaderboards. Off here because a leaderboard needs other participants to mean anything. |
| `market-research` | **off** | LLM-driven market research, and the hourly scheduler that runs it. |

If you try to use a gated feature anyway, nothing fails silently. The sidebar entry is hidden, the
REST endpoints answer `403`, and the MCP tools disappear from the tool list — and if you call one
directly, it tells you exactly which flag to switch on.

Flags live in **your own Postgres**, not in a config file, so a change survives restarts and
redeploys. You are the administrator of your own instance — the local user is given the admin role
automatically at boot, so you can change any flag with no database editing needed. (If you have
been running an older build, the promotion happens the first time you start this one; no action
needed on your side.)

**From the web UI** — the way most people will do it. Sign in
(`http://localhost:8085/api/v1/auth/dev-login`), then open **Admin → Feature Flags**
(`http://localhost:3000/admin/feature-flags`). Toggle a flag, or restrict it to specific email
addresses.

**From the API:**

```bash
# Get a session cookie
curl -s -c cookies.txt http://localhost:8085/api/v1/auth/dev-login >/dev/null

# See every flag and its current state
curl -s -b cookies.txt http://localhost:8085/api/v1/admin/feature-flags | jq

# Switch challenges on for everyone
curl -s -b cookies.txt -X PUT http://localhost:8085/api/v1/admin/feature-flags/challenges \
  -H 'Content-Type: application/json' -d '{"enabled":true}'

# Or switch it on for named people only
curl -s -b cookies.txt -X PUT http://localhost:8085/api/v1/admin/feature-flags/challenges \
  -H 'Content-Type: application/json' \
  -d '{"enabled":true,"enabledForEmails":["local@quorumtrading.local"]}'

# What is available to me right now? (works signed out too)
curl -s http://localhost:8085/api/v1/features | jq
```

Changes take effect within about five seconds across the instance — no restart.

> **Turning `market-research` on works, but check your LLM first.** The flag is the only thing
> gating research — switch it on and the tools, the REST endpoints and the hourly scheduler all
> come to life. Its agents reason through platform tool calls, though, so the verdicts are only as
> good as the model behind them: a weak or unconfigured engine returns thin analysis rather than an
> obvious error. Set up your LLM engine (section 4) before drawing conclusions from a research run.

> **Changing the shipped defaults.** The `quorumtrading.features.*.default` properties are seeds
> only, read the first time an instance boots. Editing them will not move a flag that already has
> a row — that is deliberate, so a redeploy cannot silently undo a change you made. Use the API or
> the admin page.

## Data & lifecycle

- All data persists in Docker volumes (`qt_local_pg`, `qt_local_redis`, `qt_local_secrets`).
- Stop: `docker compose -f docker-compose.local.yml down` (data kept).
- Wipe everything: `docker compose -f docker-compose.local.yml down -v`.

## Notes

- Backend + API + MCP: `http://localhost:8085`. Swagger UI: `http://localhost:8085/q/swagger-ui`.
- Web UI: `http://localhost:3000` — boots by default. The index page shows a **local home** (status
  + next steps) instead of the hosted marketing page (the frontend asks `GET /api/v1/instance`
  which edition it's talking to). MCP is a full peer of the UI: you can drive everything from an
  agent and skip the UI with the headless command in section 1.
- Everything belongs to a single fixed user, **`local@quorumtrading.local`** ("Local") — created
  automatically; no sign-up, no OAuth. A browser session is one click away via
  `http://localhost:8085/api/v1/auth/dev-login`.
- Hacking on the UI itself? Run it natively for hot reload instead of the container:
  `cd quorumtrading-frontend && npm install && npm run dev` (needs Node 20+). It reaches the backend
  on `:8085` out of the box — no `.env.local` needed.
- Broker regions: 🇺🇸 US (Alpaca) always; 🇪🇺 EU (Saxo, **experimental — see section 6**) appears once
  `SAXO_ENABLED=true` with your personal OAuth app credentials. The hosted SaaS stays US-only.
- Keep your MCP token private — it grants full access to your account.
