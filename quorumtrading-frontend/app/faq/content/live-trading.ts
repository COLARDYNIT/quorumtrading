export const content = `
Once you've validated a strategy through backtesting, you can go live. Connect a broker account and let the AI agents trade with real money.

## How to go live

1. **Backtest first** — validate your strategy against historical data
2. **Paper trade** — connect an Alpaca paper account to test with fake money in real-time market conditions
3. **Go live** — when you're confident, switch to a live broker account

## Supported brokers

QuorumTrading currently supports **Alpaca** — a commission-free API-first brokerage. You can connect both paper (simulated) and live (real money) accounts.

Broker accounts are set up through the web UI in your account settings. Credentials are stored securely and never pass through the MCP layer.

## Paper trading vs. live trading

**Paper trading** uses real market data but fake money. It's the bridge between backtesting (historical data) and live trading (real money). Your AI agents make decisions in real time, but no actual trades are placed.

**Live trading** is the real thing. The AI agents make decisions, and trades execute in your brokerage account. Real money is at stake.

## Safety

- You can **pause or stop** any live simulation at any time
- You can set **budget limits** to cap how much money the AI can use
- You always see every decision the agents make, with full reasoning
- QuorumTrading is **not financial advice** — always do your own research

---

**Get started:** [Connect a broker](/settings/broker-accounts) | **See also:** [Backtesting](/faq/backtesting) | [Portfolio](/faq/portfolio)
`;
