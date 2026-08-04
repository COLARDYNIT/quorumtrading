---
name: getting-stock-data-keys
description: Walk the user through getting a free stock market-data API key (Tiingo or Alpaca paper) with Chrome browser assistance, wire it into the self-hosted QuorumTrading stack, and verify stocks work. Use when stock backfills fail with "no market-data credentials are configured", when get_market_data_status reports stockUsable false, or when the user asks how to enable stock data / get a Tiingo or Alpaca key.
---

# Getting a free stock-data key

Crypto works keyless. **Stocks need one free API key** — this skill gets one, wires it in, and
proves it works. Two options:

| Provider | Effort | What you get |
|---|---|---|
| **Tiingo** (recommended first) | ~2 min: email signup → token page | Stock backfill + backtesting |
| **Alpaca** (paper account) | ~5-10 min: account + paper keys | Stock data **and** later paper/live trading |

## Ground rules for the browser part

Use the Claude-in-Chrome tools (`mcp__claude-in-chrome__*`; load via ToolSearch, call
`tabs_context_mcp` first, open a **new tab**). This is *assisted* signup, not automated signup:

- **Ask the user which email to use** before touching a form. Never invent personal data.
- **The human clicks**: ToS/privacy checkboxes, CAPTCHA challenges, and the verification link in
  their inbox are the user's to do. When you reach one, stop and tell the user it's their turn.
- **Never read the key aloud**: once the token is visible on the page, take it straight into the
  env file. Don't paste it into chat, logs, or commit messages.
- If the site blocks automation or the flow looks different than described, hand the browser to
  the user with directions instead of retrying.

## Option A — Tiingo

1. Navigate to `https://www.tiingo.com/account/api/token` — signed out, this redirects to login;
   use the "Sign-up" link. Fill the user's email + a password they choose (let them type the
   password themselves), user clicks ToS + submits.
2. User confirms the verification email in their inbox.
3. Navigate back to `https://www.tiingo.com/account/api/token` — the API token is shown there.
4. Wire it in (see below) as `TIINGO_API_KEY`.

## Option B — Alpaca (paper)

1. Navigate to `https://app.alpaca.markets/signup`. Same rules: user's email, user clicks the
   agreements, user handles email verification.
2. After login, make sure the dashboard is switched to **Paper** (toggle top-left).
3. Home → "API Keys" panel → **Generate New Keys**. Both the key and the secret appear once.
4. Wire in as `ALPACA_API_KEY` + `ALPACA_API_SECRET`.

## Wiring the key into the stack

The compose file reads these shell variables (see `docker-compose.local.yml`):
`TIINGO_API_KEY`, `ALPACA_API_KEY`, `ALPACA_API_SECRET`.

1. Put the value in the repo-root `.env` (create it if absent — it is gitignored):
   ```
   TIINGO_API_KEY=<token from the browser>
   ```
2. Recreate the backend so the env lands in the container:
   ```bash
   docker compose -f docker-compose.local.yml up -d backend
   ```
   (`up -d` recreates only what changed; data volumes are untouched.)

## Verify — don't declare success without this

1. `get_market_data_status` (MCP) must now report `stockUsable: true` and the provider
   `credentialed: true` with a passing live probe.
2. Run a real backfill: `backfill_price_data` for `AAPL`, `assetType: STOCK`, a ~3-month range —
   expect `recordsInserted > 0`.
3. If the probe fails with credentials set: the key is wrong/unactivated (Tiingo requires the
   email verification first) — go back to the token page and re-check, don't loop on restarts.
