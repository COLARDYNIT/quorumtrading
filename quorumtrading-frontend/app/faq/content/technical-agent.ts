export const content = `
The Technical Agent is the **chart analyst** of the team. It reads price data and technical indicators to assess whether a stock or crypto asset is showing bullish or bearish momentum.

## What data does it receive?

### Multi-timeframe price statistics

The Technical Agent doesn't see raw price bars — it receives **aggregated statistics** across four time windows:

- **Last 24 hours** — recent momentum
- **Last 7 days** — short-term trend
- **Last 30 days** — medium-term trend
- **Last 90 days** — long-term pattern

For each window, it sees:
- **Price change %** — how much the price moved
- **Volatility level** — LOW, MEDIUM, or HIGH
- **Price range** — minimum, maximum, and current price
- **Event count** — how many data points are available

This multi-timeframe view lets the agent distinguish a short-term dip from a sustained downtrend.

### Technical indicators (stocks only)

For stocks, the agent receives a full suite of calculated indicators:

- **SMA-50 and SMA-200** — Simple Moving Averages with percentage gap (golden cross / death cross detection)
- **EMA-50 and EMA-200** — Exponential Moving Averages (when available)
- **RSI-14** — Relative Strength Index with interpretation: overbought (>=70), oversold (<=30), or neutral
- **MACD** — Moving Average Convergence Divergence: line value, signal value, histogram, plus bullish/bearish interpretation
- **Bollinger Bands** — upper, middle, and lower bands for volatility-adjusted price channels

If there isn't enough historical data to calculate reliable indicators, the agent receives a warning and adjusts its confidence accordingly.

**Note:** Technical indicators are currently skipped for crypto assets.

### Current portfolio state

- Cash available
- Total portfolio value
- Current holdings of the ticker being analyzed (units held and average cost)

This lets the agent factor in existing exposure — e.g., avoiding a BUY recommendation when you're already heavily invested in the same asset.

### Strategy guidance

The agent receives your **strategy prompt text** — the instructions you wrote in your strategy's markdown. This tells the agent which indicators to prioritize and what conditions matter to your specific trading approach.

## What it does NOT see

- Raw OHLCV price bars (only aggregated statistics)
- News headlines or social media posts
- Financial fundamentals (revenue, P/E, etc.)
- Other agents' recommendations
- Your broker credentials

## When does it skip?

The Technical Agent checks whether it has **enough context for a meaningful decision**. If the market data is too thin (e.g., a newly listed stock with only a few days of data), it will skip rather than guess. This is by design — a low-confidence analysis is worse than no analysis.

## In backtests vs. live

The agent works identically in backtests and live simulations. Indicators are calculated as-of the simulation date, not the current date, so backtests don't suffer from forward-looking bias.

---

**See also:** [Fundamental Agent](/faq/fundamental-agent) | [Sentiment Agent](/faq/sentiment-agent) | [Orchestrator](/faq/orchestrator-agent)
`;
