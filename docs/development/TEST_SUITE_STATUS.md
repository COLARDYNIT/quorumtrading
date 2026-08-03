# Test Suite Status

Where the automated suites stand and the traps that cost time getting here. Written to be picked
up cold in a fresh session.

Last updated: 2026-07-27 · branch `fix/qa-backlog-verified-defects`

---

## Current state

Everything is green and everything gates.

| Suite | Command | State |
|---|---|---|
| Backend default | `./mvnw test` (from `quorumtrading-backend/`) | ✅ green |
| Frontend unit | `npm test` | ✅ green (209) |
| Frontend type-check | `npx tsc --noEmit` | ✅ clean |
| Playwright — public/landing | `npx playwright test --project=public` | ✅ green, **gating** |
| Playwright — everything else (incl. `core-loop`) | `npx playwright test` | ✅ green (42), **gating** |

**No test is skipped in either suite.** Keep it that way — the skips that were here are the reason
two of the defects below survived. If a test cannot run, it is not covering anything; delete it or
make it run.
| Backend `@Tag("prompt-engineering")` | excluded by default | ✅ green (40) — costs real OpenRouter money |
| Backend `@Tag("reddit")` / `@Tag("external")` | excluded by default | ✅ green (9 / 3 run, 2 self-skipped) |

The `llm` group is still in `surefire.excludedGroups` but **no test carries `@Tag("llm")` any
more** — the two classes that did were misdiagnosed (see below) and now run for free on
`DummyLlmTestProfile`. Keep the exclusion: the tag is reserved for tests that genuinely assert
real frontier-model behaviour.

Running the full Playwright suite needs the backend on the **`%e2e` profile**, not `dev`:

```bash
cd quorumtrading-backend && ./mvnw quarkus:dev -Dquarkus.profile=e2e
cd quorumtrading-frontend && npx playwright test
```

To include the normally-excluded backend groups:

```bash
./mvnw test -Dsurefire.excludedGroups=prompt-engineering-expensive   # everything except the expensive one
./mvnw test -Dsurefire.excludedGroups=                                # literally everything
```

---

## The defect the e2e was hiding

`core-loop` polls for a backtest to reach `COMPLETED` and never got there. It was assumed to be
functional/timing noise. It was a real bug in `ConsensusContextBuilder.validateMarketDataAvailability`:

A backtest's **first evaluation runs at its own start date**, so the pre-flight window
`[startDate, analysisTimestamp]` was **zero-width** on that tick and matched only a price bar
sitting on that exact instant. A run starting at 14:00 UTC against 4-hourly bars (12:00, 16:00, …)
therefore reported "no price data found for any tickers" — with 66 bars in the table and
`BackfillOrchestratorService` having just validated them. That skipped evaluation #0, and at 1
skip out of 1 (a 100% gap rate) the `DataGapCircuitBreaker` tripped and paused the simulation
before it ever traded. Every retry then resumed and stopped again on the pause flag.

Daily bars stamped at UTC midnight plus midnight start dates is what kept this out of sight: those
coincide by luck. Any intraday start instant hits it.

Fix: the pre-flight window's lower bound is widened to at least `PREFLIGHT_MIN_LOOKBACK` (7 days)
before the analysis timestamp, never narrowing an already-wider window. Per-day precision for
stocks is `checkMarketClosed`'s job, not this coarse check's. Guarded by
`ConsensusContextBuilderMarketDataPreflightTest`.

Two smaller faults found alongside it:

- `ReachConsensusService` caught `Exception` around the pipeline `get(timeout)` and logged **every**
  failure as `"Some ticker pipelines timed out after 45s"`. A pipeline that threw in 40 ms reported
  as a 45-second timeout, sending diagnosis after diagnosis at latency. Now `TimeoutException`,
  `InterruptedException` and `ExecutionException` are distinguished and the cause is logged.
- The `core-loop` completion test polled with `toPass({ timeout: 90_000 })` inside Playwright's
  default **30 s** per-test timeout, so it was killed a third of the way through. Any conclusion
  drawn from "it polled 90 s and never completed" was reading a truncated run. Now
  `test.setTimeout(150_000)`.

## The skipped tests — resolved

Six tests were skipped. None had a good reason, and both groups were hiding something.

**`SimulationDataContext` — 5 tests, `describe.skip`.** Skipped as "requires complex callback
mocking", with **empty bodies** — so even unskipped they asserted nothing. `useStreamMessage` is
already mocked at module level; recording the handler it registers per message type is about ten
lines, and the real handlers then run directly. They now cover CONSENSUS_STARTED, LLM_DECISION
(analyst vs. orchestrator routing), TRADE_EXECUTION (HOLD is excluded from the trade feed),
PORTFOLIO_UPDATE (holding rows, change maths, no divide-by-zero) and ERROR (only
`CONSENSUS_FAILED` clears the banner). Verified non-vacuous by mutating both branch conditions in
the source and confirming the tests fail.

Writing them immediately surfaced that the file's default `useConsensusDecisions` mock had no
`refetch`, hidden by an `as any` cast. Every handler that refreshes the timeline would have thrown
`refetchConsensusHistory is not a function` — nothing had ever exercised them.

Also fixed: the `TimeNavigationContext` mock factory returned a **new** `jest.fn()` on every
render, so nothing the context did to `setActiveConsensus` was observable.

**`backtest.spec.ts` — 1 test, conditional `test.skip()`** when the benchmark list was empty. The
e2e database is cleaned before every run, so it was empty unless another project happened to have
created something first: the test either skipped silently or passed on the luck of the parallel
schedule, and never once asserted on a benchmark detail page. It now creates its own benchmark
through the REST API. Note it needs **two** strategies — the create endpoint returns a null
`benchmarkId` for a single strategy, which produces a standalone simulation instead (the path
`core-loop` covers).

## `ReachConsensusIntegrationTest` — resolved

`@Tag("llm")` was a misdiagnosis: these are wiring assertions and never needed a model. The
symptom — zero agent decisions — had **five** unrelated causes stacked behind it, each only
visible once the previous was cleared.

| # | Cause | Fix |
|---|---|---|
| 1 | Fixture built a `Strategy` around a `UserId` never inserted → FK violation on `orchestrator_strategies_owner_id_fkey` in `setUp` | insert the owner |
| 2 | A fresh `Simulation` is `PENDING`; `ReachConsensusService` skips anything not `SIMULATING`/`LIVE`, so the use case returned in ~34 ms having done nothing | `testSimulation.start()` |
| 3 | Fixture used the dead `## Consensus Method` strategy format; the parser moved to YAML frontmatter | use `IntegrationTestHelper.VALID_CRYPTO_STRATEGY` |
| 4 | Strategy declares **BTC** but the fixture stamped its market events `TST`, so the pre-flight found no data and recorded a skip | seed the events under `BTC` |
| 5 | Only 1 news event seeded; `SentimentAgent` needs 3 and `FundamentalAgent` 2 or they abstain silently. Market events are keyed by ticker, not by simulation, so they accumulated across methods and **how many agents voted depended on test order** | seed 3 news events per fixture |

The assertions also demanded ≥5 decisions on the theory of "4 agents + 1 orchestrator". `AgentRole`
defines three analyst roles, so 5 was never reachable — a full panel is exactly 4.

## `StrategyLLMModelOverrideIntegrationTest` — resolved

Same family, plus one of its own: the tests were `@Transactional` and built their fixtures inline,
so the simulation row was **still uncommitted** when the consensus pipeline's executor threads —
running in their own transactions — tried to write `llm_decisions`. Every round died on
`llm_decisions_simulation_id_fkey`. The main thread's pre-flight passed because it read inside the
test's own transaction, which is exactly why this looked like an agent problem. Fixtures now commit
through `QuarkusTransaction.requiringNew()` before consensus runs.

---

## What was fixed earlier, and the rot mechanism

19 of 20 Playwright failures. The cause was never cost — `%e2e` uses a dummy LLM and needs no
secrets. It was that **five projects never ran in CI and the two that did carried
`continue-on-error: true`**, so they could not fail the build. Four unrelated regressions had
accumulated:

- A first-run welcome modal (`TerminalModal`) covering the layout and swallowing clicks.
  Gated on `localStorage`; dismissed once in `auth.setup.ts`, which `storageState` then carries
  to every project.
- The nav was restructured — "Sessions" no longer exists (it is SIMULATIONS), "Dashboard" became
  "Overview", sub-links lost their `|_` prefix, and collapsed sections overlap their own
  sub-links so clicks time out on elements that report as visible.
- Headings moved to sentence case and changed level.
- **Rate limiting was enabled in `%e2e`** (only `%test` disabled it). Parallel projects burst past
  200 req/min; the resulting 429 modal blocked clicks. This produced failures that *rotated
  between runs* rather than reproducing — now disabled for `%e2e`.

Guards added so this class of rot cannot silently return: `data-testid` regions
(`sidebar-desktop` / `sidebar-mobile` / `main-content`) plus `e2e/regions.ts`, so a spec
addresses a region rather than the whole page; every e2e job gates, `core-loop` included; and the
full-stack job runs **`npx playwright test` unfiltered** rather than naming projects, so a project
added to `playwright.config.ts` is covered the day it lands. Enumerating them is what let five go
unrun in the first place.

---

## Traps that cost time

- **A non-gating step hides a product bug, not just a test bug.** `core-loop` carried
  `continue-on-error` for being "known failing"; what it was actually reporting was that no
  backtest could survive its own first evaluation.
- **A skipped test is worse than a missing one** — it reads as coverage on the dashboard. Both
  skip groups here were concealing defects (a mock missing `refetch`; a benchmark detail page that
  had never been opened by a test). "Requires complex mocking" was worth ten lines.
- **A conditional `test.skip()` on data that a clean database never has** never runs. If it also
  races another project that might create that data, it is worse than dead — it is
  nondeterministic.
- **`as any` on a mock return value disables the one check that would catch a missing field.**
- **Exception handlers that name a single cause lie.** `catch (Exception e)` under a message about
  timeouts turned a fast-failing FK violation into a 45-second timeout report.
- **A Playwright `toPass` timeout above the per-test timeout is silently truncated.** Check
  `test.setTimeout` before believing how long a poll actually ran.
- **`-pl <module>` without `-am` resolves stale jars** from `~/.m2`, producing bogus
  `cannot find symbol` and JUnit "could not load class" errors. Always pass `-am`.
- **Playwright projects other than `public` need the backend on `%e2e`.** Running them against
  `dev` hits rate limiting and pollutes the dev database (`%e2e` uses `quorumtrading_test` with
  `flyway.clean-at-start`). For the same reason, never run `./mvnw test` while an `%e2e` backend
  is up — they share that database.
- **`@TestProfile` can silently fail to apply** if inserted by a text edit that does not match.
  Verify with `grep` before drawing conclusions from the run — a wrong conclusion here is what
  produced the "the dummy provider doesn't work" dead end.
- **Work committed inside a `@Transactional` test is invisible to worker threads.** Anything the
  code under test hands to an executor runs in its own transaction and will hit FK violations
  against your uncommitted fixtures. Commit them with `QuarkusTransaction.requiringNew()`.
- **`CurrentUserProvider.getCurrentUserId()` resolves via `ResolveCurrentUserUseCase`**, not
  `UserRepository`. Mocking only the repository yields a **null** userId that mimics a
  production fail-open. Also, mocking `CurrentUserProvider` does not get past `@Authenticated` —
  that is enforced against the real `SecurityIdentity`, so add `@TestSecurity` or a
  `dev_session` cookie.
- **The wrapping exception hides the cause.** Read the surefire **XML**, not the `.txt`.
- **`Simulation.isBacktest()` is only `endDate != null`** — a paper/live run with an end date
  reports `true`. Never use it as a proxy for "no real money".
- **Market events are keyed by ticker, not by simulation.** They survive across test methods in a
  shared database, so a fixture that seeds "one more" event each time makes agent participation
  depend on execution order.

---

## Not started (from the test-rot plan)

- **Model-ID drift check** — `https://openrouter.ai/api/v1/models` returns **200 with no API
  key** (verified). A gating test asserting every model id in `application.properties` still
  exists would cost nothing, need no secret, and catch OpenRouter retiring a slug — a silent rot
  source no amount of running your own tests will find.
- **Recorded-fixture parser tests** — capture real messy model output into golden files and
  replay against `AgentResponseParser` / `ConsensusActionParser`, exercising the fallback paths a
  canned dummy never reaches.
- **Cheap OpenRouter inference canary** — decided to keep out of CI to preserve
  "no secrets in this repo"; run locally or pre-release.
- **Ollama** — considered and declined. It would not have caught any of these failures, and CPU
  inference per consensus round is too slow to gate on.
