package com.quorumtrading.application.orchestration.agents;

import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.port.out.LlmParseFailureRepository;
import com.quorumtrading.model.entity.LLMDecision;
import com.quorumtrading.model.enums.AgentRole;
import com.quorumtrading.model.entity.Position;
import com.quorumtrading.model.orchestration.AgentContext;
import com.quorumtrading.model.valueobject.IndicatorContext;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.enums.InvestmentType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quorumtrading.application.service.ModelResolutionService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Technical analysis agent.
 * Analyzes price patterns, technical indicators, and chart formations.
 */
public class TechnicalAgent implements Agent {

    private static final Logger logger = LoggerFactory.getLogger(TechnicalAgent.class);
    private static final ObjectMapper objectMapper = new ObjectMapper(); // used for indicator snapshot serialization

    private final LLMPort llmPort;
    private final long cacheTTLMinutes;
    private final ModelResolutionService modelResolutionService;
    private final LlmParseFailureRepository parseFailureRepository;

    public TechnicalAgent(LLMPort llmPort, long cacheTTLMinutes, ModelResolutionService modelResolutionService, LlmParseFailureRepository parseFailureRepository) {
        this.llmPort = llmPort;
        this.cacheTTLMinutes = cacheTTLMinutes;
        this.modelResolutionService = modelResolutionService;
        this.parseFailureRepository = parseFailureRepository;
    }

    @Override
    public LLMDecision analyze(AgentContext context) {
        try {
            logger.debug("Starting technical analysis for simulation: {}", context.getSimulationId());

            // Log market data summary in human-readable format
            logMarketDataSummary(context);

            long promptStart = System.nanoTime();
            String prompt = AgentResponseFormat.appendResponseFormat(buildPrompt(context));
            long promptMs = (System.nanoTime() - promptStart) / 1_000_000;
            logger.debug("TechnicalAgent prompt built in {}ms", promptMs);

            // Log the exact prompt being sent to the LLM
            logger.debug("=== TECHNICAL AGENT PROMPT START ===");
            logger.debug(prompt);
            logger.debug("=== TECHNICAL AGENT PROMPT END ===");

            double temperature = context.getStrategyTemperature() != null ? context.getStrategyTemperature() : 0.7;
            com.quorumtrading.model.valueobject.ModelResolution resolution =
                modelResolutionService.requireServableModel(context, getRole());
            String model = resolution.effective();
            boolean noFallback = modelResolutionService.shouldDisableFallback(context, getRole());
            LLMConfig config = new LLMConfig(model, temperature, 1000);
            if (noFallback) {
                config = config.withNoFallback();
            }

            long startTime = System.currentTimeMillis();
            LLMQueryResult llmResponse = llmPort.queryWithUsage(prompt, config);
            long executionTime = System.currentTimeMillis() - startTime;

            // Log raw LLM response for debugging
            logger.debug("=== TECHNICAL AGENT RAW LLM RESPONSE START ===");
            logger.debug("Model: {}", llmResponse.model());
            logger.debug("Response length: {} chars", llmResponse.content().length());
            logger.debug("Tokens: {} prompt, {} completion, {} total",
                llmResponse.usage().promptTokens(),
                llmResponse.usage().completionTokens(),
                llmResponse.usage().totalTokens());
            logger.debug("Cost: raw=${}, billed=${}",
                llmResponse.usage().rawCost(),
                llmResponse.usage().billedCost());
            logger.debug("Response:\n{}", llmResponse.content());
            logger.debug("=== TECHNICAL AGENT RAW LLM RESPONSE END ===");

            LLMDecision decision = AgentResponseParser.parseStandardResponse(
                llmResponse.content(), executionTime, context, prompt, llmResponse.model(), getRole().name(),
                failure -> parseFailureRepository.save(failure.withRequestedModel(model).withTemperature(temperature)));

            // Record what was ASKED for when it differs from what answered, so a substitution is
            // queryable rather than merely logged. llmModel stays the actual responder.
            if (resolution.wasSubstituted()) {
                decision.recordRequestedModel(resolution.requested());
            }

            // Record LLM usage for cost tracking
            decision.recordLLMUsage(llmResponse.usage());

            // Record indicator snapshot for audit trail
            if (context.getIndicators() != null && !context.getIndicators().tickerIndicators().isEmpty()) {
                try {
                    String indicatorJson = objectMapper.writeValueAsString(context.getIndicators().toStorageMap());
                    decision.recordIndicatorSnapshot(indicatorJson);
                    logger.debug("TechnicalAgent: Recorded indicator snapshot ({} bytes)", indicatorJson.length());
                } catch (Exception e) {
                    logger.warn("TechnicalAgent: Failed to serialize indicator snapshot: {}", e.getMessage());
                }
            }

            logger.info("Technical Agent → {}: {} (confidence: {}, {}ms)",
                context.getSimulationId().value().toString().substring(0, 8),
                decision.getRecommendation(),
                String.format("%.2f", decision.getConfidenceScore().value()),
                executionTime);

            return decision;
        } catch (Exception e) {
            logger.error("Technical agent analysis failed for simulation: {}", context.getSimulationId(), e);
            throw new RuntimeException("Technical agent analysis failed", e);
        }
    }

    @Override
    public AgentRole getRole() {
        return AgentRole.TECHNICAL;
    }

    @Override
    public boolean mayUseCachedDecision(AgentContext context) {
        // If caching is disabled (TTL = 0), always query fresh
        if (cacheTTLMinutes == 0) {
            logger.debug("TechnicalAgent: Caching disabled (TTL=0)");
            return false;
        }

        // Check if there's new price data that would require fresh analysis
        com.quorumtrading.model.marketdata.MarketDataContext marketData = context.getMarketData();
        if (marketData != null && !marketData.isEmpty()) {
            // Check if ticker has price events available
            com.quorumtrading.model.valueobject.Symbol symbol = new com.quorumtrading.model.valueobject.Symbol(context.getTicker(), InvestmentType.fromTicker(context.getTicker()));

            if (marketData.hasDataForSymbol(symbol)) {
                // Check for price events in 24h timeframe (most recent)
                List<com.quorumtrading.model.marketdata.MarketEvent> priceEvents =
                    marketData.getPriceEvents(symbol, com.quorumtrading.model.marketdata.Timeframe.LAST_24H);

                if (!priceEvents.isEmpty()) {
                    logger.debug("TechnicalAgent: New price data available, skipping cache");
                    return false;
                }
            }
        }

        // No new price data - caching allowed
        logger.debug("TechnicalAgent: No new price data, may use cached decision (TTL={}min)", cacheTTLMinutes);
        return true;
    }

    private String buildPrompt(AgentContext context) {
        // Get market data context
        com.quorumtrading.model.marketdata.MarketDataContext marketData = context.getMarketData();
        Symbol symbol = new Symbol(context.getTicker(), InvestmentType.fromTicker(context.getTicker()));

        if (marketData == null || marketData.isEmpty() || !marketData.hasDataForSymbol(symbol)) {
            logger.warn("TechnicalAgent: No market data available for symbol {}", context.getTicker());
        }

        // Multi-timeframe analysis (summarised statistics — replaces raw price bars)
        StringBuilder timeframeAnalysis = new StringBuilder();
        if (marketData != null && !marketData.isEmpty() && marketData.hasDataForSymbol(symbol)) {
            timeframeAnalysis.append("\n\nMULTI-TIMEFRAME ANALYSIS:\n");
            timeframeAnalysis.append("(Use this to distinguish short-term momentum from long-term trends)\n\n");

            // Display in order: 24h, 7d, 30d, 90d
            com.quorumtrading.model.marketdata.Timeframe[] timeframes = {
                com.quorumtrading.model.marketdata.Timeframe.LAST_24H,
                com.quorumtrading.model.marketdata.Timeframe.LAST_7D,
                com.quorumtrading.model.marketdata.Timeframe.LAST_30D,
                com.quorumtrading.model.marketdata.Timeframe.LAST_90D
            };
            String[] labels = {
                "24 HOURS (Immediate Momentum)",
                "7 DAYS (Short-term Trend)",
                "30 DAYS (Medium-term Trend)",
                "90 DAYS (Long-term Pattern)"
            };

            for (int i = 0; i < timeframes.length; i++) {
                com.quorumtrading.model.marketdata.Timeframe timeframe = timeframes[i];
                com.quorumtrading.model.marketdata.PriceStatistics stats = marketData.calculatePriceStatistics(symbol, timeframe);

                if (stats.hasData()) {
                    timeframeAnalysis.append(labels[i]).append(":\n");

                    // Price change
                    if (stats.priceChangePercent() != null) {
                        double priceChange = stats.priceChangePercent().doubleValue();
                        timeframeAnalysis.append("  Price Change: ")
                            .append(String.format("%+.2f", priceChange)).append("% (")
                            .append(priceChange > 0 ? "UP" : priceChange < 0 ? "DOWN" : "FLAT")
                            .append(")\n");
                    }

                    // Volatility
                    if (stats.volatility() != null) {
                        double volatility = stats.volatility().doubleValue();
                        String volatilityLevel = volatility < 2 ? "LOW" : volatility < 5 ? "MEDIUM" : "HIGH";
                        timeframeAnalysis.append("  Volatility: ")
                            .append(String.format("%.2f", volatility)).append("% (")
                            .append(volatilityLevel).append(")\n");
                    }

                    // Price range. Formatted at a scale the price level can carry: "%.2f"
                    // renders anything below half a cent as $0.00, so this block reached the
                    // agent as "$0.00 - $0.00 (Current: $0.00)" for a meme coin. The agent
                    // then reported that no close price had been supplied and reasoned from
                    // a substitute — in one audit, a number the user had written into their
                    // own strategy prose. The price WAS present; only this rendering lost it.
                    if (stats.minPrice() != null && stats.maxPrice() != null && stats.latestPrice() != null) {
                        timeframeAnalysis.append("  Price Range: $")
                            .append(IndicatorContext.formatPromptPrice(stats.minPrice()))
                            .append(" - $").append(IndicatorContext.formatPromptPrice(stats.maxPrice()))
                            .append(" (Current: $").append(IndicatorContext.formatPromptPrice(stats.latestPrice()))
                            .append(")\n");
                    }

                    // Event counts
                    int eventCount = marketData.getEventsForSymbol(symbol, timeframe).size();
                    timeframeAnalysis.append("  Data Points: ").append(eventCount).append(" events\n\n");
                }
            }
        }

        // Include technical indicators if available (stocks only)
        StringBuilder indicatorSection = new StringBuilder();
        if (context.getIndicators() != null && !context.getIndicators().tickerIndicators().isEmpty()) {
            indicatorSection.append("\n\n").append(context.getIndicators().toPromptText());
        }

        // Include strategy guidance if available so the agent knows which indicators matter
        String strategyGuidance = "";
        if (context.getStrategy() != null && context.getStrategy().getStrategyPrompt() != null) {
            strategyGuidance = String.format("""

                STRATEGY CONTEXT (use this to focus your analysis on the indicators the strategy cares about):
                %s

                """, context.getStrategy().getStrategyPrompt());
        }

        // Build current holdings info
        // Resolve the real asset class. Symbol equality is over (canonical, type), and the
        // portfolio keys crypto positions as CRYPTO, so a hardcoded STOCK could never match
        // one: every agent was told "0 units (no position)" while holding a crypto position,
        // and position-aware strategy rules could not be honoured.
        Symbol tickerSymbol =
                new Symbol(context.getTicker(), InvestmentType.fromTicker(context.getTicker()));
        Position currentPosition = context.getPortfolio().positions().get(tickerSymbol);
        String holdingsInfo;
        if (currentPosition != null && currentPosition.quantity().compareTo(BigDecimal.ZERO) > 0) {
            holdingsInfo = String.format("- Current Holdings of %s: %s units (avg cost: $%s/unit)",
                context.getTicker(), currentPosition.quantity(), currentPosition.averagePrice().amount());
        } else {
            holdingsInfo = String.format("- Current Holdings of %s: 0 units (no position)", context.getTicker());
        }

        return String.format("""
            You are a Technical Analyst for an AI trading system.

            CRITICAL INSTRUCTIONS:
            - This is a TRADING DECISION task, NOT a code-writing or code-investigation task
            - Make your decision ONLY based on the information provided below
            - Do NOT suggest writing code, investigating codebases, or modifying systems
            - ONLY respond with a trading decision in the required JSON format

            CURRENT PORTFOLIO:
            - Cash: $%s
            - Total Value: $%s
            %s
            %s
            %s%s

            TASK:
            Based on the technical indicators, multi-timeframe analysis, and strategy context above, recommend: BUY / SELL / HOLD

            IMPORTANT: If the strategy specifies which indicators to use, prioritize those indicators in your analysis.
            Otherwise, use the multi-timeframe data to identify:
            - Divergence between short-term momentum (24h) and long-term trends (90d)
            - Whether current volatility is higher/lower than historical patterns
            - Support/resistance levels from price ranges across timeframes

            Provide confidence score (0-1) and reasoning for your TRADING DECISION.
            """,
            context.getPortfolio().cashAvailable().amount(),
            context.getPortfolioTotalValue() != null ? context.getPortfolioTotalValue().amount() : context.getPortfolio().cashAvailable().amount(),
            holdingsInfo,
            strategyGuidance,
            timeframeAnalysis,
            indicatorSection
        );
    }

    /**
     * Log market data summary in human-readable format for debugging.
     */
    private void logMarketDataSummary(AgentContext context) {
        if (!logger.isDebugEnabled()) {
            return;
        }
        com.quorumtrading.model.marketdata.MarketDataContext marketData = context.getMarketData();
        Symbol symbol = new Symbol(context.getTicker(), InvestmentType.fromTicker(context.getTicker()));

        logger.debug("========================================");
        logger.debug("TECHNICAL AGENT - Market Data Summary");
        logger.debug("========================================");
        logger.debug("Ticker: {}", context.getTicker());
        logger.debug("Timestamp: {}", context.getTimestamp());

        if (marketData == null || marketData.isEmpty()) {
            logger.debug("❌ No market data available");
            return;
        }

        if (!marketData.hasDataForSymbol(symbol)) {
            logger.debug("❌ No data for symbol: {}", context.getTicker());
            logger.debug("Available symbols: {}", marketData.getAvailableSymbols());
            return;
        }

        logger.debug("✅ Market data available for {}", context.getTicker());

        // Log price data by timeframe
        logger.debug("\n📊 PRICE DATA BY TIMEFRAME:");
        for (com.quorumtrading.model.marketdata.Timeframe timeframe : com.quorumtrading.model.marketdata.Timeframe.values()) {
            List<com.quorumtrading.model.marketdata.MarketEvent> priceEvents = marketData.getPriceEvents(symbol, timeframe);
            com.quorumtrading.model.marketdata.PriceStatistics stats = marketData.calculatePriceStatistics(symbol, timeframe);

            logger.debug("  {} ({}):", timeframe.name(), timeframe.getDescription());
            logger.debug("    Events: {}", priceEvents.size());

            if (stats.hasData()) {
                if (stats.latestPrice() != null) {
                    logger.debug("    Latest Price: ${}", stats.latestPrice());
                }
                if (stats.priceChangePercent() != null) {
                    logger.debug("    Price Change: {:+.2f}%", stats.priceChangePercent().doubleValue());
                }
                if (stats.volatility() != null) {
                    logger.debug("    Volatility: {:.2f}%", stats.volatility().doubleValue());
                }
                if (stats.minPrice() != null && stats.maxPrice() != null) {
                    logger.debug("    Range: ${} - ${}", stats.minPrice(), stats.maxPrice());
                }
            }
        }

        // Log latest price
        Optional<BigDecimal> latestPrice = marketData.getLatestPrice(symbol);
        if (latestPrice.isPresent()) {
            logger.debug("\n💰 Current Price: ${}", latestPrice.get());
        }

        logger.debug("========================================");
    }

    /**
     * Honour the configured TTL as a real age bound. {@code cacheTTLMinutes} previously only acted
     * as an on/off switch at zero, so a stored HOLD stayed eligible forever.
     */
    @Override
    public boolean isCachedDecisionStillFresh(
            com.quorumtrading.model.entity.LLMDecision cached,
            com.quorumtrading.model.orchestration.AgentContext context) {
        if (cacheTTLMinutes <= 0) {
            return false;
        }
        if (cached == null || cached.getDecisionTimestamp() == null || context.getTimestamp() == null) {
            return false;
        }
        java.time.Duration age =
                java.time.Duration.between(cached.getDecisionTimestamp(), context.getTimestamp());
        return !age.isNegative() && age.toMinutes() <= cacheTTLMinutes;
    }
}
