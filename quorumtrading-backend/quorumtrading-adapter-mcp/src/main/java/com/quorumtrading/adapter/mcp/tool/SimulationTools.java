package com.quorumtrading.adapter.mcp.tool;

import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.adapter.mcp.dto.McpCreatedResponse;
import com.quorumtrading.adapter.mcp.dto.McpPaginatedResponse;
import com.quorumtrading.adapter.mcp.dto.McpSimulationDto;
import com.quorumtrading.adapter.mcp.dto.McpStatusResponse;
import com.quorumtrading.adapter.mcp.mapper.McpDtoMapper;
import com.quorumtrading.application.usecase.*;
import com.quorumtrading.application.usecase.command.CreateSimulationCommand;
import com.quorumtrading.application.usecase.command.DeleteSimulationCommand;
import com.quorumtrading.application.usecase.command.PauseSimulationCommand;
import com.quorumtrading.application.usecase.command.StartSimulationCommand;
import com.quorumtrading.application.usecase.query.GetSimulationQuery;
import com.quorumtrading.application.usecase.query.ListSimulationsQuery;
import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.ConsensusDecision;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.CostPreset;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.MockBrokerConfig;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.SymbolParser;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * MCP tools for simulation management.
 * Exposes list, get, create, start, and pause operations.
 */
@Singleton
@ActivateRequestContext
public class SimulationTools extends McpToolBase {

    @Inject
    ListSimulationsUseCase listSimulationsUseCase;

    @Inject
    GetSimulationUseCase getSimulationUseCase;

    @Inject
    com.quorumtrading.application.usecase.health.GetSimulationHealthUseCase getSimulationHealthUseCase;

    @Inject
    CreateSimulationUseCase createSimulationUseCase;

    @Inject
    StartSimulationUseCase startSimulationUseCase;

    @Inject
    PauseSimulationUseCase pauseSimulationUseCase;

    @Inject
    ResumeSimulationUseCase resumeSimulationUseCase;

    @Inject
    com.quorumtrading.application.usecase.UpdateSimulationNotificationPreferenceUseCase updateSimulationNotificationPreferenceUseCase;

    @Inject
    DeleteSimulationUseCase deleteSimulationUseCase;

    @Inject
    com.quorumtrading.application.usecase.SellPositionUseCase sellPositionUseCase;

    @Inject
    com.quorumtrading.application.usecase.LiquidateAllPositionsUseCase liquidateAllPositionsUseCase;

    @Inject
    com.quorumtrading.application.usecase.ForceEndSimulationUseCase forceEndSimulationUseCase;

    @Inject
    com.quorumtrading.application.usecase.EstimateBacktestCostUseCase estimateBacktestCostUseCase;

    @Inject
    com.quorumtrading.application.service.PriceHistoryWarmupInspector priceHistoryWarmupInspector;

    @Inject
    com.quorumtrading.application.usecase.CalculateSimulationCostUseCase calculateSimulationCostUseCase;

    @Inject
    McpUserContext mcpUserContext;

    @Inject
    StrategyRepository strategyRepository;

    @Inject
    BrokerAccountRepository brokerAccountRepository;

    @Inject
    PortfolioRepository portfolioRepository;

    @Inject
    com.quorumtrading.application.port.out.SimulationContributionRepository simulationContributionRepository;

    @Inject
    com.quorumtrading.application.port.out.LLMDecisionRepository llmDecisionRepository;

    @Inject
    com.quorumtrading.application.port.out.ConsensusDecisionRepository consensusDecisionRepository;

    @Inject
    com.quorumtrading.application.service.RestartSimulationFromService restartSimulationFromService;

    @Inject
    com.quorumtrading.application.usecase.contribution.GetSimulationContributionsUseCase getSimulationContributionsUseCase;

    @Inject
    McpDtoMapper mapper;

    @Inject
    com.quorumtrading.adapter.mcp.mapper.UiLinks uiLinks;

    @Inject
    com.quorumtrading.model.valueobject.NewsDataAvailability newsDataAvailability;

    @Inject
    com.quorumtrading.application.config.TradingLimitsConfig tradingLimitsConfig;

    @Tool(name = "list_simulations", description = "List trading simulations with optional status "
        + "and strategy filters. Each row already carries current value, return %, total invested, "
        + "contributions, trading mode, symbols and strategy name, so a summary needs no follow-up "
        + "call. twrPercent and mwrPercent are the exception: they need every snapshot of a run, so "
        + "they are omitted here — call get_simulation for those. "
        + "NOTE: status LIVE is a lifecycle state, not a statement about money — a backtest that has "
        + "caught up with the present reports it too. Read realMoney to tell which runs trade real "
        + "money.")
    public ToolResponse listSimulations(
        @ToolArg(description = "Filter by status: PENDING, WAITING_FOR_DATA, SIMULATING, LIVE, COMPLETED, FAILED. Leave empty for all.") Optional<String> status,
        @ToolArg(description = "Maximum number of results (default 20, max 100)") Optional<Integer> limit,
        @ToolArg(description = "Filter by strategy UUID. Only return simulations using this strategy.") Optional<String> strategyId
    ) {
        return execute(() -> {
            // Validate status enum early to give clear error with valid values
            status.ifPresent(s -> parseEnum(SimulationStatus.class, s, "status"));

            int effectiveLimit = parseLimit(limit, 20, 100);
            int fetchLimit = effectiveLimit + 1;

            ListSimulationsQuery query = new ListSimulationsQuery(
                status,
                strategyId.map(id -> new StrategyId(UUID.fromString(id))),
                Optional.empty(),
                Optional.of(fetchLimit),
                mcpUserContext.getUserId()
            );

            Page<Simulation> page = paginate(listSimulationsUseCase.execute(query), effectiveLimit);
            List<Simulation> rows = page.items();

            // Batch-resolve strategy names, trading modes, symbols, and contributions
            // (the last one avoids an N+1 when computing contribution-aware returns).
            Map<UUID, String> strategyNames = resolveStrategyNames(rows);
            Map<UUID, String> tradingModes = resolveTradingModes(rows);
            Set<UUID> inlineBrokerIds = resolveInlineBrokerAccountIds(rows);
            Map<UUID, List<String>> strategySymbols = resolveStrategySymbols(rows);
            Map<UUID, List<com.quorumtrading.model.entity.Contribution>> contributionsBySim = resolveContributions(rows);

            List<McpSimulationDto> dtos = rows.stream()
                .map(sim -> {
                    UUID stratId = sim.getStrategyId().value();
                    UUID brokerId = sim.getBrokerAccountId() != null ? sim.getBrokerAccountId().value() : null;
                    // Contributions are batch-loaded above, so the invested basis and the
                    // contribution list cost this view nothing. Leaving them null made a list row
                    // claim no capital was invested and no contributions existed, for the same
                    // simulation get_simulation reported 10000.0 and [] at the same instant.
                    List<com.quorumtrading.model.entity.Contribution> contributions =
                        contributionsBySim.getOrDefault(sim.getSimulationId().value(), List.of());
                    String totalInvested = sim.getInitialCash() != null
                        ? com.quorumtrading.model.service.PerformanceCalculator
                            .totalInvested(sim.getInitialCash(), contributions).amount().toPlainString()
                        : null;
                    String currentValue = null;
                    String returnPercent = null;
                    try {
                        Optional<PortfolioSnapshot> snap = portfolioRepository.findLatestBySimulationId(sim.getSimulationId());
                        if (snap.isPresent() && snap.get().getTotalValue() != null) {
                            currentValue = snap.get().getTotalValue().amount().toPlainString();
                            // Return on total invested (seed + Σ contributions) so DCA
                            // top-ups aren't reported as profit; equals seed when none.
                            BigDecimal pct = com.quorumtrading.model.service.PerformanceCalculator
                                .simpleReturnPercent(sim.getInitialCash(), contributions, snap.get().getTotalValue());
                            if (pct != null) {
                                returnPercent = pct.toPlainString();
                            }
                        }
                    } catch (Exception e) {
                        // Graceful degradation: currentValue stays null
                    }
                    // twrPercent/mwrPercent stay absent here: both need every snapshot for the run,
                    // which would turn one listing into an N+1. They are omitted from the JSON
                    // rather than sent as null, so a caller sees "ask get_simulation" instead of
                    // "this run has no time-weighted return".
                    return mapper.toSimulationDto(sim,
                        strategyNames.get(stratId),
                        tradingModes.get(brokerId),
                        strategySymbols.getOrDefault(stratId, List.of()),
                        currentValue, returnPercent,
                        totalInvested, null, null,
                        contributions.stream().map(mapper::toContributionDto).toList(),
                        brokerId != null && inlineBrokerIds.contains(brokerId),
                        briefNoDecisionsReason(sim));
                })
                .toList();
            McpPaginatedResponse<McpSimulationDto> response = new McpPaginatedResponse<>(dtos, dtos.size(), page.hasMore());

            return toJson(response);
        });
    }

    @Tool(name = "get_simulation", description = "Get detailed information about a specific simulation. Returns status, progress, dates, initial cash, current value, return %, trading mode, symbols, strategy name, and a live `health` block (verdict such as HEALTHY/STALLED/PAUSED_EXTERNAL, skip streak, dominant skip reasons) — read `health` first when a run seems stuck.")
    public ToolResponse getSimulation(
        @ToolArg(description = "The simulation UUID") String simulationId
    ) {
        return execute(() -> {
            SimulationId id = new SimulationId(UUID.fromString(simulationId));
            Simulation simulation = getSimulationUseCase.execute(new GetSimulationQuery(id, mcpUserContext.getUserId()));
            Strategy strategy = strategyRepository.findById(simulation.getStrategyId()).orElse(null);
            String strategyName = strategy != null ? strategy.getName() : null;
            String tradingMode = deriveTradingMode(simulation.getBrokerAccountId());
            List<String> symbols = strategy != null ? mapper.extractSymbols(strategy) : List.of();

            // Load contributions first so the headline return is measured against
            // total invested (seed + Σ contributions), not the seed alone — a DCA
            // top-up must not be reported as profit. Equals seed when no contributions.
            List<com.quorumtrading.model.entity.Contribution> contributions = List.of();
            try {
                contributions = getSimulationContributionsUseCase.execute(id, mcpUserContext.getUserId());
            } catch (Exception e) {
                // Graceful degradation: no contribution context
            }
            BigDecimal investedBasis = simulation.getInitialCash() != null
                    ? com.quorumtrading.model.service.PerformanceCalculator
                            .totalInvested(simulation.getInitialCash(), contributions).amount()
                    : null;
            String totalInvested = investedBasis != null ? investedBasis.toPlainString() : null;

            String currentValue = null;
            String returnPercent = null;
            com.quorumtrading.model.valueobject.Money currentValueMoney = null;
            try {
                Optional<PortfolioSnapshot> snap = portfolioRepository.findLatestBySimulationId(id);
                if (snap.isPresent() && snap.get().getTotalValue() != null) {
                    currentValueMoney = snap.get().getTotalValue();
                    currentValue = currentValueMoney.amount().toPlainString();
                    BigDecimal pct = com.quorumtrading.model.service.PerformanceCalculator
                        .simpleReturnPercent(simulation.getInitialCash(), contributions, currentValueMoney);
                    if (pct != null) {
                        returnPercent = pct.toPlainString();
                    }
                }
            } catch (Exception e) {
                // Graceful degradation
            }

            // Enrich with recurring-investment context: TWR + MWR.
            String twrPercent = null;
            String mwrPercent = null;
            try {
                List<PortfolioSnapshot> mwrSnapshots = portfolioRepository.findAllBySimulationId(id);
                BigDecimal twr = com.quorumtrading.model.service.PerformanceCalculator.asPercent(
                        com.quorumtrading.model.service.PerformanceCalculator.computeTwr(
                                mwrSnapshots, contributions));
                if (twr != null) twrPercent = twr.toPlainString();
                if (currentValueMoney != null) {
                    // MWR is annualised, so it must be measured on the SIMULATED axis, not on
                    // how long the run took to execute — a seven-month backtest that finishes in
                    // fifteen minutes would otherwise annualise over ~0.002 years. The as-of
                    // resolution is shared with the REST mapper on purpose: when each surface
                    // kept its own copy, they drifted and reported different returns for one run.
                    java.time.Instant mwrStart = simulation.getStartDate() != null
                            ? simulation.getStartDate()
                            : simulation.getStartedAt();
                    java.time.Instant mwrAsOf = com.quorumtrading.model.service.PerformanceCalculator
                            .resolveMwrAsOf(mwrSnapshots, simulation.getEndDate());
                    BigDecimal mwr = com.quorumtrading.model.service.PerformanceCalculator.asPercent(
                            com.quorumtrading.model.service.PerformanceCalculator.computeMwr(
                                    simulation.getInitialCash(),
                                    mwrStart,
                                    contributions,
                                    currentValueMoney,
                                    mwrAsOf));
                    if (mwr != null) mwrPercent = mwr.toPlainString();
                }
            } catch (Exception e) {
                // Graceful degradation
            }
            List<com.quorumtrading.adapter.mcp.dto.McpContributionDto> contributionDtos =
                    contributions.stream().map(mapper::toContributionDto).toList();

            com.quorumtrading.adapter.mcp.dto.McpSimulationHealthDto health = null;
            try {
                health = mapper.toHealthDto(getSimulationHealthUseCase.execute(
                        new GetSimulationQuery(id, mcpUserContext.getUserId())));
            } catch (Exception e) {
                // Graceful degradation: health is advisory, the core projection must not fail over it
            }

            return toJson(mapper.toSimulationDto(simulation, strategyName, tradingMode, symbols,
                    currentValue, returnPercent,
                    totalInvested, twrPercent, mwrPercent, contributionDtos,
                    hasInlineBroker(simulation.getBrokerAccountId()),
                    noDecisionsReason(simulation),
                    decisionsNotExecuted(simulation),
                    health));
        });
    }

    /**
     * How many decisions wanted to trade but nothing executed — the complement of
     * {@link #noDecisionsReason(Simulation)}: that field catches a run that decided nothing,
     * this one catches a run that decided plenty and traded nothing. A -2.14% headline over
     * five refused BUYs is not the strategy's return, and until this count existed the
     * documented trust check passed cleanly on exactly that run.
     *
     * <p>Only terminal runs are counted — an in-flight run legitimately accumulates refusals
     * — and null (absent), not zero, is reported when the count cannot be measured.</p>
     */
    private Long decisionsNotExecuted(Simulation simulation) {
        if (simulation.getStatus() != SimulationStatus.COMPLETED
                && simulation.getStatus() != SimulationStatus.FAILED) {
            return null;
        }
        try {
            return consensusDecisionRepository.countNotExecutedBySimulationId(
                simulation.getSimulationId().value());
        } catch (Exception e) {
            return null; // Graceful degradation: never fail a status read over an advisory field
        }
    }

    /**
     * The list-view form of {@link #noDecisionsReason(Simulation)}: flags the run and points at
     * the detail view for the reason.
     *
     * <p>The field was added to the detail view only, so the list carried it as a hard null —
     * and the list is precisely the screen where a wall of 0.0000% returns gets scanned. A
     * present-but-always-null field there reads as "checked, not flagged", which is worse than
     * omitting it. Deliberately one query per finished run rather than two: the list must stay
     * cheap, and the full explanation is one {@code get_simulation} call away.</p>
     */
    private String briefNoDecisionsReason(Simulation simulation) {
        if (simulation.getStatus() != SimulationStatus.COMPLETED) {
            return null;
        }
        try {
            boolean decidedSomething = !consensusDecisionRepository.findBySimulationId(
                simulation.getSimulationId().value(), null, null, null, null,
                List.of("BUY", "SELL", "HOLD"), 1).isEmpty();
            return decidedSomething ? null
                : "This run made no trading decision, so its reported return is not a result — "
                    + "call get_simulation for the reason.";
        } catch (Exception e) {
            return null; // Graceful degradation: never fail a listing over an advisory field
        }
    }

    /**
     * Explains a finished run that decided nothing, or null when it decided something.
     *
     * <p>A run whose every evaluation was skipped completes exactly like a healthy one —
     * COMPLETED, progress 100, return 0.0000% — so a broken backtest was indistinguishable
     * from one that held throughout, and the only evidence lived in notes on decision rows a
     * caller had to go looking for. Reported here so the headline object carries it.</p>
     *
     * <p>Only terminal runs are described: an in-flight simulation legitimately has no
     * decisions yet, and calling that out would fire on every poll of a healthy run.</p>
     */
    private String noDecisionsReason(Simulation simulation) {
        if (simulation.getStatus() != SimulationStatus.COMPLETED) {
            return null;
        }
        try {
            // A single non-SKIP decision is enough to prove the run evaluated something —
            // ask for one rather than counting them all.
            boolean decidedSomething = !consensusDecisionRepository.findBySimulationId(
                simulation.getSimulationId().value(), null, null, null, null,
                List.of("BUY", "SELL", "HOLD"), 1).isEmpty();
            if (decidedSomething) {
                return null;
            }

            List<ConsensusDecision> skips = consensusDecisionRepository.findBySimulationId(
                simulation.getSimulationId().value(), null, null, null, null, List.of("SKIP"), 1);
            long total = consensusDecisionRepository.countBySimulationIdAfter(
                simulation.getSimulationId().value(), Instant.EPOCH);

            if (total == 0) {
                return "This run completed without evaluating anything — no consensus round ran at all. "
                    + "The usual causes are a schedule that never fired inside the simulated window, "
                    + "or a window shorter than one evaluation interval. The reported return is not a result.";
            }
            String reason = skips.isEmpty() || skips.get(0).getNotes() == null
                ? "no reason was recorded"
                : skips.get(0).getNotes();
            return "All " + total + " evaluation(s) in this run were skipped, so no trading decision was ever "
                + "made and the reported return is not a result. First recorded reason: " + reason
                + ". Use query_consensus_decisions to see every skip.";
        } catch (Exception e) {
            return null; // Graceful degradation: never fail a status read over an advisory field
        }
    }

    /**
     * Whether this simulation's broker account was created inline for it rather than chosen
     * by the caller. Such an account is not reported as the simulation's broker.
     */
    private boolean hasInlineBroker(BrokerAccountId brokerAccountId) {
        if (brokerAccountId == null) {
            return false;
        }
        return brokerAccountRepository.findById(brokerAccountId)
            .map(BrokerAccount::isInline)
            .orElse(false);
    }

    @Tool(name = "create_simulation", description = "Create a simulation that executes a STRATEGY against market data for a given timeframe. " +
        "The strategy's YAML frontmatter defines which symbols are traded — you do NOT need to specify a symbol here. " +
        "IMPORTANT: A simulation runs a strategy — you MUST have a strategyId before calling this tool. " +
        "If the user asks to 'simulate BTC' or 'backtest AAPL', first call list_strategies to find a suitable strategy, " +
        "or help them create one. The strategy defines the trading rules; the simulation just runs them. " +
        "Trading modes: (1) Backtest (default) — no brokerAccountId, replays historical data with mock broker. " +
        "(2) Paper trading — pass brokerAccountId of an ALPACA_PAPER account. Must be explicitly requested by the user. " +
        "(3) Live trading — pass brokerAccountId of an ALPACA_LIVE account, trades with REAL MONEY. " +
        "For live trading you MUST get explicit user confirmation (state broker, strategy, cash) before calling, " +
        "and pass confirmLive=true — the server refuses a live broker account without it. " +
        "COST WARNING: creating a simulation STARTS it. Once its market data is ready the run begins on its own, " +
        "within seconds — there is no dormant state in which you can inspect the cost estimate and then decide. " +
        "Backtests > 1 month consume significant AI budget (4+ agent calls per trading day), so inform the user " +
        "about cost BEFORE calling this tool, then set userInformedOfCost=true. To price a backtest without " +
        "paying for it, call estimate_backtest_cost first — it takes the same strategy and dates, respects the " +
        "strategy's backtest_schedule, and creates nothing. If it reports zero trading days, the run would " +
        "decide nothing and should not be created. " +
        "Broker costs are asset-type-aware: TYPICAL_RETAIL (default) = 0% stocks + 0.25% crypto (Alpaca-aligned). " +
        "SIZING: a BUY decision without an explicit quantity fills to the strategy's tightest cap " +
        "(position_limits, else all cash above the reserve) — a tranche-based strategy whose agents " +
        "omit quantities will deploy its whole limit on the first BUY. " +
        "Returns the new simulation ID.")
    public ToolResponse createSimulation(
        @ToolArg(description = "Optional display name for the simulation") Optional<String> name,
        @ToolArg(description = "Required. UUID of the strategy that defines the trading rules. Call list_strategies first if unknown.") String strategyId,
        @ToolArg(description = "Simulation start date in ISO-8601 format (e.g., 2025-01-01T00:00:00Z)") String startDate,
        @ToolArg(description = "Simulation end date in ISO-8601 format (e.g., 2025-06-01T00:00:00Z)") String endDate,
        @ToolArg(description = "Initial cash amount in USD (e.g., 10000)") String initialCash,
        @ToolArg(description = "Optional broker account UUID for paper or live trading. Omit for backtest (mock broker). Use list_broker_accounts to find available accounts.") Optional<String> brokerAccountId,
        @ToolArg(description = "Cost preset for backtest: ZERO_FEES, TYPICAL_RETAIL, HIGH_FRICTION (default: TYPICAL_RETAIL). " +
            "TYPICAL_RETAIL = Alpaca-aligned (0% stocks, 0.25% crypto, moderate slippage). Only applies to backtests (no brokerAccountId).") Optional<String> costPreset,
        @ToolArg(description = "Set to 'true' after informing the user about AI budget cost for backtests longer than 1 month. Required for backtests > 31 days.") Optional<String> userInformedOfCost,
        @ToolArg(description = "REQUIRED 'true' when brokerAccountId names an ALPACA_LIVE or SAXO_LIVE "
            + "account: acknowledges that this simulation trades REAL money. The server refuses "
            + "without it. State the broker, strategy, symbol and cash to the user and get an "
            + "explicit yes first. Not needed for backtests or paper accounts.") Optional<String> confirmLive
    ) {
        return execute(() -> {
            Instant parsedStart = parseDate(startDate);
            Instant parsedEnd = parseDate(endDate);
            long durationDays = Duration.between(parsedStart, parsedEnd).toDays();
            if (durationDays > 31 && !"true".equals(userInformedOfCost.orElse(null))) {
                String costMessage = buildCostMessage(
                    durationDays, new StrategyId(UUID.fromString(strategyId)), 1);
                throw new IllegalArgumentException(costMessage);
            }

            BigDecimal cash = parseBigDecimal(initialCash, "initialCash");
            BrokerAccountId brokerId = brokerAccountId
                .map(id -> new BrokerAccountId(UUID.fromString(id)))
                .orElse(null);

            // Build mock broker config from cost preset (only for backtests)
            MockBrokerConfig mockConfig = null;
            if (brokerId == null) {
                CostPreset preset = parseEnum(CostPreset.class, costPreset.orElse("TYPICAL_RETAIL"), "costPreset");
                mockConfig = MockBrokerConfig.fromPreset(Money.usd(cash), preset);
            }

            CreateSimulationCommand command = new CreateSimulationCommand(
                name.orElse(null),
                new StrategyId(UUID.fromString(strategyId)),
                parsedStart,
                parsedEnd,
                Money.usd(cash),
                brokerId, null, mockConfig,
                mcpUserContext.getUserId(),
                "true".equalsIgnoreCase(confirmLive.orElse("false"))
            );

            SimulationId id = createSimulationUseCase.execute(command);
            String mode = deriveTradingMode(brokerId);
            String strategyName = strategyRepository.findById(new StrategyId(UUID.fromString(strategyId)))
                .map(Strategy::getName).orElse("Unknown");
            logger.info("MCP: Created simulation {} (broker={})", id.value(), brokerAccountId.orElse("mock"));
            String message = String.format("Simulation '%s' created — strategy '%s', %s mode, $%s initial cash",
                name.orElse("Unnamed"), strategyName, mode, initialCash);

            // Append cost estimate for backtests (no broker = backtest)
            if (brokerId == null) {
                try {
                    // Pass the dates, not the day count: for a stock strategy the day count can
                    // only approximate how many trading days the span holds.
                    var estimate = estimateBacktestCostUseCase.estimateForSimulation(
                        new StrategyId(UUID.fromString(strategyId)), parsedStart, parsedEnd,
                        mcpUserContext.getUserId());
                    message += String.format(". Estimated cost: %s (~%d LLM calls, %s)",
                        estimate.formattedCost(), estimate.totalLLMCalls(), estimate.modelName());
                    if (estimate.dataCoverageWarning() != null) {
                        message += ". WARNING: " + estimate.dataCoverageWarning();
                    }
                } catch (Exception e) {
                    logger.debug("Cost estimation for response failed: {}", e.getMessage());
                }
            }

            // Warn about news data cutoff if strategy uses news-dependent agents
            message += buildNewsAvailabilityWarning(strategyId, parsedStart);

            // Warn when the stored history cannot feed the indicators. The platform was
            // meticulous about news scarcity and silent about price scarcity, so a run
            // whose every indicator was null still reported a clean COMPLETED.
            message += buildPriceHistoryWarning(strategyId, parsedStart);

            // Warn when the strategy's own risk numbers make trading impossible: a BUY that
            // can never reach the minimum trade value converts to HOLD on every tick, and
            // until now the first sign was a finished run full of refusal notes.
            message += buildTradeHeadroomWarning(strategyId, cash);

            return toJson(new McpCreatedResponse(id.value().toString(), message,
                uiLinks.simulation(id.value().toString())));
        });
    }

    @Tool(name = "start_simulation", description = "Start a simulation that has not started on its own. " +
        "NOTE: create_simulation normally starts the run itself as soon as its market data is ready — usually " +
        "within seconds — so this tool is only needed when that did not happen (e.g. the simulation is still " +
        "PENDING because backfill stalled, or it was paused). Do NOT call create_simulation expecting a dormant " +
        "simulation you can inspect before paying for it; creating a backtest commits to running it. " +
        "The simulation runs asynchronously in the background — you will NOT be notified when it completes. " +
        "Tell the user to check back later and ask you to call get_simulation or query_portfolio_snapshots for results. " +
        "Trading mode depends on the broker account used at creation: backtest (mock), paper (ALPACA_PAPER), or live (ALPACA_LIVE).")
    public ToolResponse startSimulation(
        @ToolArg(description = "The simulation UUID to start") String simulationId
    ) {
        return execute(() -> {
            SimulationId id = new SimulationId(UUID.fromString(simulationId));
            // Check state BEFORE acting: the use case is silently idempotent, and reporting
            // "Simulation started — status: COMPLETED" to a no-op is how a newcomer concludes
            // the product lies. Say plainly when nothing happened, and what to do instead.
            Simulation before = getSimulationUseCase.execute(new GetSimulationQuery(id, mcpUserContext.getUserId()));
            SimulationStatus preStatus = before.getStatus();
            if (preStatus != SimulationStatus.PENDING) {
                String message = switch (preStatus) {
                    case COMPLETED, FAILED -> "No action taken — simulation is already " + preStatus.name()
                            + ". Starting a finished run has no effect.";
                    case WAITING_FOR_DATA -> "No action taken — simulation is WAITING_FOR_DATA; "
                            + "it starts on its own once market data is ready.";
                    default -> before.isPaused()
                            ? "No action taken — simulation is paused (status: " + preStatus.name()
                                    + "). Use resume_simulation to un-pause it."
                            : "No action taken — simulation is already running (status: " + preStatus.name() + ").";
                };
                return toJson(new McpStatusResponse(simulationId, message));
            }
            startSimulationUseCase.execute(new StartSimulationCommand(id, mcpUserContext.getUserId()));
            logger.info("MCP: Started simulation {}", id.value());
            Simulation sim = getSimulationUseCase.execute(new GetSimulationQuery(id, mcpUserContext.getUserId()));
            return toJson(new McpStatusResponse(simulationId, "Simulation started — status: " + sim.getStatus().name()));
        });
    }

    @Tool(name = "pause_simulation", description = "Pause a running simulation. Only SIMULATING or "
        + "LIVE simulations can be paused. Takes effect within one evaluation, and the scheduler "
        + "stops re-offering the run until it is resumed. This is also the concurrency lever on a "
        + "local LLM engine: parallel simulations share one serialized engine, so when two runs "
        + "starve each other into timeouts, pausing one lets the other finish — resume it after.")
    public ToolResponse pauseSimulation(
        @ToolArg(description = "The simulation UUID to pause") String simulationId
    ) {
        return execute(() -> {
            SimulationId id = new SimulationId(UUID.fromString(simulationId));
            pauseSimulationUseCase.execute(new PauseSimulationCommand(id, mcpUserContext.getUserId()));
            logger.info("MCP: Paused simulation {}", id.value());
            Simulation sim = getSimulationUseCase.execute(new GetSimulationQuery(id, mcpUserContext.getUserId()));
            String strategyName = strategyRepository.findById(sim.getStrategyId()).map(Strategy::getName).orElse(null);
            String tradingMode = deriveTradingMode(sim.getBrokerAccountId());
            return toJson(new McpStatusResponse(simulationId, "Simulation paused — status: " + sim.getStatus().name()));
        });
    }

    @Tool(name = "resume_simulation", description =
        "Resume a paused simulation from its last checkpoint. This is the counterpart to "
        + "pause_simulation. Use this — NOT start_simulation — to un-pause: start_simulation only "
        + "acts on PENDING simulations and silently does nothing to a paused one, which used to "
        + "leave a paused backtest with no way forward. "
        + "Idempotent: resuming a running or already-finished simulation has no effect. "
        + "Execution continues asynchronously; call get_simulation to check progress.")
    public ToolResponse resumeSimulation(
        @ToolArg(description = "The simulation UUID to resume") String simulationId
    ) {
        return execute(() -> {
            SimulationId id = new SimulationId(UUID.fromString(simulationId));
            // Check state BEFORE acting so the response reports what actually happened —
            // "Simulation resumed" on a run that was never paused is a fabrication.
            Simulation before = getSimulationUseCase.execute(new GetSimulationQuery(id, mcpUserContext.getUserId()));
            SimulationStatus preStatus = before.getStatus();
            if (preStatus == SimulationStatus.COMPLETED || preStatus == SimulationStatus.FAILED) {
                return toJson(new McpStatusResponse(simulationId,
                    "No action taken — simulation is already " + preStatus.name()
                    + ". Resuming a finished run has no effect."));
            }
            boolean wasPaused = before.isPaused();
            resumeSimulationUseCase.execute(
                new com.quorumtrading.application.usecase.command.ResumeSimulationCommand(
                    id, mcpUserContext.getUserId()));
            logger.info("MCP: Resumed simulation {}", id.value());
            Simulation sim = getSimulationUseCase.execute(new GetSimulationQuery(id, mcpUserContext.getUserId()));
            String message = wasPaused
                ? "Simulation resumed — status: " + sim.getStatus().name()
                    + ". Execution continues in the background."
                : "Simulation was not paused — nothing to resume; execution re-submitted in the background"
                    + " (status: " + sim.getStatus().name() + ").";
            return toJson(new McpStatusResponse(simulationId, message));
        });
    }

    @Tool(name = "update_simulation_notification_preferences", description =
            "Toggle whether executed BUY/SELL decisions for this simulation send a notification to the owner. " +
            "HOLDs never trigger notifications regardless. Channel routing (in-app vs email) is controlled " +
            "separately via update_notification_preference for the SIMULATION_DECISION type.")
    public ToolResponse updateSimulationNotificationPreferences(
        @ToolArg(description = "The simulation UUID") String simulationId,
        @ToolArg(description = "true to subscribe to BUY/SELL decision notifications, false to unsubscribe") boolean notifyDecisions
    ) {
        return execute(() -> {
            SimulationId id = new SimulationId(UUID.fromString(simulationId));
            updateSimulationNotificationPreferenceUseCase.execute(
                new com.quorumtrading.application.usecase.command.UpdateSimulationNotificationPreferenceCommand(
                    id, mcpUserContext.getUserId(), notifyDecisions));
            logger.info("MCP: notifyDecisions={} on simulation {}", notifyDecisions, id.value());
            return toJson(new McpStatusResponse(simulationId,
                "notifyDecisions set to " + notifyDecisions));
        });
    }

    private Map<UUID, String> resolveStrategyNames(List<Simulation> simulations) {
        Map<UUID, String> names = new HashMap<>();
        simulations.stream()
            .map(s -> s.getStrategyId().value())
            .collect(Collectors.toSet())
            .forEach(sid -> strategyRepository.findById(new StrategyId(sid))
                .ifPresent(s -> names.put(sid, s.getName())));
        return names;
    }

    private Map<UUID, String> resolveTradingModes(List<Simulation> simulations) {
        Map<UUID, String> modes = new HashMap<>();
        simulations.stream()
            .map(Simulation::getBrokerAccountId)
            .filter(id -> id != null)
            .map(BrokerAccountId::value)
            .collect(Collectors.toSet())
            .forEach(bid -> brokerAccountRepository.findById(new BrokerAccountId(bid))
                .ifPresent(a -> modes.put(bid, tradingModeFromBrokerType(a.getBrokerType()))));
        // null key = no broker = BACKTEST
        modes.put(null, "BACKTEST");
        return modes;
    }

    /**
     * Broker account ids, among these simulations, that were created inline rather than chosen
     * by the user. Batched with the same single lookup per distinct account that the trading
     * modes use, so listing does not turn into an N+1.
     */
    private Set<UUID> resolveInlineBrokerAccountIds(List<Simulation> simulations) {
        Set<UUID> inlineIds = new java.util.HashSet<>();
        simulations.stream()
            .map(Simulation::getBrokerAccountId)
            .filter(id -> id != null)
            .map(BrokerAccountId::value)
            .collect(Collectors.toSet())
            .forEach(bid -> brokerAccountRepository.findById(new BrokerAccountId(bid))
                .filter(BrokerAccount::isInline)
                .ifPresent(a -> inlineIds.add(bid)));
        return inlineIds;
    }

    private Map<UUID, List<String>> resolveStrategySymbols(List<Simulation> simulations) {
        Map<UUID, List<String>> symbols = new HashMap<>();
        simulations.stream()
            .map(s -> s.getStrategyId().value())
            .collect(Collectors.toSet())
            .forEach(sid -> strategyRepository.findById(new StrategyId(sid))
                .ifPresent(s -> symbols.put(sid, mapper.extractSymbols(s))));
        return symbols;
    }

    /**
     * Batch-load contributions for a page of simulations in one query, keyed by
     * simulation UUID. Avoids an N+1 when computing contribution-aware returns for
     * each row of list_simulations. Sims with no contributions are simply absent.
     */
    private Map<UUID, List<com.quorumtrading.model.entity.Contribution>> resolveContributions(List<Simulation> simulations) {
        Map<UUID, List<com.quorumtrading.model.entity.Contribution>> result = new HashMap<>();
        List<com.quorumtrading.model.valueobject.SimulationId> ids =
            simulations.stream().map(Simulation::getSimulationId).toList();
        simulationContributionRepository.findBySimulationIds(ids)
            .forEach((simId, contributions) -> result.put(simId.value(), contributions));
        return result;
    }

    private String deriveTradingMode(BrokerAccountId brokerAccountId) {
        if (brokerAccountId == null) return "BACKTEST";
        return brokerAccountRepository.findById(brokerAccountId)
            .map(a -> tradingModeFromBrokerType(a.getBrokerType()))
            .orElse("BACKTEST");
    }

    private static String tradingModeFromBrokerType(BrokerType type) {
        return switch (type) {
            case MOCK -> "BACKTEST";
            case ALPACA_PAPER, SAXO_SIM -> "PAPER";
            case ALPACA_LIVE, SAXO_LIVE -> "LIVE";
        };
    }

    /**
     * Build a cost-aware message for backtests > 31 days.
     * Falls back to the generic message if estimation fails.
     */
    private String buildCostMessage(long durationDays, StrategyId strategyId, int strategyCount) {
        try {
            var estimate = estimateBacktestCostUseCase.estimateForSimulation(
                strategyId, durationDays, mcpUserContext.getUserId());
            return "This backtest spans " + durationDays + " days. "
                + "Estimated cost: " + estimate.formattedCost()
                + " (" + estimate.formattedRange() + " range, ~" + estimate.totalLLMCalls()
                + " LLM calls using " + estimate.modelName() + "). "
                + "Please inform the user about the cost, "
                + "then retry with userInformedOfCost=true.";
        } catch (Exception e) {
            logger.warn("Cost estimation failed, using generic message: {}", e.getMessage());
            return "This backtest spans " + durationDays + " days. Each trading day triggers 4+ AI agent consensus calls, "
                + "which consumes AI budget. Please inform the user about the cost implications, "
                + "then retry with userInformedOfCost=true. Never refuse to create the backtest — just ensure the user knows the cost.";
        }
    }

    @Tool(name = "estimate_backtest_cost", description =
        "Price a backtest WITHOUT creating or running it. Returns the estimated AI spend, the "
        + "confidence range, the number of consensus rounds and LLM calls, and the model the "
        + "estimate is priced against. "
        + "Use this before create_simulation whenever cost matters — creating a simulation starts "
        + "it, so there is no way to create one, read its cost, and then decide. "
        + "The estimate respects the strategy's backtest_schedule: narrowing trading_days is the "
        + "documented way to control spend, and a narrowed schedule is quoted at its real rate — including zero "
        + "when the window contains no day the schedule fires on, which means the run would decide nothing. "
        + "The response also cross-checks the window against STORED PRICE DATA: a dataCoverageWarning "
        + "field appears when the window is not covered (no bars, or a large share of expected days "
        + "missing) — the estimate prices the schedule, not the data, so treat that warning as 'this "
        + "run would evaluate nothing there' and backfill before creating anything. "
        + "The quote is priced against the currently selected LLM engine; check get_llm_status if you need to "
        + "know the run can actually execute. Also returns estimatedWallClockSeconds projected from the "
        + "engine's observed latency — read it especially for free local engines, where the dollar figure "
        + "is $0.00 but the run can cost hours.")
    public ToolResponse estimateBacktestCost(
            @ToolArg(description = "UUID of the strategy to price") String strategyId,
            @ToolArg(description = "Backtest start date (YYYY-MM-DD)") String startDate,
            @ToolArg(description = "Backtest end date (YYYY-MM-DD)") String endDate
    ) {
        return execute(() -> {
            StrategyId id = new StrategyId(UUID.fromString(strategyId));
            java.time.Instant from = parseDate(startDate);
            java.time.Instant to = parseDate(endDate);
            if (!to.isAfter(from)) {
                throw new IllegalArgumentException(
                    "endDate must be after startDate (got " + startDate + " → " + endDate + ")");
            }
            // The date-aware overload, not the day-count one: how many trading days a span holds
            // depends on which weekday it starts on, and only the dates carry that.
            var timed = estimateBacktestCostUseCase.estimateForSimulationWithTime(
                id, from, to, mcpUserContext.getUserId());
            return toJson(mapper.toCostEstimateDto(
                timed.estimate(), timed.estimatedWallClockSeconds(), timed.timeBasis()));
        });
    }

    @Tool(name = "sell_position", description =
        "Sell an entire position of a single symbol at market price. " +
        "WARNING: This executes a real market sell order — you MUST get explicit user confirmation " +
        "(state simulation name, symbol, quantity, and that it will sell at current market price) before calling. " +
        "Only works for LIVE or paper simulations with open positions. " +
        "NOT available for backtests: a backtest replays historical data, so a manual sell would be " +
        "filled at today's market price and corrupt the run. Not available once a simulation has ended. " +
        "If the market is closed (stocks), the order will fail — inform the user.")
    public ToolResponse sellPosition(
        @ToolArg(description = "The simulation UUID. Call get_simulation first to verify the simulation exists and has positions.") String simulationId,
        @ToolArg(description = "The ticker symbol to sell (e.g., BTC, AAPL). Must exactly match the symbol in the portfolio — call query_portfolio_snapshots to verify.") String symbol,
        @ToolArg(description = "Investment type of the symbol: CRYPTO or STOCK. Must match the symbol.") String investmentType
    ) {
        return execute(() -> {
            SimulationId id = new SimulationId(UUID.fromString(simulationId));
            InvestmentType type = parseEnum(InvestmentType.class, investmentType, "investmentType");
            Symbol sym = SymbolParser.parse(symbol, type);
            var result = sellPositionUseCase.execute(id, sym, mcpUserContext.getUserId());
            if (!result.success()) {
                return toJson(new McpStatusResponse(simulationId, result.message()));
            }
            return toJson(new McpStatusResponse(simulationId,
                String.format("Sold %s %s at $%s", result.filledQuantity().toPlainString(), symbol,
                    result.executionPrice().toPlainString())));
        });
    }

    @Tool(name = "liquidate_all_positions", description =
        "Cancel all open orders and sell ALL positions in a simulation at market price. " +
        "WARNING: This is a destructive bulk action — it sells EVERY position the simulation holds. " +
        "You MUST get explicit user confirmation before calling. Tell the user exactly which simulation " +
        "and how many positions will be liquidated. " +
        "NOT available for backtests: a backtest replays historical data, so a manual liquidation " +
        "would be filled at today's market price. Not available once a simulation has ended. " +
        "For stocks, the market must be open or orders will fail.")
    public ToolResponse liquidateAllPositions(
        @ToolArg(description = "The simulation UUID. Call get_simulation first to confirm it has positions worth liquidating.") String simulationId
    ) {
        return execute(() -> {
            SimulationId id = new SimulationId(UUID.fromString(simulationId));
            var result = liquidateAllPositionsUseCase.execute(id, mcpUserContext.getUserId());
            return toJson(result);
        });
    }

    @Tool(name = "force_end_simulation", description =
        "Permanently end a simulation and set its status to COMPLETED. " +
        "WARNING: This is IRREVERSIBLE — the simulation cannot be restarted after ending. " +
        "You MUST get explicit user confirmation before calling. State the simulation name, current status, " +
        "and whether positions will be liquidated. " +
        "If liquidatePositions is true, all positions are sold at market price before completing. " +
        "If liquidatePositions is false AND the simulation uses an Alpaca broker (paper or live), " +
        "warn the user that positions will remain open in Alpaca without QuorumTrading monitoring. " +
        "Only works for non-terminal simulations (not already COMPLETED or FAILED).")
    public ToolResponse forceEndSimulation(
        @ToolArg(description = "The simulation UUID to end permanently.") String simulationId,
        @ToolArg(description = "Whether to liquidate all positions before ending. 'true' sells everything at market price. 'false' leaves positions as-is (risky for Alpaca accounts).") String liquidatePositions
    ) {
        return execute(() -> {
            SimulationId id = new SimulationId(UUID.fromString(simulationId));
            boolean liquidate = Boolean.parseBoolean(liquidatePositions);
            var command = new com.quorumtrading.application.usecase.command.ForceEndSimulationCommand(id, liquidate, mcpUserContext.getUserId());
            var result = forceEndSimulationUseCase.execute(command);
            return toJson(new McpStatusResponse(simulationId, result.message()));
        });
    }

    @Tool(name = "get_simulation_cost", description = "Calculate the AI cost of a simulation (billed to the user) by summing token usage across all consensus decisions. " +
        "Returns total cost with markup, token counts, and decision count. " +
        "For in-progress simulations, includes progress percentage, a partial flag, and an estimated total cost extrapolated from current progress.")
    public ToolResponse getSimulationCost(
        @ToolArg(description = "UUID of the simulation") String simulationId
    ) {
        return execute(() -> {
            var query = new com.quorumtrading.application.usecase.query.CalculateSimulationCostQuery(
                UUID.fromString(simulationId), mcpUserContext.getUserId());
            var cost = calculateSimulationCostUseCase.execute(query);

            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("simulationId", simulationId);
            result.put("cost", cost.formattedCost());

            // Local engines DO record completion tokens while reporting a placeholder for the
            // prompt (2 tokens for a 4,543-character prompt), so the simulation's prompt-token
            // sum is never zero and a zero-check never fires. Ask how many agent calls carry a
            // prompt-token value that cannot describe the prompt actually sent.
            long uncountedPromptCalls = llmDecisionRepository
                .countUncountedPromptTokensBySimulationId(UUID.fromString(simulationId));

            result.put("promptTokens", uncountedPromptCalls > 0 ? null : cost.totalPromptTokens());
            result.put("completionTokens", cost.totalCompletionTokens());
            result.put("totalTokens", uncountedPromptCalls > 0 ? null : cost.totalTokens());
            if (uncountedPromptCalls > 0) {
                result.put("promptTokensNote", String.format(
                    "Prompt tokens are unavailable: %d agent call(s) in this simulation ran on an "
                    + "engine that does not count prompt tokens, so any sum would understate real "
                    + "usage by orders of magnitude. Completion tokens (%d) and decision count "
                    + "(%d) are accurate; cost reflects what the engine reported.",
                    uncountedPromptCalls, cost.totalCompletionTokens(), cost.decisionCount()));
            }
            result.put("decisionCount", cost.decisionCount());
            result.put("partial", cost.partial());
            result.put("progressPercent", cost.progressPercent());

            if (cost.partial()) {
                result.put("estimatedTotalCost", cost.formattedEstimatedTotalCost());
                if (cost.progressPercent() > 0) {
                    result.put("warning", String.format(
                        "Simulation is %d%% complete. Cost shown is partial. Estimated total: %s",
                        cost.progressPercent(), cost.formattedEstimatedTotalCost()));
                } else {
                    result.put("warning", "Simulation has not started processing yet. Cost may be $0.00.");
                }
            }

            return toJson(result);
        });
    }

    @Tool(name = "restart_simulation_from_decision",
        description = "Restart a BACKTEST simulation from a specific consensus decision. " +
            "Archives LLM costs from deleted decisions, deletes consensus decisions, LLM decisions, " +
            "and portfolio snapshots at or after the selected decision's timestamp, then sets the " +
            "simulation to SIMULATING (paused). The user can then resume when ready. " +
            "Only works on BACKTEST simulations that are COMPLETED, FAILED, or paused.")
    public ToolResponse restartSimulationFromDecision(
        @ToolArg(description = "The simulation UUID to restart") String simulationId,
        @ToolArg(description = "The consensus decision ID to restart from (decisions at and after this point will be deleted)") long consensusDecisionId
    ) {
        return execute(() -> {
            UUID simUuid = UUID.fromString(simulationId);
            var result = restartSimulationFromService.execute(simUuid, consensusDecisionId, mcpUserContext.getUserId());
            logger.info("MCP: Restarted simulation {} from decision {}", simulationId, consensusDecisionId);

            Map<String, Object> response = new java.util.LinkedHashMap<>();
            response.put("simulationId", simulationId);
            response.put("status", "Simulation restarted — now SIMULATING (paused). Resume when ready.");
            response.put("restartTimestamp", result.restartTimestamp().toString());
            response.put("consensusDecisionsDeleted", result.consensusDecisionsToDelete());
            response.put("llmDecisionsDeleted", result.llmDecisionsToDelete());
            response.put("portfolioSnapshotsDeleted", result.portfolioSnapshotsToDelete());
            response.put("archivedCostUsd", result.archivedCostUsd().toPlainString());
            response.put("archivedTokens", result.archivedTokens());
            response.put("portfolioCashAtRestart", result.portfolioCashAtRestart().toPlainString());
            response.put("portfolioValueAtRestart", result.portfolioValueAtRestart().toPlainString());
            return toJson(response);
        });
    }

    @Tool(name = "delete_simulation", description = "Delete a simulation and all associated consensus decisions, portfolio snapshots, and trade history. The simulation will be permanently removed.")
    public ToolResponse deleteSimulation(
        @ToolArg(description = "The simulation UUID to delete") String simulationId
    ) {
        return execute(() -> {
            SimulationId id = new SimulationId(UUID.fromString(simulationId));
            deleteSimulationUseCase.execute(new DeleteSimulationCommand(id, mcpUserContext.getUserId()));
            logger.info("MCP: Deleted simulation {}", id.value());
            return toJson(new McpStatusResponse(simulationId, "Simulation and all associated data deleted"));
        });
    }

    /**
     * Build a news data availability warning if the strategy uses Fundamental or Sentiment agents
     * and the simulation start date is before the earliest available news date.
     * Returns an empty string if no warning is needed.
     */
    /**
     * Warns when the price history stored before the start date cannot feed the
     * indicators, so a user learns it while deciding rather than after paying.
     *
     * <p>Never throws: a warning that cannot be produced must not prevent a
     * simulation from being created.</p>
     */
    String buildPriceHistoryWarning(String strategyId, Instant startDate) {
        try {
            Strategy strategy = strategyRepository.findById(new StrategyId(UUID.fromString(strategyId)))
                .orElse(null);
            if (strategy == null) {
                return "";
            }
            var config = new com.quorumtrading.model.orchestration.StrategyConfigParser()
                .parse(strategy.getContent());
            return priceHistoryWarmupInspector.describe(
                priceHistoryWarmupInspector.inspect(config, startDate));
        } catch (Exception e) {
            logger.debug("Price-history warm-up check failed for strategy {}: {}", strategyId, e.getMessage());
            return "";
        }
    }

    String buildNewsAvailabilityWarning(String strategyId, Instant startDate) {
        if (!newsDataAvailability.isBeforeCutoff(startDate)) {
            return "";
        }
        try {
            Strategy strategy = strategyRepository.findById(new StrategyId(UUID.fromString(strategyId))).orElse(null);
            if (strategy == null) return "";

            var config = new com.quorumtrading.model.orchestration.StrategyConfigParser().parse(strategy.getContent());
            var agents = config.getEffectiveEnabledAgents();
            boolean usesNewsAgents = agents.contains(com.quorumtrading.model.enums.AgentRole.FUNDAMENTAL)
                || agents.contains(com.quorumtrading.model.enums.AgentRole.SENTIMENT);
            if (!usesNewsAgents) return "";

            return String.format(". WARNING: This backtest starts before %s, but news data is only available from that date. " +
                "Your strategy uses Fundamental/Sentiment agents that rely on news data — " +
                "consensus decisions before %s will have no news context, potentially reducing accuracy",
                newsDataAvailability.earliestNewsDate(), newsDataAvailability.earliestNewsDate());
        } catch (Exception e) {
            logger.debug("Failed to check news availability for strategy {}: {}", strategyId, e.getMessage());
            return "";
        }
    }

    /**
     * Warns at creation time when the strategy's own risk numbers make trading impossible.
     *
     * <p>{@code initialCash × (1 − reserve_cash_percentage) × position_limit} is the largest
     * BUY the engine will ever attempt for a ticker; when that lands under the per-asset-class
     * minimum trade value, every BUY converts to HOLD before it reaches the broker. The
     * cheatsheet's own example numbers walk a small portfolio straight into this, and the
     * first sign used to be a finished run full of "below minimum" notes.</p>
     *
     * <p>Never throws: a warning that cannot be produced must not prevent a simulation from
     * being created.</p>
     */
    String buildTradeHeadroomWarning(String strategyId, BigDecimal initialCash) {
        try {
            Strategy strategy = strategyRepository.findById(new StrategyId(UUID.fromString(strategyId)))
                .orElse(null);
            if (strategy == null || initialCash == null) {
                return "";
            }
            var config = new com.quorumtrading.model.orchestration.StrategyConfigParser()
                .parse(strategy.getContent());
            BigDecimal investable = initialCash.multiply(
                BigDecimal.valueOf(1.0 - config.getReserveCashPercentage()));
            var limits = config.getPositionLimits();
            java.util.Set<String> cryptoNames = config.getCryptoTickers() == null
                ? java.util.Set.of()
                : config.getCryptoTickers().stream()
                    .map(s -> s.ticker()).collect(java.util.stream.Collectors.toSet());
            java.util.List<String> allTickers = new java.util.ArrayList<>(cryptoNames);
            if (config.getStockTickers() != null) {
                config.getStockTickers().forEach(s -> allTickers.add(s.ticker()));
            }
            java.util.List<String> starved = new java.util.ArrayList<>();
            for (String ticker : allTickers) {
                double limitFraction = limits != null && limits.get(ticker) != null ? limits.get(ticker) : 1.0;
                BigDecimal headroom = investable.multiply(BigDecimal.valueOf(limitFraction));
                BigDecimal minimum = cryptoNames.contains(ticker)
                    ? tradingLimitsConfig.crypto() : tradingLimitsConfig.stock();
                if (headroom.compareTo(minimum) < 0) {
                    starved.add(String.format("%s (max order $%s vs $%s minimum)",
                        ticker, headroom.setScale(2, java.math.RoundingMode.HALF_UP), minimum));
                }
            }
            if (starved.isEmpty()) {
                return "";
            }
            return ". WARNING: the risk settings leave too little to trade for "
                + String.join(", ", starved)
                + " — initialCash × (1 − reserve_cash_percentage) × position_limit is below the "
                + "minimum trade value, so every BUY for these tickers will convert to HOLD. "
                + "Raise initialCash, lower reserve_cash_percentage, or raise the position limit";
        } catch (Exception e) {
            logger.debug("Trade-headroom check failed for strategy {}: {}", strategyId, e.getMessage());
            return "";
        }
    }
}
