package com.quorumtrading.application.service.consensus;

import com.quorumtrading.application.port.out.ActiveLlmEnginePort;
import com.quorumtrading.application.port.out.ConsensusDecisionRepository;
import com.quorumtrading.application.port.out.FinancialDataPort;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SystemModelDefaultsPort;
import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.application.service.IndicatorEnrichmentService;
import com.quorumtrading.model.entity.ConsensusDecision;
import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Position;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.enums.AgentRole;
import com.quorumtrading.model.enums.ConsensusActionType;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.LlmProvider;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.enums.TradingMode;
import com.quorumtrading.model.marketdata.FinancialMetrics;
import com.quorumtrading.model.marketdata.MarketDataContext;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.marketdata.Timeframe;
import com.quorumtrading.model.orchestration.AgentContext;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.valueobject.IndicatorContext;
import com.quorumtrading.model.valueobject.MarketHours;
import com.quorumtrading.model.valueobject.ModelId;
import com.quorumtrading.model.valueobject.ModelPreferences;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.TradingContext;
import com.quorumtrading.model.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds everything an agent needs to evaluate a ticker: multi-timeframe market data,
 * indicators, portfolio state, recent-trade history, model selection, cooldown state,
 * and the per-ticker {@link AgentContext}. Also hosts the pre-flight market-data
 * availability check.
 *
 * <p>Plain POJO wired via {@code ApplicationConfig} (CDI convention: application
 * services are produced, not annotated). Extracted move-only from
 * {@code ReachConsensusService} — behavior preserved.</p>
 */
public class ConsensusContextBuilder {

    private static final Logger logger = LoggerFactory.getLogger(ConsensusContextBuilder.class);

    private final MarketEventRepository marketEventRepository;
    private final IndicatorEnrichmentService indicatorEnrichmentService;
    private final FinancialDataPort financialDataPort;
    private final PortfolioRepository portfolioRepository;
    private final ConsensusDecisionRepository consensusDecisionRepository;
    private final UserBudgetRepository userBudgetRepository;
    private final SystemModelDefaultsPort systemModelDefaultsPort;
    private final ActiveLlmEnginePort activeLlmEnginePort;

    public ConsensusContextBuilder(
            MarketEventRepository marketEventRepository,
            IndicatorEnrichmentService indicatorEnrichmentService,
            FinancialDataPort financialDataPort,
            PortfolioRepository portfolioRepository,
            ConsensusDecisionRepository consensusDecisionRepository,
            UserBudgetRepository userBudgetRepository,
            SystemModelDefaultsPort systemModelDefaultsPort) {
        this(marketEventRepository, indicatorEnrichmentService, financialDataPort, portfolioRepository,
                consensusDecisionRepository, userBudgetRepository, systemModelDefaultsPort, Optional::empty);
    }

    public ConsensusContextBuilder(
            MarketEventRepository marketEventRepository,
            IndicatorEnrichmentService indicatorEnrichmentService,
            FinancialDataPort financialDataPort,
            PortfolioRepository portfolioRepository,
            ConsensusDecisionRepository consensusDecisionRepository,
            UserBudgetRepository userBudgetRepository,
            SystemModelDefaultsPort systemModelDefaultsPort,
            ActiveLlmEnginePort activeLlmEnginePort) {
        this.marketEventRepository = marketEventRepository;
        this.indicatorEnrichmentService = indicatorEnrichmentService;
        this.financialDataPort = financialDataPort;
        this.portfolioRepository = portfolioRepository;
        this.consensusDecisionRepository = consensusDecisionRepository;
        this.userBudgetRepository = userBudgetRepository;
        this.systemModelDefaultsPort = systemModelDefaultsPort;
        this.activeLlmEnginePort = activeLlmEnginePort;
    }

    /** Holds resolved user model preferences: global + per-agent for a single trading context. */
    public record UserModelPrefs(String globalModel, Map<AgentRole, String> agentModels) {
        static final UserModelPrefs EMPTY = new UserModelPrefs(null, Map.of());
    }

    /** Complete model selection for a consensus round: user prefs + system defaults. */
    public record ModelSelection(
            TradingContext tradingContext,
            UserModelPrefs userModelPrefs,
            String systemDefault,
            Map<AgentRole, String> systemAgentDefaults) {
    }

    /** A ticker skipped because its position cooldown has not yet elapsed. */
    public record CooldownSkip(String note, Duration elapsed, Duration cooldown) {
    }

    /** A ticker skipped because the market was closed (no price bar) on the evaluation day. */
    public record MarketClosedSkip(String note) {
    }

    /**
     * Backtest guard for STOCK tickers: if the evaluation day has no price bar (market holiday,
     * or a gap in backfilled data), the mock broker cannot execute a trade at that timestamp —
     * running the agents would only burn LLM calls to produce a decision that gets REJECTED.
     * Live/paper simulations are exempt: they execute at broker prices, not historical bars.
     *
     * @return a skip with a human-readable note when the ticker should not be evaluated
     */
    public Optional<MarketClosedSkip> checkMarketClosed(
            Simulation simulation,
            StrategyConfig strategyConfig,
            String ticker,
            Instant analysisTimestamp) {
        if (!simulation.isBacktest()) {
            return Optional.empty();
        }
        Optional<Symbol> stockSymbol = strategyConfig.getStockTickers().stream()
            .filter(symbol -> symbol.ticker().equals(ticker))
            .findFirst();
        if (stockSymbol.isEmpty()) {
            return Optional.empty(); // crypto trades 24/7 — bars exist every day
        }

        // The trading day belongs to the VENUE, not to whatever timezone the user happened to
        // write their schedule in. Deriving it from the strategy's own zone made the date wrong
        // for any clock far enough from New York to cross midnight: an evaluation at Friday
        // 17:00Z is Friday 13:00 in New York with a bar on the books, but Saturday in Tokyo — so
        // the lookup asked for Saturday, found nothing, and skipped. A Tokyo-clock strategy
        // skipped EVERY evaluation and still reported COMPLETED at 0.00%.
        LocalDate tradingDay = analysisTimestamp.atZone(MarketHours.US_EASTERN).toLocalDate();
        // Bars are stamped in UTC — daily bars at the UTC midnight of their trading date, intraday
        // bars at their real UTC time. Both fall inside the UTC calendar day, so the lookup window
        // must be the UTC day. Using the exchange-local day instead shifts the window forward by the
        // zone offset (e.g. +4h for America/New_York in summer), which pushes the day's own daily
        // bar out of range and pulls the NEXT day's bar in — so every Friday and every pre-holiday
        // session was wrongly reported as "market closed" and skipped.
        Instant dayStart = tradingDay.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd = tradingDay.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        long priceBars = marketEventRepository.countEventsInRange(
            stockSymbol.get(), EventType.PRICE, dayStart, dayEnd);
        if (priceBars > 0) {
            return Optional.empty();
        }
        return Optional.of(new MarketClosedSkip(
            "Consensus skipped for " + ticker + ": no price bar on " + tradingDay
                + " (market closed — holiday or non-trading day)"));
    }

    /**
     * Calculate indicators for the given tickers (stock and crypto — both read from price_bars).
     * Pass analysisTimestamp so indicators use historical prices up to the backtest date,
     * not current prices (which would give wrong SMA/RSI/MACD values in backtests).
     */
    public IndicatorContext calculateIndicators(List<Symbol> tickers, Instant analysisTimestamp) {
        return tickers.isEmpty()
            ? IndicatorContext.empty()
            : indicatorEnrichmentService.calculateIndicators(tickers, analysisTimestamp);
    }

    /**
     * Look up user model preferences (global + per-agent) and system defaults for the
     * simulation's trading context.
     */
    public ModelSelection resolveModelSelection(Simulation simulation, UserId ownerId) {
        // Look up user's model preferences (global + per-agent) based on simulation mode
        TradingContext tradingContext = getTradingContext(simulation);
        UserModelPrefs userModelPrefs = getUserModelPreferences(ownerId, tradingContext);
        if (userModelPrefs.globalModel() != null) {
            logger.info("Using user's preferred model for {}: {}", tradingContext, userModelPrefs.globalModel());
        }
        if (!userModelPrefs.agentModels().isEmpty()) {
            logger.info("User has per-agent model overrides for {}: {}", tradingContext, userModelPrefs.agentModels());
        }

        // Look up system defaults for this context, scoped to the engine that will run the call.
        // Engine-blind defaults are how the platform came to compute an OpenRouter id and then run
        // whatever the local server happened to have.
        LlmProvider engine = activeLlmEnginePort.activeEngine().orElse(null);
        String systemDefault = systemModelDefaultsPort.getDefaultModel(engine, tradingContext).orElse(null);
        Map<AgentRole, String> systemAgentDefaults =
                systemModelDefaultsPort.getAgentDefaults(engine, tradingContext);

        return new ModelSelection(tradingContext, userModelPrefs, systemDefault, systemAgentDefaults);
    }

    /**
     * Build an AgentContext for a specific ticker, used for parallel agent queries.
     */
    public AgentContext buildAgentContext(
            String ticker,
            Simulation simulation,
            Portfolio portfolio,
            Map<Timeframe, List<MarketEvent>> marketEventsByTimeframeForTicker,
            StrategyConfig strategyConfig,
            Instant analysisTimestamp,
            IndicatorContext indicators,
            ModelSelection modelSelection) {

        // Create MarketDataContext for this ticker
        Map<Timeframe, List<MarketEvent>> filteredData = filterMarketEventsByTimeframe(
            marketEventsByTimeframeForTicker, ticker);

        MarketDataContext marketDataContext = MarketDataContext.from(
            filteredData, Set.of(ticker, "MARKET"), analysisTimestamp);

        // Get news headlines
        Symbol tickerSymbol = resolveSymbol(ticker, strategyConfig);
        Symbol marketSymbol = new Symbol("MARKET", InvestmentType.STOCK);
        List<String> tickerNews = marketDataContext.getNewsHeadlines(tickerSymbol, 10);
        List<String> marketNews = marketDataContext.getNewsHeadlines(marketSymbol, 10);
        List<String> recentNews = new ArrayList<>();
        recentNews.addAll(tickerNews);
        recentNews.addAll(marketNews);

        // Pre-compute current prices and portfolio total value (shared across all agents)
        Map<Symbol, Money> currentPrices = new HashMap<>();
        if (!marketDataContext.isEmpty()) {
            for (Symbol symbol : marketDataContext.getAvailableSymbols()) {
                if ("MARKET".equals(symbol.ticker())) {
                    continue;
                }
                marketDataContext.getLatestPrice(symbol).ifPresent(price ->
                    currentPrices.put(symbol, new Money(price, Money.Currency.USD)));
            }
        }
        Money portfolioTotalValue = portfolio.calculateTotalValue(currentPrices);

        // Build recent trade history for anti-whipsaw context
        String recentTradeHistory = formatRecentTradeHistory(
            simulation.getSimulationId(), analysisTimestamp);

        // Fetch financial metrics for stock tickers in live mode only.
        // Backtests skip this — Finnhub returns current metrics, not historical,
        // which would introduce forward-looking bias.
        FinancialMetrics financialMetrics = null;
        if (financialDataPort != null && !simulation.isBacktest()) {
            financialMetrics = financialDataPort.getFinancialMetrics(tickerSymbol).orElse(null);
        }

        return new AgentContext(
            simulation.getSimulationId(),
            ticker,
            portfolio,
            marketDataContext,
            recentNews,
            Map.of(),
            analysisTimestamp,
            strategyConfig,
            indicators,
            modelSelection.userModelPrefs().globalModel(),
            modelSelection.userModelPrefs().agentModels(),
            strategyConfig.getLlmTemperature(),
            modelSelection.systemDefault(),
            modelSelection.systemAgentDefaults(),
            currentPrices,
            portfolioTotalValue,
            recentTradeHistory,
            !simulation.isBacktest(),
            financialMetrics
        );
    }

    /**
     * Position cooldown probe: returns the skip descriptor when the ticker's last
     * BUY/SELL is still within the strategy's cooldown window, empty otherwise.
     */
    public Optional<CooldownSkip> checkPositionCooldown(
            Simulation simulation, StrategyConfig strategyConfig,
            String ticker, Instant analysisTimestamp) {
        if (strategyConfig.getPositionCooldown() == null) {
            return Optional.empty();
        }
        Duration cooldown = strategyConfig.getPositionCooldown();
        List<ConsensusDecision> lastTrades = consensusDecisionRepository.findBySimulationIdAndTicker(
            simulation.getSimulationId().value(), ticker,
            List.of(ConsensusActionType.BUY.name(), ConsensusActionType.SELL.name()), 1, null, null);
        if (lastTrades.isEmpty()) {
            return Optional.empty();
        }
        ConsensusDecision lastTrade = lastTrades.get(0);
        Duration elapsed = Duration.between(lastTrade.getDecisionTimestamp(), analysisTimestamp);

        MarketHours schedule = strategyConfig.getScheduleForMode(simulation.isBacktest());
        boolean inCooldown = isInCooldown(
            strategyConfig.getTradingMode(), cooldown, schedule,
            lastTrade.getDecisionTimestamp(), analysisTimestamp);

        if (!inCooldown) {
            return Optional.empty();
        }

        long daysAgo = elapsed.toDays();
        String lastActionType = lastTrade.getAction() != null
            ? lastTrade.getAction().type().name() : ConsensusActionType.UNKNOWN.name();
        String lastDate = lastTrade.getDecisionTimestamp().toString().substring(0, 10);
        String note = String.format(
            "Position cooldown: last trade was %s %s on %s (%dd ago), cooldown %s not yet elapsed",
            lastActionType, ticker, lastDate, daysAgo, formatDuration(cooldown));
        return Optional.of(new CooldownSkip(note, elapsed, cooldown));
    }

    /**
     * Determines whether a position is still within cooldown.
     * <p>
     * For STOCK mode with day-scale cooldowns (>= 1 day), uses calendar-day snapping:
     * the trade timestamp is truncated to the start of day in the strategy's timezone,
     * then cooldown days are added. Any evaluation on the target day is valid.
     * This prevents off-by-one slot skipping caused by intra-day timestamp drift.
     * <p>
     * For CRYPTO mode or sub-day cooldowns, uses exact duration comparison.
     */
    public static boolean isInCooldown(TradingMode tradingMode, Duration cooldown,
                                       MarketHours schedule, Instant tradeTimestamp, Instant analysisTimestamp) {
        if (tradingMode == TradingMode.STOCK && cooldown.toDays() >= 1) {
            ZoneId tz = (schedule != null) ? schedule.timezone() : ZoneId.of("America/New_York");
            LocalDate tradeDate = tradeTimestamp.atZone(tz).toLocalDate();
            Instant cooldownEnd = tradeDate.plusDays(cooldown.toDays()).atStartOfDay(tz).toInstant();
            return analysisTimestamp.isBefore(cooldownEnd);
        } else {
            Duration elapsed = Duration.between(tradeTimestamp, analysisTimestamp);
            return elapsed.compareTo(cooldown) < 0;
        }
    }

    /**
     * Format a Duration as a human-readable string (e.g., "14d", "12h", "2h").
     */
    public static String formatDuration(Duration duration) {
        long days = duration.toDays();
        if (days > 0) {
            return days + "d";
        }
        long hours = duration.toHours();
        if (hours > 0) {
            return hours + "h";
        }
        long minutes = duration.toMinutes();
        return minutes + "m";
    }

    /**
     * Format last 10 executed BUY/SELL trades for anti-whipsaw context.
     * Returns null if no trades found.
     */
    private String formatRecentTradeHistory(
            SimulationId simulationId,
            Instant analysisTimestamp) {
        try {
            List<ConsensusDecision> recentTrades = consensusDecisionRepository.findBySimulationId(
                simulationId.value(), null, null, true, null,
                List.of(ConsensusActionType.BUY.name(), ConsensusActionType.SELL.name()), 10);

            if (recentTrades == null || recentTrades.isEmpty()) {
                return null;
            }

            StringBuilder sb = new StringBuilder();
            for (ConsensusDecision trade : recentTrades) {
                String action = trade.getAction() != null
                    ? trade.getAction().type().pastTenseLabel()
                    : ConsensusActionType.UNKNOWN.pastTenseLabel();
                String symbol = trade.getSymbol() != null ? trade.getSymbol().ticker() : "?";
                String qty = trade.getQuantity() != null
                    ? trade.getQuantity().stripTrailingZeros().toPlainString() : "?";
                String price = trade.getExecutionPrice() != null
                    ? "$" + trade.getExecutionPrice().amount().toPlainString() : "N/A";

                long daysAgo = ChronoUnit.DAYS.between(
                    trade.getDecisionTimestamp(), analysisTimestamp);
                String timeAgo = daysAgo == 0 ? "today"
                    : daysAgo == 1 ? "1 day ago"
                    : daysAgo + " days ago";

                String date = trade.getDecisionTimestamp().toString().substring(0, 10);
                sb.append(String.format("- %s %s: %s %s units @ %s (%s)%n",
                    date, symbol, action, qty, price, timeAgo));
            }
            return sb.toString().stripTrailing();
        } catch (Exception e) {
            logger.warn("Failed to load recent trade history for simulation {}: {}",
                simulationId, e.getMessage());
            return null;
        }
    }

    /**
     * Filter market events by timeframe for a specific ticker.
     * Includes both ticker-specific events AND general "MARKET" events
     * (market-wide news that affects all tickers).
     */
    private Symbol resolveSymbol(String ticker, StrategyConfig strategyConfig) {
        for (Symbol s : strategyConfig.getCryptoTickers()) {
            if (s.ticker().equals(ticker)) return s;
        }
        for (Symbol s : strategyConfig.getStockTickers()) {
            if (s.ticker().equals(ticker)) return s;
        }
        return new Symbol(ticker, InvestmentType.fromTicker(ticker));
    }

    public Map<Timeframe, List<MarketEvent>> filterMarketEventsByTimeframe(
            Map<Timeframe, List<MarketEvent>> marketEventsByTimeframe,
            String ticker) {

        if (marketEventsByTimeframe == null || marketEventsByTimeframe.isEmpty()) {
            return Map.of();
        }

        Map<Timeframe, List<MarketEvent>> filtered = new HashMap<>();

        for (Map.Entry<Timeframe, List<MarketEvent>> entry : marketEventsByTimeframe.entrySet()) {
            Timeframe timeframe = entry.getKey();
            // Include ticker-specific events AND general "MARKET" events
            List<MarketEvent> events = entry.getValue().stream()
                .filter(event -> ticker.equals(event.symbol()) || "MARKET".equals(event.symbol()))
                .collect(Collectors.toList());

            if (!events.isEmpty()) {
                filtered.put(timeframe, events);
            }
        }

        return filtered;
    }

    /**
     * Fetch latest portfolio for the simulation or create initial one if none exists.
     * Converts PortfolioSnapshot to Portfolio by deserializing positions JSON.
     *
     * @param simulationId Simulation identifier
     * @param timestamp Current analysis timestamp (for logging only)
     * @return Portfolio (current state or initial state)
     */
    public Portfolio getLatestPortfolio(SimulationId simulationId, Instant timestamp) {
        Optional<PortfolioSnapshot> snapshotOpt = portfolioRepository.findLatestBySimulationId(simulationId);

        if (snapshotOpt.isPresent()) {
            PortfolioSnapshot snapshot = snapshotOpt.get();
            logger.debug("Found portfolio snapshot at {} for simulation {}",
                snapshot.getTimestamp(), simulationId.value().toString().substring(0, 8));

            // Convert PortfolioSnapshot to Portfolio - positions are already deserialized by PortfolioMapper
            Map<Symbol, Position> positions = snapshot.getPositions();
            return new Portfolio(simulationId, positions, snapshot.getCashAvailable());
        }

        // No snapshots exist yet, return initial portfolio
        logger.debug("No portfolio snapshots found, initializing portfolio for simulation {}",
            simulationId.value().toString().substring(0, 8));
        return new Portfolio(simulationId, Map.of(), new Money(new BigDecimal("100000.00"), Money.Currency.USD)); // Default $100k (matches SimulationExecutor and CreateBenchmarkService)
    }

    /**
     * Fetch multi-timeframe market data (24h, 7d, 30d, 90d) up to analysisTimestamp.
     * Returns a map with timeframe keys: "last_24h", "last_7d", "last_30d", "last_90d"
     * Includes both ticker-specific events AND general "MARKET" events.
     *
     * @param strategyConfig Strategy configuration containing tickers
     * @param analysisTimestamp Upper bound for data fetching (exclusive)
     * @return Map of timeframe → List<MarketEvent>
     */
    public Map<Timeframe, List<MarketEvent>> fetchMultiTimeframeMarketDataForTickers(
            StrategyConfig strategyConfig,
            Instant analysisTimestamp) {

        Map<Timeframe, List<MarketEvent>> marketEventsByTimeframe = new HashMap<>();

        // Add "MARKET" to crypto tickers so general market-wide news is included
        Set<String> cryptoTickersWithMarket = strategyConfig.getCryptoTickers().stream()
            .map(symbol -> symbol.ticker())
            .collect(Collectors.toSet());
        cryptoTickersWithMarket.add("MARKET");

        // Extract stock ticker strings
        Set<String> stockTickers = strategyConfig.getStockTickers().stream()
            .map(symbol -> symbol.ticker())
            .collect(Collectors.toSet());

        // 1. Last 24 hours
        Instant oneDayAgo = analysisTimestamp.minus(24, ChronoUnit.HOURS);
        List<MarketEvent> last24h = marketEventRepository.findByTickersAndTimeRange(
            cryptoTickersWithMarket,  // Include MARKET for general news
            stockTickers,
            oneDayAgo,
            analysisTimestamp,
            null, // No event type filter
            50    // Limit to 50 recent events
        );
        marketEventsByTimeframe.put(Timeframe.LAST_24H, last24h);
        logger.debug("Fetched {} events for last 24h: {} to {}", last24h.size(), oneDayAgo, analysisTimestamp);

        // 2. Last 7 days
        Instant sevenDaysAgo = analysisTimestamp.minus(7, ChronoUnit.DAYS);
        List<MarketEvent> last7d = marketEventRepository.findByTickersAndTimeRange(
            cryptoTickersWithMarket,  // Include MARKET for general news
            stockTickers,
            sevenDaysAgo,
            oneDayAgo,
            null,
            100   // Limit to 100 events
        );
        marketEventsByTimeframe.put(Timeframe.LAST_7D, last7d);
        logger.debug("Fetched {} events for last 7d: {} to {}", last7d.size(), sevenDaysAgo, oneDayAgo);

        // 3. Last 30 days
        Instant thirtyDaysAgo = analysisTimestamp.minus(30, ChronoUnit.DAYS);
        List<MarketEvent> last30d = marketEventRepository.findByTickersAndTimeRange(
            cryptoTickersWithMarket,  // Include MARKET for general news
            stockTickers,
            thirtyDaysAgo,
            sevenDaysAgo,
            null,
            100
        );
        marketEventsByTimeframe.put(Timeframe.LAST_30D, last30d);
        logger.debug("Fetched {} events for last 30d: {} to {}", last30d.size(), thirtyDaysAgo, sevenDaysAgo);

        // 4. Last 90 days (NEWS only for long-term trends)
        Instant ninetyDaysAgo = analysisTimestamp.minus(90, ChronoUnit.DAYS);
        List<MarketEvent> last90d = marketEventRepository.findByTickersAndTimeRange(
            cryptoTickersWithMarket,  // Include MARKET for general news
            stockTickers,
            ninetyDaysAgo,
            thirtyDaysAgo,
            EventType.NEWS,
            100
        );
        marketEventsByTimeframe.put(Timeframe.LAST_90D, last90d);
        logger.debug("Fetched {} events for last 90d: {} to {}", last90d.size(), ninetyDaysAgo, thirtyDaysAgo);

        return marketEventsByTimeframe;
    }

    /**
     * The first evaluation of a backtest runs at the simulation's own start date, so the raw
     * window {@code [startDate, analysisTimestamp]} is <em>zero-width</em> on that tick and only
     * finds a bar if one happens to sit on that exact instant. A backtest starting at 14:00 UTC
     * against 4-hourly bars (12:00, 16:00, …) therefore reported "no price data at all", skipped
     * its very first evaluation, and — at 1 skip out of 1, a 100% gap rate — tripped the data-gap
     * circuit breaker, which paused the run before it ever traded. Daily bars stamped at UTC
     * midnight plus midnight start dates is what kept this hidden: those coincide by luck.
     *
     * <p>Widening the lower bound to at least this far back keeps the question the check is
     * actually asking ("does this ticker have price data around this period at all?") answerable
     * on the first tick. It is deliberately generous — per-day precision for stocks is the job of
     * {@link #checkMarketClosed}, not of this coarse pre-flight.</p>
     */
    private static final Duration PREFLIGHT_MIN_LOOKBACK = Duration.ofDays(7);

    /**
     * Validates that market data exists for all tickers before consensus.
     * DEFENSE IN DEPTH: Final check to prevent consensus from running without data.
     *
     * @param tickers Set of tickers to validate
     * @param startDate Start date for data availability check, widened to at least
     *                  {@link #PREFLIGHT_MIN_LOOKBACK} before {@code endDate}
     * @param endDate End date for data availability check
     * @return true if at least one price event exists for at least one ticker, false otherwise
     */
    public boolean validateMarketDataAvailability(Set<Symbol> tickers, Instant startDate, Instant endDate) {
        try {
            Instant windowStart = earliest(startDate, endDate.minus(PREFLIGHT_MIN_LOOKBACK));
            for (Symbol ticker : tickers) {
                long priceEventCount = marketEventRepository.countEventsInRange(
                    ticker,
                    EventType.PRICE,
                    windowStart,
                    endDate
                );

                if (priceEventCount > 0) {
                    logger.debug("Market data validation: ticker {} has {} price events",
                        ticker.ticker(), priceEventCount);
                    return true; // At least one ticker has data - allow consensus to proceed
                }

                logger.warn("Market data validation: ticker {} has 0 price events", ticker.ticker());
            }

            // No tickers have any price data
            logger.error("Market data validation FAILED: No price data found for any of {} tickers in [{} - {}]",
                tickers.size(), windowStart, endDate);
            return false;

        } catch (Exception e) {
            logger.error("Market data validation failed with exception: {}", e.getMessage(), e);
            return false; // Fail safe: don't start consensus if validation fails
        }
    }

    private static Instant earliest(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    /**
     * Get the TradingContext based on simulation mode (backtest vs live).
     * Uses isBacktest() to distinguish mock-broker backtests from real broker simulations,
     * and simulation status LIVE to distinguish live trading from paper trading.
     */
    private TradingContext getTradingContext(Simulation simulation) {
        if (simulation.isBacktest()) {
            return TradingContext.BACKTESTING;
        }
        return switch (simulation.getStatus()) {
            case LIVE -> TradingContext.LIVE_TRADING;
            default -> TradingContext.PAPER_TRADING;
        };
    }

    /**
     * Look up user's model preferences (global + per-agent) for the given trading context.
     */
    private UserModelPrefs getUserModelPreferences(UserId ownerId, TradingContext context) {
        if (ownerId == null) {
            return UserModelPrefs.EMPTY;
        }
        try {
            return userBudgetRepository.findByUserId(ownerId)
                .map(budget -> {
                    String globalModel = budget.getPreferredModel(context)
                        .map(ModelId::value)
                        .orElse(null);

                    ModelPreferences prefs = budget.getModelPreferences();
                    Map<AgentRole, String> agentModels = new HashMap<>();
                    for (AgentRole role : AgentRole.values()) {
                        prefs.getForAgent(context, role)
                            .map(ModelId::value)
                            .ifPresent(model -> agentModels.put(role, model));
                    }

                    return new UserModelPrefs(globalModel, agentModels);
                })
                .orElse(UserModelPrefs.EMPTY);
        } catch (Exception e) {
            logger.warn("Failed to look up user model preferences for user {}: {}",
                ownerId, e.getMessage());
            return UserModelPrefs.EMPTY;
        }
    }
}
