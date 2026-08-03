# ADR-0003: A day starts at 00:00 UTC

**Status:** Accepted — implemented
**Date:** 2026-08-01
**Context:** "Today" was derived two different ways across the codebase — UTC in some places, the host's timezone in others

## Decision

Wherever the product needs to know **which day it is**, the day runs from `00:00:00Z` to
`23:59:59.999…Z`. Derive it with `LocalDate.now(ZoneOffset.UTC)`, `Instant.truncatedTo(DAYS)`, or by
passing `ZoneOffset.UTC` explicitly. Never `LocalDate.now()`, `ZoneId.systemDefault()`, or
`new Date()`.

This is a rule about **calendar-day boundaries**, not about market sessions. See
[Scope](#scope-what-is-and-is-not-a-day-boundary) — reading it too broadly would break trading.

## Context

Two definitions of "a day" coexisted, and nothing pointed that out:

| Already UTC | Used the host's timezone |
|---|---|
| `RedisFreeModelRateLimiter` (rate-limit day) | `RecordLLMUsageUseCase` (daily spend limit) |
| `PlanFundingDay` (DCA funding day) | `GetUserBudgetUseCase`, `GetLLMUsageStatisticsUseCase` |
| `ClassifyGapsService`, `DataHorizonProbeService` | `Simulation.start()` (LIVE vs backtest classification) |
| | `Simulation`/`Benchmark` backtest-window validation |

So a user's **daily spend limit reset at a different instant than their rate-limit window**, for no
reason anyone had decided — the two were simply written months apart by different hands.

**Production never noticed, and that was the problem.** ECS containers run UTC, so
`ZoneId.systemDefault()` *is* UTC there and both definitions coincided. The divergence was invisible
exactly where it mattered and only appeared on developer machines, which is the worst possible
distribution: no production symptom to investigate, and local behaviour that differs from the
deployed system in ways that look like flaky tests rather than a design gap.

Two concrete failures came out of it before this rule was written down:

- **A backtest ending today could not be created, one day a year.** `Simulation` and `Benchmark`
  each derived tomorrow's midnight as `todayMidnight.plus(1, DAYS)` — a fixed 24 hours. A local day
  is not always 24 hours. On the DST fall-back day it is 25, so the boundary landed at 23:00 today
  and a window ending in the final hour was rejected as reaching into the future. In
  `Europe/Brussels` on 2026-10-25, `todayMidnight + 24h` is `22:00Z` while `23:59:59` local is
  `22:59:59Z`. UTC has no transitions, so CI could never have caught it.
- **`ContributionTest` was flaky around local midnight**, because the test built a LIVE simulation
  from a UTC-truncated instant while `Simulation.start()` classified it against the host zone's
  midnight. It was patched by making the *test* match the host zone — treating the symptom, and
  leaving the two definitions in place.

## Why UTC

- **It is the only boundary every subsystem can agree on without asking anyone a question.** Budget
  days, rate-limit windows, DCA funding days, and simulation classification are all
  server-side accounting. Making them agree matters more than matching any particular wall clock.
- **It is already what the storage layer means.** Daily price bars are stamped at UTC midnight, and
  `ConsensusContextBuilder.checkMarketClosed` deliberately queries the UTC calendar day for exactly
  that reason (a previous attempt to use the exchange-local day made every Friday and every
  pre-holiday session report "market closed").
- **It removes DST from the arithmetic entirely.** UTC has no transitions, so "tomorrow's midnight"
  is unambiguously 24 hours later and the class of bug above cannot recur.
- **It makes tests deterministic.** A domain test that pins an instant now asserts the same thing in
  every timezone and at every hour, which is enforceable (see [Enforcement](#enforcement)).

## Scope: what is and is not a day boundary

**Governed by this ADR** — anything answering *"which calendar day is it?"*:

- Budget and spend accounting (daily limits, usage statistics, monthly rollover)
- Rate-limit windows
- DCA funding days (`PlanFundingDay`)
- Backtest/LIVE classification and backtest-window validation
- Market-data gap classification and price-bar day lookup

**Not governed by this ADR** — these keep their own zone deliberately:

- **Market sessions and trading hours.** An exchange opens at 09:30 `America/New_York`, and that is
  not negotiable by us. `AlpacaBrokerAdapter`'s market-hours check, `USMarketHolidayCalendar`, and
  the `timezone` field in a strategy's `live_schedule` / `backtest_schedule` frontmatter all stay
  exchange-local. **Converting these to UTC would be a bug, not consistency** — it would move every
  strategy's evaluation time by hours and break trading twice a year.
- **Display and labelling.** `MarketResearchOrchestrator` stamps a `"MMM yyyy"` label onto generated
  strategy names using the host zone. That is a cosmetic label, not a boundary any logic keys on. It
  is left as-is; if it ever becomes user-visible in a way that matters, it should follow the user's
  zone, not UTC.

The distinction is: **accounting is UTC, markets are local, labels are cosmetic.**

## Consequences

- **No production behaviour change.** Containers run UTC, so every changed site already behaved this
  way in prod. This makes the intent explicit and stops it depending on deployment configuration.
- **Developer machines now match production.** The domain suites pass identically under
  `America/Los_Angeles` (UTC−7), `UTC`, and `Pacific/Kiritimati` (UTC+14) — verified, not assumed.
- **A user's day is not their local day.** Someone in Brussels sees their daily spend limit reset at
  02:00 local (01:00 in winter); someone in Los Angeles at 17:00 the previous afternoon. This is a
  real, accepted UX cost — see the alternative below. If it ever becomes a support burden, the fix is
  a per-user timezone, which is a product feature, not a bug fix.
- **`ZoneId.systemDefault()` in backend main sources is now a smell.** The only remaining use is the
  cosmetic label above.

## Alternatives considered

- **Per-user timezone.** The friendliest option for billing UX — a user's day is their day. Rejected
  for now because it requires storing and maintaining a zone per user, makes the shared rate limiter
  per-user-zone, and leaves cross-user aggregates ragged (no single instant at which "today"
  finishes for everyone). Worth revisiting as a product feature; it is not the default.
- **The host's timezone** (the status quo for half the sites). Rejected: it makes behaviour depend on
  where the process happens to run, which is how the divergence went unnoticed in the first place. It
  also drags DST into every day-boundary computation.
- **Exchange timezone (`America/New_York`).** Correct for market sessions and already used there.
  Rejected as the *global* rule: it is arbitrary for billing, and it would make a European
  self-hoster's spend limit reset on New York's calendar.

## Implementation

Nine sites moved to UTC:

- `RecordLLMUsageUseCase`, `GetUserBudgetUseCase`, `GetLLMUsageStatisticsUseCase`
- `Simulation.validateBacktestDates`, `Simulation.start()`, `Benchmark.validateBacktestDates`
- `AlpacaDividendInfoAdapter`, `BackfillJobMapper` (×2)

The duplicated backtest-window rule was extracted to `BacktestWindow`, which takes `now` and `zone`
as **parameters** rather than reading the clock itself. Every caller passes `ZoneOffset.UTC`; the
zone stays a parameter because that is what makes the DST arithmetic testable at all — a calendar
rule exercisable only on the day the suite happens to run is a rule nobody can test, which is how
the fall-back bug survived. `BacktestWindowTest` pins fixed instants on both transition days.

## Enforcement

`DomainTestClockTest` (`quorumtrading-bootstrap`, `com.quorumtrading.architecture`) freezes the
per-file count of wall-clock reads across domain test sources and fails on a new file or a grown
count. The domain already takes the instant as a parameter — `InvestmentPlan.create(…, now)`,
`isDueAt(now)`, `BacktestWindow.extendsIntoTheFuture(…, now, zone)` — so a test reading
`Instant.now()` is discarding that design and coupling itself to the day it runs. The frozen counts
are a ceiling meant to fall.

This does not yet cover application or adapter tests, which have legitimate reasons to observe real
time (schedulers, rate limiters, live integration tests). Extending it is a judgement call, not an
oversight.
