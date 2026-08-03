# QuorumTrading Developer Onboarding Guide

Welcome to QuorumTrading! This guide will help you get started with the codebase quickly.

## Table of Contents

1. [First Steps](#first-steps)
2. [Architecture Overview](#architecture-overview)
3. [Development Workflow](#development-workflow)
4. [Common Tasks](#common-tasks)
5. [Testing Guidelines](#testing-guidelines)
6. [Code Style](#code-style)
7. [Getting Help](#getting-help)

## First Steps

### Prerequisites

Install the following tools:

- **Java 25** - `java -version` should show 25.x
- **Maven** - optional; the repo ships the `./mvnw` wrapper
- **Node.js 20+** - `node --version`
- **Docker Desktop** - For PostgreSQL and Redis
- **Git** - For version control
- **IDE** - IntelliJ IDEA (recommended) or VS Code

### Initial Setup

```bash
# 1. Clone repository
git clone https://github.com/COLARDYNIT/quorumtrading.git
cd quorumtrading

# 2. Start infrastructure
docker-compose up -d postgres redis

# 3. Create test database (one-time setup; the build runs tests against it)
psql postgresql://quorumtrading:dev-password@localhost:55432/postgres -c "CREATE DATABASE quorumtrading_test OWNER quorumtrading;"

# 4. Set environment variables
export OPENROUTER_API_KEY="sk-or-v1-..."  # Create one at https://openrouter.ai

# 5. Build backend
cd quorumtrading-backend
./mvnw clean install

# 6. Start backend (dev profile runs on port 8085)
./mvnw quarkus:dev

# 7. Verify backend (in new terminal)
curl http://localhost:8085/q/health

# 8. Install frontend dependencies (from repo root)
cd quorumtrading-frontend
npm install

# 9. Start frontend
npm run dev

# 10. Open browser
open http://localhost:3000
```

### Verify Setup

- Frontend: http://localhost:3000
- Backend API: http://localhost:8085/api/v1
- Swagger UI: http://localhost:8085/q/swagger-ui
- Health check: http://localhost:8085/q/health

## Architecture Overview

### High-Level Architecture

QuorumTrading uses **hexagonal architecture** (also known as ports and adapters):

```
┌─────────────────────────────────────────┐
│          Bootstrap Module               │
│      (Application Wiring + Config)      │
└───────────┬─────────────────────────────┘
            │
┌───────────▼─────────────────────────────┐
│          Adapter Modules                │
│  ┌──────────────────────────────────┐   │
│  │ REST    │ WebSocket │ Persistence│   │
│  ├──────────────────────────────────┤   │
│  │ LLM Providers (OpenRouter, etc.) │   │
│  ├──────────────────────────────────┤   │
│  │ Market Data (Alpaca, Finnhub, …) │   │
│  └──────────────────────────────────┘   │
└───────────┬─────────────────────────────┘
            │
┌───────────▼─────────────────────────────┐
│       Application Module (Ports)        │
│      (Interfaces for Adapters)          │
└───────────┬─────────────────────────────┘
            │
┌───────────▼─────────────────────────────┐
│          Domain Module                  │
│      (Pure Business Logic)              │
│  • NO framework dependencies            │
│  • NO database annotations              │
│  • NO HTTP concerns                     │
└─────────────────────────────────────────┘
```

### Key Concepts

**1. Domain Layer** (`quorumtrading-model`)
- Pure Java POJOs with business logic
- No dependencies on frameworks or infrastructure
- Can be tested without database or HTTP server

**2. Application Layer** (`quorumtrading-application`)
- Defines port interfaces (contracts)
- Examples: `LLMPort`, `MarketDataPort`, `BrokerPort` (in `port/out/`)
- Adapters implement these interfaces

**3. Adapter Layer** (multiple modules)
- Implements port interfaces
- Handles external communication
- Examples: REST API, database, LLM providers

**4. Bootstrap Layer** (`quorumtrading-bootstrap`)
- Wires everything together
- Configuration-driven provider selection
- Main application entry point

### Module Structure

```
quorumtrading-backend/
├── quorumtrading-model              # Domain entities (Portfolio, Position, Trade)
├── quorumtrading-application        # Port interfaces, use cases, AI agents
├── quorumtrading-adapter-persistence # Database (JPA/Panache)
├── quorumtrading-adapter-rest        # REST API + RFC 9457 errors (incl. chat tools)
├── quorumtrading-adapter-websocket   # Real-time streaming
├── quorumtrading-adapter-mcp         # MCP server (tools for Claude Code etc.)
├── quorumtrading-adapter-redis       # Rate limiting / caching
├── quorumtrading-adapter-llm-*       # LLM providers: openrouter, local, router
├── quorumtrading-adapter-marketdata-* # Market data: alpaca, finnhub, reddit, rss, saxo, tiingo
├── quorumtrading-adapter-broker-*    # Brokers: mock, alpaca, saxo
├── quorumtrading-adapter-payment-stripe # Stripe payments
└── quorumtrading-bootstrap          # Main application + schedulers
```

(Run `ls quorumtrading-backend` for the current list — modules come and go.)

## Development Workflow

### Daily Workflow

```bash
# 1. Pull latest changes
git pull origin main

# 2. Start infrastructure (if not running)
docker-compose up -d

# 3. Start backend dev mode (auto-reload)
cd quorumtrading-backend
./mvnw quarkus:dev

# 4. Start frontend dev mode (auto-reload)
cd quorumtrading-frontend
npm run dev

# 5. Make changes and see them live!
```

### Creating a Feature Branch

```bash
# Create feature branch
git checkout -b feature/my-new-feature

# Make changes, commit frequently
git add .
git commit -m "feat: add new trading strategy"

# Push to remote
git push -u origin feature/my-new-feature

# Create pull request on GitHub
```

### Running Tests

All commands below are shown from the repo root:

```bash
# Backend tests (needs docker postgres + redis up)
cd quorumtrading-backend && ./mvnw test

# Frontend tests
cd quorumtrading-frontend && npm test

# Architecture tests (verify module boundaries, ArchUnit)
cd quorumtrading-backend && ./mvnw test -Dtest=HexagonalArchitectureTest

# LLM integration tests (requires API key; script lives in scripts/ under the repo root)
export OPENROUTER_API_KEY="sk-or-v1-..."
./scripts/run-integration-tests.sh
```

## Common Tasks

### Task 1: Add a New REST Endpoint

**Goal:** Add `GET /api/v1/strategies/{id}/validate` endpoint

**Steps:**

1. **Define request/response DTOs** (`quorumtrading-adapter-rest/src/main/java/com/quorumtrading/adapter/rest/dto/response/`)

```java
// ValidateStrategyResponse.java
package com.quorumtrading.adapter.rest.dto.response;

public record ValidateStrategyResponse(
    boolean valid,
    List<String> errors
) {}
```

2. **Add resource method** (`quorumtrading-adapter-rest/src/main/java/com/quorumtrading/adapter/rest/resource/StrategyResource.java`)

Strategies are identified by UUID (their `name` is a display field, not an identifier). The resource already injects the `StrategyRepository` port:

```java
@GET
@Path("/{id}/validate")
@Produces(MediaType.APPLICATION_JSON)
@Operation(summary = "Validate strategy")
public Response validateStrategy(@PathParam("id") String id) {
    StrategyId strategyId = new StrategyId(UUID.fromString(id));
    var strategy = strategyRepository.findById(strategyId)
        .orElseThrow(() -> new NotFoundException("Strategy not found"));

    // Validation logic here
    var response = new ValidateStrategyResponse(true, List.of());
    return Response.ok(response).build();
}
```

3. **Write integration test** (`quorumtrading-adapter-rest/src/test/java/.../StrategyResourceTest.java`)

```java
@Test
void shouldValidateStrategy() {
    given()
        .pathParam("id", existingStrategyId)
    .when()
        .get("/api/v1/strategies/{id}/validate")
    .then()
        .statusCode(200)
        .body("valid", equalTo(true));
}
```

4. **Test manually** with Swagger UI (http://localhost:8085/q/swagger-ui)

### Task 2: Add Domain Logic

**Goal:** Add risk calculation to Portfolio

**Steps:**

1. **Add method to domain model** (`quorumtrading-model/src/main/java/com/quorumtrading/model/entity/Portfolio.java`)

`Portfolio` is an immutable record built from value objects (`Symbol`, `Money`) — never raw doubles for money (see the "Primitive Obsession" anti-pattern in [CLAUDE.md](../CLAUDE.md)):

```java
public record Portfolio(
    SimulationId simulationId,
    Map<Symbol, Position> positions,
    Money cashAvailable
) {
    // Existing methods (buy, sell, calculateTotalValue)...

    /** Risk = largest position value / total portfolio value (0 when empty). */
    public BigDecimal calculateRiskScore(Map<Symbol, Money> currentPrices) {
        if (positions.isEmpty()) {
            return BigDecimal.ZERO;
        }
        Money totalValue = calculateTotalValue(currentPrices);
        BigDecimal maxPositionValue = positions.values().stream()
            .map(p -> p.calculateValue(
                currentPrices.getOrDefault(p.symbol(), p.averagePrice())).amount())
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);

        return maxPositionValue.divide(totalValue.amount(), 4, RoundingMode.HALF_UP);
    }
}
```

2. **Write domain test** (`quorumtrading-model/src/test/java/.../PortfolioTest.java`)

```java
@Test
void shouldCalculateRiskScore() {
    Money cash = new Money(new BigDecimal("50000"), Money.Currency.USD);
    Portfolio portfolio = new Portfolio(SimulationId.generate(), new HashMap<>(), cash);

    Symbol btc = new Symbol("BTC", InvestmentType.CRYPTO);
    Money btcPrice = new Money(new BigDecimal("50000"), Money.Currency.USD);
    Portfolio updated = portfolio.buy(btc, new BigDecimal("0.5"), btcPrice);

    // 25,000 position / 50,000 total value = 0.5
    assertThat(updated.calculateRiskScore(Map.of(btc, btcPrice)))
        .isEqualByComparingTo("0.5");
}
```

3. **Run test** (no database needed!)

```bash
cd quorumtrading-backend/quorumtrading-model
../mvnw test -Dtest=PortfolioTest
```

### Task 3: Add a New LLM Provider

**Goal:** Add support for Anthropic Claude

**Steps:**

1. **Create new module** (`quorumtrading-backend/quorumtrading-adapter-llm-anthropic/`)

```xml
<!-- pom.xml -->
<project>
    <artifactId>quorumtrading-adapter-llm-anthropic</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.quorumtrading</groupId>
            <artifactId>quorumtrading-application</artifactId>
        </dependency>
        <!-- Anthropic SDK here -->
    </dependencies>
</project>
```

2. **Implement port** (`AnthropicAdapter.java`)

```java
package com.quorumtrading.adapter.llm.anthropic;

import com.quorumtrading.application.port.out.LLMPort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AnthropicAdapter implements LLMPort {

    @Override
    public LLMQueryResult queryWithUsage(String prompt, LLMConfig config) {
        // Call Anthropic API, return content + token usage
    }

    @Override
    public String getProviderName() { return "anthropic"; }

    @Override
    public boolean isAvailable() { /* e.g. API key configured */ }
}
```

3. **Add to bootstrap** (`quorumtrading-bootstrap/pom.xml`)

```xml
<dependency>
    <groupId>com.quorumtrading</groupId>
    <artifactId>quorumtrading-adapter-llm-anthropic</artifactId>
    <version>${project.version}</version>
</dependency>
```

The LLM router (`LLMRouterAdapter` in `quorumtrading-adapter-llm-router`) discovers all `LLMPort` beans via CDI — no provider registry to edit. Provider selection happens at runtime; see [LLM Configuration](./configuration/LLM_CONFIGURATION.md).

4. **Configure** (`application.properties`)

```properties
quorumtrading.llm.anthropic.api-key=${ANTHROPIC_API_KEY:not-configured}
```

5. **Test**

```bash
export ANTHROPIC_API_KEY="sk-ant-..."
./mvnw quarkus:dev
```

### Task 4: Debug a Simulation

**Goal:** Understand why a simulation is producing unexpected results

First find the simulation ID — from the frontend URL, or:

```sql
SELECT id, name, status FROM simulations ORDER BY created_at DESC LIMIT 10;
```

Then follow the [Debugging Simulation Failures](./development/COMMON_TASKS.md#debugging-simulation-failures) recipe in COMMON_TASKS.md — it covers tailing the per-profile log files, the SQL queries against `llm_decisions` / `consensus_decisions` / `portfolio_snapshots`, and enabling DEBUG logging for the consensus service. After changing log levels, rerun the simulation and check the logs.

## Testing Guidelines

### Test Pyramid

```
       ┌─────────────┐
       │  E2E Tests  │  (Playwright, slow, few tests)
       ├─────────────┤
      │   API Tests   │  (REST Assured, medium speed)
      ├───────────────┤
     │  Integration    │  (with database, slower)
     ├─────────────────┤
    │   Domain Tests    │  (pure logic, fast, many tests)
    └───────────────────┘
```

### Domain Tests (Preferred)

**Characteristics:**
- Fast (<1ms per test)
- No database, HTTP, or external dependencies
- Pure business logic
- Should be the majority of your tests

**Example:**

```java
@Test
void shouldRejectBuyWithInsufficientFunds() {
    Money cash = new Money(new BigDecimal("100"), Money.Currency.USD);
    Portfolio portfolio = new Portfolio(SimulationId.generate(), new HashMap<>(), cash);

    Symbol btc = new Symbol("BTC", InvestmentType.CRYPTO);
    Money price = new Money(new BigDecimal("50000"), Money.Currency.USD);

    assertThrows(IllegalStateException.class,
        () -> portfolio.buy(btc, BigDecimal.ONE, price));
}
```

### Integration Tests

**Characteristics:**
- Use `@QuarkusTest` annotation
- Database/HTTP available
- Slower but test real interactions

**Example:**

```java
@QuarkusTest
class StrategyResourceTest {

    @Test
    void shouldCreateStrategy() {
        given()
            .contentType(ContentType.JSON)
            .body(new CreateStrategyRequest("My Strategy", null, "# Strategy"))
        .when()
            .post("/api/v1/strategies")
        .then()
            .statusCode(201);
    }
}
```

### Architecture Tests

**Characteristics:**
- Enforce module boundaries
- Prevent cyclic dependencies
- Run with unit tests

**Example** (abridged from `HexagonalArchitectureTest` in `quorumtrading-bootstrap/src/test/java/com/quorumtrading/architecture/`):

```java
@Test
void domainModelShouldNotDependOnAnyInfrastructure() {
    ArchRule rule = noClasses()
        .that().resideInAPackage("com.quorumtrading.model..")
        .should().dependOnClassesThat()
            .resideInAnyPackage(
                "com.quorumtrading.application..",
                "com.quorumtrading.adapter..",
                "jakarta.persistence..",
                "io.quarkus.."
            )
        .because("Domain model must be pure and have zero infrastructure dependencies");

    rule.check(classes);
}
```

### Project Testing Constraints

- Tests run against PostgreSQL `quorumtrading_test`, NOT H2
- Adapter tests use a `MockProducer` for CDI beans from other modules — adapters never depend on other adapters
- Entities use `allocationSize=1` on sequences to avoid sequence generation errors
- Test-profile properties use the `%test.` prefix in the main `application.properties`

## Code Style

### Java Code Style

Project-specific conventions (the full DDD anti-pattern list is in [CLAUDE.md](../CLAUDE.md)):

```java
// 1. Use records for immutable DTOs and domain objects
public record ValidateStrategyResponse(boolean valid, List<String> errors) {}

// 2. Use value objects (Symbol, Money, Quantity) for domain concepts,
//    never raw primitives — "Primitive Obsession" is a rejected review comment
Money price = new Money(new BigDecimal("50000"), Money.Currency.USD);

// 3. Validate invariants in compact constructors (fail fast)
public record Money(BigDecimal amount, Currency currency) {
    public Money {
        if (amount == null || currency == null) {
            throw new IllegalArgumentException("Amount and currency cannot be null");
        }
    }
}

// 4. Domain entities carry behavior (no anemic models) and stay free of
//    JPA/JSON annotations — mapping happens in the persistence adapter
```

### TypeScript Code Style

Frontend conventions live in the [Frontend Style Guide](../quorumtrading-frontend/app/styles/STYLE_GUIDE.md) and the [Frontend Development Guide](./frontend/FRONTEND_DEVELOPMENT.md). The two rules that bite newcomers:

- Verify every API shape against the backend OpenAPI spec (`npm run validate:api`) — never assume response structure
- Reuse the `app/components/shared/v2/` primitives (`Panel`, `StatusPill`, …) before writing new UI components

### Commit Messages

Follow **Conventional Commits**:

```bash
# Format: <type>(<scope>): <description>

# Types:
feat:     # New feature
fix:      # Bug fix
docs:     # Documentation only
style:    # Code style (formatting, no logic change)
refactor: # Code refactoring
test:     # Adding or updating tests
chore:    # Build/tooling changes

# Examples:
git commit -m "feat(api): add strategy validation endpoint"
git commit -m "fix(orchestration): handle null agent recommendations"
git commit -m "docs(readme): update deployment instructions"
git commit -m "test(domain): add portfolio risk calculation tests"
```

## Getting Help

### Resources

1. **Documentation**
   - [README.md](../README.md) - Project overview
   - [CLAUDE.md](../CLAUDE.md) - Architecture section: hexagonal layering, module structure, testing constraints
   - [BOUNDED_CONTEXT_CANVAS.md](./BOUNDED_CONTEXT_CANVAS.md) - Domain model
   - Swagger UI: http://localhost:8085/q/swagger-ui

2. **Code Examples**
   - Look at existing tests for patterns
   - Check `StrategyResource` for REST endpoint examples
   - Check `OpenRouterAdapter` for LLM integration

3. **Communication**
   - Ask questions or report bugs via GitHub issues
   - Code reviews: Create PR and request review

### Common Questions

**Q: Where do I put business logic?**
A: In the domain layer (`quorumtrading-model`). No framework dependencies allowed!

**Q: Where do I put database queries?**
A: In the persistence adapter (`quorumtrading-adapter-persistence`). Implement repository interfaces defined in application layer.

**Q: Where do I put REST endpoints?**
A: In the REST adapter (`quorumtrading-adapter-rest/src/main/java/com/quorumtrading/adapter/rest/resource/`). Use DTOs for request/response.

**Q: How do I switch LLM providers?**
A: Provider selection is runtime — the `LLMRouterAdapter` routes across all available providers (with circuit breakers and fallback). See [LLM Configuration](./configuration/LLM_CONFIGURATION.md).

**Q: How do I run only one test?**
A: `./mvnw test -Dtest=ClassName#methodName`

**Q: Tests are slow, how can I speed them up?**
A: Write domain tests instead of integration tests. Domain tests don't need database.

### Debugging Tips

1. **Enable debug logging**
   ```properties
   quarkus.log.category."com.quorumtrading".level=DEBUG
   ```

2. **Use Quarkus dev mode**
   ```bash
   ./mvnw quarkus:dev
   # Press 'r' to restart
   # Press 'h' for help
   ```

3. **Check health endpoints**
   ```bash
   curl http://localhost:8085/q/health
   ```

4. **View database contents**
   ```bash
   psql postgresql://quorumtrading:dev-password@localhost:55432/quorumtrading
   \dt  # List tables
   SELECT * FROM orchestrator_strategies;
   ```

5. **Test REST API**
   - Use Swagger UI: http://localhost:8085/q/swagger-ui
   - Or use curl/Postman
