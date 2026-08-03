package com.quorumtrading.application.orchestration.agents;

import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.port.out.LlmParseFailureRepository;
import com.quorumtrading.model.entity.LLMDecision;
import com.quorumtrading.model.enums.AgentRole;
import com.quorumtrading.model.orchestration.AgentContext;
import com.quorumtrading.model.entity.Position;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.enums.InvestmentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quorumtrading.application.service.ModelResolutionService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class SentimentAgent implements Agent {

    private static final Logger logger = LoggerFactory.getLogger(SentimentAgent.class);

    /**
     * Minimum number of news + social media events needed to produce meaningful sentiment analysis.
     * Below this threshold in backtests, the agent is skipped to avoid wasting an LLM call
     * on a near-random guess. Adjust this value to tune when the agent participates.
     */
    private static final int MIN_NEWS_EVENTS_FOR_BACKTEST = 3;

    private final LLMPort llmPort;
    private final long cacheTTLMinutes;
    private final ModelResolutionService modelResolutionService;
    private final LlmParseFailureRepository parseFailureRepository;

    public SentimentAgent(LLMPort llmPort, long cacheTTLMinutes, ModelResolutionService modelResolutionService, LlmParseFailureRepository parseFailureRepository) {
        this.llmPort = llmPort;
        this.cacheTTLMinutes = cacheTTLMinutes;
        this.modelResolutionService = modelResolutionService;
        this.parseFailureRepository = parseFailureRepository;
    }

    @Override
    public LLMDecision analyze(AgentContext context) {
        try {
            // Log market data summary in human-readable format
            logMarketDataSummary(context);

            long promptStart = System.nanoTime();
            String prompt = AgentResponseFormat.appendResponseFormat(buildPrompt(context));
            long promptMs = (System.nanoTime() - promptStart) / 1_000_000;
            logger.debug("SentimentAgent prompt built in {}ms", promptMs);

            // Log the exact prompt being sent to the LLM
            logger.debug("=== SENTIMENT AGENT PROMPT START ===");
            logger.debug(prompt);
            logger.debug("=== SENTIMENT AGENT PROMPT END ===");

            double temperature = context.getStrategyTemperature() != null ? context.getStrategyTemperature() : 0.7;
            com.quorumtrading.model.valueobject.ModelResolution resolution =
                modelResolutionService.requireServableModel(context, getRole());
            String model = resolution.effective();
            if (context.isLiveSimulation() && model != null && !model.toLowerCase().endsWith(":online")) {
                logger.info("SentimentAgent: Enabling web search for live simulation (model: {} → {}:online)", model, model);
                model = model + ":online";
            }
            final String effectiveModel = model;
            boolean noFallback = modelResolutionService.shouldDisableFallback(context, getRole());
            LLMConfig config = new LLMConfig(effectiveModel, temperature, 1000);
            if (noFallback) {
                config = config.withNoFallback();
            }

            long startTime = System.currentTimeMillis();
            LLMQueryResult llmResponse = llmPort.queryWithUsage(prompt, config);
            long executionTime = System.currentTimeMillis() - startTime;

            // Log raw LLM response for debugging response parsing issues
            logger.debug("=== SENTIMENT AGENT RAW LLM RESPONSE START ===");
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
            logger.debug("=== SENTIMENT AGENT RAW LLM RESPONSE END ===");

            LLMDecision decision = AgentResponseParser.parseStandardResponse(
                llmResponse.content(), executionTime, context, prompt, llmResponse.model(), getRole().name(),
                failure -> parseFailureRepository.save(failure.withRequestedModel(effectiveModel).withTemperature(temperature)));

            // Record what was ASKED for when it differs from what answered, so a substitution is
            // queryable rather than merely logged. llmModel stays the actual responder.
            if (resolution.wasSubstituted()) {
                decision.recordRequestedModel(resolution.requested());
            }

            // Record LLM usage for cost tracking
            decision.recordLLMUsage(llmResponse.usage());

            logger.info("Sentiment Agent → {}: {}", context.getSimulationId().value().toString().substring(0, 8), decision.getRecommendation());
            return decision;
        } catch (Exception e) {
            logger.error("Sentiment agent failed", e);
            throw new RuntimeException("Sentiment agent failed", e);
        }
    }

    @Override
    public AgentRole getRole() {
        return AgentRole.SENTIMENT;
    }

    @Override
    public ContextCheck checkContext(AgentContext context) {
        // Live simulations can search the web — always have enough context
        if (context.isLiveSimulation()) {
            return ContextCheck.ok();
        }

        // For backtests, we need actual news/social data to form a sentiment opinion
        com.quorumtrading.model.marketdata.MarketDataContext marketData = context.getMarketData();
        if (marketData == null || marketData.isEmpty()) {
            return insufficientNews(0);
        }

        Symbol symbol = new Symbol(context.getTicker(), InvestmentType.fromTicker(context.getTicker()));
        int availableEvents = marketData.getNewsEvents(symbol, MIN_NEWS_EVENTS_FOR_BACKTEST).size();

        return availableEvents >= MIN_NEWS_EVENTS_FOR_BACKTEST
            ? ContextCheck.ok()
            : insufficientNews(availableEvents);
    }

    private static ContextCheck insufficientNews(int availableEvents) {
        return ContextCheck.insufficient(String.format(
            "only %d of %d required news/social events stored for this period",
            availableEvents, MIN_NEWS_EVENTS_FOR_BACKTEST));
    }

    @Override
    public boolean mayUseCachedDecision(AgentContext context) {
        // If caching is disabled (TTL = 0), always query fresh
        if (cacheTTLMinutes == 0) {
            logger.debug("SentimentAgent: Caching disabled (TTL=0)");
            return false;
        }

        // Check if there's new sentiment data (news/social) that would require fresh analysis
        com.quorumtrading.model.marketdata.MarketDataContext marketData = context.getMarketData();
        if (marketData != null && !marketData.isEmpty()) {
            com.quorumtrading.model.valueobject.Symbol symbol = new com.quorumtrading.model.valueobject.Symbol(context.getTicker(), InvestmentType.fromTicker(context.getTicker()));

            if (marketData.hasDataForSymbol(symbol)) {
                // Check for news events in 24h and 7d timeframes
                List<com.quorumtrading.model.marketdata.MarketEvent> newsEvents = marketData.getNewsEvents(symbol, 10);

                if (!newsEvents.isEmpty()) {
                    logger.debug("SentimentAgent: New sentiment data available, skipping cache");
                    return false;
                }
            }
        }

        // No new sentiment data - caching allowed
        logger.debug("SentimentAgent: No new sentiment data, may use cached decision (TTL={}min)", cacheTTLMinutes);
        return true;
    }

    private String buildPrompt(AgentContext context) {
        StringBuilder sentimentData = new StringBuilder();

        // Get market data context
        com.quorumtrading.model.marketdata.MarketDataContext marketData = context.getMarketData();
        Symbol symbol = new Symbol(context.getTicker(), InvestmentType.fromTicker(context.getTicker()));

        if (marketData != null && !marketData.isEmpty() && marketData.hasDataForSymbol(symbol)) {
            // Get news and social media events
            List<com.quorumtrading.model.marketdata.MarketEvent> newsEvents = marketData.getNewsEvents(symbol, 30);

            // Separate into social media and news
            List<com.quorumtrading.model.marketdata.MarketEvent> socialEvents = newsEvents.stream()
                .filter(e -> e.eventType() == com.quorumtrading.model.enums.EventType.SOCIAL)
                .toList();
            List<com.quorumtrading.model.marketdata.MarketEvent> newsOnly = newsEvents.stream()
                .filter(e -> e.eventType() == com.quorumtrading.model.enums.EventType.NEWS)
                .toList();

            // Extract Reddit posts for sentiment
            sentimentData.append("\nSOCIAL MEDIA SENTIMENT:\n");
            if (!socialEvents.isEmpty()) {
                for (com.quorumtrading.model.marketdata.MarketEvent event : socialEvents) {
                    Map<String, Object> data = event.rawData();
                    sentimentData.append("- [").append(event.timestamp())
                        .append("] ").append(event.symbol())
                        .append(": ").append(data.getOrDefault("title", "No title"))
                        .append(" (Score: ").append(data.getOrDefault("score", "N/A"))
                        .append(", Comments: ").append(data.getOrDefault("num_comments", "N/A"))
                        .append(")\n");
                }
            } else {
                sentimentData.append("- No recent social media activity\n");
            }

            // Extract news headlines and summaries for sentiment analysis
            sentimentData.append("\nNEWS HEADLINES (for sentiment):\n");
            if (!newsOnly.isEmpty()) {
                for (com.quorumtrading.model.marketdata.MarketEvent event : newsOnly) {
                    Map<String, Object> data = event.rawData();
                    sentimentData.append("- [").append(event.timestamp())
                        .append("] ").append(event.symbol())
                        .append(": ").append(data.getOrDefault("headline", data.getOrDefault("title", "No headline")))
                        .append('\n');
                    String summary = (String) data.get("summary");
                    if (summary != null && !summary.isBlank()) {
                        sentimentData.append("  ").append(truncate(summary, 400)).append('\n');
                    }
                }
            } else {
                sentimentData.append("- No recent news\n");
            }
        } else {
            logger.warn("SentimentAgent: No market data available for symbol {}", context.getTicker());
        }

        // Include pre-calculated sentiment scores if available
        if (!context.getSentimentScores().isEmpty()) {
            sentimentData.append("\nPRE-CALCULATED SENTIMENT SCORES (-1 to +1):\n");
            for (Map.Entry<String, Double> entry : context.getSentimentScores().entrySet()) {
                sentimentData.append("- ").append(entry.getKey()).append(": ")
                    .append(String.format("%.2f", entry.getValue())).append('\n');
            }
        }

        sentimentData.append("\nAnalysis Timestamp: ").append(context.getTimestamp()).append('\n');

        // Add multi-timeframe sentiment perspective
        StringBuilder sentimentEvolution = new StringBuilder();
        if (marketData != null && !marketData.isEmpty() && marketData.hasDataForSymbol(symbol)) {
            sentimentEvolution.append("\n\nSENTIMENT EVOLUTION ACROSS TIMEFRAMES:\n");
            sentimentEvolution.append("(Use this to distinguish temporary sentiment spikes from sustained trends)\n\n");

            com.quorumtrading.model.marketdata.Timeframe[] timeframes = {
                com.quorumtrading.model.marketdata.Timeframe.LAST_24H,
                com.quorumtrading.model.marketdata.Timeframe.LAST_7D,
                com.quorumtrading.model.marketdata.Timeframe.LAST_30D,
                com.quorumtrading.model.marketdata.Timeframe.LAST_90D
            };
            String[] labels = {"24h (Recent)", "7d (Short-term)", "30d (Medium-term)", "90d (Long-term)"};

            sentimentEvolution.append("News & Sentiment Activity:\n");
            for (int i = 0; i < timeframes.length; i++) {
                com.quorumtrading.model.marketdata.Timeframe timeframe = timeframes[i];
                Map<com.quorumtrading.model.enums.EventType, Long> eventCounts =
                    marketData.getEventTypeCounts(symbol, timeframe);

                long newsCount = eventCounts.getOrDefault(com.quorumtrading.model.enums.EventType.NEWS, 0L);
                long socialCount = eventCounts.getOrDefault(com.quorumtrading.model.enums.EventType.SOCIAL, 0L);

                // Get price trend for correlation analysis
                com.quorumtrading.model.marketdata.PriceStatistics stats = marketData.calculatePriceStatistics(symbol, timeframe);
                String priceTrend = "UNKNOWN";
                if (stats.hasData() && stats.priceChangePercent() != null) {
                    double priceChange = stats.priceChangePercent().doubleValue();
                    priceTrend = priceChange > 0 ? "UP" : priceChange < 0 ? "DOWN" : "FLAT";
                }

                sentimentEvolution.append("  ").append(labels[i]).append(": ")
                    .append(newsCount).append(" news, ").append(socialCount)
                    .append(" social posts (price ").append(priceTrend).append(")\n");
            }

            // Interpretation guidance
            sentimentEvolution.append("\nSentiment Pattern Interpretation:\n");
            sentimentEvolution.append("- Rising news/social activity + price UP = Growing positive sentiment\n");
            sentimentEvolution.append("- Rising news/social activity + price DOWN = Growing negative sentiment\n");
            sentimentEvolution.append("- High activity in short-term only = Temporary hype/panic\n");
            sentimentEvolution.append("- Sustained activity across timeframes = Fundamental sentiment shift\n");
            sentimentEvolution.append("- Low activity with price movement = Sentiment lagging price action\n");
        }

        // Include strategy guidance if available
        String strategyGuidance = "";
        if (context.getStrategy() != null && context.getStrategy().getStrategyPrompt() != null) {
            strategyGuidance = String.format("""

                STRATEGY CONTEXT (use this to understand what the strategy cares about):
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
            You are a Sentiment Analyst for an AI trading system.

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
            Based on social media sentiment, news tone, sentiment scores, and evolution above, recommend: BUY / SELL / HOLD

            IMPORTANT: If the strategy specifies decision criteria, prioritize those over generic sentiment analysis.
            Otherwise, use multi-timeframe sentiment evolution to:
            - Distinguish temporary sentiment spikes from sustained trends
            - Identify whether current sentiment is leading or lagging price action
            - Assess if news volume indicates growing/declining market interest

            Analyze overall market sentiment and community perception toward the assets.
            Provide confidence score (0-1) and reasoning for your TRADING DECISION.
            """, context.getPortfolio().cashAvailable().amount(),
            context.getPortfolioTotalValue() != null ? context.getPortfolioTotalValue().amount() : context.getPortfolio().cashAvailable().amount(),
            holdingsInfo,
            strategyGuidance,
            sentimentData, sentimentEvolution);
    }

    private static String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
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
        logger.debug("SENTIMENT AGENT - Market Data Summary");
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

        // Get news and social media events
        List<com.quorumtrading.model.marketdata.MarketEvent> newsEvents = marketData.getNewsEvents(symbol, 30);

        // Separate into social media and news
        List<com.quorumtrading.model.marketdata.MarketEvent> socialEvents = newsEvents.stream()
            .filter(e -> e.eventType() == com.quorumtrading.model.enums.EventType.SOCIAL)
            .toList();
        List<com.quorumtrading.model.marketdata.MarketEvent> newsOnly = newsEvents.stream()
            .filter(e -> e.eventType() == com.quorumtrading.model.enums.EventType.NEWS)
            .toList();

        // Log social media sentiment
        logger.debug("\n💬 SOCIAL MEDIA: {} posts", socialEvents.size());
        if (!socialEvents.isEmpty()) {
            int count = 0;
            for (com.quorumtrading.model.marketdata.MarketEvent event : socialEvents) {
                if (count >= 3) {
                    logger.debug("  ... and {} more", socialEvents.size() - 3);
                    break;
                }
                Map<String, Object> data = event.rawData();
                logger.debug("  [{}] {}: {} (Score: {}, Comments: {})",
                    event.timestamp(),
                    event.symbol(),
                    data.getOrDefault("title", "No title"),
                    data.getOrDefault("score", "N/A"),
                    data.getOrDefault("num_comments", "N/A"));
                count++;
            }
        }

        // Log news headlines
        logger.debug("\n📰 NEWS: {} articles", newsOnly.size());
        if (!newsOnly.isEmpty()) {
            int count = 0;
            for (com.quorumtrading.model.marketdata.MarketEvent event : newsOnly) {
                if (count >= 3) {
                    logger.debug("  ... and {} more", newsOnly.size() - 3);
                    break;
                }
                Map<String, Object> data = event.rawData();
                String title = (String) data.getOrDefault("headline", data.getOrDefault("title", "No headline"));
                logger.debug("  [{}] {}: {}", event.timestamp(), event.symbol(), title);
                count++;
            }
        }

        // Log sentiment activity across timeframes
        logger.debug("\n📊 SENTIMENT ACTIVITY:");
        com.quorumtrading.model.marketdata.Timeframe[] timeframes = {
            com.quorumtrading.model.marketdata.Timeframe.LAST_24H,
            com.quorumtrading.model.marketdata.Timeframe.LAST_7D,
            com.quorumtrading.model.marketdata.Timeframe.LAST_30D,
            com.quorumtrading.model.marketdata.Timeframe.LAST_90D
        };
        for (com.quorumtrading.model.marketdata.Timeframe timeframe : timeframes) {
            Map<com.quorumtrading.model.enums.EventType, Long> eventCounts = marketData.getEventTypeCounts(symbol, timeframe);
            long newsCount = eventCounts.getOrDefault(com.quorumtrading.model.enums.EventType.NEWS, 0L);
            long socialCount = eventCounts.getOrDefault(com.quorumtrading.model.enums.EventType.SOCIAL, 0L);
            logger.debug("  {}: {} news, {} social posts", timeframe.getDescription(), newsCount, socialCount);
        }

        // Log pre-calculated sentiment scores
        if (!context.getSentimentScores().isEmpty()) {
            logger.debug("\n🎯 PRE-CALCULATED SENTIMENT SCORES:");
            for (Map.Entry<String, Double> entry : context.getSentimentScores().entrySet()) {
                String emoji = entry.getValue() > 0.3 ? "😊" : entry.getValue() < -0.3 ? "😟" : "😐";
                logger.debug("  {}: {:.2f} {}", entry.getKey(), entry.getValue(), emoji);
            }
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
