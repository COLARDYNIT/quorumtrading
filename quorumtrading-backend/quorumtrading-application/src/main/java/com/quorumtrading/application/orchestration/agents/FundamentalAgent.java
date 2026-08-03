package com.quorumtrading.application.orchestration.agents;

import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.port.out.LlmParseFailureRepository;
import com.quorumtrading.model.entity.LLMDecision;
import com.quorumtrading.model.enums.AgentRole;
import com.quorumtrading.model.marketdata.FinancialMetrics;
import com.quorumtrading.model.marketdata.EarningsRecord;
import com.quorumtrading.model.marketdata.AnalystConsensus;
import com.quorumtrading.model.orchestration.AgentContext;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.enums.InvestmentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quorumtrading.application.service.ModelResolutionService;

import com.quorumtrading.model.entity.Position;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FundamentalAgent implements com.quorumtrading.application.orchestration.agents.Agent {

    private static final Logger logger = LoggerFactory.getLogger(FundamentalAgent.class);

    /**
     * Minimum number of news events needed to add value beyond what TechnicalAgent already covers.
     * Without news, fundamental analysis degrades to pure price analysis — redundant with the
     * Technical agent. Lower threshold than SentimentAgent because even a single earnings headline
     * can be meaningful for fundamentals. Adjust this value to tune when the agent participates.
     */
    private static final int MIN_NEWS_EVENTS_FOR_BACKTEST = 2;

    private final LLMPort llmPort;
    private final long cacheTTLMinutes;
    private final ModelResolutionService modelResolutionService;
    private final LlmParseFailureRepository parseFailureRepository;

    public FundamentalAgent(LLMPort llmPort, long cacheTTLMinutes, ModelResolutionService modelResolutionService, LlmParseFailureRepository parseFailureRepository) {
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
            logger.debug("FundamentalAgent prompt built in {}ms", promptMs);

            // Log the exact prompt being sent to the LLM
            logger.debug("=== FUNDAMENTAL AGENT PROMPT START ===");
            logger.debug(prompt);
            logger.debug("=== FUNDAMENTAL AGENT PROMPT END ===");

            double temperature = context.getStrategyTemperature() != null ? context.getStrategyTemperature() : 0.7;
            com.quorumtrading.model.valueobject.ModelResolution resolution =
                modelResolutionService.requireServableModel(context, getRole());
            String model = resolution.effective();
            if (context.isLiveSimulation() && model != null && !model.toLowerCase().endsWith(":online")) {
                logger.info("FundamentalAgent: Enabling web search for live simulation (model: {} → {}:online)", model, model);
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
            logger.debug("=== FUNDAMENTAL AGENT RAW LLM RESPONSE START ===");
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
            logger.debug("=== FUNDAMENTAL AGENT RAW LLM RESPONSE END ===");

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

            logger.info("Fundamental Agent → {}: {}", context.getSimulationId().value().toString().substring(0, 8), decision.getRecommendation());
            return decision;
        } catch (Exception e) {
            logger.error("Fundamental agent failed", e);
            throw new RuntimeException("Fundamental agent failed", e);
        }
    }

    @Override
    public AgentRole getRole() {
        return AgentRole.FUNDAMENTAL;
    }

    @Override
    public ContextCheck checkContext(AgentContext context) {
        // Live simulations can search the web — always have enough context
        if (context.isLiveSimulation()) {
            return ContextCheck.ok();
        }

        // For backtests, we need actual news data to provide insight beyond price analysis
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
            "only %d of %d required news events stored for this period",
            availableEvents, MIN_NEWS_EVENTS_FOR_BACKTEST));
    }

    @Override
    public boolean mayUseCachedDecision(AgentContext context) {
        // If caching is disabled (TTL = 0), always query fresh
        if (cacheTTLMinutes == 0) {
            logger.debug("FundamentalAgent: Caching disabled (TTL=0)");
            return false;
        }

        // Check if there's new news or fundamental data that would require fresh analysis
        com.quorumtrading.model.marketdata.MarketDataContext marketData = context.getMarketData();
        if (marketData != null && !marketData.isEmpty()) {
            com.quorumtrading.model.valueobject.Symbol symbol = new com.quorumtrading.model.valueobject.Symbol(context.getTicker(), InvestmentType.fromTicker(context.getTicker()));

            if (marketData.hasDataForSymbol(symbol)) {
                // Check for news events in 24h and 7d timeframes
                List<com.quorumtrading.model.marketdata.MarketEvent> newsEvents = marketData.getNewsEvents(symbol, 10);

                if (!newsEvents.isEmpty()) {
                    logger.debug("FundamentalAgent: New news/social data available, skipping cache");
                    return false;
                }
            }
        }

        // No new fundamental data - caching allowed
        logger.debug("FundamentalAgent: No new news/social data, may use cached decision (TTL={}min)", cacheTTLMinutes);
        return true;
    }

    private String buildPrompt(AgentContext context) {
        StringBuilder fundamentalData = new StringBuilder();

        // Get market data context
        com.quorumtrading.model.marketdata.MarketDataContext marketData = context.getMarketData();
        Symbol symbol = new Symbol(context.getTicker(), InvestmentType.fromTicker(context.getTicker()));

        logger.debug("FundamentalAgent building prompt - marketData null? {}", marketData == null);
        if (marketData != null) {
            logger.debug("FundamentalAgent marketData isEmpty? {}", marketData.isEmpty());
            logger.debug("FundamentalAgent available symbols: {}", marketData.getAvailableSymbols());
            logger.debug("FundamentalAgent hasDataForSymbol({}): {}", context.getTicker(), marketData.hasDataForSymbol(symbol));
        }

        if (marketData != null && !marketData.isEmpty() && marketData.hasDataForSymbol(symbol)) {
            // Extract current price for context
            fundamentalData.append("\nCURRENT MARKET PRICES:\n");
            Optional<BigDecimal> latestPrice = marketData.getLatestPrice(symbol);
            if (latestPrice.isPresent()) {
                fundamentalData.append("- ").append(context.getTicker())
                    .append(": $").append(latestPrice.get())
                    .append(" (as of ").append(context.getTimestamp()).append(")\n");
                logger.debug("FundamentalAgent: Added price for {}: ${}", context.getTicker(), latestPrice.get());
            } else {
                logger.warn("FundamentalAgent: No price data available for {}", context.getTicker());
            }

            // Extract news and social media events
            fundamentalData.append("\nRECENT NEWS & MARKET INFORMATION:\n");
            List<com.quorumtrading.model.marketdata.MarketEvent> newsEvents = marketData.getNewsEvents(symbol, 20);
            logger.debug("FundamentalAgent: Found {} news events for {}", newsEvents.size(), context.getTicker());

            if (!newsEvents.isEmpty()) {
                for (com.quorumtrading.model.marketdata.MarketEvent event : newsEvents) {
                    Map<String, Object> data = event.rawData();
                    String title = (String) data.getOrDefault("headline", data.getOrDefault("title", "No headline"));
                    String source = event.source() != null ? event.source() : event.symbol();
                    fundamentalData.append("- [").append(event.timestamp())
                        .append("] [").append(source)
                        .append("] ").append(title).append('\n');
                    String summary = (String) data.get("summary");
                    if (summary != null && !summary.isBlank()) {
                        fundamentalData.append("  ").append(truncate(summary, 400)).append('\n');
                    }
                    logger.debug("FundamentalAgent added news: [{}] {}", source, title);
                }
            } else {
                fundamentalData.append("- No recent news or market information available\n");
                logger.debug("FundamentalAgent: No news found, added fallback message");
            }
        } else {
            logger.warn("FundamentalAgent: No market data available for symbol {}", context.getTicker());
        }

        // Include structured financial metrics when available
        String financialSection = "";
        FinancialMetrics fm = context.getFinancialMetrics();
        if (fm != null && fm.hasValuationData()) {
            financialSection = formatFinancialMetrics(fm);
        }
        fundamentalData.append(financialSection);

        fundamentalData.append("\nAnalysis Timestamp: ").append(context.getTimestamp()).append('\n');

        // Add multi-timeframe context for fundamental analysis
        StringBuilder timeframeContext = new StringBuilder();

        if (marketData != null && !marketData.isEmpty() && marketData.hasDataForSymbol(symbol)) {
            timeframeContext.append("\n\nMARKET TRAJECTORY & STABILITY:\n");
            timeframeContext.append("(Use this to assess market confidence and fundamental strength)\n\n");

            // Display trend summary across timeframes
            com.quorumtrading.model.marketdata.Timeframe[] timeframes = {
                com.quorumtrading.model.marketdata.Timeframe.LAST_24H,
                com.quorumtrading.model.marketdata.Timeframe.LAST_7D,
                com.quorumtrading.model.marketdata.Timeframe.LAST_30D,
                com.quorumtrading.model.marketdata.Timeframe.LAST_90D
            };
            String[] labels = {"24h", "7d", "30d", "90d"};

            timeframeContext.append("Price Trajectory Summary:\n");
            for (int i = 0; i < timeframes.length; i++) {
                com.quorumtrading.model.marketdata.Timeframe timeframe = timeframes[i];
                com.quorumtrading.model.marketdata.PriceStatistics stats = marketData.calculatePriceStatistics(symbol, timeframe);

                if (stats.hasData() && stats.priceChangePercent() != null) {
                    double priceChange = stats.priceChangePercent().doubleValue();
                    String trend = priceChange > 0 ? "UP" : priceChange < 0 ? "DOWN" : "FLAT";

                    String volatilityLevel = "UNKNOWN";
                    if (stats.volatility() != null) {
                        double volatility = stats.volatility().doubleValue();
                        volatilityLevel = volatility < 2 ? "LOW" : volatility < 5 ? "MEDIUM" : "HIGH";
                    }

                    timeframeContext.append("  ").append(labels[i]).append(": ")
                        .append(String.format("%+.2f", priceChange)).append("% (")
                        .append(trend).append(" trend, ").append(volatilityLevel)
                        .append(" volatility)\n");
                }
            }

            // Interpret what this means for fundamentals
            timeframeContext.append("\nFundamental Interpretation Hints:\n");
            timeframeContext.append("- Consistent trend across timeframes = Strong fundamental shift\n");
            timeframeContext.append("- Divergent trends = Possible short-term noise vs. long-term value\n");
            timeframeContext.append("- Increasing volatility = Growing market uncertainty\n");
            timeframeContext.append("- Decreasing volatility = Market reaching consensus on value\n");
        } else {
            logger.info("FundamentalAgent: No multi-timeframe data available");
        }

        // Include strategy guidance if available
        String strategyGuidance = "";
        if (context.getStrategy() != null && context.getStrategy().getStrategyPrompt() != null) {
            strategyGuidance = String.format("""

                STRATEGY CONTEXT (use this to focus your fundamental analysis on what the strategy cares about):
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
                You are a Fundamental Analyst for an AI trading system.

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
                Based on the news, social media activity, market prices, trajectory, and portfolio above, recommend: BUY / SELL / HOLD

                IMPORTANT: If the strategy specifies decision criteria, prioritize those over generic fundamental analysis.
                Otherwise, consider multi-timeframe trends to:
                - Identify whether news/information aligns with long-term trajectory or is short-term noise
                - Assess if price movements reflect changing fundamentals or temporary sentiment
                - Determine if market volatility indicates fundamental uncertainty or speculation

                Analyze how news events, social media discussions, and market information may impact asset valuations and fundamentals.
                Consider both traditional news sources and community insights from platforms like Reddit.
                Provide confidence score (0-1) and reasoning for your TRADING DECISION.
                """, context.getPortfolio().cashAvailable().amount(),
                context.getPortfolioTotalValue() != null ? context.getPortfolioTotalValue().amount() : context.getPortfolio().cashAvailable().amount(),
                holdingsInfo,
                strategyGuidance,
                fundamentalData, timeframeContext);
    }

    private String formatFinancialMetrics(FinancialMetrics fm) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== FINANCIAL METRICS ===\n");

        // Company Profile
        if (fm.companyName() != null) {
            sb.append(String.format("Company: %s", fm.companyName()));
            if (fm.industry() != null) sb.append(String.format(" | Industry: %s", fm.industry()));
            if (fm.marketCapMillions() != null) {
                double capB = fm.marketCapMillions().doubleValue() / 1000.0;
                sb.append(String.format(" | Market Cap: $%.1fB", capB));
            }
            sb.append("\n\n");
        }

        // Valuation
        sb.append("Valuation:\n");
        appendMetric(sb, "P/E (TTM)", fm.peRatioTTM(), "x");
        appendMetric(sb, "Forward P/E", fm.forwardPE(), "x");
        appendMetric(sb, "P/B", fm.priceToBook(), "x");
        appendMetric(sb, "P/S", fm.priceToSales(), "x");
        appendMetric(sb, "EV/EBITDA", fm.evToEbitda(), "x");
        appendMetric(sb, "PEG", fm.pegRatio(), "");
        sb.append("\n");

        // Growth
        sb.append("Growth:\n");
        appendMetric(sb, "EPS Growth (YoY)", fm.epsGrowthYoYPercent(), "%");
        appendMetric(sb, "EPS Growth (5Y)", fm.epsGrowth5YPercent(), "%");
        appendMetric(sb, "Revenue Growth (YoY)", fm.revenueGrowthYoYPercent(), "%");
        appendMetric(sb, "Revenue Growth (5Y)", fm.revenueGrowth5YPercent(), "%");
        sb.append("\n");

        // Profitability
        sb.append("Profitability:\n");
        appendMetric(sb, "Gross Margin", fm.grossMarginPercent(), "%");
        appendMetric(sb, "Operating Margin", fm.operatingMarginPercent(), "%");
        appendMetric(sb, "Net Profit Margin", fm.netProfitMarginPercent(), "%");
        appendMetric(sb, "ROE", fm.returnOnEquityPercent(), "%");
        appendMetric(sb, "ROA", fm.returnOnAssetsPercent(), "%");
        sb.append("\n");

        // Financial Health
        sb.append("Financial Health:\n");
        appendMetric(sb, "Debt/Equity", fm.debtToEquity(), "x");
        appendMetric(sb, "Current Ratio", fm.currentRatio(), "x");
        appendMetric(sb, "Quick Ratio", fm.quickRatio(), "x");
        sb.append("\n");

        // Per Share
        sb.append("Per Share:\n");
        appendMetric(sb, "EPS (TTM)", fm.epsTTM(), "");
        appendMetric(sb, "Book Value", fm.bookValuePerShare(), "");
        appendMetric(sb, "Dividend Yield", fm.dividendYieldPercent(), "%");
        if (fm.beta() != null) {
            sb.append(String.format("  Beta: %.2f\n", fm.beta()));
        }
        sb.append("\n");

        // Earnings
        if (fm.hasEarningsData()) {
            sb.append("Recent Earnings (EPS actual vs estimate):\n");
            for (EarningsRecord er : fm.recentEarnings()) {
                if (er.actualEps() != null && er.estimateEps() != null) {
                    String outcome = er.beat() ? "beat" : er.missed() ? "missed" : "met";
                    sb.append(String.format("  Q%d %d: $%.2f vs $%.2f (%s by %+.1f%%)\n",
                        er.quarter(), er.year(), er.actualEps(), er.estimateEps(),
                        outcome, er.surprisePercent() != null ? er.surprisePercent().doubleValue() : 0.0));
                }
            }
            sb.append("\n");
        }

        // Analyst Consensus
        if (fm.hasAnalystData()) {
            AnalystConsensus ac = fm.analystConsensus();
            sb.append(String.format("Analyst Consensus (%d analysts):\n", ac.totalAnalysts()));
            sb.append(String.format("  %d Strong Buy | %d Buy | %d Hold | %d Sell | %d Strong Sell\n",
                ac.strongBuy(), ac.buy(), ac.hold(), ac.sell(), ac.strongSell()));
        }

        return sb.toString();
    }

    private void appendMetric(StringBuilder sb, String label, BigDecimal value, String suffix) {
        if (value != null) {
            sb.append(String.format("  %s: %.2f%s\n", label, value.doubleValue(), suffix));
        }
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
        logger.debug("FUNDAMENTAL AGENT - Market Data Summary");
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

        // Log current price
        Optional<BigDecimal> latestPrice = marketData.getLatestPrice(symbol);
        if (latestPrice.isPresent()) {
            logger.debug("\n💰 Current Price: ${}", latestPrice.get());
        }

        // Log news events
        List<com.quorumtrading.model.marketdata.MarketEvent> newsEvents = marketData.getNewsEvents(symbol, 20);
        logger.debug("\n📰 NEWS EVENTS: {} events", newsEvents.size());
        if (!newsEvents.isEmpty()) {
            int count = 0;
            for (com.quorumtrading.model.marketdata.MarketEvent event : newsEvents) {
                if (count >= 5) {
                    logger.debug("  ... and {} more", newsEvents.size() - 5);
                    break;
                }
                Map<String, Object> data = event.rawData();
                String title = (String) data.getOrDefault("headline", data.getOrDefault("title", "No headline"));
                String source = event.source() != null ? event.source() : event.symbol();
                logger.debug("  [{}] {}: {}", event.timestamp(), source, title);
                count++;
            }
        }

        // Log price trajectory across timeframes
        logger.debug("\n📈 PRICE TRAJECTORY:");
        com.quorumtrading.model.marketdata.Timeframe[] timeframes = {
            com.quorumtrading.model.marketdata.Timeframe.LAST_24H,
            com.quorumtrading.model.marketdata.Timeframe.LAST_7D,
            com.quorumtrading.model.marketdata.Timeframe.LAST_30D,
            com.quorumtrading.model.marketdata.Timeframe.LAST_90D
        };
        for (com.quorumtrading.model.marketdata.Timeframe timeframe : timeframes) {
            com.quorumtrading.model.marketdata.PriceStatistics stats = marketData.calculatePriceStatistics(symbol, timeframe);
            if (stats.hasData() && stats.priceChangePercent() != null) {
                double priceChange = stats.priceChangePercent().doubleValue();
                String trend = priceChange > 0 ? "↑" : priceChange < 0 ? "↓" : "→";
                logger.debug("  {}: {:+.2f}% {}", timeframe.getDescription(), priceChange, trend);
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
