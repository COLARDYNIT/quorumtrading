export const content = `
QuorumTrading exposes its full functionality through the **Model Context Protocol (MCP)** — an open standard that lets AI coding assistants interact with external tools.

## What does that mean?

You can run backtests, create strategies, check indicators, and manage simulations **directly from your terminal** or AI-powered code editor. No browser needed.

## Supported tools

Any MCP-compatible client works:

- **Claude Code** — Anthropic's CLI for Claude
- **Cursor** — AI-powered code editor
- **Windsurf** — Codeium's AI IDE
- **Any MCP client** — the protocol is open, so any compatible tool can connect

## What you can do

From your terminal, you can ask your AI assistant things like:

- "Backfill 6 months of AAPL price data and show me the indicators"
- "Create a momentum strategy for NVDA and backtest it over Q1 2024"
- "What's the current RSI and MACD for SPY?"
- "Compare my top 3 strategies by Sharpe ratio"

The AI assistant calls QuorumTrading's tools behind the scenes and reports back with results.

## Getting started with Claude Code

1. [Generate an API token](/settings/api-tokens) in your QuorumTrading account settings
2. Add the MCP server to Claude Code:

\`\`\`bash
claude mcp add --transport http quorumtrading https://api.quorumtrading.com/mcp --header "Authorization: Bearer YOUR_API_TOKEN"
\`\`\`

3. Start a conversation and ask Claude to interact with QuorumTrading — it will discover the available tools automatically.

For other MCP clients (Cursor, Windsurf, etc.), consult their documentation on how to add an HTTP MCP server with a bearer token header.

## Why this matters

For developers and power users, the browser UI is optional. You can integrate QuorumTrading into your existing workflow — scripts, notebooks, CI pipelines, or just conversational prompts in your terminal.

---

**Get a token:** [API Tokens](/settings/api-tokens) | **See also:** [What is QuorumTrading?](/faq/what-is-quorumtrading) | [Strategies](/faq/strategies)
`;
