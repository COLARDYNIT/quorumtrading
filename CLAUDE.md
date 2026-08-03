# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

QuorumTrading is a SaaS platform for multi-LLM trading consensus and backtesting. It orchestrates multiple AI agents (Technical, Fundamental, Sentiment) to make collaborative trading decisions through consensus mechanisms, with historical backtesting capabilities.

## Technology Stack

- **Backend:** Quarkus 3.x + Java 25, PostgreSQL 16 + Flyway, Redis 7
- **Frontend:** Next.js 16 + React 19 + TypeScript, styled-components, TanStack Query + Zustand
- **LLM:** OpenRouter (primary), LocalLLM with fallback chain
- **Infrastructure:** AWS ECS Fargate, Terraform, GitHub Actions

## Quick Start

```bash
# 1. Start infrastructure
docker-compose up -d postgres redis

# 2. Create test database (one-time setup)
psql postgresql://quorumtrading:dev-password@localhost:55432/postgres -c "CREATE DATABASE quorumtrading_test OWNER quorumtrading;"

# 3. Start backend (http://localhost:8085)
cd quorumtrading-backend && ./mvnw quarkus:dev

# 4. Start frontend (http://localhost:3000)
cd quorumtrading-frontend && npm install && npm run dev
```

### Local Profile (Isolated Testing)
```bash
# Backend on port 8085, database quorumtrading_local
cd quorumtrading-backend && ./mvnw quarkus:dev -Dquarkus.profile=local

# Frontend on port 3001
cd quorumtrading-frontend && ./start-local.sh
```

### Common Commands

**Backend (from `quorumtrading-backend/`):**
```bash
./mvnw quarkus:dev                          # Development with live reload
./mvnw test                                 # Run all tests
./mvnw test -Dtest=ClassName                # Run specific test class
./mvnw test -Dtest=ClassName#methodName     # Run specific test method
./mvnw clean package                        # Build JAR
```

**LLM integration tests (from repo root — script lives at `scripts/`, not inside the backend module):**
```bash
./scripts/run-integration-tests.sh          # Requires OPENROUTER_API_KEY
```

**Frontend (from `quorumtrading-frontend/`):**
```bash
npm run dev              # Development server
npm test                 # Unit tests (Jest)
npm run test:e2e         # E2E tests (Playwright)
npm run build            # Production build
npm run validate:api     # Verify API contract from backend OpenAPI
```

**Database:**
```bash
psql postgresql://quorumtrading:dev-password@localhost:55432/quorumtrading
```

### Key URLs
- **Backend API:** http://localhost:8085/api/v1
- **Swagger UI:** http://localhost:8085/q/swagger-ui
- **OpenAPI Spec:** http://localhost:8085/q/openapi (serves YAML; append `?format=json` for JSON)
- **Frontend:** http://localhost:3000

### Log Files
All backend profiles log to `/tmp` for easy access:
- **Dev profile:** `/tmp/quorumtrading-dev.log`
- **Local profile:** `/tmp/quorumtrading-local.log`
- **Production:** `/tmp/quorumtrading.log`

Logs rotate at 10MB with 5 backups. Use `tail -f /tmp/quorumtrading-dev.log` to follow in real time.

## Architecture

QuorumTrading follows **hexagonal/ports-and-adapters architecture** with strict layer separation and DDD principles.

### Module Structure (4 Layers)
All backend modules live under `quorumtrading-backend/`:
```
quorumtrading-model/           # Domain: Entities, Value Objects, Domain Services
quorumtrading-application/     # Use Cases (CQRS), Ports, Orchestration Services, AI Agents
quorumtrading-adapter-*/       # Adapters: REST, WebSocket, MCP, Persistence, Redis, LLM (incl. router), Broker (Mock, Alpaca, Saxo), Market Data, Payment (Stripe) — list with `ls quorumtrading-backend/quorumtrading-adapter-*`
quorumtrading-bootstrap/       # Quarkus entry point, Schedulers, CDI wiring
```

### Key Domain Concepts
- **Simulation** - State machine (PENDING → WAITING_FOR_DATA → SIMULATING → LIVE → COMPLETED/FAILED)
- **Strategy** - Markdown-based trading rules with YAML frontmatter
- **Portfolio** - Immutable with buy/sell domain methods
- **AI Agents** - Technical, Fundamental, Sentiment, Orchestrator
- **Consensus Methods** - MAJORITY (agent votes, no orchestrator LLM), ORCHESTRATOR_OVERRIDE (orchestrator LLM decides)
- **Contribution** - Cash injected into a LIVE simulation after start (recurring-investment top-up or manual). Aggregate child of Simulation; persisted as its own row with `(planId, contributionDate)` UNIQUE for idempotent scheduler retries.
- **InvestmentPlan** - Recurring DCA-style plan (state machine: ACTIVE ⇄ PAUSED, both → TERMINATED). Owns: budget mode (FIXED_AMOUNT / FIXED_OR_PARTIAL / ALL_AVAILABLE), day-of-month 1–28, picker policy (AI_PICK in v1), per-type ticker allow/block-list. Daily scheduler fires due plans.
- **InvestmentAllocation** - Immutable audit row for one plan execution attempt — success, skip (with reason), or failure. One row per tick regardless of outcome; drives the user-facing timeline and MCP audit tools.
- **StrategyPickerPort** - Output port that chooses a strategy each tick. Default is `HeuristicStrategyPicker` (most-recently-updated active strategy in the user's pool); `LlmStrategyPicker` is selectable via `quorumtrading.picker.mode=llm` (default `heuristic`) and falls back to the heuristic on any LLM failure so plans never break.
- **Notification** - Per-channel row (IN_APP, EMAIL). Each notification type fans out into N rows so delivery state is tracked independently per channel. NotificationPreference is keyed by `(userId, type)`; missing rows fall back to system defaults applied at dispatch time.

### Recurring Investment Subsystem

The DCA feature spans five phases and is wired end-to-end:

1. **Notifications foundation** — in-app inbox + per-type channel preferences. `NotificationService` orchestrates fan-out; one row per (user × type × channel). `InAppNotificationDispatcher` persists immediately; `EmailNotificationDispatcher` renders templates and calls the `EmailSender` port.
2. **Contribution stream** — `Simulation.buildContribution(amount, date, planId, allocId)` factory enforces LIVE-state invariant. `SimulationPortfolio.recordContribution(amount)` bumps both `cashAvailable` and `cashReserved`. `PerformanceCalculator` computes TWR (geometric chain over sub-periods broken at contributions) and MWR/IRR (Newton-Raphson on cashflows).
3. **InvestmentPlan + manual execute** — full CRUD + REST + MCP. `ExecuteInvestmentPlanService` runs the orchestration: budget check → picker → find-or-create LIVE simulation → record contribution → write allocation row → dispatch notification → advance schedule. Skip/fail paths still write allocation rows and notify.
4. **Email adapter** — Quarkus mailer (SMTP, works with SES). Per-type unsubscribe via HMAC-signed token (no DB lookup). Templates cover all `NotificationType` values (see `EmailTemplateRegistry`) with HTML-escaped user copy.
5. **Autonomous scheduler** — `DcaExecutionScheduler` daily cron probes `findDueForExecution(now)` (partial index on `next_execution_at WHERE status='ACTIVE'`). Per-plan try/catch isolates failures. **Disabled by default** via `quorumtrading.scheduler.investments.enabled`.

**Critical feature flags (default OFF):**
- `quorumtrading.scheduler.investments.enabled` — autonomous monthly execution (real broker action)
- `quorumtrading.notifications.email.enabled` — outgoing email delivery
- `quarkus.mailer.mock` — set to `false` in prod, true elsewhere

**Idempotency:** the primary same-day guard is in `ExecuteInvestmentPlanService.fundedEarlierToday` — it probes `investment_allocations` for an already-funded (`EXECUTED` / `EXECUTED_PARTIAL_DEPLOY`) row for `(planId, UTC funding day)` before anything moves money. It must live at the allocation level because the **`NEW_SIMULATION` path writes no contribution row at all** (the cash is the new simulation's initial capital, and initial capital is deliberately not a Contribution — `PerformanceCalculator` breaks TWR sub-periods at contributions). Keying only on the contribution left the first funding invisible and let exactly one extra funding per day through. The contribution-level `UNIQUE (source_plan_id, contribution_date)` partial index plus the probe in `SimulationContributionService` remain as defence in depth for top-ups. Both guards derive their day from `PlanFundingDay` so they cannot disagree. **A race remains:** the allocation probe is read-then-write, and the DB constraint only backstops the top-up path — two concurrent ticks that both take `NEW_SIMULATION` can still fund twice.

**Live trading guardrail:** creating a plan against an `ALPACA_LIVE` broker requires `confirmLive=true` in the create payload.

**See:** [Email Readiness Checklist](docs/operations/EMAIL_READINESS.md) before flipping email on in production.

### Runtime Feature Flags

Two things are called "feature flags" in this repo and they are not the same:

1. **Configuration switches** (`quorumtrading.scheduler.investments.enabled` and friends) — read
   from `application.properties`, changed by a redeploy.
2. **Runtime flags** — rows in `feature_flags`, changed by an administrator at runtime, evaluated by
   the **Unleash SDK** (`quorumtrading-adapter-featureflag-unleash`). Registry is the `Feature` enum
   in the domain, not the table: an unknown row is ignored, and a new enum constant is manageable
   immediately.

There is **no Unleash server**. `DatabaseToggleSource` implements the SDK's `IFeatureRepository`
so definitions come from our own Postgres — no sidecar, and a self-hosted install has working flags.
The SDK is pinned to **8.4.0**, the last pure-Java release; 9+ evaluates through a Rust core loaded
via JNA, which we cannot take on while shipping a GraalVM native binary. Do not bump it without
running `-Pnative`.

Enforcement, all going through `EvaluateFeatureUseCase`:

| Surface | Mechanism |
|---|---|
| REST | `FeatureFlagGateFilter` — path-prefix, `@Priority(AUTHORIZATION)` **not** `@PreMatching` (identity must exist for email targeting), aborts 403 naming the flag |
| MCP invoke | `McpToolBase.isFeatureEnabled()` / `featureDisabled()` guards |
| MCP list | `FeatureFlagMcpFilters` (`ToolFilter`/`PromptFilter`) — evaluated **anonymously**, so an email-targeted flag stays hidden but remains callable |
| Frontend | `useFeatureEnabled()` hides nav only; direct navigation is deliberately still allowed |
| Schedulers | `MarketResearchScheduler` evaluates anonymously and idles when off — a timer acts for nobody, so an email-targeted flag must not make it run for everyone |

Market research used to be gated a second way, by `quorumtrading.edition` (`HostedOnlyFeatureFilter`,
`McpToolBase.hostedOnlyFeature()`, a `/research` route guard). That was removed in favour of the
flag alone — the flag is now the only thing standing between a self-hoster and market research, and
switching it on genuinely enables the feature rather than hitting a second wall. `quorumtrading.edition`
still governs the index page, broker regions, the AI assistant, and who counts as a flag administrator.

`FeatureGatedToolContractTest` holds the invocation guard and the list filter in step — either
alone is a bug that only appears with the flag off.

Defaults in `application.properties` are **seeds**, applied by `SeedFeatureFlagsService` on first
boot only, and the fallback when evaluation fails. They never overwrite an existing row.

Admin rights reuse `users.role='ADMIN'`, enforced in the use case (not the resource) so every
driving adapter inherits it, with no per-edition exemption. A self-hoster reaches the screen
because `LocalBootstrapProvisioner` promotes its owner to ADMIN on every boot (idempotent, so
installs created before that existed self-heal on restart). An earlier attempt exempted standalone
inside the guard instead; that was true at the service layer only, and the sidebar went on hiding
the admin section from precisely the user the exemption was for. Promoting the row keeps one rule
that every layer already read.

### Removed Adapters
The following adapter modules were removed (Jan 2026) to reduce dead code:
- **Bedrock** (`adapter-llm-bedrock`): Unimplemented stub, threw UnsupportedOperationException
- **Coinbase** (`adapter-marketdata-coinbase`): the original always-on adapter was removed Jan 2026 (consolidated to Alpaca). **Re-introduced 2026-07** under the same name as a **keyless** public-candles crypto source — one of the standalone zero-config crypto adapters (peer of `adapter-marketdata-kraken`); default on in dev/`%local`, off in prod. See [docs/adr/0002](docs/adr/0002-standalone-hosted-price-source.md).
- **NewsAPI** (`adapter-marketdata-newsapi`): Disabled, $449/mo replaced by free Finnhub News + RSS feeds
- **AlphaVantage** (`adapter-marketdata-alphavantage`): Disabled, returns 0 events for future-dated sims, Alpaca handles backfill

### DDD Anti-Patterns to Avoid
- **Primitive Obsession:** Use value objects (`Symbol`, `Money`, `Quantity`), not primitives
- **Infrastructure Leakage:** Domain entities must NOT have JPA/JSON annotations
- **Anemic Domain Model:** Domain objects must contain business logic
- **Bounded Context Violations:** Use ports/events, not direct service calls
- **Read models per adapter:** derived views (leaderboards, rankings, performance tables) belong
  in one use case that every driving adapter renders. When REST and MCP each assembled the
  challenge leaderboard from repositories, they drifted until the same challenge reported two
  different return percentages — see `BuildChallengeLeaderboardUseCase`.

### Architecture Guardrails (ArchUnit)

`quorumtrading-bootstrap/src/test/java/com/quorumtrading/architecture/` enforces the layering.
Three rules describe where the architecture is going and carry an exhaustive **freeze list** of
the classes that predate them:

| Rule | Freeze list |
|---|---|
| `drivingAdaptersShouldGoThroughUseCasesNotOutputPorts` | REST/MCP/WebSocket classes still injecting `port.out` repositories |
| `applicationLayerShouldNotDependOnSerializationOrRuntimeFrameworks` | application classes still using Jackson / SmallRye / Micrometer types |
| `domainEntitiesShouldNotExposePublicSetters` | entities in `model.entity` that still have `setX(...)` |

Entries are **exact fully-qualified names, never wildcards**, so a new class cannot silently
inherit an exemption — it fails the build until it is fixed or argued onto the list in review.
The lists are meant to shrink. Adding a name needs a reason in the commit message; if you are
adding one to make a build pass, fix the class instead.

`ADAPTER_ROOT_PACKAGES` must list every `quorumtrading-adapter-*` module — a module missing from
it is silently exempt from adapter-isolation checks.

### Testing Constraints
- **Test Database:** Use PostgreSQL `quorumtrading_test`, NOT H2
- **Adapter Isolation:** Adapters use MockProducer, do not depend on other adapters
- **Entity Sequences:** Use `allocationSize=1` to avoid sequence generation errors
- **Test Profile:** Properties must use `%test.` prefix in main `application.properties`

### Quality gates

Four gates run in CI. All of them are ratchets — each is set at or just above the current
measurement, so the only direction is up. **Lowering a threshold to make a build pass defeats the
gate**; fix the code, or argue the number in review.

| Gate | Where | Current setting | Fails when |
|---|---|---|---|
| Aggregate coverage | `scripts/check-backend-coverage.sh` | 60% instruction / 47% branch | whole-backend coverage drops |
| **Changed-line coverage** | `scripts/check-diff-coverage.sh` | backend 80%, frontend 30% | the lines a PR *changed* are undertested |
| **Java smells** | `maven-pmd-plugin`, bound to `verify` | 566 violations frozen | a new PMD violation appears |
| **TS/JS smells** | `eslint-plugin-sonarjs` via `npm run lint` | `--max-warnings 848` | any new warning appears |

The changed-line gate is the one that matters most: an aggregate floor barely moves when a single
untested class lands, which is exactly how coverage decays. Its floors sit **above** each stack's
aggregate (61.9% backend, 5.3% frontend), so every PR that passes drags the total upward.

**PMD baseline.** `quorumtrading-backend/pmd-baseline.properties` is a generated freeze list, the
same device as the exempt lists in `HexagonalArchitectureTest`. Regenerate with
`./scripts/generate-pmd-baseline.sh` — which clears the file and re-runs the analysis itself,
because reading the reports by hand is circular (the `check` goal writes them with the current
baseline already subtracted). Run it after *fixing* violations; entries added to absorb new ones
show up in the diff.

> **SpotBugs and FindSecBugs are not here, and it is not an oversight.** SpotBugs reads bytecode
> and its BCEL dependency cannot parse Java 25 class files (major version 69) — every module
> reports "No classes found to analyze". PMD reads source, so it works. Revisit once BCEL ships
> Java 25 support; until then there is **no taint-analysis security scanning** on the backend.
> Once the repo is public, CodeQL code scanning is free and fills that gap.

### Tag-excluded tests: run them yourself before a backend PR

`./mvnw test`, and therefore CI, skips the tag groups listed in `surefire.excludedGroups` in the
parent pom. They need real model spend or live third-party HTTP, which this repo deliberately has
no secrets for. **CI can never catch a regression in them, and a green PR says nothing about
them.** Nothing enforces this — it is on whoever opens the PR.

Before pushing a PR that changes backend code, run the group(s) your change could plausibly break:

| If you touched | Run | Needs |
|---|---|---|
| chat tools, agent prompts, `ChatToolRegistry`, `prompts/*.md` | `ChatPromptEngineeringTest` (40) | `OPENROUTER_API_KEY` — **costs real money** |
| `adapter-marketdata-reddit` | `RedditAdapterTest` (9) | **local only** — no credentials, but Reddit blocks GitHub runners, so this can never be moved into CI |
| news / RSS / Finnhub market-data paths | `HybridNewsSourcesIntegrationTest` (3) | network only (`FINNHUB_API_KEY` optional) |

```bash
# One class with every group enabled
./mvnw test -pl quorumtrading-bootstrap -am -Dtest=ChatPromptEngineeringTest -Dsurefire.excludedGroups=

# Everything except the one that costs the most
./mvnw test -Dsurefire.excludedGroups=prompt-engineering-expensive
```

Say in the PR description which ones you ran and what happened — including "not run, because …".
Saying nothing reads as "covered", and it is not. The `llm` group is still listed in
`excludedGroups` but currently has no members; it is reserved for tests that genuinely assert
real frontier-model behaviour.

**See:** the [Architecture](#architecture) section above for layer/module rules and [docs/BOUNDED_CONTEXT_CANVAS.md](docs/BOUNDED_CONTEXT_CANVAS.md) for the domain model, and [docs/development/TEST_SUITE_STATUS.md](docs/development/TEST_SUITE_STATUS.md) for what each suite currently covers.

## Configuration

### Key Configuration Files
- **Backend:** `quorumtrading-bootstrap/src/main/resources/application.properties`
- **Frontend:** `quorumtrading-frontend/.env.local` (see `.env.local.example`)
- **Docker:** `docker-compose.yml`

### Trading Modes
- **BACKTEST Mode:** Always uses `MockBrokerAdapter` for historical simulation
- **LIVE Mode:** Uses configured broker (`MockBrokerAdapter`, `AlpacaBrokerAdapter`, or `SaxoBrokerAdapter`)
- **Alpaca:** Multiple concurrent LIVE simulations are allowed
- **Saxo:** experimental/untested — **off by default in every profile** (opt in with `SAXO_ENABLED=true`); stays off on SaaS until broker approval regardless. See [docs/RUN_MODES.md](docs/RUN_MODES.md)

### LLM Routing & Fallback
The `quorumtrading-adapter-llm-router` module (`LLMRouterAdapter`, implements `LLMPort`) routes across providers discovered via CDI, with circuit breakers and per-provider metrics; runtime provider setup lives at `quorumtrading.llm.setup-url`. The LocalLLM adapter's CLI fallback chain is `claude → qwen` with exponential backoff blacklisting (codex/gemini deliberately skipped as too slow — see `quorumtrading.llm.local.tool.fallback`).

**See:** [Broker Configuration](docs/configuration/BROKER_CONFIGURATION.md), [LLM Configuration](docs/configuration/LLM_CONFIGURATION.md)

## Development Guidelines

### Reviewing a design canvas

When a design agent (claude.ai/design) hands off an HTML/CSS canvas with a feature claim, check the claim against [docs/CAPABILITIES.md](docs/CAPABILITIES.md) **before** implementing. The doc lists what works today, what's gated behind a flag, which fields are hedged with `<HedgedValue/>` until the backend ships them, and what's deliberately not built. A canvas that asks to render a backend-gap field needs a backend story first — don't fake numbers in the UI. A canvas that asks for a removed or out-of-scope feature needs the user's sign-off before any code is written.

### Frontend API Development

**CRITICAL:** Before implementing any frontend API integration, verify the API contract from the backend OpenAPI specification at `http://localhost:8085/q/openapi` (YAML by default — add `?format=json` if you are piping it into `jq`). Never assume API structure.

**See:** [Frontend Development Guide](docs/frontend/FRONTEND_DEVELOPMENT.md)

### Frontend UI Development

Before creating new UI components, check the [Frontend Style Guide](quorumtrading-frontend/app/styles/STYLE_GUIDE.md):
- Use `app/components/shared/v2/` primitives (`Panel`, `StatusPill`, `ActionPill`, `KpiStrip`, `AgentCard`, `SimHeader`, `VoteChip`, `HedgedValue`). The older `SectionTitle` / `AnimatedCard` / `PageSection` are legacy and being phased out — check before importing.
- Sans (IBM Plex Sans) for chrome + body; mono (IBM Plex Mono) only for numerics, IDs, timestamps, code.
- No terminal-glow text-shadows on body text; cyan focus ring is the global focus signal.
- Use `<HedgedValue/>` (em-dash + amber Δ-sup) wherever a backend field hasn't shipped yet — see [docs/CAPABILITIES.md § 15](docs/CAPABILITIES.md) for the live list.

### Database

- **Migrations:** `quorumtrading-adapter-persistence/src/main/resources/db/migration/`
- **Cascade Deletes:** Deleting a strategy cascades to all related simulations, decisions, and snapshots

**See:** [Database Schema Guide](docs/database/DATABASE_SCHEMA.md)

## Documentation Index

| Topic | Location |
|-------|----------|
| **Run modes** (SaaS / dev / standalone / test / e2e — profiles, DBs, auth, flags) | [docs/RUN_MODES.md](docs/RUN_MODES.md) |
| Product capabilities (feature inventory + backend gaps) | [docs/CAPABILITIES.md](docs/CAPABILITIES.md) |
| Quick Reference | [docs/development/QUICK_REFERENCE.md](docs/development/QUICK_REFERENCE.md) |
| Common Tasks | [docs/development/COMMON_TASKS.md](docs/development/COMMON_TASKS.md) |
| Bounded Context Canvas (domain model) | [docs/BOUNDED_CONTEXT_CANVAS.md](docs/BOUNDED_CONTEXT_CANVAS.md) |
| Frontend Development | [docs/frontend/FRONTEND_DEVELOPMENT.md](docs/frontend/FRONTEND_DEVELOPMENT.md) |
| Frontend Style Guide | [quorumtrading-frontend/app/styles/STYLE_GUIDE.md](quorumtrading-frontend/app/styles/STYLE_GUIDE.md) |
| Broker Configuration | [docs/configuration/BROKER_CONFIGURATION.md](docs/configuration/BROKER_CONFIGURATION.md) |
| LLM Configuration | [docs/configuration/LLM_CONFIGURATION.md](docs/configuration/LLM_CONFIGURATION.md) |
| Database Schema | [docs/database/DATABASE_SCHEMA.md](docs/database/DATABASE_SCHEMA.md) |
| Email Readiness Checks | [docs/operations/EMAIL_READINESS.md](docs/operations/EMAIL_READINESS.md) |
| Full documentation index (this table is a curated subset) | [docs/README.md](docs/README.md) |

## Deployment

- **Production:** https://quorumtrading.com (frontend), https://api.quorumtrading.com (backend)
- **CI:** GitHub Actions `ci.yml` — build + test only; no deploy, no AWS, no secrets in this repo
- **Deploy & infrastructure:** the private `COLARDYNIT/quorumtrading-infra` repo — owner-triggered
  `workflow_dispatch` deploys (checkout this repo at a pinned ref → ECR → ECS) and terraform apply

## Changelog Blog Posts

**After pushing major features**, ask the user if they want a changelog blog post written.

### How to Write a Blog Post

1. **Create content file** at `quorumtrading-frontend/app/blog/posts/YYYY-MM-DD-slug-name.ts`:
   ```typescript
   export const content = `
   Short intro paragraph.

   ## Section Heading

   - **Bold labels** for key points
   - Use [hyperlinks](/path) to relevant app pages

   ## Another Section

   More content here.

   ---

   *Closing tagline with [link](/path).*
   `;
   ```

2. **Add metadata** to `quorumtrading-frontend/app/blog/posts.ts` (at TOP of array):
   ```typescript
   {
     slug: 'YYYY-MM-DD-slug-name',  // Must match filename
     title: 'Post Title',
     date: 'YYYY-MM-DD',
     summary: 'One-line description for the list view.',
     tags: ['FEATURE', 'AI'],  // e.g., FEATURE, FIX, AI, UI
   },
   ```

3. **Register import** in `quorumtrading-frontend/app/blog/[slug]/page.tsx`:
   ```typescript
   const postContents: Record<string, () => Promise<{ content: string }>> = {
     'YYYY-MM-DD-slug-name': () => import('../posts/YYYY-MM-DD-slug-name'),
     // ... existing posts
   };
   ```

### Styling Guidelines
- Headings render in cyan (`terminalBlue`)
- **Bold text** renders in green (`terminalGreen`)
- Body text is gray (`textMuted`)
- Links work: `[text](/path)` or `[text](https://...)`
- Code uses backticks: \`code\`

## Key Reminders

- **Before touching anything profile/edition/auth/flag related, read [docs/RUN_MODES.md](docs/RUN_MODES.md)** — the definitive SaaS / dev / standalone / test / e2e matrix. Edition (`QT_EDITION`, drives `/api/v1/instance` + frontend index/broker gating) is NOT the same thing as the Quarkus profile (drives flags). Saxo stays off on SaaS until broker approval; dev-login on prod is a deliberate boot failure.
- **Watchdogs pause on evidence — they never fabricate terminal state** — [docs/adr/0004](docs/adr/0004-watchdogs-pause-on-evidence-never-fabricate-completion.md). No wall-clock "older than X ⇒ stuck" reapers: liveness comes from the lock lease + newest persisted decision (DB-side `NOW()`), the only permitted action is a system pause with a named reason, and guards run on observed process time (laptop suspend + Quartz catch-up bursts), never tick counts. The known-broken `EndSimulationService` backtest arm is rebuilt per the reaper/stall-detector plan (maintained in the private infra repo).
- **A day starts at 00:00 UTC** — [docs/adr/0003](docs/adr/0003-a-day-starts-at-midnight-utc.md). Budget days, rate-limit windows, DCA funding days, backtest/LIVE classification and price-bar day lookup all use `LocalDate.now(ZoneOffset.UTC)` or `Instant.truncatedTo(DAYS)`, never `LocalDate.now()` / `ZoneId.systemDefault()`. **Market sessions are the exception and stay exchange-local** (`America/New_York` hours, `USMarketHolidayCalendar`, strategy `live_schedule.timezone`) — converting those to UTC breaks trading. Prod containers run UTC, so a host-zone read looks correct in production and only misbehaves on a developer's machine.
- Testing, DDD and frontend-API rules: see [Testing Constraints](#testing-constraints), [DDD Anti-Patterns](#ddd-anti-patterns-to-avoid), and [Frontend API Development](#frontend-api-development) above
- **Before opening a backend PR, run the tag-excluded tests your change could break** — CI skips them entirely, so a green PR is silent about them. See [Tag-excluded tests](#tag-excluded-tests-run-them-yourself-before-a-backend-pr) for which to run and state the outcome in the PR description.
- CORS origins: update `application.properties` when adding new production domains
- **After major features:** Ask if a changelog blog post is needed
- **Logging context:** All log statements must include sufficient context to be useful in production. For AI consensus in simulation context, always include the simulation ID. For AI consensus in market research context, always include the research ID. For investment-plan execution, include the planId. For per-notification dispatch, include the notificationId. Avoid log statements that are meaningless without additional context.
- **DCA scheduler and email delivery are off by default** in every environment — see the critical feature flags under [Recurring Investment Subsystem](#recurring-investment-subsystem) and [Email Readiness Checks](docs/operations/EMAIL_READINESS.md) before enabling either.
