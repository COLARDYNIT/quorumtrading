export const content = `
QuorumTrading connects to [Alpaca](https://alpaca.markets) for live and paper trading, giving you access to thousands of US-listed ETFs. Here are the ones worth knowing.

## Broad Market

The big index trackers. If you want exposure to "the market", start here.

- **SPY** — SPDR S&P 500 ETF. Tracks the 500 largest US companies. The most traded ETF in the world.
- **VOO** — Vanguard S&P 500 ETF. Same index as SPY, lower expense ratio.
- **QQQ** — Invesco QQQ Trust. Tracks the Nasdaq-100 — the top 100 non-financial Nasdaq stocks. Tech-heavy.
- **VTI** — Vanguard Total Stock Market. The entire US stock market in one ticker (~4,000 stocks).
- **IWM** — iShares Russell 2000. Small-cap US stocks. More volatile, different cycle than large-caps.
- **DIA** — SPDR Dow Jones Industrial. The classic 30 blue-chip industrials.

## Sector ETFs

Bet on specific sectors of the economy. Useful for rotation strategies or hedging.

- **XLK** — Technology (Apple, Microsoft, Nvidia)
- **XLF** — Financials (banks, insurance, brokerages)
- **XLE** — Energy (Exxon, Chevron, oil & gas)
- **XLV** — Health Care (pharma, biotech, medical devices)
- **XLI** — Industrials (Boeing, Caterpillar, defense)
- **XLY** — Consumer Discretionary (Amazon, Tesla, retail)
- **XLP** — Consumer Staples (Procter & Gamble, Costco — defensive)
- **XLU** — Utilities (power companies — low volatility, high dividends)
- **XLRE** — Real Estate (REITs and property companies)

## Bonds & Fixed Income

For strategies that rotate between risk-on and risk-off.

- **TLT** — iShares 20+ Year Treasury. Long-term US government bonds. Inversely correlated with equities.
- **BND** — Vanguard Total Bond Market. Broad bond exposure.
- **SHY** — iShares 1-3 Year Treasury. Short-term, cash-like stability.
- **HYG** — iShares High Yield Corporate. Junk bonds — higher yield, higher risk.

## Commodities

Gold, silver, oil — uncorrelated with equities.

- **GLD** — SPDR Gold Shares. Physical gold price.
- **SLV** — iShares Silver Trust. Physical silver price.
- **USO** — United States Oil Fund. Crude oil futures.

## International

Diversify beyond the US market.

- **EFA** — iShares MSCI EAFE. Developed markets outside the US (Europe, Japan, Australia).
- **EEM** — iShares MSCI Emerging Markets. China, India, Brazil, and more.
- **VEA** — Vanguard FTSE Developed Markets. Similar to EFA, lower fees.
- **VXUS** — Vanguard Total International. All non-US stocks in one ETF.

## Leveraged & Inverse

High risk, high reward. These reset daily — designed for short-term trading, not holding.

- **TQQQ** — 3x daily Nasdaq-100. Triple the gains, triple the losses.
- **SQQQ** — -3x daily Nasdaq-100. Profits when Nasdaq falls.
- **SPXL** — 3x daily S&P 500.
- **UVXY** — 1.5x VIX. Spikes during market fear.

## How to Use These in QuorumTrading

Every ETF listed above can be used in your [strategies](/strategies). Set the ticker in your strategy's \`stock_tickers\` frontmatter, and the platform handles the rest — price data backfill, technical indicators, and AI consensus.

A few ideas to explore:

- **Regime switching** on SPY or QQQ using SMA crossovers
- **Sector rotation** — rotate between XLK, XLE, and XLV based on momentum
- **Risk-off hedging** — pair an equity strategy with TLT or GLD signals
- **Volatility plays** — use UVXY or TQQQ for short-term momentum strategies

Start with a [backtest simulation](/simulations) to validate your thesis before going live.

---

*All ETFs above are tradeable through Alpaca on QuorumTrading. [Create a strategy](/strategies) and put them to work.*
`;
