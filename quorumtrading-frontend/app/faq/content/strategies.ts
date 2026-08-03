export const content = `
A strategy is a set of written rules that tell the AI agents **what to look for** and **how to trade**. You write them in plain text (Markdown) — no coding required.

## What each agent actually sees

Every consensus round, the AI agents receive a data package. Your strategy instructions should reference this data — the more specific you are, the better the agents perform.

### Technical indicators (all agents)

These are calculated automatically for each ticker in your strategy:

- **RSI (14-period)** — values 0–100. Below 30 is oversold, above 70 is overbought
- **SMA-50 and SMA-200** — Simple Moving Averages. When SMA-50 crosses above SMA-200, it's a "golden cross" (bullish). The reverse is a "death cross" (bearish)
- **MACD** — MACD line, signal line, and histogram. Histogram > 0 means bullish momentum, < 0 means bearish
- **Bollinger Bands** — upper band, lower band, and middle (SMA-20). Price near the lower band suggests oversold, near the upper suggests overbought

### Price statistics (Technical Agent)

Multi-timeframe price data across **24 hours, 7 days, 30 days, and 90 days**:

- Current price and price change %
- Volatility level
- Min/max range
- Trend direction (rising, falling, sideways)

### News & fundamentals (Fundamental Agent)

- Recent **news headlines** from financial news sources
- **Financial metrics** for stocks: revenue, earnings, P/E ratio, market cap (not available for crypto)
- Market trajectory analysis across timeframes

### Social sentiment (Sentiment Agent)

- **Reddit posts** — actual post titles and content from relevant subreddits
- **Sentiment scores** — aggregated positive/negative/neutral scores
- Sentiment evolution over time (is sentiment improving or declining?)

### Portfolio state (all agents)

- Current cash balance and total portfolio value
- Current positions (what you hold, quantity, average cost basis)
- Recent trade history (last 5–10 trades with reasons)

---

## Example strategies

### 1. Momentum + Mean Reversion (BTC)

\`\`\`
## Core Approach
Ride momentum when strong, buy dips when indicators show oversold conditions.

## BUY Criteria
- RSI drops below 35 AND is recovering (turning upward)
- Price is near or below the lower Bollinger Band
- MACD histogram is turning positive (momentum shifting bullish)
- At least 2 of 3 agents recommend BUY

## SELL Criteria
- RSI rises above 72
- Price reaches or exceeds the upper Bollinger Band
- MACD histogram turns negative after being positive
- OR any 2 agents recommend SELL

## Risk Management
- Never invest more than 40% of portfolio in a single trade
- If portfolio drops 8% from peak, sell everything and wait
\`\`\`

### 2. Trend Following (Stocks — AAPL, MSFT)

\`\`\`
## Strategy
Follow the major trend using moving average crossovers. Only trade with the trend.

## BUY Signal
- SMA-50 is ABOVE SMA-200 (uptrend confirmed)
- RSI is between 40 and 65 (not overbought, has room to run)
- Recent news sentiment is neutral or positive
- Price pulled back to near SMA-50 (buying the dip within an uptrend)

## SELL Signal
- SMA-50 crosses BELOW SMA-200 (trend reversal)
- RSI exceeds 75 (take profits)
- Strong negative news with negative social sentiment

## Position Sizing
- Maximum 30% of portfolio per stock
- Keep at least 20% in cash as reserve
\`\`\`

### 3. News-Driven Contrarian (ETH)

\`\`\`
## Philosophy
When sentiment is extremely negative but fundamentals haven't changed,
that's often a buying opportunity. When everyone is euphoric, consider selling.

## BUY when ALL of these:
- Reddit sentiment score is strongly negative (fear/panic)
- Price has dropped 5%+ in the last 7 days
- RSI is below 30 (technically oversold)
- No major negative fundamental news (exchange hacks, regulatory bans)

## SELL when ANY of these:
- Reddit sentiment is extremely positive (euphoria/greed)
- RSI exceeds 80
- Price has risen 20%+ in 30 days without a pullback
- Negative regulatory news appears

## Risk
- Never more than 50% of portfolio in ETH
- If a trade loses 10%, cut it
\`\`\`

### 4. Conservative Dividend Approach (SPY)

\`\`\`
## Approach
Low-frequency, long-term. Only trade when there's a clear signal.
Default action should be HOLD.

## BUY
- SMA-50 is above SMA-200 (bull market confirmed)
- RSI has pulled back below 45 from a higher level (dip in uptrend)
- Bollinger Bands are not extremely wide (volatility is manageable)
- Invest up to 60% of portfolio

## SELL
- SMA-50 crosses below SMA-200 (bear market signal)
- OR RSI stays above 75 for consecutive evaluations

## HOLD (default)
- When signals are mixed or unclear, always HOLD
- Do not trade based on news alone — wait for technical confirmation
- Keep at least 40% in cash
\`\`\`

### 5. Multi-Signal Aggressive (NVDA)

\`\`\`
## Strategy
Combine all three agent perspectives. Only act when
technical, fundamental, and sentiment align.

## Strong BUY (allocate up to 50%)
- Technical: RSI between 30–50, MACD histogram turning positive,
  price above SMA-200
- Fundamental: Positive earnings news or analyst upgrades
- Sentiment: Reddit sentiment trending positive

## Moderate BUY (allocate up to 25%)
- At least 2 of 3 agents recommend BUY
- RSI is not above 70

## SELL
- 2 of 3 agents recommend SELL
- OR RSI exceeds 80
- OR Bollinger Bands show price above upper band for 3+ days

## Stop Loss
- Sell immediately if any position drops 12% from entry price
- Never hold a losing position hoping it recovers
\`\`\`

---

## Tips for effective strategies

- **Be specific about indicators** — say "RSI below 30" not "oversold". The agents have the exact RSI value.
- **Reference the data they actually get** — RSI, SMA-50/200, MACD, Bollinger Bands, news headlines, fundamentals.
- **Set clear position limits** — "max 30% per position" prevents the AI from going all-in. Note that all rules are *agent-interpreted*, not enforced as order types — the platform only issues whole-position BUY/SELL/HOLD; stop-loss-style triggers depend on the agents deciding to SELL when your rule fires.
- **Define what HOLD means** — agents default to action; tell them when inaction is the right choice.
- **Include risk language** — drawdown thresholds, cash reserves, when to step out. The council reads these on every tick, but again: discretionary, not hard-enforced.
- **Keep it readable** — use headings, bullets, and clear sections. The AI parses Markdown well.

---

**Try it:** [Create a new strategy](/strategies/new) | [Browse community strategies](/strategies/public) | **See also:** [Backtesting](/faq/backtesting)
`;
