export const content = `
Dollar-cost averaging is the most boring good idea in investing: put money in on a schedule, regardless of the headlines. QuorumTrading now automates it — but routed through your AI strategies instead of a single fund. Meet **Investment Plans**, live now in the sidebar.

## What an Investment Plan Does

An investment plan is a standing instruction: *on this day each month, put this much money to work using my strategy.* You set it up once and it tops up a live strategy on your schedule.

- **Budget mode** — invest a **fixed amount**, a **fixed-or-partial** amount (take what's available, up to your target), or **all available** cash
- **Day of the month** — pick any day from the 1st to the 28th
- **AI picks the strategy** — the plan chooses which of your active strategies to fund each cycle
- **Ticker policy** — keep an allow-list or block-list so a plan never strays into names you don't want

Each cycle records a single **allocation** row — whether it invested, skipped (and why), or failed — so there's always an honest audit trail of what your money did.

## Honest Performance Accounting

Here's the part we care about most. When you add cash to a strategy that's already running, that cash is **money you invested — not money your strategy made.** Most tools get this wrong and quietly count your own deposits as gains.

We don't.

Say you start a strategy with **$200**, your plan adds **$200** the next month, and the portfolio is worth **$403** today. Your headline return is **+0.6%** — what the strategy actually earned on the money — *not* +100%. The $200 you contributed shows up as invested capital, never as profit.

The simulation detail page breaks this down three ways:

- **Total return on invested** — the plain-English number: did the money you put in grow?
- **Time-weighted return (TWR)** — the strategy's skill, stripped of when you happened to add cash. This is the fair number for comparing strategies.
- **Money-weighted return (MWR/IRR)** — your personal return, given exactly when your contributions landed.

Every contribution is also marked on the [portfolio chart](/simulations) — a labelled tick where the cash arrived — so a deposit never looks like a sudden rally. Drawdown is measured the same honest way: a top-up can never masquerade as a recovery.

## You Stay in Control

Plans are built to run quietly, but nothing happens behind your back:

- **A timeline** of every contribution, with dates and amounts
- **Notifications** — in-app and email — each time a plan acts, so you're never surprised
- **Run it yourself** any time from the plan page, or let it follow the schedule you set
- **Pause, resume, or stop** a plan whenever you like

If you use the AI assistant, plans are fully scriptable over MCP:

\`\`\`
> create_investment_plan brokerAccountId=... amount=200 dayOfMonth=1
> list_investment_allocations planId=...
> execute_investment_plan_now planId=...
\`\`\`

## Under the Hood

- A **Contribution** is a first-class event on a live simulation — its own record, with a unique \`(plan, date)\` guard so a retried run can never double-fund you
- Returns everywhere — the detail header, leaderboards, strategy stats, and the assistant — now measure against **total invested**, so contribution-funded strategies are ranked fairly against single-deposit ones
- TWR and MWR are computed over the full cash-flow timeline, breaking the return into clean sub-periods at every contribution

---

*Open [Investment Plans](/investment-plans) to set up your first one — or ask the AI assistant to walk you through it.*
`;
