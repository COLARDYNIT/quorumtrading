# QuorumTrading Backend

A high-performance backtesting platform for multi-agent AI trading strategies built with Quarkus.

## Overview

QuorumTrading Backend provides a comprehensive REST API and WebSocket streaming for managing trading strategies, running backtests, and analyzing results. The platform simulates multiple LLM agents making independent trading decisions that are aggregated through a consensus mechanism.

## Architecture

QuorumTrading backend follows **hexagonal architecture** (ports and adapters) with a multi-module Maven structure:

- **Domain Layer** (`quorumtrading-model`): Pure business logic with zero infrastructure dependencies
- **Application Layer** (`quorumtrading-application`): Port interfaces defining contracts for external systems
- **Adapters**: Pluggable implementations for infrastructure concerns
  - **Persistence**: JPA/Panache repositories
  - **REST**: JAX-RS resources with RFC 9457 error handling
  - **WebSocket**: Real-time event streaming
  - **LLM**: OpenRouter, LocalLLM (CLI-based), Router (failover across providers)
  - **Market Data**: Alpaca, Tiingo, Finnhub, Reddit, RSS, Saxo providers
  - **Broker**: Mock, Alpaca, Saxo
  - **Other**: MCP server, Redis cache, Stripe payments
- **Bootstrap**: Main application with configuration-driven provider selection

**Key Benefits:**
- Framework-agnostic domain logic
- Runtime provider switching via configuration
- Fast domain tests without infrastructure
- ArchUnit enforced architectural boundaries

See the Architecture section of [CLAUDE.md](../CLAUDE.md) for layer/module rules and [docs/BOUNDED_CONTEXT_CANVAS.md](../docs/BOUNDED_CONTEXT_CANVAS.md) for the domain model.

## Technology Stack

- **Framework:** Quarkus 3.x
- **Java Version:** 25
- **Architecture:** Hexagonal (21 Maven modules)
- **Database:** PostgreSQL 16 with JSONB support
- **Cache:** Redis
- **API:** RESTEasy Reactive with OpenAPI/Swagger
- **WebSocket:** Real-time streaming updates
- **Database Migration:** Flyway
- **Testing:** JUnit 5, REST Assured, ArchUnit

## Quick Start

### Prerequisites

- Java 25+
- Maven 3.8+
- PostgreSQL 16+ (via `docker-compose up -d postgres redis` from the repo root, listening on port 55432)
- Redis 7+

### Run in Development Mode

```bash
./mvnw quarkus:dev
```

The application will start at http://localhost:8085 (dev profile)

### Access API Documentation

- **Swagger UI:** http://localhost:8085/q/swagger-ui
- **OpenAPI JSON:** http://localhost:8085/q/openapi (Accept: application/json)
- **OpenAPI YAML:** http://localhost:8085/q/openapi

## REST API Documentation

The QuorumTrading backend provides a comprehensive REST API and WebSocket streaming for real-time updates.

### Quick Start

1. **Start the application:**
   ```bash
   ./mvnw quarkus:dev
   ```

2. **Access Swagger UI:**
   - Open http://localhost:8085/q/swagger-ui
   - Interactive API documentation with request/response examples

3. **OpenAPI Specification:**
   - JSON: http://localhost:8085/q/openapi (Accept: application/json)
   - YAML: http://localhost:8085/q/openapi

### REST API Overview

The core API resource groups (see Swagger UI for the full list):

#### 1. Strategies (Trading Strategy Management)
- `GET /api/v1/strategies` - List all strategies
- `GET /api/v1/strategies/{id}` - Get strategy by ID
- `POST /api/v1/strategies` - Create new strategy
- `PUT /api/v1/strategies/{id}` - Update strategy
- `DELETE /api/v1/strategies/{id}` - Delete strategy

**Example: Create Strategy**
```bash
curl -X POST http://localhost:8085/api/v1/strategies \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My Strategy",
    "description": "RSI mean-reversion strategy",
    "markdownContent": "# My Strategy\n\nBuy when RSI < 30, sell when RSI > 70"
  }'
```

#### 2. Simulations (Backtesting Management)
- `POST /api/v1/simulations` - Create new simulation
- `GET /api/v1/simulations/{id}` - Get simulation by ID
- `GET /api/v1/simulations` - List simulations (with optional filters)
- `DELETE /api/v1/simulations/{id}` - Delete simulation (cannot delete running simulations)
- `POST /api/v1/simulations/{id}/start` - Start simulation execution

**Example: Create Simulation**
```bash
curl -X POST http://localhost:8085/api/v1/simulations \
  -H "Content-Type: application/json" \
  -d '{
    "name": "BTC Strategy Backtest 2023",
    "strategyId": "550e8400-e29b-41d4-a716-446655440000",
    "startDate": "2023-01-01T00:00:00Z",
    "endDate": "2023-12-31T23:59:59Z",
    "initialCash": 10000.00
  }'
```

#### 3. Market Data (Historical Events)
- `GET /api/v1/market-data/events` - Query market events

**Query Parameters:**
- `from` (required) - Start timestamp (ISO-8601)
- `to` (required) - End timestamp (ISO-8601)
- `symbol` (optional) - Filter by trading symbol (e.g., BTC, ETH)
- `source` (optional) - Filter by data source
- `eventType` (optional) - Filter by event type (PRICE, NEWS, SOCIAL)
- `limit` (optional) - Max results (1-1000)

**Example: Query Market Events**
```bash
curl "http://localhost:8085/api/v1/market-data/events?from=2023-01-01T00:00:00Z&to=2023-01-31T23:59:59Z&symbol=BTC&limit=100"
```

#### 4. Decisions (Agent Decision History)
- `GET /api/v1/decisions/llm` - List LLM agent decisions
- `GET /api/v1/decisions/consensus` - List consensus decisions

**Example: Query LLM Decisions**
```bash
curl "http://localhost:8085/api/v1/decisions/llm?simulationId=550e8400-e29b-41d4-a716-446655440000&limit=50"
```

#### 5. Portfolio (Portfolio Snapshots)
- `GET /api/v1/portfolios/snapshots` - Query portfolio snapshots over time

**Example: Query Portfolio Snapshots**
```bash
curl "http://localhost:8085/api/v1/portfolios/snapshots?simulationId=550e8400-e29b-41d4-a716-446655440000"
```

### WebSocket Streaming

The API provides real-time updates via WebSocket for:
- Simulation progress updates
- Agent decisions
- Market data events
- Portfolio changes

**WebSocket Endpoint:** `ws://localhost:8085/ws/stream`

#### Connection Example (JavaScript)

```javascript
const ws = new WebSocket('ws://localhost:8085/ws/stream');

ws.onopen = () => {
  // Subscribe to simulation progress updates
  ws.send(JSON.stringify({
    action: 'subscribe',
    channel: 'simulation.progress',
    filters: {
      simulationId: '550e8400-e29b-41d4-a716-446655440000'
    }
  }));
};

ws.onmessage = (event) => {
  const message = JSON.parse(event.data);
  console.log('Received:', message);
  // {
  //   channel: 'simulation.progress',
  //   timestamp: '2025-01-20T10:30:00Z',
  //   data: { simulationId: '...', status: 'RUNNING', progress: 50 }
  // }
};

// Unsubscribe
ws.send(JSON.stringify({
  action: 'unsubscribe',
  channel: 'simulation.progress'
}));
```

#### Available Channels

1. **simulation.progress** - Simulation status and progress updates
   - Subscribe with `simulationId` filter
   - Receive: status, progress, message

2. **agent.decisions** - Real-time LLM agent decisions
   - Subscribe with `simulationId` filter
   - Receive: decisionId, agentId, recommendation, confidence

3. **market.events** - Market data events (public, no filter needed)
   - Receive: symbol, eventType, timestamp, data

4. **portfolio.changes** - Portfolio snapshot updates
   - Subscribe with `simulationId` filter
   - Receive: totalValueUsd, cashAvailable, positions

## LLM Providers

QuorumTrading supports multiple LLM provider implementations through the hexagonal architecture's LLMPort interface. Choose the provider that best fits your deployment needs.

### LLM Provider Routing

The **LLM Router** (`quorumtrading-adapter-llm-router`) provides intelligent routing across multiple LLM providers:

- **Automatic Failover**: Routes queries to available providers in preference order
- **Circuit Breaker**: SmallRye Fault Tolerance skips providers with open circuits
- **Metrics**: Tracks invocations, errors, and response times per provider
- **Configuration**: `quorumtrading.llm.router.provider-order=local-llm,openrouter`

All agents use the router automatically via CDI. The router discovers providers at startup and manages circuit breaker state independently per adapter.

### OpenRouter Provider (Default)

Cloud-based LLM provider with access to multiple models.

**Configuration:**
```properties
# API Key (required, environment variable)
OPENROUTER_API_KEY=sk-or-v1-...

# Model selection per trading mode
quorumtrading.llm.defaults.backtesting=mistralai/mistral-small-3.2-24b-instruct
quorumtrading.llm.defaults.paper-trading=meta-llama/llama-4-scout
quorumtrading.llm.defaults.live-trading=anthropic/claude-sonnet-5.5
quorumtrading.llm.defaults.research=mistralai/mistral-small-3.2-24b-instruct

# Model fallback chain when the requested model is unavailable
quorumtrading.llm.openrouter.model.fallback=mistralai/mistral-small-3.2-24b-instruct,meta-llama/llama-4-scout,cohere/command-r7b-12-2024,deepseek/deepseek-v3.2
```

**Features:**
- Multiple model support
- Automatic retry with exponential backoff
- Rate limit handling (429 responses)

### LocalLLM Provider

Local CLI-based LLM provider using installed tools (Claude, Qwen, Codex, Gemini).

> This provider shells out to a CLI, so it needs the binary on the **same machine as the backend** —
> it is correctly unavailable inside the Docker image. To use a host CLI from the dockerized backend,
> run a bridge on the host and use the `LOCAL_HTTP` provider instead:
> [`tools/claude-openai-bridge`](../tools/claude-openai-bridge/README.md) for `claude`,
> [`scripts/local-llm-cli-shim.py`](../scripts/local-llm-cli-shim.py) for the others.

**Supported Tools:**
- **Claude CLI** - Anthropic's Claude via CLI (default, recommended)
- **Qwen CLI** - Qwen Code via CLI (first fallback)
- **Codex CLI** - OpenAI's Codex via CLI
- **Gemini CLI** - Google's Gemini via CLI

**Installation:**

```bash
# Claude (default - recommended)
npm install -g @anthropic-ai/claude-code

# Qwen (fallback)
npm install -g @qwen-code/qwen-code
```

**Configuration:**

```properties
# Enable LocalLLM provider (off by default; on in the self-host local profile)
quorumtrading.llm.local.enabled=true

# Default tool
quorumtrading.llm.local.default-tool=claude

# Tool fallback chain (codex/gemini deliberately excluded: too slow)
quorumtrading.llm.local.tool.fallback=claude,qwen
```

**Features:**
- Fallback chain with exponential-backoff blacklisting of failing tools
- Response normalization (strips metadata and formatting)
- No API keys required (uses local CLI tools)
- Privacy-focused (data stays local)

**Architecture:**
- Strategy Pattern: Pluggable CLI executors
- Parser Pattern: Tool-specific response normalization
- Lazy verification: Only checks tool availability on first use

**Troubleshooting:**

If you get "tool not installed" errors:
1. Verify tool is installed: `which claude` or `which qwen`
2. Check PATH includes npm global binaries: `echo $PATH`
3. Reinstall tool if needed
4. The fallback chain (`claude,qwen`) will activate automatically

See [quorumtrading-adapter-llm-local/README.md](quorumtrading-adapter-llm-local/README.md) for full details.

---

## Market Data Caching

Historical simulations (1-3 months back) use cached market data to avoid API limitations:

- **Cache-first approach**: Adapters check `market_events` table before API calls
- **Gap detection**: Missing time ranges automatically fetched from APIs
- **Quality tracking**: Cache hit ratio and coverage percentage logged per fetch
- **Retention policy**: 90-day cleanup job runs daily at 3 AM
- **Reddit pagination**: Uses `/search.json` API with pagination to reach ~2-3 weeks back

**Architecture:**
- Repository-backed adapters pattern (e.g., RedditAdapter, FinnhubNewsAdapter, RSSFeedAdapter)
- Shared `market_events` table with source-specific prefixes
- Transparent to domain layer (hexagonal architecture maintained)

**Testing:**
```bash
# Test with historical date range
curl -X POST http://localhost:8085/api/v1/simulations \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "BTC",
    "startDate": "2025-01-01T00:00:00Z",
    "endDate": "2025-01-30T00:00:00Z",
    ...
  }'

# Check cache hit rate in logs
tail -f /tmp/quorumtrading-dev.log | grep "Quality tracking"
```

---

### Error Handling

All errors follow RFC 9457 Problem Details format:

```json
{
  "type": "https://api.quorumtrading.com/problems/validation-error",
  "title": "Validation Error",
  "status": 400,
  "detail": "Request validation failed",
  "instance": "/api/v1/strategies",
  "timestamp": "2025-01-20T10:30:00Z",
  "errors": [
    {
      "field": "version",
      "message": "Version is required",
      "rejectedValue": null
    }
  ]
}
```

**HTTP Status Codes:**
- `200` - Success
- `201` - Created (POST requests)
- `204` - No Content (DELETE requests)
- `400` - Bad Request (validation failed)
- `404` - Not Found
- `409` - Conflict (e.g., duplicate version, cannot delete running simulation)
- `422` - Unprocessable Entity (e.g., time range too large)
- `500` - Internal Server Error

## Database

### Migrations

Flyway migrations (in `quorumtrading-adapter-persistence/src/main/resources/db/migration/`) run automatically at application startup (`quarkus.flyway.migrate-at-start=true`).

### Configuration

Database configuration is in `quorumtrading-bootstrap/src/main/resources/application.properties`:

```properties
# Database
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=quorumtrading
quarkus.datasource.password=dev-password
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:55432/quorumtrading

# Flyway
quarkus.flyway.migrate-at-start=true
```

## Testing

Tests use a real PostgreSQL database `quorumtrading_test` on `localhost:55432` (not H2). One-time setup:

```bash
docker-compose up -d postgres redis   # from the repo root
psql postgresql://quorumtrading:dev-password@localhost:55432/postgres \
  -c "CREATE DATABASE quorumtrading_test OWNER quorumtrading;"
```

Run the test suite:

```bash
./mvnw clean test
```

Run with coverage:

```bash
./mvnw clean verify
```

## Building

### JVM Mode

```bash
./mvnw clean package
java -jar quorumtrading-bootstrap/target/quarkus-app/quarkus-run.jar
```

### Native Mode

```bash
./mvnw package -Pnative
./quorumtrading-bootstrap/target/quorumtrading-bootstrap-1.0.0-SNAPSHOT-runner
```

## Project Structure

```
quorumtrading-backend/
├── quorumtrading-model/               # Domain: entities, value objects, domain services
├── quorumtrading-application/         # Use cases (CQRS), ports, orchestration, AI agents
├── quorumtrading-adapter-persistence/ # JPA/Panache repositories + Flyway migrations
├── quorumtrading-adapter-rest/        # JAX-RS REST resources
├── quorumtrading-adapter-websocket/   # WebSocket streaming
├── quorumtrading-adapter-mcp/         # MCP server
├── quorumtrading-adapter-redis/       # Redis cache
├── quorumtrading-adapter-llm-*/       # LLM providers: openrouter, local, router
├── quorumtrading-adapter-marketdata-*/# Market data: alpaca, tiingo, finnhub, reddit, rss, saxo
├── quorumtrading-adapter-broker-*/    # Brokers: mock, alpaca, saxo
├── quorumtrading-adapter-payment-stripe/ # Stripe payments
├── quorumtrading-bootstrap/           # Quarkus entry point, schedulers, CDI wiring
│   └── src/main/resources/application.properties
├── pom.xml
└── README.md
```

## Further Documentation

- **Swagger UI:** http://localhost:8085/q/swagger-ui (interactive documentation)
- **OpenAPI Spec:** http://localhost:8085/q/openapi (machine-readable specification)
- **Quarkus Guides:** https://quarkus.io/guides/

## License

Copyright (c) 2025 QuorumTrading. All rights reserved.
