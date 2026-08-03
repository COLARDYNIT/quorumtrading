# Run Modes — the definitive reference

QuorumTrading runs in four operational modes plus an e2e variant. **Profile** (Quarkus) selects the
flag set; **edition** (`quorumtrading.edition`) is a *descriptor* exposed via anonymous
`GET /api/v1/instance` that tells the frontend what to render — it changes no backend behavior by
itself. Mixing those two concepts up is the #1 mistake this document exists to prevent.

## The matrix

| | **SaaS (prod)** | **Dev** | **Standalone (self-host)** | **Test** | **E2E** |
|---|---|---|---|---|---|
| Activation | default profile (deployed) | `./mvnw quarkus:dev` | `-Dquarkus.profile=local` or `docker-compose.local.yml` | `%test`, auto via `@QuarkusTest` | `-Dquarkus.profile=e2e` (Playwright backend) |
| Backend port | 8080 | **8085** | **8085** | random/test | 8085 |
| Database | `quorumtrading` (RDS in prod) | `quorumtrading` @ 55432 | `quorumtrading_local` @ 55432 (native) / container pg (compose) | **`quorumtrading_test`** @ 55432 — never H2 | `quorumtrading_test` |
| Edition → index page | `saas` → marketing hero | `saas` → marketing hero | **`standalone` → LocalHome** | `saas` (override per-test) | inherited |
| Auth | **OIDC only** (Google, GitLab) | dev-login | dev-login | dev-login | dev-login |
| User identity | real OAuth users | `local@quorumtrading.local` | `local@quorumtrading.local` | same (+ arbitrary via cookie) | same / `fut-…@` |
| LLM | OpenRouter | OpenRouter (local CLI opt-in via env) | **runtime choice**: local CLI ⇄ OpenRouter (selection flag ON) | `test/model` stubs | DummyExecutor |
| LLM selection endpoints (`/api/v1/llm/*`) | 404 (flag off) | 404 (flag off, env opt-in) | **enabled** | off (TestProfile opt-in) | off |
| Brokers offered (`/instance.brokers`) | `ALPACA` only | `ALPACA` | `ALPACA` (Saxo experimental, **off** by default; opt in with `SAXO_ENABLED=true`) | `ALPACA` (Saxo off, stub creds) | `ALPACA` (trades route to mock via `%e2e.quorumtrading.broker.default`) |
| MCP auto-provision (token + owner) | off | off | **on in compose** (`LOCAL_AUTO_PROVISION=true`) | off | off |
| Schedulers (quartz) | on, JDBC-clustered | on | on | **off**, RAM store | — |
| DCA scheduler / email | off by default (flags) | off | off | off | off |
| Frontend | quorumtrading.com | :3000 (`npm run dev`) | **:3000** — compose boots the UI by default (`QT_FRONTEND_ENABLED=true`); headless via `up backend` (`QT_FRONTEND_ENABLED=false`); native dev: :3001 | n/a | :3000 |
| Log file | `/tmp/quorumtrading.log` | `/tmp/quorumtrading-dev.log` | `/tmp/quorumtrading-local.log` | `/tmp/quorumtrading.log` (bootstrap) / console (module tests) | `/tmp/quorumtrading.log` |

Source of truth: `quorumtrading-backend/quorumtrading-bootstrap/src/main/resources/application.properties`
(profile-keyed `%dev.` / `%local.` / `%test.` / `%e2e.` entries).

## Per-mode notes

### SaaS (prod, default profile)
- **`SecurityStartupValidator` hard-fails boot** if `quorumtrading.auth.dev-login-enabled=true` in
  the prod profile. No override exists — by design.
- EU broker (Saxo) must stay **off** on SaaS: offering Saxo in a hosted product requires the
  broker's explicit approval (months/years). Standalone-first is the rollout strategy. The
  frontend hides the EU option automatically because `/instance.brokers` won't contain `SAXO`.
- `quarkus.mailer.mock=false` only here; email + DCA scheduler remain feature-flagged off until
  the go-live checklists pass (see CLAUDE.md).

### Dev
- Identical shape to SaaS (hero index, OpenRouter) **plus** the dev-login bypass so no OAuth dance.
  This is for developing QT itself.
- LLM runtime-selection and the local CLI adapter are present but **off**; opt in with
  `QT_LLM_SELECTION_ENABLED=true QT_LLM_LOCAL_ENABLED=true` if you need them in dev.
- Dev and standalone both bind 8085 — you can only run one at a time.

### Standalone (self-host, `%local`)
- The product for end users running QT on their own machine. Single fixed user, no OAuth,
  runtime LLM engine choice (`/api/v1/llm/setup`), US broker (Alpaca); EU (Saxo) is opt-in and
  experimental. UI and MCP are equal peers.
- Two ways to run: `docker compose -f docker-compose.local.yml up` boots the full stack — UI (:3000)
  + backend (:8085, REST + MCP) — auto-provisions the MCP token and prints the boot banner; add
  `backend` to the command (`up backend`) for a headless, agent-only run. Or native
  `./mvnw quarkus:dev -Dquarkus.profile=local` (+ frontend on 3001). See
  [LOCAL_SETUP.md](../LOCAL_SETUP.md).
- The LLM selection endpoints are **anonymous by design** here — acceptable only because the
  anonymous dev-login already grants full access on localhost. That trade-off must never travel to
  SaaS (the master flag keeps the endpoints 404 there).

### Test (`%test`, every `@QuarkusTest`)
- **Real PostgreSQL `quorumtrading_test` on 55432** (docker compose postgres must be up; create the
  DB once per the README). Flyway runs `clean` + `migrate` at start — migrations are exercised on
  every suite run.
- **H2 is on the test classpath but must never be used as a datasource.** If you see an H2 URL in
  test config, that's a bug.
- Redis and Keycloak are provided automatically by **Quarkus Dev Services** (testcontainers, random
  ports) during *adapter-module* tests; bootstrap tests use the compose redis on 6379
  (`%test.quarkus.redis.hosts`) — which is why mistake #4 below says to start redis too. Only
  postgres is always explicitly configured.
- Adapter modules are isolated: missing CDI beans are satisfied by each module's own
  `MockProducer`/`MockUseCaseProducer` (adapter-rest, adapter-mcp, adapter-persistence,
  adapter-llm-router, adapter-websocket). A new port usually means a new mock there.
- Bootstrap integration tests **run by default** with `./mvnw test`. Only `@Tag`ged classes
  (`llm`, `prompt-engineering`, `prompt-engineering-expensive`, `reddit`, `external`) are excluded, via
  `surefire.excludedGroups` in the parent pom — include them with `-Dsurefire.excludedGroups=""`
  (LLM ones need `OPENROUTER_API_KEY`).
- Per-class config overrides use `@TestProfile(SomeQuarkusTestProfile.class)` — e.g.
  `InstanceResourceStandaloneTest` flips edition/Saxo without touching global test config.

### E2E (`%e2e`, Playwright)
- Backend started with profile `e2e`: dev-login on, **DummyExecutor** stubs all AI responses,
  mock broker only. Frontend on :3000; auth state captured once via `e2e/auth.setup.ts`
  (navigates to `/api/v1/auth/dev-login`).
- The dummy LLM is reachable because `%e2e` sets `quorumtrading.llm.local.enabled=true`
  (router discovers `local-llm` with `default-tool=dummy`). Agent-decision prompts get a
  deterministic JSON answer: **BUY when flat, HOLD once holding** — one trade per backtest.
- Flyway runs `clean-at-start`; after migrations, `E2eMarketDataSeeder` (bootstrap, gated on
  `quorumtrading.e2e.seed-market-data.enabled`) seeds 16 days of BTC price bars + news, so
  backtests start instantly without any provider backfill. The core-loop spec
  (`e2e/core-loop.spec.ts`, project `core-loop`) exercises login → strategy → backtest →
  results end-to-end and runs in CI without secrets (`e2e-core-loop` job).

### Smoke (`%smoke`, native integration tests)

A sixth profile, deliberately left out of the matrix above because nobody runs the product in it —
it exists for exactly one caller.

- **Who activates it:** the `*IT` classes in `quorumtrading-bootstrap/src/test`. They are
  `@QuarkusIntegrationTest`, so unlike every other test here they do **not** boot Quarkus in the
  test JVM — they launch the *packaged artifact* as a separate process. Under `-Pnative` that is
  the native binary production runs, which is the point: a missing `@RegisterForReflection` is
  invisible on the JVM and fatal in the image.
- **How the profile gets set:** `quarkus.test.integration-test-profile=smoke` on
  maven-failsafe-plugin (bootstrap pom); the launcher turns it into `-Dquarkus.profile=smoke`. The
  artifact is **built** with prod config and only **launched** with this one, so build-time config
  stays production's — the binary tested is the binary shipped.
- **Why not just launch it as prod:** prod is intentionally un-bootable on a CI runner and must
  stay so. `%prod` demands `UNSUBSCRIBE_SECRET` / `BROKER_CREDENTIALS_ENCRYPTION_KEY` /
  `OIDC_SESSION_ENCRYPTION_SECRET` with no fallback, and `SecurityStartupValidator` hard-fails on
  dev-login under prod — which the smoke tests need in order to authenticate at all. A separate
  profile softens neither.
- **What it changes:** postgres `quorumtrading_test` @ 55432, dev-login on, OIDC tenants off,
  background schedulers and market-data collection off. Nothing else. Every line in the `%smoke`
  block is a way the smoke run differs from production, so the block is kept short on purpose.
- **Where it runs:** the production deploy in the private `quorumtrading-infra` repo, after the
  native image is built and before it is pushed to ECR. `./mvnw verify` runs the same tests
  locally against the packaged fast-jar; the app repo's CI passes `-DskipITs` and skips them, since
  it never packages an artifact.

## Auth in one table

| Mechanism | prod | dev | local | test | e2e |
|---|---|---|---|---|---|
| OIDC (Google/GitLab) | ✅ | — | — | — | — |
| `GET /auth/dev-login` → `dev_session` cookie (fixed user **Local**) | ⛔ boot-fails | ✅ | ✅ | ✅ | ✅ |
| `GET /auth/fut-login` (fresh throwaway user per call) | ⛔ | ✅ | ✅ | ✅ | ✅ |
| MCP `Bearer qt_…` token | ✅ | ✅ | ✅ | ✅ | ✅ |
| Test shortcut: `.cookie("dev_session", email)` / `@TestSecurity(user=email)` | — | — | — | ✅ | — |

The fixed identity is `local@quorumtrading.local` / "Local" / sub `dev-login-local-sub`
(renamed from `e2e-test@…` by migration V121; users are keyed by **email only**).

## Common mistakes this doc exists to prevent

1. **Edition ≠ profile.** `QT_EDITION` drives what `/instance` reports — and therefore the
   frontend's index page, broker gating, and the AI assistant. Everything else comes from the
   individual flags; `%local` happens to default both sensibly.
   - **Market research is a runtime flag, not an edition boundary.** The `market-research` flag is
     evaluated through `EvaluateFeatureUseCase` on every surface: REST `/api/v1/market-research/**`
     aborts **403** naming the flag (`FeatureFlagGateFilter`), the seven MCP research tools refuse
     and are hidden from `tools/list`, the sidebar entry disappears, and `MarketResearchScheduler`
     idles. `/research` itself stays reachable by direct URL — the frontend hides nav only, by
     design. It ships **off under the `%local` profile** (note: profile, not edition — see
     `%local.quorumtrading.features.market-research.default=false`) and on elsewhere, but that is a
     first-boot seed and a fallback, not a wall: an administrator switches it on under Admin →
     Feature flags or with `PUT /api/v1/admin/feature-flags/market-research`. On a self-host
     `LocalBootstrapProvisioner` promotes the local owner to `ADMIN`, so that person *is* the
     administrator. See [CAPABILITIES.md § 7](CAPABILITIES.md).
2. **Never enable dev-login or LLM-selection endpoints on SaaS.** One is a boot failure, the other
   is an account-takeover hole on a multi-user deployment.
3. **Never enable `SAXO_ENABLED` on SaaS** until the broker's hosted-integration approval exists.
4. **Tests run against `quorumtrading_test` on real postgres** — not H2, not the dev DB. If tests
   "can't connect", start `docker compose up -d postgres redis` first.
5. **Dev and standalone share port 8085** — a confusing 404/stale-UI usually means you're talking
   to the other one (or to the main-worktree frontend on :3000 vs the EU one on :3001).
6. **Flag defaults are OFF in every mode** for: DCA scheduler and email delivery (everywhere); the
   Saxo broker + market-data adapters (everywhere, **including `%local`** — experimental, opt in with
   `SAXO_ENABLED=true`). The LLM local adapter is the one that flips on in `%local` (and only there).
   Promotion order is documented in CLAUDE.md's go-live checklists.

## Related docs

- [LOCAL_SETUP.md](../LOCAL_SETUP.md) — running the standalone edition
- [CLAUDE.md § Architecture](../CLAUDE.md#architecture) — module/test structure
- [docs/configuration/BROKER_CONFIGURATION.md](configuration/BROKER_CONFIGURATION.md), [docs/configuration/LLM_CONFIGURATION.md](configuration/LLM_CONFIGURATION.md)
