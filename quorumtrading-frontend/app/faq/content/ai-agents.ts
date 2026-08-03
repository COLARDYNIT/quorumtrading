export const content = `
Each AI agent has a specific job. They don't share notes until the vote — each one forms an independent opinion based on different data.

## The team

### Technical Agent

Reads **price charts and indicators** — RSI, MACD, Bollinger Bands, moving averages. Analyzes multi-timeframe price trends (24h, 7d, 30d, 90d) to distinguish short-term noise from real momentum shifts.

[Full details: Technical Agent](/faq/technical-agent)

### Fundamental Agent

Looks at **company health and news** — P/E ratios, revenue growth, profit margins, analyst ratings, earnings surprises, and financial news. In live simulations, it gets 20+ financial metrics.

[Full details: Fundamental Agent](/faq/fundamental-agent)

### Sentiment Agent

Gauges **market mood** from news headlines, Reddit posts, and pre-calculated sentiment scores. Uses balanced sampling (70% news, 30% social) and tracks how sentiment evolves across timeframes.

[Full details: Sentiment Agent](/faq/sentiment-agent)

### Orchestrator

Runs the meeting and makes the final call. Sees each agent's vote and confidence score, plus recent trade history to prevent whipsaw trading. Only active in Orchestrator Override consensus mode.

[Full details: Orchestrator](/faq/orchestrator-agent)

## How they work together

All agents receive your **strategy prompt** so they know what to focus on. They analyze independently and in parallel — no agent sees another agent's work. Their individual recommendations (BUY/SELL/HOLD + confidence) feed into the consensus mechanism.

## Think of it like...

*Four coworkers who each specialize in something different. One is the chart nerd, one reads annual reports for fun, one is glued to Twitter, and one is the manager who listens to everyone and decides.*

---

**See also:** [Consensus](/faq/consensus) | [Strategies](/faq/strategies)
`;
