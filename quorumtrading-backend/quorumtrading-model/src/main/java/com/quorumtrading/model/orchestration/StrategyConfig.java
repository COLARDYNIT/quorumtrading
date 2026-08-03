package com.quorumtrading.model.orchestration;

import com.quorumtrading.model.enums.AgentRole;
import com.quorumtrading.model.enums.ConsensusMethod;
import com.quorumtrading.model.enums.TradingMode;
import com.quorumtrading.model.valueobject.MarketHours;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.enums.InvestmentType;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Parsed configuration from a strategy markdown file.
 * Contains all settings for how the multi-agent system should operate.
 *
 * <p><b>Dual Schedule:</b></p>
 * Strategies define separate schedules for live trading and backtesting.
 * For STOCK mode: {@code liveSchedule} and {@code backtestSchedule} (MarketHours).
 * For CRYPTO mode: {@code liveIntervalSeconds} and {@code backtestIntervalSeconds}.
 *
 * <p><b>Symbol Sets:</b></p>
 * Stock and crypto tickers are stored as {@link Symbol} objects with explicit types,
 * ensuring type safety and preventing ambiguity about asset classes.
 */
public class StrategyConfig {
    private String version;
    private ConsensusMethod consensusMethod;
    private double consensusThreshold;
    private Map<String, Double> positionLimits;
    private double reserveCashPercentage;
    private Double maxOrderFraction;     // Max fraction of portfolio value per single BUY order (null = not configured)
    private Set<Symbol> stockTickers;    // Stock symbols to trade (e.g., new Symbol("AAPL", STOCK))
    private Set<Symbol> cryptoTickers;   // Crypto symbols to trade (e.g., new Symbol("BTC", CRYPTO))
    private String strategyPrompt; // Markdown body after frontmatter - strategic guidance for orchestrator

    // Trading mode
    private TradingMode tradingMode;     // CRYPTO (interval-based) or STOCK (scheduled times)

    // Dual schedule: separate live and backtest configurations
    private int liveIntervalSeconds;       // CRYPTO mode: interval for live trading
    private int backtestIntervalSeconds;   // CRYPTO mode: interval for backtesting (default: 1w)
    private MarketHours liveSchedule;      // STOCK mode: market hours for live trading
    private MarketHours backtestSchedule;  // STOCK mode: market hours for backtesting

    // LLM model override (optional, set in strategy frontmatter)
    private String llmModel;             // Pinned LLM model (null = use user preference or system default)
    private Double llmTemperature;       // Pinned temperature (null = use agent default)
    private Map<AgentRole, String> llmAgentModels; // Per-agent LLM model overrides (null = use llmModel)

    // Agent selection (optional, set in strategy frontmatter)
    private Set<AgentRole> enabledAgents; // Subset of agents to use (null = all 4 specialized agents)

    // Position cooldown (optional, set in strategy frontmatter)
    private Duration positionCooldown; // Cooldown after BUY/SELL before same ticker can trade again (null = no cooldown)

    // Portfolio snapshot interval (optional, defaults to 6h)
    private Duration portfolioSnapshotInterval; // How often to record portfolio value snapshots (null = 6h default)

    public StrategyConfig() {}

    // --- Dual schedule getters ---

    public int getLiveIntervalSeconds() { return liveIntervalSeconds; }
    public void setLiveIntervalSeconds(int liveIntervalSeconds) { this.liveIntervalSeconds = liveIntervalSeconds; }

    public int getBacktestIntervalSeconds() { return backtestIntervalSeconds; }
    public void setBacktestIntervalSeconds(int backtestIntervalSeconds) { this.backtestIntervalSeconds = backtestIntervalSeconds; }

    public MarketHours getLiveSchedule() { return liveSchedule; }
    public void setLiveSchedule(MarketHours liveSchedule) { this.liveSchedule = liveSchedule; }

    public MarketHours getBacktestSchedule() { return backtestSchedule; }
    public void setBacktestSchedule(MarketHours backtestSchedule) { this.backtestSchedule = backtestSchedule; }

    /**
     * Returns the appropriate MarketHours schedule for the given mode.
     *
     * @param isBacktest true for backtest schedule, false for live schedule
     * @return the MarketHours for the requested mode
     */
    public MarketHours getScheduleForMode(boolean isBacktest) {
        return isBacktest ? backtestSchedule : liveSchedule;
    }

    /**
     * Returns the appropriate interval (in seconds) for the given mode.
     *
     * @param isBacktest true for backtest interval, false for live interval
     * @return interval in seconds
     */
    public int getIntervalForMode(boolean isBacktest) {
        return isBacktest ? backtestIntervalSeconds : liveIntervalSeconds;
    }

    // --- Deprecated accessors (delegate to live versions for backward compatibility) ---

    /**
     * @deprecated Use {@link #getLiveIntervalSeconds()} or {@link #getIntervalForMode(boolean)} instead.
     */
    @Deprecated
    public int getEvaluationIntervalSeconds() { return liveIntervalSeconds; }

    /**
     * @deprecated Use {@link #setLiveIntervalSeconds(int)} instead.
     */
    @Deprecated
    public void setEvaluationIntervalSeconds(int evaluationIntervalSeconds) {
        this.liveIntervalSeconds = evaluationIntervalSeconds;
    }

    /**
     * @deprecated Use {@link #getLiveSchedule()} or {@link #getScheduleForMode(boolean)} instead.
     */
    @Deprecated
    public MarketHours getMarketHours() { return liveSchedule; }

    /**
     * @deprecated Use {@link #setLiveSchedule(MarketHours)} instead.
     */
    @Deprecated
    public void setMarketHours(MarketHours marketHours) { this.liveSchedule = marketHours; }

    // --- Standard getters and setters ---

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public ConsensusMethod getConsensusMethod() { return consensusMethod; }
    public void setConsensusMethod(ConsensusMethod consensusMethod) {
        this.consensusMethod = consensusMethod;
    }
    public double getConsensusThreshold() { return consensusThreshold; }
    public void setConsensusThreshold(double consensusThreshold) {
        this.consensusThreshold = consensusThreshold;
    }
    public Map<String, Double> getPositionLimits() { return positionLimits; }
    public void setPositionLimits(Map<String, Double> positionLimits) {
        this.positionLimits = positionLimits;
    }
    public double getReserveCashPercentage() { return reserveCashPercentage; }
    public void setReserveCashPercentage(double reserveCashPercentage) {
        this.reserveCashPercentage = reserveCashPercentage;
    }
    public Double getMaxOrderFraction() { return maxOrderFraction; }
    public void setMaxOrderFraction(Double maxOrderFraction) {
        this.maxOrderFraction = maxOrderFraction;
    }
    public Set<Symbol> getStockTickers() { return stockTickers; }
    public void setStockTickers(Set<Symbol> stockTickers) {
        this.stockTickers = stockTickers;
    }
    public Set<Symbol> getCryptoTickers() { return cryptoTickers; }
    public void setCryptoTickers(Set<Symbol> cryptoTickers) {
        this.cryptoTickers = cryptoTickers;
    }
    public String getStrategyPrompt() { return strategyPrompt; }
    public void setStrategyPrompt(String strategyPrompt) { this.strategyPrompt = strategyPrompt; }
    public TradingMode getTradingMode() { return tradingMode; }
    public void setTradingMode(TradingMode tradingMode) { this.tradingMode = tradingMode; }
    public String getLlmModel() { return llmModel; }
    public void setLlmModel(String llmModel) { this.llmModel = llmModel; }
    public Double getLlmTemperature() { return llmTemperature; }
    public void setLlmTemperature(Double llmTemperature) { this.llmTemperature = llmTemperature; }
    public Set<AgentRole> getEnabledAgents() { return enabledAgents; }
    public void setEnabledAgents(Set<AgentRole> enabledAgents) { this.enabledAgents = enabledAgents; }
    public Map<AgentRole, String> getLlmAgentModels() { return llmAgentModels; }
    public void setLlmAgentModels(Map<AgentRole, String> llmAgentModels) { this.llmAgentModels = llmAgentModels; }
    public Duration getPositionCooldown() { return positionCooldown; }
    public void setPositionCooldown(Duration positionCooldown) { this.positionCooldown = positionCooldown; }
    public Duration getPortfolioSnapshotInterval() { return portfolioSnapshotInterval; }
    public void setPortfolioSnapshotInterval(Duration portfolioSnapshotInterval) { this.portfolioSnapshotInterval = portfolioSnapshotInterval; }

    private static final Duration DEFAULT_PORTFOLIO_SNAPSHOT_INTERVAL = Duration.ofHours(6);

    /**
     * Returns the effective portfolio snapshot interval.
     * Falls back to 6 hours if not explicitly configured.
     */
    public Duration getEffectivePortfolioSnapshotInterval() {
        return portfolioSnapshotInterval != null ? portfolioSnapshotInterval : DEFAULT_PORTFOLIO_SNAPSHOT_INTERVAL;
    }

    /**
     * Get the LLM model for a specific agent role.
     * Falls back to the global llmModel if no agent-specific override is set.
     * Returns null if neither is set.
     */
    public String getLlmModelForAgent(AgentRole role) {
        if (llmAgentModels != null && role != null) {
            String agentModel = llmAgentModels.get(role);
            if (agentModel != null && !agentModel.isBlank()) {
                return agentModel;
            }
        }
        return llmModel; // Global fallback (may be null)
    }

    /**
     * Returns the effective set of enabled agents. If none were explicitly configured,
     * returns all 4 specialized agents (backward compatible default).
     */
    public Set<AgentRole> getEffectiveEnabledAgents() {
        if (enabledAgents == null || enabledAgents.isEmpty()) {
            return Set.of(AgentRole.TECHNICAL, AgentRole.FUNDAMENTAL, AgentRole.SENTIMENT);
        }
        return enabledAgents;
    }

    @Override
    public String toString() {
        return "StrategyConfig{" +
                "version='" + version + '\'' +
                ", consensusMethod=" + consensusMethod +
                ", consensusThreshold=" + consensusThreshold +
                ", liveIntervalSeconds=" + liveIntervalSeconds +
                ", backtestIntervalSeconds=" + backtestIntervalSeconds +
                ", positionLimits=" + positionLimits +
                ", reserveCashPercentage=" + reserveCashPercentage +
                ", maxOrderFraction=" + maxOrderFraction +
                ", stockTickers=" + stockTickers +
                ", cryptoTickers=" + cryptoTickers +
                ", strategyPrompt='" + strategyPrompt + '\'' +
                ", tradingMode=" + tradingMode +
                ", liveSchedule=" + liveSchedule +
                ", backtestSchedule=" + backtestSchedule +
                ", llmModel='" + llmModel + '\'' +
                ", llmTemperature=" + llmTemperature +
                ", llmAgentModels=" + llmAgentModels +
                ", enabledAgents=" + enabledAgents +
                ", positionCooldown=" + positionCooldown +
                ", portfolioSnapshotInterval=" + portfolioSnapshotInterval +
                '}';
    }
}
