export const content = `
Every trading decision on QuorumTrading is made by AI agents — and running those agents costs money. The **AI budget** is how you pay for that usage.

## How it works

Your budget is a **prepaid, non-refundable balance** that gets consumed as AI agents make decisions. Each time the agents analyze the market and reach consensus, it costs a small amount depending on the AI model used.

Things that consume budget:

- **Backtesting** — each simulated trading day triggers a full consensus round (all agents analyze and vote)
- **Live trading** — same as backtesting, but in real time
- **Market research** — AI agents explore trading ideas and suggest strategies
- **Assistant chat** — conversations with the built-in AI assistant

## Why prepaid?

AI inference has a real cost per request. The prepaid model keeps things simple — you top up your balance, use it at your own pace, and never get surprised by a bill. You can see exactly how much each decision costs in your usage history.

## Cost awareness

- A **single consensus round** (one trading day in a simulation) costs a few cents, depending on the AI model
- **Longer backtests consume more** — a 90-day backtest runs 90 consensus rounds
- **More expensive models** (like Claude Opus) cost more per decision than lighter models (like Haiku)
- You can configure which models to use in [AI Config](/settings/config) to balance quality vs. cost

## Daily spending cap

You can set a **daily spending cap** to prevent runaway costs. If your spending hits the cap, simulations, research, and chat pause until the next day. This is especially useful when running multiple backtests in parallel.

You can set or remove your daily cap at any time from the [Budget page](/settings/budget).

## Topping up

You can add funds to your balance from the [Budget page](/settings/budget). Payments are processed securely through Stripe. All top-ups are non-refundable.

---

**See also:** [Backtesting & Simulations](/faq/backtesting) | [The AI agents](/faq/ai-agents)
`;
