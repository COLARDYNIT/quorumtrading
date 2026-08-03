# Database Schema Guide

## Migration Ownership

- **Location:** `quorumtrading-backend/quorumtrading-adapter-persistence/src/main/resources/db/migration/`
- **Owner:** The `quorumtrading-adapter-persistence` module owns all database schema and migrations (hexagonal architecture principle)
- **Discovery:** Bootstrap module automatically discovers migrations on classpath via Quarkus Flyway
- **Single Source of Truth:** No duplicate migration files in test resources or other modules. (The canonical sequence has gaps — e.g. V11 and V122 are skipped; Flyway does not require contiguous numbering.)

## Migration Files

The migration directory is the **authoritative source** — do not rely on this doc for a per-migration list; it will go stale. As of July 2026 the schema runs through V123.

Major schema eras for orientation:

- **V1-V9**: Initial schema (strategies, simulations, decisions, `portfolio_snapshots` in V3)
- **V10-V17**: Market data caching, strategy optimistic locking, UUID migration
- **V18**: **CASCADE DELETE constraints** - critical for data integrity
- **V27**: Drop unused tables (`portfolios` from V9, `llm_performance` — both had 0 records)
- **V29-V32**: Leaderboards (renamed to `simulation_runs` in V32)
- **V44 / V49**: `users` and `broker_accounts` tables
- **V65-V66**: Rename `simulation_runs` → `benchmarks`; add `owner_id` for authorization
- **V69-V71**: Split `market_events` into typed tables (`price_bars`, `news_events`, `social_events`), then drop `market_events`
- **V105+**: Challenges
- **V111-V113**: Notifications, simulation contributions, investment plans (DCA subsystem)
- **V117**: Saxo broker accounts OAuth lease

## Key Tables

### orchestrator_strategies
- Strategy versions with markdown content
- UUID primary key, optimistic locking
- YAML frontmatter + markdown body

### simulations
- Backtest runs with date ranges
- Status: PENDING → WAITING_FOR_DATA → SIMULATING → LIVE → COMPLETED/FAILED
- Links to strategy via foreign key

### llm_decisions
- Individual agent recommendations
- Links to simulation
- Stores confidence, reasoning, recommended action

### consensus_decisions
- Final aggregated decisions from consensus coordinator
- Links to simulation
- Stores final action taken (BUY/SELL/HOLD)

### price_bars / news_events / social_events
- Typed market data tables (created in V69; the old `market_events` table was dropped in V71)
- Event types: PRICE, NEWS, SOCIAL
- 90-day retention policy enforced by `MarketDataCleanupJob` (daily 3 AM cron) against the typed tables

### portfolio_snapshots
- Portfolio state at each evaluation point
- Tracks cash, positions, total value over time
- Used for performance analysis and charting

### benchmarks
- Groups simulations for comparison (lineage: `leaderboards` → `simulation_runs` in V32 → `benchmarks` in V65)
- `owner_id` added in V66 for access control
- Supports backtest and live modes
- Auto-generated names

## Cascade Delete Behavior (V18)

When a strategy is deleted, PostgreSQL automatically cascades the deletion:
1. All `simulations` linked to that strategy are deleted
2. All `llm_decisions` for those simulations are deleted
3. All `consensus_decisions` for those simulations are deleted
4. All `portfolio_snapshots` for those simulations are deleted

Newer simulation children follow the same pattern — e.g. `simulation_contributions` (V112) has `ON DELETE CASCADE` to `simulations`. (V18 also covered `llm_performance`, but that table was dropped in V27.)

This ensures referential integrity and prevents orphaned records.

## Migration Idempotency

- Most migrations use `IF NOT EXISTS` / `IF EXISTS` clauses and conditional logic; a few early ones (e.g. V2) are plain DDL. Flyway version tracking prevents re-runs regardless
- Safe to run migrations on clean test databases with `clean-at-start=true`
- Compatible with `baseline-on-migrate=true` for existing databases

## Test Database Configuration

The backend uses a **separate PostgreSQL database** (`quorumtrading_test`) for running tests to prevent pollution of the development database (`quorumtrading`). For the full port/profile/database matrix across run modes, see [docs/RUN_MODES.md](../RUN_MODES.md).

### How it Works
1. Tests run with the `%test` profile, which overrides the datasource URL to `quorumtrading_test`
2. The database is cleaned before tests start (`clean-at-start=true`), then Flyway recreates the schema from the persistence adapter's `db/migration` directory
3. Tests execute against the isolated database; the development database (`quorumtrading`) remains untouched
4. **Config files:**
   - Main: `quorumtrading-backend/quorumtrading-bootstrap/src/main/resources/application.properties` (`%test.` properties)
   - Persistence Adapter: `quorumtrading-adapter-persistence/src/test/resources/application.properties` (module-local test config)

### Database Setup

Create the test database once via the Quick Start in [CLAUDE.md](../../CLAUDE.md). Note that Postgres is exposed on host port **55432** (overridable via `QT_POSTGRES_PORT` in docker-compose):

```bash
# Verify test database isolation
psql postgresql://quorumtrading:dev-password@localhost:55432/quorumtrading_test -c "\dt"
```

**IMPORTANT:** A per-module `src/test/resources/application.properties` applies only to that module's own tests (the persistence adapter uses one for its datasource and clean-at-start). Test config that must apply across modules — including the bootstrap module — belongs in the main `application.properties` with the `%test.` prefix.
