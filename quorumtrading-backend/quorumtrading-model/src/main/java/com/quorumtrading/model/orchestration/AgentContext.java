package com.quorumtrading.model.orchestration;

import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.enums.AgentRole;
import com.quorumtrading.model.marketdata.FinancialMetrics;
import com.quorumtrading.model.marketdata.MarketDataContext;
import com.quorumtrading.model.valueobject.IndicatorContext;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable context passed to agents during analysis.
 * Contains all necessary information for an agent to make a trading decision.
 * <p>
 * Model resolution uses per-agent maps. The {@link com.quorumtrading.application.service.ModelResolutionService}
 * reads these maps to resolve the correct model for each agent role.
 */
public class AgentContext {
    private final SimulationId simulationId;
    private final String ticker;
    private final Portfolio portfolio;
    private final MarketDataContext marketData;
    private final List<String> recentNews;
    private final Map<String, Double> sentimentScores;
    private final Instant timestamp;
    private final StrategyConfig strategy;
    private final IndicatorContext indicators;
    private final Double strategyTemperature;

    // User preferences: AgentRole -> model ID (global user pref stored under null key convention not possible,
    // so global pref is stored redundantly for every role, or callers use getPreferredModelGlobal())
    private final String preferredModelGlobal;             // User's global model pref for this context
    private final Map<AgentRole, String> preferredAgentModels; // User's per-agent prefs

    // System defaults
    private final String systemDefaultModel;
    private final Map<AgentRole, String> systemAgentDefaults;

    // Pre-computed values to avoid redundant calculation across agents
    private final Map<Symbol, Money> currentPrices;
    private final Money portfolioTotalValue;

    // Recent trade history for anti-whipsaw context
    private final String recentTradeHistory;

    // Whether this is a live (non-backtest) simulation — enables web search via :online models
    private final boolean liveSimulation;

    // Structured financial data for fundamental analysis (null for crypto/ETFs)
    private final FinancialMetrics financialMetrics;

    /**
     * Minimal constructor (no model overrides).
     */
    public AgentContext(SimulationId simulationId,
                       String ticker,
                       Portfolio portfolio,
                       MarketDataContext marketData,
                       List<String> recentNews,
                       Map<String, Double> sentimentScores,
                       Instant timestamp,
                       StrategyConfig strategy,
                       IndicatorContext indicators) {
        this(simulationId, ticker, portfolio, marketData, recentNews, sentimentScores,
            timestamp, strategy, indicators, null, null, null, null, null, null, null, null, false, null);
    }

    /**
     * Legacy constructor with single preferredModel, strategyModel, strategyTemperature.
     * The strategyModel is now read from StrategyConfig directly by ModelResolutionService.
     */
    public AgentContext(SimulationId simulationId,
                       String ticker,
                       Portfolio portfolio,
                       MarketDataContext marketData,
                       List<String> recentNews,
                       Map<String, Double> sentimentScores,
                       Instant timestamp,
                       StrategyConfig strategy,
                       IndicatorContext indicators,
                       String preferredModel,
                       String strategyModel,
                       Double strategyTemperature) {
        this(simulationId, ticker, portfolio, marketData, recentNews, sentimentScores,
            timestamp, strategy, indicators, preferredModel,
            Map.of(), strategyTemperature, null, null, null, null, null, false, null);
    }

    /**
     * Full constructor with per-agent model support.
     */
    public AgentContext(SimulationId simulationId,
                       String ticker,
                       Portfolio portfolio,
                       MarketDataContext marketData,
                       List<String> recentNews,
                       Map<String, Double> sentimentScores,
                       Instant timestamp,
                       StrategyConfig strategy,
                       IndicatorContext indicators,
                       String preferredModelGlobal,
                       Map<AgentRole, String> preferredAgentModels,
                       Double strategyTemperature,
                       String systemDefaultModel,
                       Map<AgentRole, String> systemAgentDefaults,
                       Map<Symbol, Money> currentPrices,
                       Money portfolioTotalValue,
                       String recentTradeHistory,
                       boolean liveSimulation,
                       FinancialMetrics financialMetrics) {
        this.simulationId = simulationId;
        this.ticker = ticker;
        this.portfolio = portfolio;
        this.marketData = marketData;
        this.recentNews = recentNews;
        this.sentimentScores = sentimentScores;
        this.timestamp = timestamp;
        this.strategy = strategy;
        this.indicators = indicators;
        this.preferredModelGlobal = preferredModelGlobal;
        this.preferredAgentModels = preferredAgentModels != null ? Map.copyOf(preferredAgentModels) : Map.of();
        this.strategyTemperature = strategyTemperature;
        this.systemDefaultModel = systemDefaultModel;
        this.systemAgentDefaults = systemAgentDefaults != null ? Map.copyOf(systemAgentDefaults) : Map.of();
        this.currentPrices = currentPrices != null ? Map.copyOf(currentPrices) : Map.of();
        this.portfolioTotalValue = portfolioTotalValue;
        this.recentTradeHistory = recentTradeHistory;
        this.liveSimulation = liveSimulation;
        this.financialMetrics = financialMetrics;
    }

    // === Core getters ===
    public SimulationId getSimulationId() { return simulationId; }
    public String getTicker() { return ticker; }
    public Portfolio getPortfolio() { return portfolio; }
    public MarketDataContext getMarketData() { return marketData; }
    public List<String> getRecentNews() { return recentNews; }
    public Map<String, Double> getSentimentScores() { return sentimentScores; }
    public Instant getTimestamp() { return timestamp; }
    public StrategyConfig getStrategy() { return strategy; }
    public IndicatorContext getIndicators() { return indicators; }
    public Double getStrategyTemperature() { return strategyTemperature; }

    // === Model resolution data ===
    public String getPreferredModelGlobal() { return preferredModelGlobal; }
    public Map<AgentRole, String> getPreferredAgentModels() { return preferredAgentModels; }
    public String getSystemDefaultModel() { return systemDefaultModel; }
    public Map<AgentRole, String> getSystemAgentDefaults() { return systemAgentDefaults; }

    // === Pre-computed values (avoid redundant computation across agents) ===
    public Map<Symbol, Money> getCurrentPrices() { return currentPrices; }
    public Money getPortfolioTotalValue() { return portfolioTotalValue; }

    // === Anti-whipsaw context ===
    public String getRecentTradeHistory() { return recentTradeHistory; }

    // === Simulation mode ===
    public boolean isLiveSimulation() { return liveSimulation; }

    // === Financial data for fundamental analysis ===
    public FinancialMetrics getFinancialMetrics() { return financialMetrics; }

    // === Backwards compatible methods (used by agents before refactor) ===

    /**
     * Get user's global preferred model.
     * @deprecated Agents should use ModelResolutionService instead.
     */
    @Deprecated
    public String getPreferredModel() {
        return preferredModelGlobal;
    }

    /**
     * Get strategy's global pinned model.
     * @deprecated Agents should use ModelResolutionService instead.
     */
    @Deprecated
    public String getStrategyModel() {
        return strategy != null ? strategy.getLlmModel() : null;
    }

    /**
     * Returns true if the strategy has any model override (global or per-agent).
     * @deprecated Agents should use ModelResolutionService.shouldDisableFallback() instead.
     */
    @Deprecated
    public boolean hasStrategyModelOverride() {
        if (strategy != null) {
            String globalModel = strategy.getLlmModel();
            if (globalModel != null && !globalModel.isBlank()) {
                return true;
            }
            Map<AgentRole, String> agentModels = strategy.getLlmAgentModels();
            if (agentModels != null && !agentModels.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * @deprecated Use {@link #getMarketData()} instead for type-safe market data access.
     */
    @Deprecated
    public Map<String, Object> getRecentMarketEvents() {
        return Map.of();
    }
}
