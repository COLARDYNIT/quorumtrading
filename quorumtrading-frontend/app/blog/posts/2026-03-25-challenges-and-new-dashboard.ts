export const content = `
QuorumTrading now has a gamification system. Admins can create **Challenges** — time-bound competitions on a single ticker — and users compete to build the best-performing strategy. Plus, the dashboard got a complete redesign to guide new users instead of dropping them into a chat window.

## Challenges: Compete on Strategy

A challenge locks in the rules: one ticker, one starting balance, one time window. Your job is to craft the strategy that earns the most.

- **Single ticker** — everyone trades the same asset (e.g. BTC, AAPL)
- **Fixed initial cash** — level playing field, no capital advantage
- **Time-bound** — start date, end date, and a join deadline
- **Ranked by portfolio value** — the only metric that matters

Each entry also shows **LLM cost** and **execution time** — not for ranking, but because seeing how much AI budget a strategy burns is genuinely interesting. Did the $0.12 strategy beat the $2.40 one?

### Smart Leaderboard

The challenge detail page shows a [leaderboard](/challenges) with a smart sidebar: top 5 performers are always visible, plus your own entry with the entries directly above and below you for context. Edge cases handled — if you're in the top 5, no duplication. If you're #20, you see a separator then your neighborhood.

### Joining via MCP

If you use the AI assistant, you can join challenges directly:

\`\`\`
> list_challenges
> join_challenge challengeId=... simulationId=...
\`\`\`

If you already have an entry, submitting a new one replaces it. The old simulation stays in your account — it's just detached from the challenge.

## Dashboard Redesign

The old dashboard was a full-screen chat terminal. FUT testing showed new users would register, see a blinking cursor, and not know what to do. The new layout fixes that:

- **Active challenges** are the hero element at the top — immediate engagement hook
- **Getting Started steps** for new users: Research → Strategy → Backtest → Join Challenge
- **Quick action cards** replace the tiny chat pills — more visible, more clickable
- **Active simulations** strip shows what's running right now
- **AI chat** moved to a sidebar widget with a full-screen overlay — still one click away, but no longer the default experience

The chat overlay preserves everything: conversation history sidebar, session management, quick actions, model selector. Nothing was removed — it just earned its own space.

## Under the Hood

- New **Challenge** domain entity (aggregate root) with date-based status derivation
- **Simulation** entity gained a \`challengeId\` field — same pattern as \`benchmarkId\`
- Replace-entry logic uses direct SQL updates to avoid stale overwrites from long-running simulation loops
- Chart data endpoint reuses the benchmark compact format — same Recharts visualization
- REST API at \`/api/v1/challenges\` with full CRUD + join + chart-data

---

*Head to [Challenges](/challenges) to see what's live — or ask the AI assistant to walk you through joining one.*
`;
