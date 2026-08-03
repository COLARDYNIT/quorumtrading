package com.quorumtrading.application.service;

import com.quorumtrading.application.event.ConsensusStartedEvent;
import com.quorumtrading.application.exception.BudgetDepletedException;
import com.quorumtrading.application.exception.DailySpendLimitExceededException;
import com.quorumtrading.application.orchestration.StrategyParser;
import com.quorumtrading.application.orchestration.agents.ConsensusActionParser;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.service.consensus.AgentInvoker;
import com.quorumtrading.application.service.consensus.ConsensusContextBuilder;
import com.quorumtrading.application.service.consensus.ConsensusOutcome;
import com.quorumtrading.application.service.consensus.ConsensusOutcomeRecorder;
import com.quorumtrading.application.service.consensus.ConsensusTradeCoordinator;
import com.quorumtrading.application.service.consensus.DataGapCircuitBreaker;
import com.quorumtrading.application.usecase.ReachConsensusUseCase;
import com.quorumtrading.application.usecase.command.ReachConsensusCommand;
import com.quorumtrading.model.entity.ConsensusDecision;
import com.quorumtrading.model.entity.LLMDecision;
import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.ConsensusMethod;
import com.quorumtrading.model.enums.RecommendationType;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.marketdata.Timeframe;
import com.quorumtrading.model.orchestration.AgentContext;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.service.MajorityVoteCalculator;
import com.quorumtrading.model.valueobject.ConsensusAction;
import com.quorumtrading.model.valueobject.IndicatorContext;
import com.quorumtrading.model.valueobject.Symbol;
import io.micrometer.core.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Orchestrates a full consensus round across all AI agents.
 *
 * For the given portfolio and market conditions, this service:
 * 1. Queries each specialized agent (Technical, Fundamental, Sentiment) in parallel
 * 2. Saves each LLMDecision and fires WebSocket event for real-time updates
 * 3. Creates and queries orchestrator agent with all agent decisions
 * 4. Saves orchestrator LLMDecision and fires WebSocket event
 * 5. Creates ConsensusDecision based on orchestrator's final recommendation
 *
 * <p>The heavy lifting is delegated to cohesive components (all in
 * {@code application.service.consensus}): {@link AgentInvoker} (agent + orchestrator
 * LLM invocation), {@link ConsensusContextBuilder} (market data, indicators, model
 * selection, cooldowns, agent context), {@link ConsensusOutcomeRecorder} (persistence
 * of decisions, skip rows and LLM usage), {@link ConsensusTradeCoordinator} (broker
 * portfolio + trade execution + notification) and {@link DataGapCircuitBreaker}
 * (skip accounting / pause). This class keeps only the flow.</p>
 */
public class ReachConsensusService implements ReachConsensusUseCase {

    private static final Logger logger = LoggerFactory.getLogger(ReachConsensusService.class);

    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final StrategyParser strategyParser;
    private final com.quorumtrading.application.usecase.budget.RecordLLMUsageUseCase recordLLMUsageUseCase;
    private final EventPublisher<ConsensusStartedEvent> consensusStartedEventPublisher;
    private final AgentInvoker agentInvoker;
    private final ConsensusContextBuilder contextBuilder;
    private final ConsensusOutcomeRecorder outcomeRecorder;
    private final ConsensusTradeCoordinator tradeCoordinator;
    private final DataGapCircuitBreaker circuitBreaker;
    private final ExecutorService executorService;
    private final int pipelineTimeoutSeconds;

    public ReachConsensusService(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            StrategyParser strategyParser,
            com.quorumtrading.application.usecase.budget.RecordLLMUsageUseCase recordLLMUsageUseCase,
            EventPublisher<ConsensusStartedEvent> consensusStartedEventPublisher,
            AgentInvoker agentInvoker,
            ConsensusContextBuilder contextBuilder,
            ConsensusOutcomeRecorder outcomeRecorder,
            ConsensusTradeCoordinator tradeCoordinator,
            DataGapCircuitBreaker circuitBreaker,
            ExecutorService executorService) {
        this(simulationRepository, strategyRepository, strategyParser, recordLLMUsageUseCase,
            consensusStartedEventPublisher, agentInvoker, contextBuilder, outcomeRecorder,
            tradeCoordinator, circuitBreaker, executorService, 45);
    }

    public ReachConsensusService(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            StrategyParser strategyParser,
            com.quorumtrading.application.usecase.budget.RecordLLMUsageUseCase recordLLMUsageUseCase,
            EventPublisher<ConsensusStartedEvent> consensusStartedEventPublisher,
            AgentInvoker agentInvoker,
            ConsensusContextBuilder contextBuilder,
            ConsensusOutcomeRecorder outcomeRecorder,
            ConsensusTradeCoordinator tradeCoordinator,
            DataGapCircuitBreaker circuitBreaker,
            ExecutorService executorService,
            int pipelineTimeoutSeconds) {
        this.simulationRepository = simulationRepository;
        this.strategyRepository = strategyRepository;
        this.strategyParser = strategyParser;
        this.recordLLMUsageUseCase = recordLLMUsageUseCase;
        this.consensusStartedEventPublisher = consensusStartedEventPublisher;
        this.agentInvoker = agentInvoker;
        this.contextBuilder = contextBuilder;
        this.outcomeRecorder = outcomeRecorder;
        this.tradeCoordinator = tradeCoordinator;
        this.circuitBreaker = circuitBreaker;
        this.executorService = executorService;
        this.pipelineTimeoutSeconds = pipelineTimeoutSeconds;
    }

    @Override
    @Timed(value = "consensus.round", description = "Full consensus round for all tickers")
    public void execute(ReachConsensusCommand command) {
        logger.info("Reaching consensus for simulation: {} at timestamp: {}",
            command.simulationId().value().toString().substring(0, 8), command.analysisTimestamp());

        // Generate unique consensus request ID
        String consensusRequestId = UUID.randomUUID().toString();

        try {
            // Step 1: Fetch Simulation from repository
            Simulation simulation = simulationRepository.findById(command.simulationId())
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found: " + command.simulationId()));

            // Step 1.1: Verify simulation is active (not paused)
            // Accept both SIMULATING (historical backfill) and LIVE (real-time) statuses
            boolean isSimulating = simulation.getStatus() == SimulationStatus.SIMULATING;
            boolean isLive = simulation.getStatus() == SimulationStatus.LIVE;
            if ((!isSimulating && !isLive) || simulation.isPaused()) {
                logger.info("Simulation {} is not active (status: {}, paused: {}), skipping consensus",
                    command.simulationId().value().toString().substring(0, 8),
                    simulation.getStatus(),
                    simulation.isPaused());
                circuitBreaker.publishSkipped(
                    command.simulationId(), consensusRequestId, command.analysisTimestamp(),
                    "Simulation is not active (status: " + simulation.getStatus() + ", paused: " + simulation.isPaused() + ")"
                );
                return; // Skip consensus execution for paused or non-active simulations
            }

            // Step 2: Fetch Strategy from repository
            Strategy strategy = strategyRepository.findById(simulation.getStrategyId())
                .orElseThrow(() -> new IllegalArgumentException("Strategy not found: " + simulation.getStrategyId()));

            // Step 2.1: Budget pre-check: Fail fast if strategy owner's AI budget is depleted
            // This prevents initiating LLM calls that would incur costs when user cannot pay
            if (strategy.getOwnerId() != null) {
                recordLLMUsageUseCase.requireAvailableBudget(strategy.getOwnerId());
                logger.debug("Budget pre-check passed for user {} (simulation {})",
                    strategy.getOwnerId(), command.simulationId().value().toString().substring(0, 8));
            }

            // Step 3: Parse strategy configuration
            StrategyConfig strategyConfig = strategyParser.parse(strategy.getContent());

            // Step 3.1: PRE-FLIGHT CHECK - Validate market data availability
            // CRITICAL: Running consensus without market data makes no sense
            Set<Symbol> allTickerSymbols = new HashSet<>();
            allTickerSymbols.addAll(strategyConfig.getStockTickers());
            allTickerSymbols.addAll(strategyConfig.getCryptoTickers());

            if (!allTickerSymbols.isEmpty()) {
                boolean hasMarketData = contextBuilder.validateMarketDataAvailability(
                    allTickerSymbols,
                    simulation.getStartDate(),
                    command.analysisTimestamp()
                );

                if (!hasMarketData) {
                    logger.warn("No market data available for simulation {} at timestamp {}. " +
                        "Consensus will be skipped for this evaluation period.",
                        command.simulationId().value().toString().substring(0, 8),
                        command.analysisTimestamp());

                    // Create a "skipped" consensus decision to record this in the timeline
                    outcomeRecorder.createSkippedConsensusDecision(
                        simulation,
                        command.analysisTimestamp(),
                        "Consensus skipped: No market data available for tickers at " + command.analysisTimestamp() +
                        ". This can happen when backtesting periods with incomplete historical data."
                    );

                    // CIRCUIT BREAKER: Track skipped evaluations and pause if threshold exceeded
                    circuitBreaker.recordDataGapSkip(
                        simulation, command.analysisTimestamp(), allTickerSymbols, consensusRequestId);
                    return; // Skip this evaluation, continue to next
                }

                logger.debug("Pre-flight check passed: Market data available for {} tickers", allTickerSymbols.size());
            }

            // Fire ConsensusStartedEvent AFTER all pre-flight checks pass
            consensusStartedEventPublisher.publish(new ConsensusStartedEvent(
                command.simulationId(),
                consensusRequestId,
                command.analysisTimestamp()
            ));
            logger.debug("Fired ConsensusStartedEvent with requestId: {} for simulation: {}",
                consensusRequestId, command.simulationId().value().toString().substring(0, 8));

            // Step 4: Fetch current Portfolio from repository (or create initial one)
            Portfolio currentPortfolio = contextBuilder.getLatestPortfolio(
                command.simulationId(), command.analysisTimestamp());
            logger.debug("Fetched current portfolio with cash: {} and {} positions",
                currentPortfolio.cashAvailable(), currentPortfolio.positions().size());

            // Step 4.1: Initialize broker portfolio state if not already initialized
            tradeCoordinator.initializeBrokerPortfolio(command.simulationId(), currentPortfolio);

            // Step 5: Fetch multi-timeframe market data up to analysisTimestamp
            long marketDataStart = System.nanoTime();
            Map<Timeframe, List<MarketEvent>> marketDataForTickers = contextBuilder.fetchMultiTimeframeMarketDataForTickers(
                strategyConfig,
                command.analysisTimestamp()
            );
            long marketDataMs = (System.nanoTime() - marketDataStart) / 1_000_000;
            logger.info("Fetched multi-timeframe market data: {} timeframes in {}ms",
                marketDataForTickers.size(), marketDataMs);

            // Step 5.1: Calculate indicators for all tickers. Crypto was historically excluded
            // (no local crypto bars back then), which left the Technical agent without the very
            // RSI/SMA/MACD values strategies reference — the keyless crypto feed now fills
            // price_bars, so crypto indicators compute from the same HistoricalPricePort as stocks.
            long indicatorStart = System.nanoTime();
            List<Symbol> indicatorTickers = new ArrayList<>(strategyConfig.getStockTickers());
            indicatorTickers.addAll(strategyConfig.getCryptoTickers());
            IndicatorContext indicators = contextBuilder.calculateIndicators(
                indicatorTickers, command.analysisTimestamp());
            long indicatorMs = (System.nanoTime() - indicatorStart) / 1_000_000;
            logger.info("Calculated indicators for {} tickers in {}ms (asOf={})",
                indicatorTickers.size(), indicatorMs, command.analysisTimestamp());

            String simIdShort = command.simulationId().value().toString().substring(0, 8);

            // Get all tickers from strategy (both stock and crypto)
            Set<String> allTickers = new HashSet<>();
            strategyConfig.getStockTickers().stream()
                .map(symbol -> symbol.ticker())
                .forEach(allTickers::add);
            strategyConfig.getCryptoTickers().stream()
                .map(symbol -> symbol.ticker())
                .forEach(allTickers::add);

            if (allTickers.isEmpty()) {
                logger.warn("[sim={}] No tickers defined in strategy config, skipping consensus", simIdShort);
                return;
            }

            logger.info("Processing {} tickers with parallel agent queries: {}", allTickers.size(), allTickers);

            // Look up user model preferences + system defaults for the simulation mode
            ConsensusContextBuilder.ModelSelection modelSelection =
                contextBuilder.resolveModelSelection(simulation, strategy.getOwnerId());

            // PIPELINED PHASES: Agent queries → Orchestrator calls (overlapping across tickers)
            // Each ticker's orchestrator starts as soon as its agents complete,
            // allowing Ticker A's orchestrator to run while Ticker B's agents are still in-flight.
            long contextBuildStart = System.nanoTime();
            Map<String, AgentContext> tickerContexts = new LinkedHashMap<>();
            for (String ticker : allTickers) {
                AgentContext context = contextBuilder.buildAgentContext(
                    ticker, simulation, currentPortfolio, marketDataForTickers,
                    strategyConfig, command.analysisTimestamp(), indicators, modelSelection);
                tickerContexts.put(ticker, context);
            }
            long contextBuildMs = (System.nanoTime() - contextBuildStart) / 1_000_000;

            long pipelineStart = System.nanoTime();

            // For each ticker: fire agents → when done, save decisions → fire orchestrator
            // All of this happens concurrently across tickers
            Map<String, CompletableFuture<ConsensusOutcome>> pipelineFutures = new LinkedHashMap<>();

            for (String ticker : allTickers) {
                // Position cooldown: skip entire pipeline if last BUY/SELL was too recent
                Optional<ConsensusContextBuilder.CooldownSkip> cooldownSkip =
                    contextBuilder.checkPositionCooldown(simulation, strategyConfig, ticker, command.analysisTimestamp());
                if (cooldownSkip.isPresent()) {
                    ConsensusContextBuilder.CooldownSkip skip = cooldownSkip.get();
                    outcomeRecorder.saveTickerSkip(
                        simulation, strategyConfig, ticker, command.analysisTimestamp(), skip.note());
                    logger.info("[sim={}] Position cooldown: skipping {} ({} elapsed, cooldown {})",
                        simIdShort, ticker,
                        ConsensusContextBuilder.formatDuration(skip.elapsed()),
                        ConsensusContextBuilder.formatDuration(skip.cooldown()));
                    continue;
                }

                // Market closed (stock backtests): no price bar on the evaluation day means the
                // mock broker cannot execute — skip before burning agent LLM calls.
                Optional<ConsensusContextBuilder.MarketClosedSkip> marketClosedSkip =
                    contextBuilder.checkMarketClosed(simulation, strategyConfig, ticker, command.analysisTimestamp());
                if (marketClosedSkip.isPresent()) {
                    outcomeRecorder.saveTickerSkip(
                        simulation, strategyConfig, ticker, command.analysisTimestamp(), marketClosedSkip.get().note());
                    logger.info("[sim={}] Market closed: skipping {} at {} (no price bar on evaluation day)",
                        simIdShort, ticker, command.analysisTimestamp());
                    continue;
                }

                AgentContext ctx = tickerContexts.get(ticker);
                boolean isMajority = strategyConfig.getConsensusMethod() == ConsensusMethod.MAJORITY;

                CompletableFuture<ConsensusOutcome> pipeline = CompletableFuture
                    .supplyAsync(() -> agentInvoker.queryAgentsInParallel(ctx), executorService)
                    .thenComposeAsync(agentDecisions -> {
                        // Save agent decisions (fast, no LLM call)
                        outcomeRecorder.saveAgentDecisions(ticker, simulation, agentDecisions);

                        // Enabled agents that produced no decision — named in the note, with the
                        // abstention reason or the failure, so "1/1 votes" is explainable.
                        String participationNote = agentInvoker.buildParticipationNote(
                            ctx, strategyConfig.getEffectiveEnabledAgents(), agentDecisions);

                        // Check if ALL agents failed (SKIP) — skip consensus
                        boolean allAgentsFailed = agentDecisions.stream()
                            .allMatch(d -> d.getRecommendation() == RecommendationType.SKIP);

                        if (allAgentsFailed) {
                            logger.warn("All {} agents returned SKIP for ticker {} - creating SKIP consensus{}",
                                agentDecisions.size(), ticker, participationNote);
                            // The note is what makes this round diagnosable, and this branch used
                            // to be the only one that dropped it.
                            outcomeRecorder.createSkipConsensusForTicker(ticker, simulation, agentDecisions,
                                strategyConfig, command.analysisTimestamp(), participationNote);
                            return CompletableFuture.completedFuture((ConsensusOutcome) null);
                        }

                        if (isMajority) {
                            // MAJORITY: Skip orchestrator LLM call — agent votes determine the final action
                            ConsensusOutcome outcome = appendNote(
                                computeMajorityVote(agentDecisions, ticker, strategyConfig.getConsensusThreshold()),
                                participationNote);
                            logger.info("[sim={}] Majority vote for {}: {}", simIdShort, ticker, outcome.note());
                            return CompletableFuture.completedFuture(outcome);
                        }

                        // ORCHESTRATOR_OVERRIDE: Fire orchestrator LLM call, fallback to majority vote on failure
                        return CompletableFuture.supplyAsync(() -> {
                            try {
                                LLMDecision decision = agentInvoker.invokeOrchestrator(ctx, agentDecisions);
                                return new ConsensusOutcome(
                                    ConsensusActionParser.fromLlmJson(decision.getRecommendedAction()),
                                    decision.getTickerQuantity(),
                                    decision,
                                    agentDecisions,
                                    "Orchestrator-led consensus decision for ticker: " + ticker + participationNote
                                );
                            } catch (Exception e) {
                                logger.warn("[sim={}] Orchestrator failed for {}, falling back to majority vote: {}",
                                    simIdShort, ticker, e.getMessage());
                                return appendNote(
                                    computeMajorityVote(agentDecisions, ticker, strategyConfig.getConsensusThreshold()),
                                    participationNote);
                            }
                        }, executorService);
                    }, executorService);

                pipelineFutures.put(ticker, pipeline);
            }

            // Wait for all pipelines (agents + orchestrators) to complete. The timeout is
            // configurable (quorumtrading.consensus.pipeline-timeout-seconds): hosted APIs answer
            // in seconds, but local engines (Ollama, CLI shims) can take minutes per call — with
            // too small a timeout every round is dropped without a decision row.
            try {
                CompletableFuture.allOf(pipelineFutures.values().toArray(new CompletableFuture[0]))
                    .get(pipelineTimeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                logger.warn("[sim={}] Some ticker pipelines timed out after {}s, continuing with available results",
                    simIdShort, pipelineTimeoutSeconds);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("[sim={}] Interrupted while waiting for ticker pipelines", simIdShort);
            } catch (ExecutionException e) {
                // A pipeline that threw used to be reported as "timed out after 45s" — the catch
                // was on Exception and the message assumed the only way out was the clock. That
                // sent every diagnosis after a pipeline failure looking at latency and timeouts,
                // while the actual cause sat unlogged in the swallowed exception.
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                logger.warn("[sim={}] A ticker pipeline failed ({}: {}), continuing with available results",
                    simIdShort, cause.getClass().getSimpleName(), cause.getMessage(), cause);
            }

            // PHASE 2b: Execute trades SEQUENTIALLY (portfolio state evolves)
            long phase2bStart = System.nanoTime();
            int decidedTickers = 0;    // pipelines that produced a consensus outcome (BUY/SELL/HOLD)
            int llmFailedTickers = 0;  // pipelines that produced nothing: engine failed or timed out
            for (String ticker : allTickers) {
                try {
                    CompletableFuture<ConsensusOutcome> future = pipelineFutures.get(ticker);
                    if (future == null) {
                        continue; // Cooldown skip — no pipeline was started for this ticker
                    }
                    if (!future.isDone() || future.isCompletedExceptionally()) {
                        logger.warn("[sim={}] Pipeline for ticker {} did not complete, skipping trade", simIdShort, ticker);
                        // Leave an audit row — a round that vanishes without record makes the
                        // decision timeline silently lossy (looks identical to "never evaluated").
                        outcomeRecorder.createSkippedConsensusDecision(simulation, command.analysisTimestamp(),
                            "Consensus pipeline for " + ticker + " did not complete within "
                                + pipelineTimeoutSeconds + "s (slow or failed LLM engine)");
                        llmFailedTickers++;
                        continue;
                    }

                    ConsensusOutcome result = future.join();
                    if (result == null) {
                        llmFailedTickers++; // all-agents-failed SKIP consensus (audit row already written)
                        continue; // No trade needed
                    }
                    decidedTickers++;

                    ConsensusDecision savedConsensus = outcomeRecorder.recordConsensusOutcome(
                        ticker, result, simulation, strategyConfig,
                        command.analysisTimestamp(), strategy.getOwnerId());
                    if (savedConsensus != null) {
                        tradeCoordinator.executeTradeAndNotify(ticker, savedConsensus, simulation);
                    }

                    // CRITICAL: Refresh portfolio from broker after each ticker's trade execution
                    currentPortfolio = tradeCoordinator.currentPortfolio(command.simulationId());
                    logger.debug("Refreshed portfolio after {} - Cash: {}, Positions: {}",
                        ticker, currentPortfolio.cashAvailable(), currentPortfolio.positions().size());

                } catch (Exception tickerEx) {
                    logger.error("[sim={}] Failed to process trade for ticker {}: {}", simIdShort, ticker, tickerEx.getMessage(), tickerEx);
                }
            }

            long phase2bMs = (System.nanoTime() - phase2bStart) / 1_000_000;
            long pipelineMs = (System.nanoTime() - pipelineStart) / 1_000_000;
            long totalMs = marketDataMs + indicatorMs + contextBuildMs + pipelineMs;
            logger.info("[sim={}] Timings: marketData={}ms indicators={}ms contextBuild={}ms pipeline={}ms trades={}ms total={}ms",
                simIdShort, marketDataMs, indicatorMs, contextBuildMs, pipelineMs - phase2bMs, phase2bMs, totalMs);

            logger.info("[sim={}] Completed consensus for all {} tickers", simIdShort, allTickers.size());

            // recordSuccess resets the consecutive-skip counter, so a round where the engine
            // answered for nobody must not take that branch — it kept the counters and the
            // circuit breaker at zero through hours of all-SKIP rounds, which is the exact
            // stall the counters exist to make visible. Benign rounds (cooldown, market
            // closed) still count as success: a long weekend must not trip the breaker.
            if (decidedTickers == 0 && llmFailedTickers > 0) {
                circuitBreaker.recordLlmFailureSkip(simulation, command.analysisTimestamp(), consensusRequestId,
                    "LLM engine produced no decision for any of " + llmFailedTickers
                        + " evaluated ticker(s) — engine failed or timed out");
            } else {
                circuitBreaker.recordSuccess(simulation);
            }

        } catch (BudgetDepletedException | DailySpendLimitExceededException budgetEx) {
            logger.error("Budget exhausted during consensus for simulation {}: {}",
                command.simulationId().value().toString().substring(0, 8), budgetEx.getMessage());
            throw budgetEx;
        } catch (Exception e) {
            logger.error("Failed to reach consensus for simulation: {}",
                command.simulationId(), e);
            throw new RuntimeException("Consensus process failed", e);
        }
    }

    /**
     * Compute majority vote from agent decisions.
     * Winner = action with most votes. Tie → HOLD.
     * Uses the highest-confidence winning agent's recommendedAction for the finalAction JSON.
     *
     * <p>Abstain rule: HOLD agents with confidence below {@code consensusThreshold} abstain
     * (they don't count as votes). BUY/SELL votes always count regardless of confidence.</p>
     *
     * <p>Vote aggregation is delegated to the pure domain service
     * {@link MajorityVoteCalculator}; this method only maps the result onto a
     * {@link ConsensusOutcome} (action JSON, quantity, note).</p>
     *
     * @param agentDecisions List of agent LLM decisions
     * @param ticker The ticker symbol being evaluated
     * @param consensusThreshold Confidence threshold below which HOLD agents abstain
     * @return ConsensusOutcome with no orchestrator decision
     */

    private static ConsensusOutcome appendNote(ConsensusOutcome outcome, String suffix) {
        if (suffix.isEmpty()) {
            return outcome;
        }
        return new ConsensusOutcome(outcome.action(), outcome.quantity(),
            outcome.orchestratorDecision(), outcome.agentDecisions(), outcome.note() + suffix);
    }

    private ConsensusOutcome computeMajorityVote(List<LLMDecision> agentDecisions, String ticker, double consensusThreshold) {
        // Vote aggregation rules (SKIP filtering, HOLD abstention, tie handling)
        // live in the pure domain service MajorityVoteCalculator.
        MajorityVoteCalculator.MajorityVoteResult vote =
            MajorityVoteCalculator.compute(agentDecisions, consensusThreshold);

        return switch (vote.outcome()) {
            // Unreachable from the current call sites (an all-SKIP agent list is intercepted
            // upstream and recorded via createSkipConsensusForTicker), kept as a defensive
            // default. Recording it as HOLD would misstate history: HOLD is a decision the
            // engine made, SKIP is the engine failing to decide — and a single HOLD row is
            // enough to convince every "has this sim ever decided?" diagnostic that it did.
            case ALL_SKIPPED -> new ConsensusOutcome(
                ConsensusActionParser.skip(
                    ticker, "All agents skipped — no agent produced a usable opinion"),
                BigDecimal.ZERO, null, agentDecisions,
                "Majority consensus: all agents skipped — recorded as SKIP for " + ticker);

            case ALL_ABSTAINED -> new ConsensusOutcome(
                ConsensusActionParser.hold(
                    ticker, 0.0, 0.0, "All agents abstained (low-confidence HOLD), defaulting to HOLD"),
                BigDecimal.ZERO, null, agentDecisions,
                String.format("Majority consensus: all %d agents abstained, defaulting to HOLD for %s",
                    vote.abstainedCount(), ticker));

            case TIE -> new ConsensusOutcome(
                ConsensusActionParser.hold(
                    ticker, 0.0, 0.5, "Tied vote (" + vote.tieDetail() + "), defaulting to HOLD"),
                BigDecimal.ZERO, null, agentDecisions,
                String.format("Majority consensus: tied vote (%s), defaulting to HOLD for %s",
                    vote.tieDetail(), ticker));

            case WINNER -> {
                LLMDecision bestAgent = vote.winningAgent();
                ConsensusAction finalAction = ConsensusActionParser.fromLlmJson(bestAgent.getRecommendedAction());
                // Specialist agents don't set tickerQuantity — fall back to the quantity the
                // parser extracted from their recommendedAction JSON (null when absent/unparseable,
                // in which case the execution service uses its max-affordable default).
                BigDecimal quantity = bestAgent.getTickerQuantity();
                if (quantity == null) {
                    quantity = finalAction.quantity();
                }

                // Name them. "(1 abstained)" named nobody, so a user had to read the agent array
                // and compare each confidence against their own frontmatter to find out who was
                // dropped and why — while the OTHER abstention mechanism (missing news) had always
                // explained itself. Same word, same field, one of them useless.
                String abstainNote = vote.abstainedCount() > 0
                    ? " [" + String.join("; ", vote.abstained()) + "]" : "";
                String note = String.format("Majority consensus: %s with %d/%d agent votes%s for %s",
                    vote.winner(), vote.winningVotes(), vote.totalVotes(), abstainNote, ticker);

                yield new ConsensusOutcome(finalAction, quantity, null, agentDecisions, note);
            }
        };
    }
}
