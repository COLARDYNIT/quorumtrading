# Contributing to QuorumTrading

Thanks for your interest in contributing. This document covers the legal and practical basics.

## Licensing of contributions — CLA required

QuorumTrading is **dual-licensed** (AGPL-3.0 + commercial — see [`LICENSING.md`](LICENSING.md)).
To keep that model viable, every contributor must agree to the
**Contributor License Agreement** ([`CLA.md`](CLA.md)) before their contribution can be merged.

The CLA lets the maintainer (COLARDYN IT) distribute your contribution under both the AGPL and
commercial terms. You retain copyright in your contribution and remain free to use it elsewhere.

It's the standard **Harmony Individual CLA** with the keep-open option (Outbound License Option
Five): in exchange for the commercial-licensing right, the maintainer **permanently guarantees your
contribution also stays available under the project's open-source licence (AGPL-3.0-or-later)**. The
open version you contribute to can never be taken away from the community.

In practice the CLA is recorded automatically on your first pull request (e.g. via a CLA bot);
you don't need to send anything by hand.

## Ground rules

- By contributing, you confirm your work is your own creation (or properly attributed) and that
  you are entitled to license it — see CLA §3.
- **Never** include secrets, credentials, API keys, real brokerage tokens, or personal data in a
  contribution or in test fixtures.
- Keep the architecture boundaries: this is a hexagonal/ports-and-adapters codebase — see the
  Architecture section of [`CLAUDE.md`](CLAUDE.md) and, for the domain model,
  [`docs/BOUNDED_CONTEXT_CANVAS.md`](docs/BOUNDED_CONTEXT_CANVAS.md). Domain code stays free of
  infrastructure concerns; adapters don't depend on other adapters.

## Development workflow

- Backend: `cd quorumtrading-backend && ./mvnw quarkus:dev` (port 8085). Tests against PostgreSQL
  `quorumtrading_test`, **not** H2.
- Frontend: `cd quorumtrading-frontend && npm run dev`.
- **Compile and run the relevant tests before opening a PR.** A change isn't done without passing
  tests.
- Match the surrounding code's style, naming, and idiom.

See [`docs/development/QUICK_REFERENCE.md`](docs/development/QUICK_REFERENCE.md) for more.

## Trademark

Contributing does not grant any right to the QuorumTrading name or logo — see
[`TRADEMARK.md`](TRADEMARK.md).
