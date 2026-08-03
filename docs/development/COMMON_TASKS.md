# Common Development Tasks

> **Ports, profiles, databases, log paths:** see [docs/RUN_MODES.md](../RUN_MODES.md) — the definitive matrix. Short version used below: dev/local backend runs on **8085** (8080 is prod-only), Postgres host port is **55432**.

## Adding a New Agent

1. Create `NewAgent.java` in `quorumtrading-backend/quorumtrading-application/src/main/java/com/quorumtrading/application/orchestration/agents/` implementing the `Agent` interface
2. Implement `analyze(AgentContext)` method
3. Add the new role to the `AgentRole` enum in `quorumtrading-model/.../enums/AgentRole.java` (the `Agent` interface imports it — there is a single copy)
4. Add a `@Produces` method for the new agent in `quorumtrading-bootstrap/.../config/ApplicationConfig.java` — its producer collects all agents via `Instance<Agent>` and hands them to `ReachConsensusService`, which runs them in parallel automatically

## Adding a New Market Data Provider

1. Create `NewProviderAdapter.java` in `quorumtrading-adapter-marketdata-{provider}/`
2. Implement `MarketDataPort` interface
3. Add configuration in `application.properties`: `quorumtrading.providers.newprovider.enabled=true`
4. Register in `MarketDataCollectionJob` scheduled job
5. Update market data collection logic if providing price data

## Adding a New Consensus Method

1. Add enum value to `ConsensusMethod` in `quorumtrading-model`
2. Implement logic in `ReachConsensusService` (`quorumtrading-application/.../service/ReachConsensusService.java`) — the `MAJORITY` / `ORCHESTRATOR_OVERRIDE` branch around the `isMajority` check
3. Update `StrategyConfigParser.java` to recognize new method in markdown
4. Add integration test alongside `ReachConsensusIntegrationTest` / `ConsensusToTradeIntegrationTest` in `quorumtrading-bootstrap/src/test/java/com/quorumtrading/integration/`

## Debugging Simulation Failures

### 1. Check Logs
Backend outputs detailed timestamps and agent responses. The log file is per-profile (see [docs/RUN_MODES.md](../RUN_MODES.md)): dev → `/tmp/quorumtrading-dev.log`, local → `/tmp/quorumtrading-local.log`, prod → `/tmp/quorumtrading.log`.
```bash
tail -f /tmp/quorumtrading-dev.log
grep -E "ERROR|WARN" /tmp/quorumtrading-dev.log | tail -50
```

### 2. Query Database
```sql
SELECT * FROM llm_decisions WHERE simulation_id = '<uuid>' ORDER BY decision_timestamp;
SELECT * FROM consensus_decisions WHERE simulation_id = '<uuid>';
SELECT * FROM portfolio_snapshots WHERE simulation_id = '<uuid>' ORDER BY timestamp;
```

### 3. Enable DEBUG Logging
The agent and LLM-adapter categories already ship at DEBUG (`com.quorumtrading.application.orchestration.agents`, `com.quorumtrading.adapter.llm`). To debug the consensus service itself, change the existing key in `application.properties` from `INFO` to `DEBUG`:
```properties
quarkus.log.category."com.quorumtrading.application.service.ReachConsensusService".level=DEBUG
```
The key appears twice — once unprefixed and once `%dev.`-prefixed. In `quarkus:dev` the `%dev.` entry wins regardless of file order, so edit that one (or both); appending a new unprefixed DEBUG line has no effect in dev mode.

## Creating Strategies and Simulations

### Strategy Format

Strategies are written in markdown with YAML frontmatter (`version`, `consensus_method`, `stock_tickers`, `crypto_tickers`, `live_schedule`, optional `backtest_schedule` defaulting to Wed 10:00).

The full frontmatter spec is maintained as a keep-in-sync set — refer to those rather than this doc:
- `quorumtrading-backend/quorumtrading-application/src/main/resources/prompts/strategy-designer.md` — LLM agent prompt (canonical spec)
- `quorumtrading-backend/quorumtrading-adapter-mcp/.../resource/GuideContent.java` (`STRATEGY_FORMAT`) — MCP guide for humans/Claude Code
- `quorumtrading-backend/quorumtrading-application/.../chat/ChatToolRegistry.java` — chat tool descriptions teach the frontmatter (incl. `live_schedule`/`backtest_schedule`) to the web-chat LLM

Parsing lives in `quorumtrading-model/.../orchestration/StrategyConfigParser.java`. For worked examples, call the MCP `get_guide` tool with topic `strategy-format` — the guide includes complete example strategies.

### Bulk Create

The one-off seeding script (`create-strategies-and-simulations.sh`) was removed (2026-06-11 —
it hardcoded a dead port and a sibling-checkout path). To bulk-create strategies, loop the
`POST /api/v1/strategies` call below over your own markdown files, or drive it through the MCP
`create_strategy` tool from an agent (see the `strategy-format` guide via `get_guide` for the
required frontmatter).

### Manual API Usage

**Create Strategy:**
```bash
curl -X POST http://localhost:8085/api/v1/strategies \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My Strategy",
    "markdownContent": "---\nversion: v1.0\n...\n---\n# Strategy content"
  }'
```

**Create Simulation:**
```bash
curl -X POST http://localhost:8085/api/v1/simulations \
  -H "Content-Type: application/json" \
  -d '{
    "strategyId": "550e8400-e29b-41d4-a716-446655440000",
    "name": "My Strategy November",
    "startDate": "2025-11-01T00:00:00Z",
    "endDate": "2025-11-30T23:59:59Z"
  }'
```

## Creating Benchmarks (Strategy Comparison)

Benchmarks allow you to run multiple strategies simultaneously for comparison. (This feature was formerly called *Leaderboards* — the table lineage is `leaderboards` → `simulation_runs` → `benchmarks`, migrations V32/V65. For competitive comparison between users, see **Challenges** instead.)

### Features
- Multi-strategy comparison (select multiple strategies)
- Two modes: **Backtest** (historical analysis, `startDate` + `endDate`) or **Live** (`endDate` null/omitted)
- Simulations linked via `benchmark_id` column on `simulations`
- Auto-generated names when `name` is omitted (based on dates)

### Via Web UI

1. Navigate to `http://localhost:3000/benchmarks/new`
2. **Select Strategies:** Choose which strategies to compare
3. **Choose Mode:** Backtest (requires start/end dates) or Live (start date only)
4. **Optional Name:** Provide custom name or leave empty for auto-generation
5. **Submit:** Creates benchmark + simulations, redirects to detail page

### Via API

**Create backtest benchmark:**
```bash
curl -X POST http://localhost:8085/api/v1/benchmarks \
  -H "Content-Type: application/json" \
  -d '{
    "strategyIds": [
      "8a4a2eca-df4e-47ba-bf33-c4a6b5f47f91",
      "af85f2dc-d11e-49bb-92d4-29ad87e7f9dd"
    ],
    "startDate": "2025-11-01T00:00:00Z",
    "endDate": "2025-11-30T23:59:59Z",
    "name": "November 2025 Performance Test"
  }'
```

**Create live benchmark:**
```bash
curl -X POST http://localhost:8085/api/v1/benchmarks \
  -H "Content-Type: application/json" \
  -d '{
    "strategyIds": ["<uuid1>", "<uuid2>"],
    "startDate": "2025-11-10T12:00:00Z"
  }'
```

See `BenchmarkResource.java` (or the OpenAPI spec at `http://localhost:8085/q/openapi`) for the full payload — broker config (`mockConfig` vs `brokerAccountId` + `capitalPerStrategy`) and additional read endpoints (`/{id}/entries`, `/{id}/chart-data`, `/{id}/overlays`).

### Benchmark Management
- View all: `GET http://localhost:8085/api/v1/benchmarks`
- View detail: `GET http://localhost:8085/api/v1/benchmarks/{id}`
- Frontend: `http://localhost:3000/benchmarks/{id}`

## Database Operations

### Backups

Postgres listens on host port **55432** (see `docker-compose.yml` — 5432 is deliberately avoided).

**Create backup:**
```bash
mkdir -p backups
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
pg_dump postgresql://quorumtrading:dev-password@localhost:55432/quorumtrading > /tmp/quorumtrading-db-${TIMESTAMP}.sql
zip backups/quorumtrading-db-${TIMESTAMP}.zip /tmp/quorumtrading-db-${TIMESTAMP}.sql
rm /tmp/quorumtrading-db-${TIMESTAMP}.sql
```

**Restore from backup:**
```bash
unzip backups/quorumtrading-db-<timestamp>.zip -d /tmp
psql postgresql://quorumtrading:dev-password@localhost:55432/quorumtrading < /tmp/quorumtrading-db-<timestamp>.sql
```

### Backup Storage
- Convention: `backups/quorumtrading-db-<timestamp>.zip` (compressed SQL dump)
- The `backups/` directory is not in the repo — create it locally; keep dumps out of version control (they may contain user data)
