# Quick Reference Guide

## Quick Start

> Ports, profiles, and databases are documented authoritatively in [docs/RUN_MODES.md](../RUN_MODES.md).

### Start Development Environment
```bash
# 1. Start infrastructure
docker-compose up -d postgres redis

# 2. Start backend (http://localhost:8085 — port 8080 is production/container only)
cd quorumtrading-backend && ./mvnw quarkus:dev

# 3. Start frontend (http://localhost:3000)
cd quorumtrading-frontend && npm install && npm run dev
```

### Local Profile (Isolated Testing)
```bash
# Backend on port 8085 (same as dev), database quorumtrading_local
cd quorumtrading-backend && ./mvnw quarkus:dev -Dquarkus.profile=local

# Frontend on port 3001
cd quorumtrading-frontend && ./start-local.sh
```

**Use local profile for:**
- Testing migrations without affecting dev database
- Reproducing bugs in isolation

**Note:** dev and local profiles share port 8085, so they cannot run at the same time without overriding the port.

## Essential Commands

### Backend
```bash
cd quorumtrading-backend

./mvnw quarkus:dev                          # Development with live reload
./mvnw test                                  # All tests incl. bootstrap integration tests (needs docker postgres+redis up)
./mvnw test -Dtest=SimulationExecutorTest   # Run specific test
./mvnw clean package                         # Build JVM jar

# LLM integration tests — the script lives at repo-root scripts/, so from inside
# quorumtrading-backend invoke it as ../scripts/... (requires OPENROUTER_API_KEY)
../scripts/run-integration-tests.sh
```

### Frontend
```bash
cd quorumtrading-frontend

npm install          # Install dependencies (first run / after lockfile changes)
npm run dev          # Development server
npm test             # Unit tests (Jest)
npm run test:e2e     # E2E tests (Playwright)
npm run build        # Production build
npm run validate:api # Verify API contract against backend OpenAPI spec
```

### Database
```bash
# Connect to PostgreSQL (host port 55432 — chosen to avoid conflicts with other local Postgres instances)
psql postgresql://quorumtrading:dev-password@localhost:55432/quorumtrading

# View logs
docker-compose logs -f postgres redis

# Create test database (one-time setup)
psql postgresql://quorumtrading:dev-password@localhost:55432/postgres -c "CREATE DATABASE quorumtrading_test OWNER quorumtrading;"
```

### Logs
Each backend profile writes its own file:
- **Dev profile:** `/tmp/quorumtrading-dev.log`
- **Local profile:** `/tmp/quorumtrading-local.log`
- **Production/default:** `/tmp/quorumtrading.log`

```bash
# Backend logs (dev profile)
tail -f /tmp/quorumtrading-dev.log
grep -E "ERROR|WARN" /tmp/quorumtrading-dev.log | tail -50
# Note: the log format abbreviates package names, so grep a short substring or class name
grep "websocket" /tmp/quorumtrading-dev.log
```

## Key URLs

- **Backend API:** http://localhost:8085/api/v1
- **Swagger UI:** http://localhost:8085/q/swagger-ui
- **OpenAPI Spec:** http://localhost:8085/q/openapi
- **Health Check:** http://localhost:8085/q/health/ready
- **Prometheus Metrics:** http://localhost:8085/q/metrics
- **Frontend:** http://localhost:3000
- **Dashboard:** http://localhost:3000/dashboard
- **Simulations:** http://localhost:3000/simulations
- **Strategies:** http://localhost:3000/strategies

### Monitoring (requires `docker-compose up -d prometheus grafana`)
- **Grafana:** http://localhost:3001 (admin/admin) — same port as the local-profile frontend, so don't run both at once
- **Prometheus:** http://localhost:9090

## Environment Variables

### Required
```bash
export OPENROUTER_API_KEY="sk-or-v1-..."    # LLM Provider
```

### Optional
```bash
export FINNHUB_API_KEY="..."
export FINNHUB_NEWS_ENABLED="true"
export RSS_FEEDS_ENABLED="true"
export ALPACA_API_KEY="..."
export ALPACA_API_SECRET="..."
```

## Configuration Files

- Backend: `quorumtrading-backend/quorumtrading-bootstrap/src/main/resources/application.properties`
- Frontend: `quorumtrading-frontend/.env.local` (see `.env.local.example`)
- Docker: `docker-compose.yml`
