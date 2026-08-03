# QuorumTrading capabilities

Authoritative inventory of what the product **does** today, what's **off behind a flag**, what's **hedged** until the backend ships it, and what's **deliberately not built**. Used to spot design-canvas claims that conflict with the real feature set.

**How to use this doc**
- Read the section that matches the canvas's claim.
- Look for the capability bullet (✅), the constraint, the backend gap (🔶), or the "Not supported" line (⛔).
- If the canvas asks for something marked 🔶, the design is fine in principle but needs a backend story first — flag it; don't fake numbers in the UI.
- If the canvas asks for something marked ⛔, push back. Either the design is wrong or the product is about to grow a feature; in either case the conversation happens before the implementation.
- Last-resort sources of truth: `http://localhost:8085/q/openapi` (endpoint surface), `quorumtrading-application/.../service/` (use cases), `docs/BOUNDED_CONTEXT_CANVAS.md` (domain model).

This doc lists **surface behaviors**, not how they're built. Architecture details (hexagonal layering, module structure, testing constraints) live in the Architecture section of the repo-root [CLAUDE.md](../CLAUDE.md); the domain model lives in [docs/BOUNDED_CONTEXT_CANVAS.md](BOUNDED_CONTEXT_CANVAS.md).

Last refreshed: 7 July 2026.

---

## 1 · Trading + AI consensus

✅ Multi-agent consensus per decision: **Technical · Fundamental · Sentiment** specialists, optional **Orchestrator** tie-breaker.
✅ Consensus methods: `MAJORITY` (agent votes, no orchestrator LLM call), `ORCHESTRATOR_OVERRIDE` (orchestrator LLM decides).
✅ Per-decision action: `BUY`, `SELL`, `HOLD` on a ticker. Quantities are LLM-proposed and capped — buys at the max affordable amount (cash buffer applied), sells at current holdings. Fractional share quantities (scale 8) fill on Alpaca + mock brokers; Saxo equities/ETFs fill whole shares only (cash residue carries forward).
✅ Decision log: full agent vote breakdown, reasoning text, consensus time, AI cost. System-originated `SKIP` rows appear when market data is missing or agents fail (no trade fires).
✅ Restart-from-decision: rewind a sim to any historical decision and replay forward with the current strategy version.

🔶 Decision-level AI cost is tracked in aggregate per sim (`get_simulation_cost`) but not surfaced per-row in the v2 decision table.

⛔ Not supported: short positions, margin, options, futures, stop-loss / take-profit orders (decisions only fire BUY/SELL/HOLD).

---

## 2 · Strategies

✅ Markdown body with **YAML frontmatter** (rules, tickers, agent weights, `live_schedule` + optional `backtest_schedule`).
✅ Versioning: every save increments `version`; older versions are retained on related sims.
✅ Visibility: `private` (default) or `public` (lands in the public library at `/strategies/public`).
✅ Lineage: tracked via backend fields `parentStrategyId` + `generation` (0 = original, increments per derived generation). The UI surfaces it via the frontend type `StrategyLineageOrigin = 'OWN' | 'PUBLIC'` (whether the parent strategy is your own or from the public library).
✅ Cascade delete: deleting a strategy deletes its simulations, decisions, portfolio snapshots.
✅ CRUD via REST, MCP (`create_strategy`, `update_strategy`, `delete_strategy`, …), and frontend.

⛔ Not supported: collaborative editing, comments, sharing-link tokens, fork-tracking UI, per-version diff viewer.

---

## 3 · Simulations

✅ Three trading modes — gated by `brokerAccountId`:
| Mode | Broker | Money | Notes |
|---|---|---|---|
| Mock backtest | always `MockBrokerAdapter` | no | BACKTEST runs are **always mock**, never paper/live — not configurable. |
| Paper | `ALPACA_PAPER`, `SAXO_SIM` 🚦 | no | Real market data, fake account. |
| Live | `ALPACA_LIVE`, `SAXO_LIVE` 🚦 | yes | Real money. Multiple concurrent live sims are allowed. |

⛔ **Simulations are USD-only.** `CreateSimulationRequest` has no `currency` field and the response
always reports `"currency":"USD"`. There is no FX handling anywhere in a simulation. This matters
most to non-US users: the documented route to a European company is its US listing, so a EUR
investor backtesting ASML or SAP is measuring a blended equity + USD/EUR return and the reported
figure is not the return they would have experienced in euros. `find_ticker` warns per ticker (it
volunteers that a depositary receipt "includes a foreign-exchange component"); nothing warns at
portfolio level. Recurring investment plans *are* currency-aware — see §5 — simulations are not.

🚦 Saxo broker + market data adapters are **off behind `SAXO_ENABLED`** (`quorumtrading.broker.saxo.enabled` / `quorumtrading.marketdata.saxo.enabled`, default `false` in **every** profile including `%local`/standalone — opt in with `SAXO_ENABLED=true`). See Section 10.1 for the OAuth/re-auth specifics. ⚠️ **Experimental / largely untested:** the onboarding, token-refresh and trading paths are implemented and unit/WireMock-tested, but every Saxo wire contract is self-labelled "unconfirmed against a live account," the live integration tests never run in CI, and it has not been exercised end-to-end against a real Saxo account. Alpaca is the supported broker; treat Saxo as WIP.

✅ State machine: `PENDING → SIMULATING → LIVE → COMPLETED | FAILED`. `WAITING_FOR_DATA` while backfill runs.
✅ Actions: start, pause, resume, force-end, sell single position, liquidate all positions, restart-from-decision.
✅ Portfolio snapshots are time-series; chart overlays available.
✅ Per-sim AI cost tracking.
✅ External-pause flag: sim can be paused **by the system** (e.g. budget cap hit, broker error) — `externalPauseReason` is surfaced on the row.

🔶 `simulation.twrPercent` and `mwrPercent` are NOT on the list endpoint — only on the detail endpoint. The list-page Return column renders `<HedgedValue/>` until this lands.

⛔ Not supported: short positions, margin trading, options, futures, partial-position close, take-profit/stop-loss orders, OCO orders.

---

## 4 · Portfolio + market data

### Which markets you can trade

Venue coverage is a property of the **broker you attach**, not of the platform or the strategy
format.

| Broker | Instruments | Where |
|---|---|---|
| Default (US) | US-listed equities and major crypto | everywhere |
| Saxo 🚦 | ⚠️ **experimental connectivity only — not EU market access** | self-hosted only, opt-in |

**Trading European equities is not possible today, on any deployment.** On the hosted service Saxo
is off and stays off. On a self-hosted instance you may opt in with `SAXO_ENABLED=true` and connect
a Saxo account — that path is **experimental and largely untested**, and connecting it does *not*
unlock European venues: exchange-qualified tickers are refused at strategy creation regardless of
which broker is attached (see the refusal note below). Saxo is an integration you can exercise, not
a market you can trade. Treat any doc, checklist or UI affordance implying otherwise as stale.

⚠️ **Exchange-qualified tickers (`RHM:XETR`, `ASML:XAMS`, `AZN:XLON`, and the dotted aliases
`RHM.DE`, `AZN.L`) are refused at strategy creation.** No configured broker trades a non-US venue
and no price source covers one, so there is no honest answer other than refusal.

The refusal is now unconditional. It previously fired only when the root symbol had no US
listing — so `AZN:XLON` passed validation on the strength of a broker confirming `AZN`, the New
York depositary receipt, and the backtest then ran against **that** instrument in USD while still
reporting `AZN:XLON`. A run labelled as London was observed buying the NYSE receipt and declaring
success. Two causes, both closed: validation accepted a broker answer about the bare root as
though it were about the venue, and price lookups keyed on the root so both symbols collapsed to
the same rows. Lookups now key on the venue-qualified form, so a venue with no data returns
nothing — a visible absence instead of another instrument's prices.

To trade a non-US company,
run `find_ticker` with the company name and use the ticker it returns: it resolves the tradable US
route, tells you when that route is a depositary receipt rather than the home listing, and reports
the home listing separately so you can see what you are not getting. Do not copy an ADR symbol out
of documentation — whether a given receipt is tradable varies by broker and changes over time.

✅ Portfolio object is immutable; `buy()` and `sell()` return new portfolio.
✅ Positions tracked per ticker with cost basis + current quantity.
✅ Cash split: `cashAvailable` + `cashReserved` (reserved for in-flight orders).
✅ Price data backfilled per ticker (`backfill_price_data`).
✅ Technical indicators on demand (`get_indicators`): RSI, MACD, SMA-50/200, Bollinger Bands, etc.
✅ Historical events log (`query_market_events`, `list_historical_events`): dividends, splits, earnings.
✅ Market data adapters: **Alpaca primary** for crypto + stocks; **Tiingo** deep-history fallback for daily/weekly/monthly stock bars (no intraday, no crypto — default on outside prod, **off in prod** behind `TIINGO_ENABLED`). AlphaVantage removed Jan 2026; the old always-on Coinbase adapter too (Coinbase is back as a **keyless** crypto source — see next line).
✅ **Keyless crypto adapters** — two per-provider `MarketDataPort`s that need **no API key**: `KrakenMarketDataAdapter` (`quorumtrading.marketdata.kraken` — public OHLC, ~2yr of daily bars in one call, native weekly) and `CoinbaseMarketDataAdapter` (`quorumtrading.marketdata.coinbase` — public candles, deeper daily history back to ~2016 via pagination). Both advertise `CRYPTO`+`BACKFILL`+`CANDLES`+`INTRADAY`+`DAILY` and degrade to empty on failure; the application layer's provider selection composes them (widest-result-wins / fallback chain — **no orchestrator module**). Default **on** (dev + `%local`), **off** in prod (Alpaca is primary there) and in test/e2e (no live external calls). This is what makes crypto backfill/backtest work out of the box in the standalone edition. (The keyless `adapter-marketdata-coinbase` is a fresh public-candles reader — not the always-on Coinbase adapter removed Jan 2026.) See [ADR-0002](adr/0002-standalone-hosted-price-source.md).
🚦 Saxo market data adapter exists but is off behind `SAXO_ENABLED` (default `false` everywhere, including `%local`/standalone; opt in with `SAXO_ENABLED=true`) — experimental, see the WIP note in the trading-modes section.
✅ News: Finnhub News + RSS feeds (NewsAPI adapter removed Jan 2026 — saved $449/mo).

⛔ Not supported: tick-level data, options chains, futures curves, FX pairs, alternative data feeds.

---

## 5 · Recurring investment plans (DCA)

✅ Plan budget modes: `FIXED_AMOUNT`, `FIXED_OR_PARTIAL` (fixed if budget allows, else what's left), `ALL_AVAILABLE`.
✅ Schedule: day-of-month `1–28`. **Monthly only** — no weekly/biweekly/quarterly.
✅ Currency at plan level (USD default).
✅ Picker policy: `AI_PICK` — the only accepted value. ⚠️ **The name overpromises:** the default picker runs no AI at all. `HeuristicStrategyPicker` deterministically takes the most-recently-updated active strategy in the user's pool; an LLM picker is selectable via `quorumtrading.picker.mode=llm` (default `heuristic`) and falls back to the heuristic on any LLM failure. A plan's `riskTolerance` and `maxStrategiesActive` preferences are accepted and stored, but the heuristic picker does not read them — only the ticker allow/block list affects its choice.
✅ State machine: `ACTIVE ⇄ PAUSED → TERMINATED`.
✅ Per-ticker allow/block-list on the plan.
✅ Live-broker guardrail: creating an `ALPACA_LIVE` plan requires `confirmLive=true` in the create payload.
✅ Manual execute via `POST /investment-plans/{id}/execute`.
✅ **Idempotency**: `simulation_contributions` has a partial UNIQUE index on `(source_plan_id, contribution_date)`. Scheduler retries are safe.
✅ Allocation audit row per execution attempt: `EXECUTED`, `SKIPPED_*`, or `FAILED_*` — visible on the plan timeline.

🔶 `plan.currentValue` (DCA plan market value) — not on backend yet. UI shows `<HedgedValue/>`.
🔶 `plan.lastContribTs` (last contribution timestamp) — same.

⛔ Not supported: weekly/biweekly schedules, conditional rules (e.g. "only buy if RSI < 30"), dynamic budget adjustment, multi-currency baskets, team-shared plans.

🚦 **Off by default** in every environment: `quorumtrading.scheduler.investments.enabled`. Manual execute works; the daily 06:00 UTC cron does not fire until flipped. See `docs/operations/EMAIL_READINESS.md` and the Go-Live Checklist in `CLAUDE.md`.

---

## 6 · Notifications

✅ Channels per type: `IN_APP`, `EMAIL`. One row per `(userId, type, channel)` so delivery state is tracked independently.
✅ User preferences keyed by `(userId, type)`. Missing rows fall back to system defaults at dispatch.
✅ Notification types: simulation status (incl. `SIMULATION_PAUSED_BUDGET`, `SIMULATION_LOW_BUDGET`), plan execution (executed / skipped / failed), budget alerts (80% warning / 100% depleted — see Section 10.4), broker re-auth (`BROKER_REAUTH_REQUIRED`), market research completion.
✅ Email rendering: Quarkus mailer over SMTP (works with SES). Templates are HTML-escaped.
✅ Per-type unsubscribe: HMAC-signed token in the email link — **no DB lookup** required to validate.

🔶 `notification.actor` (who/what fired this notification) — not on backend yet.

⛔ Not supported: SMS, push (web/iOS/Android), Slack/Discord webhooks, per-thread digest, snooze.

🚦 **Off by default**: `quorumtrading.notifications.email.enabled`. In-app notifications work; outgoing email is disabled until SES is verified and the unsubscribe secret is rotated. See `docs/operations/EMAIL_READINESS.md`.

---

## 7 · Market research

🚦 **Behind the `market-research` feature flag** (§ 16a). On by default for the hosted product, off
by default on a standalone self-host. With the flag off: REST under `/api/v1/market-research`
answers `403` naming the flag, all seven MCP research tools are dropped from `tools/list` and
refuse if called anyway, the sidebar entry is hidden, and `MarketResearchScheduler` stays idle so
nothing executes on a timer. **An administrator can switch it on anywhere**, including on a
self-hosted instance, whose local owner is provisioned as an administrator at boot.

*Quality caveat, not a gate:* the research agents reason by calling platform tools mid-analysis
(`get_indicators`, `find_ticker`, …), which works best with a conversational LLM provider. Routed
to a plain-text path they still answer, but with less to reason from — so verdicts track the
configured engine's capability. Check `GET /api/v1/config/llm-status` before treating a
`viabilityScore` as meaningful on a thinly-configured instance.

✅ Create a research run with a prompt + target tickers + interval (`create_market_research`).
✅ Multi-agent execution: Strategy Designer, Viability Assessor, Technical/Fundamental/Sentiment Analysts, News Sentiment.
✅ Viability score (`viabilityScore`) computed per execution; three buckets in UI: pink < 0.4 · amber 0.4–0.7 · brand > 0.7.
✅ Pause / resume / execute-now.
✅ Auto-backtest toggle: when on, completed research auto-creates a backtest sim with the resulting strategy.

🔶 `research.viabilityScore` is NOT present for RUNNING/PENDING runs — only on completed runs. UI hides the bar for those states.

⛔ Not supported: cross-research diff/merge, scheduled re-runs, multi-tenant shared research.

---

## 8 · Benchmarks

✅ Compose a benchmark from N strategies + a time range (`create_benchmark`, `compose_benchmark`).
✅ Two modes: `BACKTEST` (mock) or `LIVE` (real Alpaca data).
✅ Overlay simulations on a benchmark chart (`get_benchmark_overlays`).
✅ Per-entry return % rendered client-side.

🔶 `benchmark.entry.returnPct` is denormalized client-side because the list endpoint doesn't ship it.

⛔ Not supported: cross-user benchmark leaderboards, public benchmark library, benchmark-of-benchmarks.

---

## 9 · Challenges

✅ Single-ticker challenges with start/end dates and a starting cash amount per participant.
✅ Join a challenge (`join_challenge`); each participant gets a sandboxed sim.
✅ Leaderboard per challenge (sorted by portfolio value).
✅ Three list buckets: Upcoming · Active · Completed.

🔶 `challenge.leaderboard` (top entry on the list endpoint) — not on backend yet. List view shows participant count only; the top entry comes from the detail endpoint.

⛔ Not supported: team challenges, monetary prizes, real-money entry fees, cross-platform invitations, multi-ticker challenges.

---

## 10 · Settings

### 10.1 Broker accounts
✅ Types: `MOCK`, `ALPACA_PAPER`, `ALPACA_LIVE`, plus 🚦 `SAXO_SIM`, `SAXO_LIVE` (off behind `SAXO_ENABLED` / `quorumtrading.broker.saxo.enabled`, default `false` in every profile including `%local`/standalone; opt in with `SAXO_ENABLED=true`).
✅ Statuses: `ACTIVE`, `INVALID_CREDENTIALS`, `NEEDS_REAUTH`. (Disabled is treated as inactive — same color as `INVALID_CREDENTIALS` in the UI.)
✅ Credential validation on save (`validate_broker_credentials`).
✅ Live trading guardrail: connecting an `ALPACA_LIVE` broker requires explicit user confirmation.
🚦 Saxo uses OAuth with rotating refresh tokens (onboarding redirect flow, no stored API keys). When the refresh fails, the account flips to `NEEDS_REAUTH` and a `BROKER_REAUTH_REQUIRED` notification fires. Saxo equities/ETFs fill **whole shares only** — no fractional fills.

🔶 `broker.accountValue` (current NAV) — not on backend yet.

⛔ Not supported: brokers other than Alpaca and Saxo (Schwab, IBKR, Robinhood, etc.). Coinbase is a keyless **market-data** source only (§8) — not a broker; you cannot trade through it.

### 10.2 API tokens
✅ Personal-access tokens for REST + MCP. Fields: `id`, `name`, `tokenPrefix`, `createdAt`, `lastUsedAt`, `expiresAt`, `revoked`. (See `app/lib/types/apiToken.ts`.)
✅ One-time secret reveal on creation; only the hash is stored.
✅ Optional expiry (`expiresInDays` on create); otherwise the token lives until revoked.
✅ Per-token last-used tracking (nullable for never-used → renders as `<Dim>never</Dim>`, NOT hedged).
✅ Revoke action.

⛔ Not supported: **per-token scopes** (no `scopes` field; a token grants the same access as the issuing user — design canvases that show `sims:read` / `sims:write` / `alpaca:exec` style scope strings are mock fixtures, not real). Organization-owned tokens. OAuth-app authorization flow. Token rotation schedules.

### 10.3 LLM model preferences
✅ Per-context model selection. Contexts: `RESEARCH`, `BACKTESTING`, `PAPER_TRADING`, `LIVE_TRADING`, `CHAT`.
✅ Per-agent overrides within a context (Technical / Fundamental / Sentiment / Orchestrator can each pick a different model). Per-user choice is model-per-context/agent only.
✅ LocalLLM fallback chain: `claude → qwen` (system-level config `quorumtrading.llm.local.tool.fallback`, **not per user**).
✅ Intelligent failover: LocalLLM adapter blacklists failing providers with exponential backoff.
✅ Available-models catalog (`get_available_models`) sourced from OpenRouter primary.
🚦 Runtime LLM engine selection (LOCAL CLI vs OPENROUTER): `/api/v1/llm/providers` + `/providers/select`, the `/api/v1/llm/setup` page, and MCP `LlmProviderTools` — behind `quorumtrading.llm.selection.enabled` + `quorumtrading.llm.local.enabled` (both default `false`; on in `%local`).

🔶 `config.lastTestRespMs` (last round-trip per model) — not on backend yet.

⛔ Not supported: per-strategy model overrides, model A/B testing, custom OpenRouter fine-tunes.

### 10.4 Budget + usage
✅ **Lifetime prepaid balance**, not a monthly cap — the limit is consumed by `lifetimeSpend`; monthly spend is shown for display only.
✅ **Every new account starts with a $2.00 welcome balance** (`quorumtrading.user.welcome-budget-cents=200`), and it applies on self-hosted installs too. A self-hoster running on their own OpenRouter key still hits this ceiling — it caps platform-tracked AI spend, not the provider's billing. `GET /api/v1/budget` reports it; raise it with the property, or top up. Note that `POST /api/v1/budget/top-up` is a hosted-payments concept and is exposed on standalone as well.
✅ Threshold alerts: **80% warning** + **100% depleted** (no 50% threshold).
✅ Optional per-user **daily spend limit** — enforced at the LLM call (`DailySpendLimitExceededException`).
✅ **Hard-stop on depletion**: LLM operations throw `BudgetDepletedException`; running sims are system-paused (`externalPauseReason`, see Section 3) and a `SIMULATION_PAUSED_BUDGET` notification fires.
✅ Stripe budget top-ups ($5 / $10 / $50) at `/settings/budget` — behind `quorumtrading.stripe.enabled` (`false` in dev/test, `true` in prod).
✅ Usage breakdowns: **by model** (calls × tokens × cost) and **by feature** (Market Research / Backtest / Live / etc.).
✅ Projected month-end spend.

⛔ Not supported: per-feature caps, monthly budget reset cycles.

### 10.5 Account
✅ Profile fields: email, displayName, OIDC providers list, joined date.
✅ Delete account (cascades to all owned data).

⛔ Not supported: change email, two-factor auth, active sessions list, device management.

---

## 11 · Authentication

✅ OAuth providers (live set depends on env): Google, GitLab. (Apple / GitHub / Microsoft / Amazon configurable per environment but only Google + GitLab are enabled in production today.)
✅ Dev login on `localhost`: "Continue as Dev User" + "Continue as FUT User" (fresh account) — gated by hostname check.
✅ Session expiry → redirect to `/auth/signin?expired=true` with banner.
✅ Return-URL roundtrip: `?returnTo=…` is persisted in `sessionStorage.auth_redirect` so the post-OAuth landing is correct.
✅ "Last used provider" sorting: localStorage `last_auth_provider` raises the primary OAuth button.
✅ Sign-up requires Terms acceptance (checkbox gates the OAuth buttons).

⛔ Not supported: email/password auth, magic-link auth, social-login linking (one OAuth identity per account), enterprise SSO (SAML/OIDC beyond the OAuth list), MFA, account recovery flow.

---

## 12 · AI Assistant (chat)

✅ Council-aware chat surface at `/assistant`. Reads the user's strategies, simulations, market research, recent decisions for context.
✅ Quick-action chips adapt to user state (e.g. "Compare Strategies" only when ≥ 2 sims completed).
✅ Multiple chat sessions per user; switchable from sidebar.
✅ Streaming responses via SSE.

⛔ Not supported: file uploads, code execution, image generation, voice input.

---

## 13 · MCP integration

✅ Trading platform exposed as an MCP server. AI agents (Claude Code, Cursor, etc.) can drive the platform without touching the UI.
✅ Tool surface mirrors REST capabilities — see `mcp__quorumtrading__*` in any MCP client.
✅ Auth via personal API token (same tokens as Section 10.2). Each token is scoped to one user.
✅ Guided prompts: `full_research_cycle`, `create_backtest`, `analyze_simulation`, `compare_strategies`, `analyze_market_research`, `setup_live_trading`, `diagnose_simulation`, `explain_indicators`, `setup_recurring_investment`, `review_investment_plans`. (Authoritative list: `adapter-mcp/.../prompt/McpPrompts.java`.)

⛔ Not supported: shared/team MCP tokens, OAuth-scoped MCP access, websocket MCP transport.

---

## 14 · Public surface (unauth)

✅ Landing page at `/` — hero, council demo, MCP terminal demo, time-machine backtest demo, footer CTA.
✅ Blog (changelog) at `/blog` + `/blog/[slug]`.
✅ FAQ at `/faq` + `/faq/[slug]`.
✅ Legal: `/terms`, `/privacy`.
✅ Public strategy library at `/strategies/public` + `/strategies/public/[id]`.
✅ Public challenge browsing (read-only) — but joining requires sign-in.

⛔ Not supported: pricing page / subscription tiers (note: Stripe **budget top-ups are shipped** — see Section 10.4; only the public pricing surface is not built), demo sandbox, marketing site beyond what's listed.

---

## 15 · Backend gaps surfaced with `<HedgedValue/>`

Fields the UI reserves space for so layouts won't reflow when the backend ships them. Render `<HedgedValue/>` (em-dash + amber Δ-sup) wherever a column needs to hold the slot.

| Field | Surface | Note |
|---|---|---|
| `simulation.twrPercent` | /simulations list Return column | mwrPercent too — both on detail endpoint only |
| `research.viabilityScore` | /research RUNNING/PENDING runs | Hidden today; could be HedgedValue placeholder |
| `benchmark.entry.returnPct` | /benchmarks list | Denormalized client-side today |
| `challenge.leaderboard` | /challenges list (top-entry column) | Detail endpoint only |
| `plan.currentValue` | /investment-plans list + detail KpiStrip | DCA plan market value |
| `plan.lastContribTs` | /investment-plans list | Last contribution timestamp |
| `notification.actor` | /notifications row attribution | "Who/what fired this" |
| `broker.accountValue` | /settings/broker-accounts row | Broker NAV |
| `config.lastTestRespMs` | /settings/config | Per-model round-trip time |

⚠️ Distinct from these: `token.lastUsedAt = null` (api-tokens never-used) is **semantic**, not a hedge — render `<Dim>never</Dim>`, not `<HedgedValue/>`.

---

## 16 · Feature flags — off by default

### 16a · Runtime feature flags (database-backed, Unleash)

Distinct from the configuration switches below: these live in the `feature_flags` table, are changed
at runtime by an administrator, and can be targeted at individual users by email. Evaluated by the
Unleash SDK reading definitions from our own database — there is no Unleash server.

| Flag | Prod | Standalone | Dev/test | Gates |
|---|---|---|---|---|
| `challenges` | off | off | on | Challenges + leaderboards (§ 9) |
| `market-research` | on | off | on | Market research + its scheduler (§ 7) |

A disabled flag hides the sidebar entry, makes REST answer `403`, and drops the feature's tools from
the MCP `tools/list` — each refusal names the flag. **Direct navigation to a hidden page still
works**; the page renders and the API refuses, which is the intended behaviour, not a gap.

The `quorumtrading.features.*.default` properties are seeds applied on first boot only. Editing one
does not move a flag that already has a row — a redeploy must not undo an administrator's change.

Managed at `/admin/feature-flags` (`users.role='ADMIN'` only, on every edition — the standalone
owner is provisioned as an admin rather than exempted from the check).
Designs must not assume a flagged feature is present — call `GET /api/v1/features`.

### 16b · Configuration switches — off by default

These flip during the go-live process. Designs that assume them on must check the deployment state first.

| Flag | Default | Flips when |
|---|---|---|
| `quorumtrading.scheduler.investments.enabled` | `false` | After weeks of clean paper execution + manual-execute verification |
| `quorumtrading.notifications.email.enabled` | `false` | After SES domain verification + unsubscribe secret rotation |
| `quarkus.mailer.mock` | `true` (non-prod), `false` (prod) | Production deploy only |
| `quorumtrading.broker.saxo.enabled` + `quorumtrading.marketdata.saxo.enabled` (both via `SAXO_ENABLED`) | `false` (opt-in even in `%local` — experimental) | After Saxo OAuth onboarding is verified in the target environment |
| `quorumtrading.llm.local.enabled` + `quorumtrading.llm.selection.enabled` | `false` (on in `%local`) | Self-host edition: runtime engine selection (LOCAL CLI vs OpenRouter) |
| `quorumtrading.stripe.enabled` | `false` (dev/test), `true` (prod) | Production deploy only |

---

## 17 · Removed / deliberately not built

Drop-stack — when a design canvas asks for one of these, the answer is "no" until product re-prioritizes.

**Removed (Jan 2026)** — see `CLAUDE.md → Removed Adapters` for context:
- ⛔ **Bedrock** LLM adapter — was unimplemented stub.
- 🔄 **Coinbase** market data — the always-on adapter was removed Jan 2026 (consolidated to Alpaca); **re-added 2026-07 as a keyless, standalone-only** public-candles crypto source (§8).
- ⛔ **NewsAPI** — $449/mo; replaced by free Finnhub News + RSS feeds.
- ⛔ **AlphaVantage** — Alpaca handles backfill.

**Deleted route (May 2026)**:
- ⛔ `/simulations/[id]/decisions` standalone page — was orphaned (zero inbound links) and rendered hardcoded mock data. The canonical decisions surface is the inline panel on `/simulations/[id]`.

**Stripe** — budget top-ups are ✅ shipped (prod-enabled behind `quorumtrading.stripe.enabled`, flag-off in dev/test — see Section 10.4). Pricing page / subscription tiers remain ⛔ not built.

**Deliberately not built — product scope**:
- ⛔ Mobile native apps (iOS/Android). Web only; responsive design at 390×844 minimum.
- ⛔ Multi-tenancy: no organizations, teams, role-based access (other than admin/regular). Every entity is owned by one user.
- ⛔ Real-time collaboration (no shared strategy editing, no co-watching live sims).
- ⛔ Risk profiles ("conservative / aggressive") — agents have no per-user risk persona.
- ⛔ Tax reporting, P&L statements, 1099 generation.
- ⛔ Referral program, affiliate links.
- ⛔ Strategy marketplace with monetary purchase (public library is free / browse-only).
- ⛔ Custom indicator builders (indicators come from the price-data adapter, not user-authored).
- ⛔ Per-decision manual override ("ignore consensus, do X anyway"). Decisions are immutable audit records.

---

## 18 · Verification before implementing a design canvas claim

When a design proposes a new column / panel / metric:

1. Find the closest section above. Search by keyword (`leaderboard`, `currentValue`, etc.).
2. If it's ✅, check the implementation hints in `quorumtrading-application/.../service/` — what's already there might already cover the design.
3. If it's 🔶, the design is fine but **don't ship the UI alone**. File the backend gap, render `<HedgedValue/>` in the meantime, and tell the user the gap blocks the design from delivering its intent.
4. If it's ⛔, push back. Either the design is asking for something we deliberately don't support, or this is a real product decision that needs the user's go-ahead first.

When this doc and the backend OpenAPI disagree, **trust the OpenAPI** and update this doc.

---

*Maintained by the team. Refresh after any feature ship, gap close, or removal — and bump the "Last refreshed" stamp at the top of this file.*
