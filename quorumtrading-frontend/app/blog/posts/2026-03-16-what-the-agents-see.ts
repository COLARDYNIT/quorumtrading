export const content = `
Ever wondered what data each AI agent actually sees before making a trading recommendation? This post breaks down exactly what information flows to the Technical, Fundamental, Sentiment, and Orchestrator agents — and where it comes from.

## The Shared Foundation

Every agent receives the same **AgentContext** — a universal data container assembled fresh for each consensus round. It includes the current portfolio state, recent trade history, strategy rules, and a rich multi-timeframe view of the market. The differences lie in what each agent *focuses on* within that context.

### Multi-Timeframe Price Analysis

All agents receive a statistical summary of price action across four timeframes:

- **24 hours** — latest price, price change %, volatility, min/max range, trend direction
- **7 days** — same statistics, capturing the short-term trajectory
- **30 days** — medium-term momentum and volatility
- **90 days** — long-term context (news events only at this range)

These are *not* raw candlestick bars. The system computes **min, max, earliest, latest, change %, volatility (standard deviation), and trend (UP/DOWN/FLAT)** for each window. This keeps prompts compact while giving agents the statistical picture they need.

### Portfolio State

Every agent knows:

- **Cash available** and **total portfolio value**
- **Current holdings** of the ticker being analyzed (units + average cost)
- **Recent trade history** — the last 10 BUY/SELL trades with timestamps, prices, and P&L

This prevents agents from recommending trades the portfolio cannot support and gives them context to avoid whipsaw trading.

---

## Technical Agent

The Technical Agent is a pure chart reader. It ignores news and fundamentals entirely, focusing on price patterns and mathematical indicators.

### What It Sees

**Price statistics** across all four timeframes (described above).

**Technical indicators** (stocks only — crypto is excluded due to 24/7 trading noise):

- **SMA-50 and SMA-200** — Simple Moving Averages with gap percentage. A golden cross (SMA-50 crosses above SMA-200) is a classic bullish signal.
- **EMA-50 and EMA-200** — Exponential Moving Averages, giving more weight to recent prices
- **RSI-14** — Relative Strength Index on a 0-100 scale. Above 70 is overbought, below 30 is oversold, with interpretation labels included
- **MACD** — Moving Average Convergence Divergence with line, signal, and histogram values, plus a bullish/bearish label based on crossover direction
- **Bollinger Bands** — Upper, middle, and lower bands (20-period, 2 standard deviations). Price near the upper band suggests overbought; near the lower band, oversold.

All indicators are calculated in-process from historical price data, not fetched from an external API. In backtests, they use only data available *up to* the analysis timestamp — no forward-looking bias.

### What It Does NOT See

- No news or social media
- No financial metrics or earnings data
- No other agents' opinions

### Data Sources

| Data | Source |
|------|--------|
| Price history | Alpaca API (stocks + crypto), Tiingo (historical stocks) |
| Indicators | Computed in-process from price bars |

---

## Fundamental Agent

The Fundamental Agent evaluates a ticker based on its financial health, valuation, news flow, and analyst opinions. It is the most data-rich agent.

### What It Sees

**All the price statistics** from the Technical Agent (for market trajectory context, not chart analysis).

**News and market events** — up to 20 items, balanced **70% traditional news / 30% social media**:

- **Finnhub News** — market and company news, earnings announcements, SEC filings
- **RSS Feeds** — financial news from publishers like Seeking Alpha, Yahoo Finance, and others
- **Reddit** — general market discussion from subreddits like r/wallstreetbets and r/CryptoCurrency

Each news item includes the timestamp, source, headline, and a 400-character summary.

**Financial metrics** (stocks only, live mode only — excluded from backtests to avoid forward-looking bias):

- **Company profile** — name, industry, market cap, shares outstanding
- **Valuation ratios** — P/E (trailing + forward), Price-to-Book, Price-to-Sales, EV/EBITDA, PEG ratio
- **Growth metrics** — EPS growth (YoY + 5-year), Revenue growth (YoY + 5-year)
- **Profitability** — gross margin, operating margin, net profit margin, ROE, ROA
- **Financial health** — debt-to-equity, current ratio, quick ratio
- **Per-share data** — EPS (TTM), book value, dividend yield, beta
- **Recent earnings** — last several quarters showing actual vs. estimate EPS, surprise %, and beat/miss labels
- **Analyst consensus** — the count of Strong Buy, Buy, Hold, Sell, and Strong Sell ratings from Wall Street analysts

### What It Does NOT See

- No technical indicators (that is the Technical Agent's domain)
- No pre-calculated sentiment scores

### Data Sources

| Data | Source |
|------|--------|
| Financial metrics | Finnhub Financial Metrics API |
| News | Finnhub News + RSS feeds |
| Social data | Reddit public API |
| Earnings + analyst data | Finnhub |

---

## Sentiment Agent

The Sentiment Agent reads the room. It analyzes the emotional tone and momentum of news coverage and social media chatter.

### What It Sees

**Social media posts** from Reddit, filtered by engagement:

- Post title, upvote score, comment count, timestamp
- Detected ticker symbols mentioned in the text
- Minimum engagement thresholds (e.g., 500+ upvotes) to filter noise

**News headlines** — the same news the Fundamental Agent receives, but the Sentiment Agent reads them for *tone and momentum*, not financial substance.

**Pre-calculated sentiment scores** — when available, a score from **-1.0 (extremely bearish)** to **+1.0 (extremely bullish)** per data source.

**Sentiment evolution across timeframes** — a summary showing how social and news activity correlates with price trends:

- 24h: X news items, Y social posts, price trend UP/DOWN/FLAT
- 7d, 30d: same structure
- 90d: news only (social media data decays too fast)

This cross-referencing helps the agent detect **sentiment-price divergences** — for example, overwhelming bullish social media chatter while the price is trending down, which often signals unsustainable hype.

### What It Does NOT See

- No technical indicators
- No financial metrics or earnings data
- No valuation ratios

### Data Sources

| Data | Source |
|------|--------|
| Social media | Reddit public API (r/wallstreetbets, r/CryptoCurrency) |
| News | Finnhub News + RSS feeds |
| Sentiment scores | Pre-computed from event analysis |

---

## Orchestrator Agent

The Orchestrator is the decision-maker. It does not analyze raw market data independently — instead, it synthesizes the specialist agents' recommendations into a final trading decision.

### What It Sees

**All specialist agent decisions**, each containing:

- **Agent role** — Technical, Fundamental, or Sentiment
- **Recommendation** — BUY, SELL, HOLD, or SKIP
- **Confidence score** — 0.0 to 1.0
- **Full reasoning** — the agent's written explanation for its recommendation

**Portfolio context** — same cash, holdings, and total value that the specialists received.

**Recent trade history** — the last 10 trades, with timestamps, prices, and profit/loss. This gives the Orchestrator **anti-whipsaw context** — it can see if the portfolio just bought yesterday and avoid selling today on a minor dip.

**Trading limits** — concrete minimum trade sizes:

- **Stocks**: minimum \$50 trade size
- **Crypto**: minimum \$20 trade size
- At the current price, what the minimum quantity translates to in units

**Technical indicators** — the same indicator snapshot the Technical Agent received, so the Orchestrator can cross-check the Technical Agent's interpretation.

**Strategy guidance** — if the strategy has specific instructions (e.g., "prioritize technical signals over sentiment"), the Orchestrator sees them here.

### What It Does NOT See

- No raw news or social media (it trusts the Sentiment Agent's summary)
- No financial metrics (it trusts the Fundamental Agent's analysis)

### Two Consensus Methods

The Orchestrator is only invoked in **ORCHESTRATOR_OVERRIDE** mode, where it reviews all agent decisions and makes the final call. In **MAJORITY VOTE** mode, the agents' votes are tallied directly — no Orchestrator LLM call is made. HOLD votes with confidence below a threshold abstain, and ties default to HOLD.

---

## The Data Pipeline

Here is how data flows from source to agent, step by step:

1. **Price backfill** — historical OHLCV data is fetched from Alpaca (or Tiingo for older stock data) and stored locally
2. **Market data fetch** — for each consensus round, the system queries four time windows (24h, 7d, 30d, 90d) with event caps (50, 100, 100, 100 events respectively)
3. **Indicator calculation** — for stock tickers, RSI/MACD/SMA/EMA/Bollinger are computed from the stored price history up to the analysis timestamp
4. **Financial metrics** — for live simulations with stock tickers, current valuation and earnings data is fetched from Finnhub
5. **Context assembly** — all data is combined into per-ticker AgentContext objects, with news filtered by ticker relevance and balanced between news (70%) and social (30%) sources
6. **Parallel agent execution** — Technical, Fundamental, and Sentiment agents run concurrently, each focusing on their domain
7. **Orchestrator synthesis** — the Orchestrator reviews all three recommendations and makes the final decision (or majority vote is used)

### What Stocks Get vs. Crypto

| Data Type | Stocks | Crypto |
|-----------|--------|--------|
| Multi-timeframe price stats | Yes | Yes |
| Technical indicators (RSI, MACD, etc.) | Yes | No |
| News headlines | Yes | Yes |
| Social media (Reddit) | Yes | Yes |
| Financial metrics | Yes (live only) | No |
| Earnings & analyst consensus | Yes (live only) | No |
| Portfolio state | Yes | Yes |
| Trade history | Yes | Yes |

### Backtest vs. Live

| Data Type | Backtest | Live |
|-----------|----------|------|
| Price data | Historical replay | Real-time |
| Technical indicators | Calculated from historical data | Calculated from current data |
| Financial metrics | Excluded (avoids forward bias) | Included |
| News & social | Historical events stored in DB | Recent events |

---

## Why This Matters

The agent architecture is designed around a principle: **each agent should see exactly the data relevant to its expertise, nothing more**. The Technical Agent does not get distracted by earnings surprises. The Sentiment Agent does not try to interpret P/E ratios. And the Orchestrator does not re-analyze raw data — it focuses on reconciling expert opinions.

This separation means you can read each agent's reasoning and understand *exactly* what informed its decision. When the Technical Agent says "RSI at 28 suggests oversold," you know it is looking at the 14-period RSI calculated from real price history. When the Fundamental Agent says "P/E of 45 is stretched," you know it is reading live Finnhub data. No black boxes.

Explore your agents' reasoning on any consensus decision from the [simulations page](/simulations).

---

*Your agents are only as good as the data they see. Now you know exactly what that is.*
`;
