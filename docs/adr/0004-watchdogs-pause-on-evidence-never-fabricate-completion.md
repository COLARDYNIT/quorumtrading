# ADR-0004: Watchdogs pause on evidence — they never fabricate terminal state

**Status:** Accepted — implementation planned (reaper/stall-detector plan, maintained in the private infra repo)
**Date:** 2026-08-02
**Context:** A wall-clock "stuck simulation" reaper silently truncated a healthy backtest and reported it as a clean 100% COMPLETED

## Decision

Any component that acts on a run it does not itself execute (a watchdog, reaper, janitor,
recovery job) is bound by four rules:

1. **Liveness is evidence, never age.** "Stuck" must be established from execution evidence —
   a stale or absent lock lease, no new persisted decision — never from wall-clock elapsed time
   since some start marker. Elapsed time measures how long something has existed, not whether it
   is alive. All freshness comparisons happen **DB-side against `NOW()`** (the `locked_at` column
   is `timestamp without time zone`; a Java-side comparison inherits the host-zone hazard of
   [ADR-0003](0003-a-day-starts-at-midnight-utc.md)).
2. **The only permitted transition is an honest, recoverable one:** a system pause with a named
   reason (`updatePauseState(..., true, "REASON")` — the vocabulary `DATA_GAP_DETECTED`,
   `LLM_FAILURE_DETECTED`, `EXECUTION_STALLED` …). A watchdog never writes `COMPLETED` or
   `FAILED`, never sets `progress`, never releases broker capital, never publishes completion
   events. Fabricating a terminal state converts an operational hiccup into a wrong number that
   flows into leaderboards, benchmarks and strategy statistics as an authoritative result.
3. **A watchdog may not count time it was not awake for.** Standalone hosts are laptops: on wake
   from a suspend, *every* wall-clock signal has aged together while the run is healthy and about
   to resume, and the Quartz misfire policy in this repo
   (`simple-trigger-type-override=ignore-misfire-policy`) replays every missed firing in a
   back-to-back burst. Guards are therefore measured in **observed process time** — act only
   after `stall-grace` of own uptime, re-arm after any observation gap — and **never in tick
   counts**, which a catch-up burst satisfies in milliseconds.
4. **Lifecycle columns have one writer.** The generic `save()` must not copy
   `status`/`completedAt`/`failureReason` from a long-lived in-memory entity; state transitions go
   through narrow, compare-and-set style repository methods. Without this, any out-of-band
   transition is silently reverted by the next routine save — `@Version` does not protect a
   save() that re-reads the row in its own transaction.

Legitimate terminal transitions remain exactly two: the **executor's own** completion when its
loop genuinely finishes, and a **user-commanded** force-end — which uses
`Simulation.forceComplete()`, deliberately *not* stamping `progress=100`, and says so.
The endDate-based completion of `LIVE` simulations is the executor-adjacent case that stays: a
LIVE run past its endDate has genuinely finished its period.

## Context

`EndSimulationService` force-completed any `SIMULATING` backtest older than
`startedAt + 1 hour`, every 5 minutes, forever — no reading of the lock lease, recent decisions,
progress, or even the `paused` flags. Each firing wrote `COMPLETED` + `progress=100`, released the
broker capital reservation, and published a `SimulationCompletedEvent`. The comment justifying the
heuristic — "most backtests complete within minutes" — was written for hosted OpenRouter latencies
the week before the platform grew local-engine support where a single call takes minutes.

What made it pathological rather than merely aggressive was the interaction with two other
defects, all three confirmed live in one incident (2026-08-01, sims `17e8b2be` and `722ba6e9`):

- **Whether the kill *stuck* was decided by a race, not a rule.** The executor's per-evaluation
  `save()` copied `status`/`completedAt` from its hours-old in-memory entity, silently
  resurrecting the row. A healthy run was killed and revived **nine times in 45 minutes** —
  leaking a $100,000 capital release while it kept trading, then eight
  `Cannot release more than reserved` errors and nine ghost completion events. A run whose
  executor was hung never saved, so its kill stuck: reported COMPLETED at 100% with data covering
  8.5 of 12 months, `paused=f`, no reason anywhere. The one hint the reaper tried to leave
  (`setPaused(true)`) was itself discarded by `save()`, which skips pause columns by design.
- **The population that actually needed a watchdog was invisible to every existing defence.** An
  evaluation that never returns writes no skip row (so the stall circuit breaker is blind) and
  holds the in-JVM `activeSimulations` guard forever (so pickup-based resume is blind in that
  process). Everything else the reaper could claim to protect against was already handled:
  crashes heal via stale-lock cleanup + pickup + checkpoint resume; data gaps, LLM failure and
  budget exhaustion pause with reasons.
- **The "fleet hygiene" justification was real but mislocated.** A zombie row does cost lock
  churn every pickup tick — but a *paused* row costs nothing (pickup filters it), so an honest
  pause delivers identical scheduler relief to a fabricated completion. The actually dominant
  scheduler cost was two other schedulers calling `findAll()` twice a minute — untouched by
  completing anything.

156 COMPLETED simulations in the incident database, every one at `progress=100`: no run had ever
been honestly recorded as ended early.

## Consequences

- The backtest arm of `EndSimulationService` is rebuilt as an evidence-based stall detector that
  pauses with `EXECUTION_STALLED` — full design, per-profile settings, race analysis and test
  reaper/stall-detector plan (private infra repo).
- Every scheduler that offers work must filter `pausedForExternalReason` as well as `paused`,
  or system-paused runs are re-offered forever and rule 2's "pause = fleet relief" equivalence
  breaks.
- Anyone tempted to add a new timeout-based janitor starts here: if the predicate contains
  "older than", it is wrong; if the action writes a terminal state, it is wrong twice.
