export const content = `
The Orchestrator is the **decision-maker** of the team. It doesn't analyze the market directly — it listens to what the other agents say and makes the final call.

## When does it run?

The Orchestrator only runs in **Orchestrator Override** consensus mode. In **Majority** mode, the system counts the agent votes directly and the Orchestrator is skipped entirely.

## What data does it receive?

### All agent recommendations

The Orchestrator sees the final output from each specialist agent:

- **Agent role** — Technical, Fundamental, or Sentiment
- **Action** — BUY, SELL, HOLD, or SKIP
- **Confidence score** — how sure the agent was (0% to 100%)
- **Strong recommendation flag** — agents with confidence >= 70% are highlighted

The Orchestrator does **not** see the agents' detailed reasoning or the raw data they analyzed. It sees only their verdict and how confident they are.

### Strategy guidance

Your strategy prompt text — the Orchestrator uses this to weigh agent opinions against your stated trading criteria. If your strategy says "prioritize technical signals over sentiment," the Orchestrator can factor that in.

It also sees the **list of enabled agents** — if your strategy only enables Technical and Fundamental, the Orchestrator knows not to expect Sentiment input.

### Current portfolio state

- Cash available
- Total portfolio value
- Current holdings with average cost per position

### Trading limits and position sizing

The Orchestrator receives specific trading constraints:

- **Minimum trade value** — $100 for stocks, $50 for crypto
- **Minimum quantity** — calculated at the current price (e.g., "at $150/share, minimum order is 1 share")
- **Auto-sizing instruction** — the system auto-sizes trades unless the Orchestrator specifies a partial position

### Technical indicators

The same indicator suite that the Technical Agent receives (RSI, MACD, SMA, EMA, Bollinger Bands) is also available to the Orchestrator for reference. This gives the Orchestrator direct market context when weighing conflicting agent opinions.

### Recent trade history

The last 10 BUY and SELL trades, including:

- **Date** of execution
- **Ticker** traded
- **Action** — BOUGHT or SOLD
- **Quantity and price**
- **Time elapsed** — how many days ago the trade happened

This prevents **whipsaw trading** — rapidly buying and selling the same asset. If the agents just recommended selling yesterday, the Orchestrator can see that and avoid a contradictory BUY today unless conditions have genuinely changed.

## What it does NOT see

- Raw news headlines or social media posts
- Financial fundamentals (P/E, revenue, margins)
- The detailed reasoning behind each agent's recommendation (only action + confidence)
- Raw OHLCV price bars
- Your broker credentials

## What it decides

The Orchestrator produces the same output format as any agent:

- **Action** — BUY, SELL, or HOLD
- **Confidence** — how certain it is
- **Reasoning** — a written explanation of why it made this call, referencing the agent votes and strategy criteria

## Fallback behavior

If the Orchestrator fails (LLM error, timeout, etc.), the system **falls back to majority vote** using the specialist agents' recommendations. The consensus is never blocked by an Orchestrator failure.

## Anti-churn logic

The Orchestrator is specifically instructed to avoid unnecessary trading. Factors it weighs:

- **Recent trades** — if you just bought yesterday, it needs a strong reason to sell today
- **Transaction costs** — frequent trading erodes returns
- **Confidence thresholds** — a marginal signal (e.g., 52% confident BUY) may warrant a HOLD instead

---

**See also:** [Technical Agent](/faq/technical-agent) | [Fundamental Agent](/faq/fundamental-agent) | [Sentiment Agent](/faq/sentiment-agent) | [Consensus](/faq/consensus)
`;
