export const content = `
The Fundamental Agent is the **financial analyst** of the team. It evaluates whether a company is worth what the market says by looking at financial reports, news, and valuation metrics.

## What data does it receive?

### News and market information

The agent receives recent news articles relevant to the ticker and to the broader market:

- **Headline and source** — which outlet published it
- **Summary** — up to 400 characters of article content
- **Ticker-specific news** — articles mentioning the asset directly
- **General market news** — broader financial news (tagged as "MARKET")

News is aggregated from multiple financial news sources and major business outlets.

### Financial metrics (live simulations only)

In live simulations, the agent receives a comprehensive set of financial data:

**Company profile:**
- Company name, industry, market capitalization, shares outstanding

**Valuation ratios:**
- P/E (TTM) and Forward P/E
- Price-to-Book (P/B) and Price-to-Sales (P/S)
- EV/EBITDA
- PEG ratio

**Growth metrics:**
- EPS growth (year-over-year and 5-year)
- Revenue growth (year-over-year and 5-year)

**Profitability:**
- Gross margin, operating margin, net margin
- Return on Equity (ROE) and Return on Assets (ROA)

**Financial health:**
- Debt-to-Equity ratio
- Current ratio and Quick ratio

**Per-share data:**
- EPS (TTM), Book Value per share
- Dividend yield, Beta

**Earnings history:**
- Recent quarterly earnings with actual vs. estimated EPS
- Earnings surprise percentage per quarter

**Analyst consensus:**
- Total number of covering analysts
- Distribution across Strong Buy, Buy, Hold, Sell, Strong Sell

### Multi-timeframe price trajectory

The agent sees price trends across 24h, 7d, 30d, and 90d windows with volatility interpretation. It uses this to check whether price trends are **consistent** (sustained momentum) or **diverging** (e.g., short-term dip in a long-term uptrend).

### Current portfolio state

- Cash available and total portfolio value
- Current holdings of the ticker being analyzed

### Strategy guidance

Your strategy prompt text, so the agent knows what fundamental criteria matter to your approach.

## What it does NOT see

- Technical indicators (RSI, MACD, Bollinger Bands)
- Social media posts or Reddit sentiment
- Other agents' recommendations
- Raw OHLCV price bars
- Your broker credentials

## When does it skip?

The Fundamental Agent requires a **minimum of 2 news events** to form a meaningful opinion. If there's not enough news coverage, it skips rather than guessing.

## Backtests vs. live — important difference

**In backtests, financial metrics are intentionally skipped.** The financial data providers return current metrics, not historical. If we fed today's P/E ratio into a 2022 backtest, the agent would be making decisions based on information that didn't exist yet — that's forward-looking bias, and it would make backtest results unreliable.

In backtests, the Fundamental Agent relies solely on **news articles from the simulation date** and **price trajectory data**.

**In live simulations**, the agent gets the full suite of financial metrics since they represent the current state of the company.

Additionally, live simulations can use **web-search-enabled LLM models** (marked as \`:online\`), giving the Fundamental Agent access to real-time web information beyond the pre-fetched data.

---

**See also:** [Technical Agent](/faq/technical-agent) | [Sentiment Agent](/faq/sentiment-agent) | [Orchestrator](/faq/orchestrator-agent)
`;
