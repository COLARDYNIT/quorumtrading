export const content = `
188 commits in three weeks. Here is what changed.

## Live Trading with Alpaca

QuorumTrading now supports **real paper and live trading** through [Alpaca](https://alpaca.markets). Connect your Alpaca account in [broker settings](/settings/broker-accounts), select a strategy, and let the AI agents trade for you.

- **Paper trading** for risk-free validation with real market data
- **Live trading** with real money (requires explicit confirmation at every step)
- **Multiple concurrent simulations** on the same broker account
- **Equity tracking** and position management synced with your broker
- **Real ticker validation** — the platform checks Alpaca's asset API before accepting a symbol

## Benchmarks & Buy-and-Hold Overlays

Benchmarks got a major upgrade. You can now overlay **buy-and-hold performance** for any reference ticker (SPY, QQQ, BTC) directly on your benchmark chart. This answers the only question that matters: *did my strategy beat the market?*

- **Compose benchmarks** from existing simulations — no need to re-run backtests
- **Add or remove simulations** from benchmarks on the fly
- **Preparing state** shown while portfolio data loads
- **Year ticks** on long timelines instead of cluttered month labels

## Strategy Scheduling Overhaul

Strategies now have **separate schedules for live and backtest mode**. Live trading can evaluate twice a day while backtests run twice a week — cutting AI costs by 5x on long backtests without affecting live responsiveness.

- **Per-agent LLM model overrides** with a 6-tier resolution chain (agent → strategy → user preference → context default → global default → hardcoded fallback)
- **Position cooldown** — skip consensus for N days after a trade to avoid overtrading
- **Agent selection** — choose which agents participate (e.g., only TECHNICAL for mechanical strategies)

## Budget & Cost Controls

Running AI agents costs money. The platform now has real guardrails.

- **Daily spend limits** — set a maximum daily AI budget in [budget settings](/settings/budget)
- **Lifetime spend tracking** — see exactly what you have spent across all operations
- **Welcome budget** — new users get a free starting balance to explore the platform
- **Cost estimation** — before creating a backtest, see the estimated cost based on duration and trading frequency
- **Auto-pause on depletion** — simulations pause gracefully when budget runs out, instead of failing
- **Stripe payments** — top up your budget with a credit card

## Performance

The consensus engine got significantly faster.

- **Async simulation execution** with parallel agent queries — agents now run concurrently instead of sequentially
- **Pipelined consensus phases** with parallel orchestrators across tickers
- **O(N squared) MACD calculation fixed** — was re-scanning the entire price history on every calculation
- **Indicator caching** — technical indicators computed once per price point, not per request
- **70-day warmup buffer** — backfills extra data before simulation start so indicators are accurate from day one
- **Benchmark chart data** served from a single compact endpoint instead of multiple round-trips

## MCP & Assistant Intelligence

The [MCP server](/blog/2026-02-03-meet-the-assistant) now has 30+ tools with richer responses.

- **get_guide** — ask the assistant to explain any platform concept (strategies, indicators, trading modes)
- **get_ticker_performance** — lightweight price lookups without creating a full simulation
- **get_simulation_cost** — check how much a running simulation has consumed
- **Auto-backfill in get_indicators** — requesting indicators for a ticker automatically backfills missing price data
- **Strategy cheatsheet** — quick reference panel for all frontmatter fields
- **Enriched tool responses** — every tool now returns contextual hints so the LLM understands what to do next

## Historical Events

A new **historical events** section shows what was happening in the market during your simulation period. Major crashes, Fed decisions, earnings surprises — mapped to your [simulation](/simulations) timeline so you can understand *why* the AI made the decisions it did.

## Security & Auth

- **Full authorization enforcement** across all API endpoints — no more unprotected routes
- **OIDC session hardening** for multi-instance ECS deployment
- **Anti-abuse** — SHA-256 email hashing prevents welcome bonus re-claims after account deletion
- **GDPR account deletion** available in settings
- **Pre-launch security audit** — CRITICAL, HIGH, and MEDIUM findings resolved

## UI & Mobile

- **Redesigned simulation timeline** with hero-style markers and year labels
- **Strategy page** now shows threshold-based return colors and Sharpe Ratio tooltips with formula breakdown
- **Mobile-first navigation** — hamburger menu, responsive chat, sidebar fixes
- **Auto-expanding chat textarea** with shift+enter for newlines
- **Chat session history** with auto-generated titles in a sidebar
- **Rate limit modal** with SVG icon instead of emoji rendering issues

---

*188 commits, zero downtime. Explore the new features on your [dashboard](/dashboard).*
`;
