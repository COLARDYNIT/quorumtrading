export const content = `
The dashboard just got a lot smarter.

## Your AI Trading Assistant

QuorumTrading now has a built-in AI assistant front and center. Instead of clicking through menus, just tell it what you need. It has direct access to every tool on the platform — backfill data, run research, create strategies, launch simulations, check indicators. All through conversation.

Head to [/dashboard](/dashboard) and try it out. A few things to get you started:

- **"Run a new market research"** — kicks off a research cycle with your configured agents
- **"Create a new simulation"** — walks you through strategy selection, ticker, and date range
- **"Check technical indicators for TSLA"** — fetches live RSI, MACD, Bollinger Bands, and moving averages
- **"Review my strategy performance"** — compares returns, win rates, and Sharpe ratios across strategies

The assistant knows your platform. Ask it to explain a consensus decision, diagnose a failing simulation, or compare two strategies head-to-head.

## Chat-Centric Dashboard

The old three-panel layout (research sidebar, strategy sidebar, simulation cards) is gone. The new dashboard is a **compact status strip** showing your active simulations at the top, and the **assistant** filling the rest.

Active simulations show live portfolio value and return percentage right in the strip. Click any chip to jump to its viewer.

New users see **quick action chips** — one-tap prompts that demonstrate what the assistant can do. Type \`/clear\` to wipe the conversation and start fresh.

## 23 Models Tested, 1 Winner

We evaluated 23 LLM models on three criteria: greeting quality, ticker recognition (does it know AAPL is a stock?), and autonomous problem-solving (can it backfill missing data without being told?).

The results were surprising. **Price does not predict quality.** The cheapest passing model — \`qwen/qwen-turbo\` at $0.05 per million tokens — outperformed \`gpt-4o\` at $2.50. Google Gemini models consistently failed. Only Gemini 2.5 Pro ($1.25) passed from the entire Google lineup.

The assistant now defaults to **Qwen Turbo**, making chat 60x cheaper than running Claude Sonnet 4. You can switch models anytime in [/settings/config](/settings/config).

## MCP Server

For power users: QuorumTrading exposes a full **MCP server** with 26+ tools. Connect it to Claude Code, Cursor, or any MCP-compatible client. Every action available in the web assistant is also available through MCP — strategies, simulations, consensus analysis, market data, and broker management.

---

*Stop clicking. Start talking. Your trading assistant is waiting on the [dashboard](/dashboard).*
`;
