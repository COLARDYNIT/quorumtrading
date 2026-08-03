export const content = `
Consensus is how the agents agree (or disagree) on what to do. After each agent forms its own opinion, they need a way to combine those opinions into one action: **BUY**, **SELL**, or **HOLD**.

## Consensus methods

### Majority

The simplest method. Each agent votes, and the majority wins. If two out of three say BUY, the system buys. No orchestrator LLM is involved — it's a pure headcount.

### Orchestrator Override

The orchestrator agent listens to every agent's full analysis — not just their vote, but their reasoning, confidence level, and supporting data. Then it makes the final call alone. This is useful when you want nuanced decision-making rather than a simple vote.

## Confidence levels

Each agent doesn't just say BUY or SELL — they also say **how confident** they are (e.g., 85% confident). A barely-confident BUY from one agent might not outweigh a strongly-confident HOLD from another.

## Think of it like...

*A jury that can operate in different modes. Sometimes it's a simple majority vote. Sometimes the judge hears everyone out and decides alone. The jurors also rate how sure they are — "I'm 90% certain" carries more weight than "maybe, I guess."*

---

**See also:** [The AI agents](/faq/ai-agents) | [Strategies](/faq/strategies)
`;
