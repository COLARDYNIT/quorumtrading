export const content = `
The Sentiment Agent is the **mood reader** of the team. It gauges how people feel about an asset by analyzing news tone and social media buzz — the things that charts and balance sheets miss.

## What data does it receive?

### News headlines

Financial news aggregated from multiple sources and major business outlets:

- **Headline** — the title of the article
- **Source** — which outlet published it
- **Summary** — up to 400 characters of article content

The agent analyzes the **tone** of these headlines — are they panicked, optimistic, neutral, cautious?

### Social media posts

Posts from investing communities on social media:

- **Post title** — what people are talking about
- **Score** — upvotes, indicating community engagement
- **Comment count** — how much discussion the post generated
- These are tagged as SOCIAL events so the agent can distinguish them from traditional news

### Pre-calculated sentiment scores

The system pre-calculates sentiment scores on a scale from **-1.0** (extremely negative) to **+1.0** (extremely positive) for each piece of content. These scores are injected into the agent's context alongside the raw text, giving it both the computed metric and the original content to judge.

### Sentiment evolution across timeframes

The agent sees how sentiment has changed over time:

- **Event counts per timeframe** — how many news and social events appeared in the last 24h, 7d, 30d, and 90d
- **Correlation with price** — was the price going UP, DOWN, or FLAT during each period?
- **Pattern interpretation** — the system flags whether sentiment appears sustained (consistent across timeframes) or temporary (spiking in 24h but flat over 30d)

This lets the agent distinguish between a **flash panic** (one bad headline) and **sustained negative sentiment** (weeks of bad press).

### Balanced sampling

To prevent recency bias, the data is balanced: roughly **70% traditional news** and **30% social media**. This stops a viral social media post from drowning out meaningful financial journalism, or vice versa.

### Current portfolio state

- Cash available and total portfolio value
- Current holdings of the ticker being analyzed

### Strategy guidance

Your strategy prompt text, so the agent knows what sentiment signals matter to your approach.

## What it does NOT see

- Technical indicators (RSI, MACD, Bollinger Bands)
- Financial fundamentals (P/E, revenue, margins)
- Other agents' recommendations
- Raw OHLCV price bars
- Your broker credentials

## When does it skip?

The Sentiment Agent requires a **minimum of 3 news or social events** to form a meaningful opinion. With fewer data points, the signal is too noisy to be useful, so it skips.

In backtests, this threshold matters — if a simulation date falls on a quiet news day, the Sentiment Agent may skip and let the other agents decide.

## Backtests vs. live

In backtests, the agent works with news and social data from the simulation date only.

In live simulations, the agent can use **web-search-enabled LLM models** (marked as \`:online\`), giving it access to real-time web search beyond the pre-fetched news and social data.

---

**See also:** [Technical Agent](/faq/technical-agent) | [Fundamental Agent](/faq/fundamental-agent) | [Orchestrator](/faq/orchestrator-agent)
`;
