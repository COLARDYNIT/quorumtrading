export const content = `
If you trade through a European (Belgian) broker account, tax handling works differently from a US account. The short version: **your broker takes care of the Belgian taxes for you, and QuorumTrading does not compute, file, or advise on tax.**

> **This is not tax advice.** QuorumTrading is a trading and research tool, not a tax advisor. Nothing here is a personal tax determination. For your own situation, talk to a qualified Belgian tax advisor (*fiscalist*).

## What your broker handles

A Belgian broker is a Belgian entity, so it **withholds and declares the relevant Belgian taxes automatically** — the transaction tax, dividend tax, and the capital-gains tax that applies from 2026. You generally don't have to calculate or file those yourself; they're handled at the source, behind your own broker login.

QuorumTrading sits **upstream** of that. We're the layer that helps the AI agents decide and place trades. The tax engine lives at your broker, not with us.

## What QuorumTrading does NOT do

To be completely clear about the boundary:

- **No tax calculation** — we never compute how much tax you owe on a trade, a dividend, or a year
- **No filing** — we don't generate or submit any tax return or government form
- **No tax advice** — we don't decide how your trading should be classified, and we don't tell you whether to make any tax election
- **No withholding** — any tax withholding happens at your broker, never inside QuorumTrading

## Classification — why it matters

Belgian tax treatment depends on **how your trading is classified**. Automatic withholding by a Belgian broker assumes you fall in the "normal management of private wealth" bucket. **Frequent, automated, higher-turnover trading can be reclassified** into a different bucket with a different tax treatment — and AI-driven trading can fall into that grey area.

That classification question is a personal, fact-specific one. **Only a qualified tax advisor can answer it for you.** If your situation isn't the standard "private wealth" case, the difference is typically reconciled through your **annual tax return** — not something QuorumTrading sees or settles.

## The capital-gains opt-out

The 2026 capital-gains tax is **withheld automatically by default** — you don't have to do anything. There's also an **opt-out** you can elect. Whether to opt out is **your decision, made at your broker, ideally with your advisor** — it's mainly relevant if your trading isn't classified as ordinary private-wealth management. QuorumTrading doesn't make that election for you and doesn't track it.

## Your transaction record

For your annual return — or for your accountant — you can rely on the **transaction history** QuorumTrading records: what was bought or sold, the quantity, the price, the currency, and when. That's a record of your trades, **not a tax document and not a computed tax figure**. Use it (or hand it to your advisor) to support whatever you're personally responsible for filing.

---

**Reminder:** This is general information, **not tax advice**. Tax rules change and depend on your personal circumstances — always confirm with a qualified tax advisor.

**See also:** [Live Trading](/faq/live-trading) | [Portfolio](/faq/portfolio)
`;
