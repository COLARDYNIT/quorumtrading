package com.quorumtrading.application.usecase;

import com.quorumtrading.application.orchestration.StrategyParser;
import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.enums.TradingMode;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.service.CostEstimationService;
import com.quorumtrading.model.service.ModelSelectionService;
import com.quorumtrading.model.valueobject.CostEstimate;

import java.time.Instant;
import com.quorumtrading.model.valueobject.LLMModelInfo;
import com.quorumtrading.model.valueobject.LLMPricing;
import com.quorumtrading.model.valueobject.MarketHours;
import com.quorumtrading.model.valueobject.ModelId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.TradingContext;
import com.quorumtrading.model.valueobject.UserId;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Use case for estimating the AI budget cost of a backtest or benchmark before creation.
 * <p>
 * Resolves the user's selected model, looks up its pricing, parses the strategy configuration
 * to determine ticker count, evaluation frequency, and agent count, then delegates to
 * {@link CostEstimationService} for the actual calculation.
 */
public class EstimateBacktestCostUseCase {

    /** Weekdays the downstream estimator assumes a stock strategy evaluates on (Mon-Fri). */
    private static final int WEEKDAYS_PER_WEEK = 5;

    private final StrategyRepository strategyRepository;
    private final LLMPort llmPort;
    private final UserBudgetRepository userBudgetRepository;
    private final ModelSelectionService modelSelectionService;
    private final StrategyParser strategyParser;
    private final CostEstimationService costEstimationService;
    private final int avgPromptTokens;
    private final int avgCompletionTokens;
    private final com.quorumtrading.application.port.out.LlmProviderPreferencePort providerPreferencePort;
    /** Nullable: without it, wall-clock projection is skipped and only dollars are estimated. */
    private final com.quorumtrading.application.port.out.LlmTelemetryPort llmTelemetryPort;

    /**
     * Nullable: when present, the date-aware estimate cross-checks the window against stored
     * price coverage, so a confidently-priced run over an empty window carries a warning.
     */
    private final BacktestDataCoverageProbe coverageProbe;

    public EstimateBacktestCostUseCase(
        StrategyRepository strategyRepository,
        LLMPort llmPort,
        UserBudgetRepository userBudgetRepository,
        ModelSelectionService modelSelectionService,
        StrategyParser strategyParser,
        CostEstimationService costEstimationService,
        int avgPromptTokens,
        int avgCompletionTokens
    ) {
        this(strategyRepository, llmPort, userBudgetRepository, modelSelectionService, strategyParser,
            costEstimationService, avgPromptTokens, avgCompletionTokens, null);
    }

    public EstimateBacktestCostUseCase(
        StrategyRepository strategyRepository,
        LLMPort llmPort,
        UserBudgetRepository userBudgetRepository,
        ModelSelectionService modelSelectionService,
        StrategyParser strategyParser,
        CostEstimationService costEstimationService,
        int avgPromptTokens,
        int avgCompletionTokens,
        com.quorumtrading.application.port.out.LlmProviderPreferencePort providerPreferencePort
    ) {
        this(strategyRepository, llmPort, userBudgetRepository, modelSelectionService, strategyParser,
            costEstimationService, avgPromptTokens, avgCompletionTokens, providerPreferencePort,
            null, null);
    }

    public EstimateBacktestCostUseCase(
        StrategyRepository strategyRepository,
        LLMPort llmPort,
        UserBudgetRepository userBudgetRepository,
        ModelSelectionService modelSelectionService,
        StrategyParser strategyParser,
        CostEstimationService costEstimationService,
        int avgPromptTokens,
        int avgCompletionTokens,
        com.quorumtrading.application.port.out.LlmProviderPreferencePort providerPreferencePort,
        com.quorumtrading.application.port.out.LlmTelemetryPort llmTelemetryPort,
        com.quorumtrading.application.port.out.HistoricalPricePort historicalPricePort
    ) {
        this.strategyRepository = strategyRepository;
        this.llmPort = llmPort;
        this.userBudgetRepository = userBudgetRepository;
        this.modelSelectionService = modelSelectionService;
        this.strategyParser = strategyParser;
        this.costEstimationService = costEstimationService;
        this.avgPromptTokens = avgPromptTokens;
        this.avgCompletionTokens = avgCompletionTokens;
        this.providerPreferencePort = providerPreferencePort;
        this.llmTelemetryPort = llmTelemetryPort;
        this.coverageProbe = historicalPricePort == null
            ? null
            : new BacktestDataCoverageProbe(historicalPricePort);
    }

    /**
     * Estimate cost for a single simulation (single strategy).
     *
     * @param strategyId   The strategy to estimate for
     * @param calendarDays Number of calendar days in the backtest period
     * @param userId       The user (to resolve model preferences)
     * @return CostEstimate
     */
    /**
     * Estimate from the actual backtest dates.
     *
     * <p>Prefer this over the calendar-day form wherever the dates are known — for a stock
     * strategy the day-count form can only approximate, because how many trading days a span
     * contains depends on which weekday it starts on.</p>
     */
    public CostEstimate estimateForSimulation(StrategyId strategyId, Instant from, Instant to,
                                              UserId userId) {
        long calendarDays = java.time.Duration.between(from, to).toDays();
        CostEstimate approximate = estimateForSimulation(strategyId, calendarDays, userId);

        Strategy strategy = strategyRepository.findById(strategyId)
            .orElseThrow(() -> new IllegalArgumentException("Strategy not found: " + strategyId.value()));
        StrategyConfig config = strategyParser.parse(strategy.getContent());
        if (config.getTradingMode() == TradingMode.CRYPTO) {
            // Crypto trades every day, so the calendar-day form is already exact.
            return withCoverageWarning(approximate, config, from, to);
        }

        // Count only the days this strategy actually evaluates on. Counting every weekday here
        // discarded the backtest_schedule narrowing the day-count path applies below, so the
        // pre-flight quote and the creation confirmation — shown to the same user seconds apart
        // — disagreed by up to 5x for one unchanged strategy.
        MarketHours backtestSchedule = config.getBacktestSchedule();
        List<java.time.DayOfWeek> scheduledDays = backtestSchedule == null
            ? null
            : backtestSchedule.tradingDays();
        long exactWeekdays = CostEstimationService.countWeekdays(
            java.time.LocalDate.ofInstant(from, java.time.ZoneOffset.UTC),
            java.time.LocalDate.ofInstant(to, java.time.ZoneOffset.UTC),
            scheduledDays);
        ResolvedModel model = resolveModel(config, userId);
        boolean usesOrchestratorLlm = config.getConsensusMethod() != null
            && config.getConsensusMethod().usesOrchestratorLlm();

        // Zero is a DETERMINATE answer, not a failed measurement, and it is the most useful thing
        // this tool can say: the window contains no day the schedule fires on, so the run will
        // evaluate nothing and complete at 0.00% having decided nothing. The old guard read
        // `exactWeekdays <= 0 || …` and returned the APPROXIMATE estimate, which floors at 1 — so
        // every Friday-less window quoted a Friday-only strategy at "1 trading day, 1 round,
        // 4 LLM calls", at the one moment this tool could have prevented an empty run.
        //
        // Note for anyone tempted to restore the `<= 0`: the floor in CostEstimationService exists
        // so the *approximation* never reaches zero on a positive window and quotes a run as free
        // while it goes on to bill agents. That reasoning does not apply here — this count is
        // exact and schedule-aware, so zero means zero, and passing it through the shared path
        // below yields a genuine zero estimate.
        if (exactWeekdays == approximate.tradingDays()) {
            return withCoverageWarning(approximate, config, from, to);
        }

        return withCoverageWarning(costEstimationService.estimate(
            exactWeekdays, true, calculateEvalsPerDay(config), countTickers(config),
            config.getEffectiveEnabledAgents().size(), usesOrchestratorLlm,
            model.pricing, avgPromptTokens, avgCompletionTokens, model.id, model.name),
            config, from, to);
    }

    /**
     * Attach a stored-price-coverage warning to a date-aware estimate.
     *
     * <p>This tool prices the schedule and knows nothing about data: a window seven years
     * before the first stored bar produced byte-identical figures to a fully covered one —
     * and since create_simulation starts the run, this is the one place the product cannot
     * afford to be optimistic. Advisory only: it never fails the estimate, and a missing
     * port (legacy wiring, unit tests) skips the check.</p>
     *
     * <p>Partial coverage warns only when more than 20% of expected days are missing:
     * exchange holidays are not known to the coverage calendar and legitimately show up as
     * one-day gaps, so a fully backfilled stock month still reports a few missing days.</p>
     */
    private CostEstimate withCoverageWarning(CostEstimate estimate, StrategyConfig config,
                                             Instant from, Instant to) {
        if (coverageProbe == null) {
            return estimate;
        }
        try {
            String warning = coverageProbe.warningFor(config, from, to);
            return warning == null ? estimate : estimate.withDataCoverageWarning(warning);
        } catch (Exception e) {
            return estimate; // advisory only — never fail an estimate over a coverage probe
        }
    }

    public CostEstimate estimateForSimulation(StrategyId strategyId, long calendarDays, UserId userId) {
        Strategy strategy = strategyRepository.findById(strategyId)
            .orElseThrow(() -> new IllegalArgumentException("Strategy not found: " + strategyId.value()));

        StrategyConfig config = strategyParser.parse(strategy.getContent());
        ResolvedModel model = resolveModel(config, userId);

        boolean isCrypto = config.getTradingMode() == TradingMode.CRYPTO;
        int evalsPerDay = calculateEvalsPerDay(config);
        int tickerCount = countTickers(config);
        int agentCount = config.getEffectiveEnabledAgents().size();

        long effectiveCalendarDays = calendarDays;
        if (isCrypto) {
            // Crypto intervals longer than a day (e.g. 1w backtest schedule) yield evalsPerDay = 0
            // in integer math. Model them as 1 eval on every Nth day instead: scale the day count
            // down by the interval length so the round count stays correct.
            //
            // The +1 is the firing on the start date itself. A schedule fires at day 0 and every
            // Nth day after, so a span of D days contains floor(D/N) + 1 firings — a weekly
            // schedule over 2026-01-01..2026-01-31 fires on the 1st, 8th, 15th, 22nd and 29th.
            // Plain floor(D/N) quoted four, the run executed five, and the quote is the number the
            // docs promise is an *upper* bound and that create_simulation echoes back to the user.
            // Under-quoting is the one direction this tool may not err in.
            int intervalSeconds = config.getBacktestIntervalSeconds();
            if (intervalSeconds > 86400) {
                effectiveCalendarDays = calendarDays * 86400 / intervalSeconds + 1;
                evalsPerDay = 1;
            }
        } else {
            // Stock mode: the estimator downstream assumes evaluation on all five weekdays
            // (calendarDays * 5 / 7). A backtest_schedule that narrows trading_days — the very
            // thing the strategy guide recommends for cost control, e.g. [MONDAY] — would then be
            // overstated fivefold. Scale the day count by the fraction of weekdays actually used.
            MarketHours backtestSchedule = config.getBacktestSchedule();
            if (backtestSchedule != null && backtestSchedule.tradingDays() != null
                    && !backtestSchedule.tradingDays().isEmpty()) {
                int activeWeekdays = Math.min(backtestSchedule.tradingDays().size(), WEEKDAYS_PER_WEEK);
                if (activeWeekdays < WEEKDAYS_PER_WEEK) {
                    effectiveCalendarDays =
                        Math.max(1, calendarDays * activeWeekdays / WEEKDAYS_PER_WEEK);
                }
            }
        }

        // MAJORITY makes no orchestrator LLM call, so it must not be billed for one.
        boolean usesOrchestratorLlm = config.getConsensusMethod() != null
            && config.getConsensusMethod().usesOrchestratorLlm();

        return costEstimationService.estimate(
            effectiveCalendarDays, isCrypto, evalsPerDay, tickerCount, agentCount,
            usesOrchestratorLlm,
            model.pricing, avgPromptTokens, avgCompletionTokens,
            model.id, model.name
        );
    }

    /**
     * Estimate cost for a benchmark (multiple strategies over the same period).
     * Returns the combined cost across all strategies.
     *
     * @param strategyIds  List of strategy IDs
     * @param calendarDays Number of calendar days
     * @param userId       The user
     * @return Combined CostEstimate
     */
    public CostEstimate estimateForBenchmark(List<UUID> strategyIds, long calendarDays, UserId userId) {
        BigDecimal totalCost = BigDecimal.ZERO;
        long totalLLMCalls = 0;
        int totalTradingDays = 0;
        long totalConsensusRounds = 0;
        String modelId = null;
        String modelName = null;
        BigDecimal costPerCall = BigDecimal.ZERO;
        int totalTickerCount = 0;
        int agentsPerRound = 0;

        for (UUID id : strategyIds) {
            CostEstimate est = estimateForSimulation(new StrategyId(id), calendarDays, userId);
            totalCost = totalCost.add(est.estimatedCost());
            totalLLMCalls += est.totalLLMCalls();
            totalConsensusRounds += est.totalConsensusRounds();
            totalTickerCount += est.tickerCount();
            // Use the last strategy's model info (they should all be the same for a user)
            if (modelId == null) {
                modelId = est.modelId();
                modelName = est.modelName();
                costPerCall = est.costPerCall();
                totalTradingDays = est.tradingDays();
                agentsPerRound = est.agentsPerRound();
            }
        }

        return CostEstimate.of(
            totalTradingDays,
            totalConsensusRounds,
            totalTickerCount,
            agentsPerRound,
            totalLLMCalls,
            costPerCall,
            totalCost,
            modelId,
            modelName
        );
    }

    private int calculateEvalsPerDay(StrategyConfig config) {
        if (config.getTradingMode() == TradingMode.STOCK) {
            // Use backtest schedule for cost estimation (backtests use fewer evals)
            return config.getBacktestSchedule() != null
                ? config.getBacktestSchedule().evaluationTimes().size()
                : 1; // default: 1 eval per trading day
        } else {
            // Crypto: 24h / backtest interval
            int intervalSeconds = config.getBacktestIntervalSeconds();
            return intervalSeconds > 0 ? 86400 / intervalSeconds : 1;
        }
    }

    private int countTickers(StrategyConfig config) {
        int count = 0;
        if (config.getStockTickers() != null) count += config.getStockTickers().size();
        if (config.getCryptoTickers() != null) count += config.getCryptoTickers().size();
        return Math.max(count, 1);
    }

    /**
     * A cost estimate plus its wall-clock projection. {@code estimatedWallClockSeconds} is null
     * when no recent latency observations exist for the active engine; {@code timeBasis} always
     * explains what the number rests on — an estimate quoted without its basis gets repeated as
     * a fact.
     */
    public record TimedCostEstimate(CostEstimate estimate, Long estimatedWallClockSeconds, String timeBasis) {}

    /** Minimum completed calls before the observed average is considered representative. */
    private static final int MIN_LATENCY_SAMPLES = 3;

    /**
     * {@link #estimateForSimulation(StrategyId, Instant, Instant, UserId)} plus a wall-clock
     * projection. Dollar cost is only half the answer: on a free local engine the estimate reads
     * $0.00 while the run costs hours — time is the real price, and it was invisible until the
     * run had already burned itself out on timeouts.
     */
    public TimedCostEstimate estimateForSimulationWithTime(StrategyId strategyId, Instant from, Instant to,
                                                           UserId userId) {
        CostEstimate estimate = estimateForSimulation(strategyId, from, to, userId);
        return withWallClock(estimate);
    }

    private TimedCostEstimate withWallClock(CostEstimate estimate) {
        com.quorumtrading.model.enums.LlmProvider provider = activeProvider();
        com.quorumtrading.application.port.out.LlmTelemetryPort.ProviderTelemetry telemetry = null;
        if (llmTelemetryPort != null) {
            try {
                telemetry = llmTelemetryPort.telemetryFor(provider).orElse(null);
            } catch (Exception e) {
                telemetry = null;
            }
        }

        if (telemetry == null || telemetry.avgLatencyMs() == null
                || telemetry.invocations() < MIN_LATENCY_SAMPLES) {
            String basis = "No recent completed calls observed for " + provider
                    + ", so wall-clock time cannot be projected yet. On a free local engine expect"
                    + " tens of seconds to minutes per call; multiply by totalLLMCalls.";
            return new TimedCostEstimate(estimate, null, basis);
        }

        long seconds = Math.round(estimate.totalLLMCalls() * telemetry.avgLatencyMs() / 1000.0);
        String basis = String.format(
                "%d calls x %dms — %s's observed average over %d completed call(s), assuming calls"
                + " run serialized (realistic for a single local engine; an upper bound for hosted"
                + " APIs, which parallelize agents).",
                estimate.totalLLMCalls(), telemetry.avgLatencyMs(), provider, telemetry.invocations());
        return new TimedCostEstimate(estimate, seconds, basis);
    }

    /** The provider whose latency governs a new run: the runtime selection, else OpenRouter. */
    private com.quorumtrading.model.enums.LlmProvider activeProvider() {
        if (providerPreferencePort == null) {
            return com.quorumtrading.model.enums.LlmProvider.OPENROUTER;
        }
        try {
            return providerPreferencePort.current()
                    .map(com.quorumtrading.application.port.out.LlmProviderPreferencePort.LlmProviderPreference::provider)
                    .orElse(com.quorumtrading.model.enums.LlmProvider.OPENROUTER);
        } catch (Exception e) {
            return com.quorumtrading.model.enums.LlmProvider.OPENROUTER; // hosted default when the store is unreadable
        }
    }

    private ResolvedModel resolveModel(StrategyConfig config, UserId userId) {
        // When a free local engine (CLI or local OpenAI-compatible server) is the selected
        // provider, every consensus call routes there — quoting an OpenRouter model's pricing
        // would be wrong on both cost and name. Strict selection ignores pinned models too.
        if (providerPreferencePort != null) {
            var current = providerPreferencePort.current();
            if (current.isPresent()
                    && current.get().provider() != com.quorumtrading.model.enums.LlmProvider.OPENROUTER) {
                String engine = current.get().provider() == com.quorumtrading.model.enums.LlmProvider.LOCAL
                    ? "local CLI engine" : "local model server";
                return new ResolvedModel("local", engine + " — free", LLMPricing.FREE);
            }
        }

        // Strategy may pin a specific model
        String modelId;
        if (config.getLlmModel() != null && !config.getLlmModel().isBlank()) {
            modelId = config.getLlmModel();
        } else {
            // Use user preference or system default
            UserBudget userBudget = userBudgetRepository.findByUserId(userId).orElse(null);
            ModelId selected = modelSelectionService.selectModel(userBudget, TradingContext.BACKTESTING);
            modelId = selected.value();
        }

        // Look up pricing from model catalog
        LLMPricing pricing = llmPort.getModelInfo(modelId)
            .map(LLMModelInfo::pricing)
            .orElse(LLMPricing.FREE);

        String modelName = llmPort.getModelInfo(modelId)
            .map(LLMModelInfo::name)
            .orElse(modelId);

        return new ResolvedModel(modelId, modelName, pricing);
    }

    private record ResolvedModel(String id, String name, LLMPricing pricing) {}
}
