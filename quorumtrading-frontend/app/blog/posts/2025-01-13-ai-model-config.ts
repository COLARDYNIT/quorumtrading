export const content = `
You can now choose which AI model powers your trading agents.

## Per-Context Model Selection

Different trading activities have different requirements. A backtest running 500 decisions needs a cost-effective model. Live trading with real money deserves a premium one.

Head to [Settings → AI Config](/settings/config) to set your preferred model for each context:

- **Market Research** — Web search enabled models only (uses \`:online\` suffix)
- **Backtesting** — High-volume historical analysis, optimize for cost
- **Paper Trading** — Balanced quality and cost
- **Live Trading** — Real money, use your best model

The system automatically filters out coding-focused models (like Codex) that aren't suited for trading analysis.

## Cost Transparency

Every agent decision now shows exactly what it cost:

- **Model used** — Which LLM processed the decision
- **Tokens** — Input and output token counts
- **Cost** — USD amount billed for that call

You'll see this in both [market research](/research) executions and [simulation](/simulations) consensus decisions. Even \`$0.00\` is shown explicitly for free models—no hidden costs.

Track your total spending on the [AI Usage](/settings/usage) page.

## Smart Defaults

If you don't set a preference, the system picks a sensible default. But for serious backtesting or live trading, you'll want to tune this.

---

*[Settings → AI Config](/settings/config). Your models, your costs, your choice.*
`;
