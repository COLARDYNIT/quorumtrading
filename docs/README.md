# QuorumTrading Documentation Index

Documentation for the QuorumTrading platform. Start with the [project README](../README.md) for an overview and [LOCAL_SETUP.md](../LOCAL_SETUP.md) to run it yourself.

## Getting Started

- **[Developer Onboarding Guide](./DEVELOPER_ONBOARDING.md)** — first-time setup, architecture overview, workflow, testing, code style
- **[Quick Reference](./development/QUICK_REFERENCE.md)** — copy-paste commands, ports, URLs, credentials, common flags
- **[Common Tasks](./development/COMMON_TASKS.md)** — recipes for recurring development chores
- **[Test Suite Status](./development/TEST_SUITE_STATUS.md)** — what each suite covers, which tests are still red and why, and the traps that make them look broken when they are not

## Run Modes & Capabilities

- **[RUN_MODES.md](./RUN_MODES.md)** — the definitive SaaS / dev / standalone / test / e2e matrix: profiles, databases, auth, editions, feature flags
- **[CAPABILITIES.md](./CAPABILITIES.md)** — live feature inventory: what works, what's flag-gated, what's deliberately not built

## Architecture

- **[Bounded Context Canvas](./BOUNDED_CONTEXT_CANVAS.md)** — DDD model: ubiquitous language, aggregates, domain events, external dependencies
- **[ADRs](./adr/)** — architecture decision records. [ADR-0003](./adr/0003-a-day-starts-at-midnight-utc.md) is the one most likely to bite you: **a day starts at 00:00 UTC** for budgets, rate limits, funding days and backtest windows — but market sessions stay exchange-local. [ADR-0004](./adr/0004-watchdogs-pause-on-evidence-never-fabricate-completion.md): **watchdogs pause on evidence, never fabricate terminal state** — if a janitor's predicate contains "older than", it is wrong.
- **[Tool System](./development/TOOL_SYSTEM.md)** — how MCP tools and chat tools share use cases
- The hexagonal layer/module structure and dependency rules are summarized in the Architecture section of [CLAUDE.md](../CLAUDE.md)

## Configuration

- **[Broker Configuration](./configuration/BROKER_CONFIGURATION.md)** — mock / Alpaca / Saxo routing and settings
- **[LLM Configuration](./configuration/LLM_CONFIGURATION.md)** — OpenRouter, local CLI fallback chain, router
- **[claude-openai-bridge](../tools/claude-openai-bridge/README.md)** — host tooling that exposes the local `claude` CLI as an OpenAI-compatible endpoint, so the dockerized backend can use a Claude subscription as an engine. Personal/self-host only, never a hosted-edition auth path
- **[OIDC Setup](./configuration/OIDC_SETUP.md)** — OAuth login providers
- **[Database Schema](./database/DATABASE_SCHEMA.md)** — tables, migrations, cascade rules

## Frontend

- **[Frontend Development](./frontend/FRONTEND_DEVELOPMENT.md)** — API contract workflow, state management, testing
- **[Style Guide](../quorumtrading-frontend/app/styles/STYLE_GUIDE.md)** — shared v2 components, typography, design rules

## Operations

- **[Email Readiness](./operations/EMAIL_READINESS.md)** — checklist before enabling outgoing email
- **[Saxo (EU) Broker Readiness](./operations/SAXO_READINESS.md)** — runbook for promoting the Saxo integration through environments
- **[Saxo OpenAPI Research](./operations/SAXO_ANSWERS.md)** — cited vendor answers backing the Saxo integration design

Production deployment (AWS/Terraform/CI) lives in a separate private infrastructure repository and is not documented here.

## Additional Resources

- **Quarkus:** https://quarkus.io/guides/
- **Next.js:** https://nextjs.org/docs
- **Swagger UI (dev):** http://localhost:8085/q/swagger-ui · **OpenAPI spec:** http://localhost:8085/q/openapi
- **[CLAUDE.md](../CLAUDE.md)** — Claude Code project instructions (also a good condensed orientation)
- **[docker-compose.yml](../docker-compose.yml)** — local infrastructure

## Documentation Standards

When updating documentation: keep it current with code changes, verify commands actually work, link related docs, and add new documents to this index. Avoid hard counts (file counts, line counts) — they rot immediately.
