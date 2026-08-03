package com.quorumtrading.model.valueobject;

/**
 * Represents the trading context for LLM model selection.
 * <p>
 * Different contexts have different quality/cost tradeoffs:
 * <ul>
 *   <li>LIVE_TRADING: Real money at stake, quality is critical</li>
 *   <li>PAPER_TRADING: Simulated trading, balanced quality/cost</li>
 *   <li>BACKTESTING: Historical analysis, high volume, cost-sensitive</li>
 *   <li>RESEARCH: Market research, balanced quality/cost</li>
 * </ul>
 */
public enum TradingContext {

    /**
     * Live trading with real money at stake.
     * Highest quality model recommended.
     */
    LIVE_TRADING,

    /**
     * Paper trading / simulation without real money.
     * Balanced quality and cost.
     */
    PAPER_TRADING,

    /**
     * Historical backtesting.
     * High volume, cost-sensitive, economy models preferred.
     */
    BACKTESTING,

    /**
     * Market research and analysis.
     * Balanced quality and cost.
     */
    RESEARCH,

    /**
     * AI chat assistant conversations.
     * Balanced quality and cost, supports tool use.
     */
    CHAT
}
