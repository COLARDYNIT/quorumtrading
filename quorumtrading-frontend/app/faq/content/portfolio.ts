export const content = `
The portfolio tracks everything you own during a simulation or live session: your **cash balance**, your **stock positions**, and the **total value** over time.

## What's tracked

- **Cash** — how much buying power you have left
- **Positions** — which stocks you hold, how many shares, and at what average cost
- **Total value** — cash + market value of all positions, updated in real time
- **Transaction history** — every buy and sell the agents made, with timestamps

## How it changes

Every time the AI agents reach consensus and decide to BUY or SELL, the portfolio updates:

- **BUY:** cash goes down, a new position appears (or an existing one grows)
- **SELL:** cash goes up, the position shrinks (or disappears)
- **HOLD:** nothing changes — the agents decided to wait

## Performance tracking

You can compare your portfolio's performance against **benchmarks** like the S&P 500 to see if the AI actually added value. A portfolio that returned 8% sounds good — until you realize the S&P returned 12% in the same period.

## Think of it like...

*Your bank statement for trading. It shows every transaction, how much you have, and whether you're winning or losing overall.*

---

**See also:** [Benchmarks](/faq/benchmarks) | [Backtesting](/faq/backtesting)
`;
