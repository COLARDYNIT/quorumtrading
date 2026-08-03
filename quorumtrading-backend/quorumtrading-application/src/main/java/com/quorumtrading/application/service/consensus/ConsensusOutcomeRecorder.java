package com.quorumtrading.application.service.consensus;

import com.quorumtrading.application.event.LLMDecisionCreatedEvent;
import com.quorumtrading.application.orchestration.agents.ConsensusActionParser;
import com.quorumtrading.application.port.out.ConsensusDecisionRepository;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.LLMDecisionRepository;
import com.quorumtrading.application.usecase.budget.RecordLLMUsageUseCase;
import com.quorumtrading.model.entity.ConsensusDecision;
import com.quorumtrading.model.entity.LLMDecision;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.enums.ConsensusMethod;
import com.quorumtrading.model.enums.ExecutionOutcome;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Persists everything a consensus evaluation produces: agent decisions (+ WebSocket
 * events), skip rows (all-agents-failed, cooldown, no-market-data), the consensus
 * decision itself (dedupe-checked), and the aggregated LLM usage billed to the owner.
 *
 * <p>Plain POJO wired via {@code ApplicationConfig} (CDI convention: application
 * services are produced, not annotated). Extracted move-only from
 * {@code ReachConsensusService} — behavior preserved.</p>
 */
public class ConsensusOutcomeRecorder {

    private static final Logger logger = LoggerFactory.getLogger(ConsensusOutcomeRecorder.class);

    private final LLMDecisionRepository llmDecisionRepository;
    private final ConsensusDecisionRepository consensusDecisionRepository;
    private final EventPublisher<LLMDecisionCreatedEvent> llmDecisionEventPublisher;
    private final RecordLLMUsageUseCase recordLLMUsageUseCase;

    public ConsensusOutcomeRecorder(
            LLMDecisionRepository llmDecisionRepository,
            ConsensusDecisionRepository consensusDecisionRepository,
            EventPublisher<LLMDecisionCreatedEvent> llmDecisionEventPublisher,
            RecordLLMUsageUseCase recordLLMUsageUseCase) {
        this.llmDecisionRepository = llmDecisionRepository;
        this.consensusDecisionRepository = consensusDecisionRepository;
        this.llmDecisionEventPublisher = llmDecisionEventPublisher;
        this.recordLLMUsageUseCase = recordLLMUsageUseCase;
    }

    /**
     * Save agent decisions to the repository and fire WebSocket events.
     * This is a fast operation (no LLM calls).
     */
    public void saveAgentDecisions(String ticker, Simulation simulation, List<LLMDecision> agentDecisions) {
        for (LLMDecision decision : agentDecisions) {
            LLMDecision saved = llmDecisionRepository.save(decision);
            LLMDecisionCreatedEvent event = new LLMDecisionCreatedEvent(
                simulation.getSimulationId(),
                saved.getId(),
                saved.getRole(),
                saved.getRecommendation().name(),
                saved.getReasoning(),
                saved.getRecommendedAction(),
                saved.getConfidenceScore() != null ? saved.getConfidenceScore().value().doubleValue() : 0.0,
                saved.getDecisionTimestamp(),
                saved.getIndicatorSnapshot(),
                saved.getUiPayload()
            );
            llmDecisionEventPublisher.publish(event);
        }
    }

    /**
     * Create a SKIP consensus decision when no agent produced a usable opinion for a ticker.
     *
     * <p>{@code participationNote} is the one thing that makes such a round diagnosable, and it
     * used to be discarded here: the caller computed it, passed it to the majority and
     * orchestrator branches, and dropped it on this one. So the single case where the user most
     * needs to know <em>why</em> nothing happened was the only case that told them nothing —
     * "All specialized agents failed" names no agent and no cause, and an audit had to read
     * container logs to find that the selected LLM engine was simply unreachable.</p>
     *
     * <p>The word "failed" is also wrong when every agent <em>abstained</em> for lack of data,
     * which is an ordinary outcome on a crypto backtest with no news. The note distinguishes the
     * two (see {@code AgentInvoker.buildParticipationNote}); the summary now follows it rather
     * than calling both a failure.</p>
     */
    public void createSkipConsensusForTicker(
            String ticker, Simulation simulation, List<LLMDecision> agentDecisions,
            StrategyConfig strategyConfig, Instant analysisTimestamp, String participationNote) {
        Symbol tickerSymbol = new Symbol(ticker, InvestmentType.fromTicker(ticker));
        String note = participationNote == null ? "" : participationNote;
        // "abstained" appears only on the data-gap branch, so its absence means every
        // non-voter was invoked and errored or timed out.
        boolean anyAbstained = note.contains("abstained");
        String summary = anyAbstained
            ? "No agent voted for " + ticker + " — see the reasons below"
            : "No agent returned a usable opinion for " + ticker;

        ConsensusDecision skippedDecision = new ConsensusDecision(
            simulation.getSimulationId(),
            analysisTimestamp,
            strategyConfig.getConsensusMethod(),
            ConsensusActionParser.skip(ticker, summary + note)
        );
        skippedDecision.setSymbol(tickerSymbol);
        skippedDecision.setQuantity(BigDecimal.ZERO);
        skippedDecision.setInvestmentType(tickerSymbol.type());
        skippedDecision.setExecutionOutcome(ExecutionOutcome.SKIPPED);
        skippedDecision.setNotes("Consensus skipped: " + summary + note);
        skippedDecision.setExecuted(false);
        for (LLMDecision decision : agentDecisions) {
            skippedDecision.addLlmDecisionId(decision.getId());
        }
        consensusDecisionRepository.save(skippedDecision);
        logger.info("[sim={}] Created SKIP consensus decision for ticker {}: {}{}",
            simulation.getSimulationId().value().toString().substring(0, 8), ticker, summary, note);
    }

    /**
     * Create a SKIP consensus decision for a single ticker that was excluded from this
     * evaluation round before any agent ran (position cooldown, market closed, …).
     * The note explains why and is shown verbatim in the decision timeline.
     */
    public void saveTickerSkip(
            Simulation simulation, StrategyConfig strategyConfig,
            String ticker, Instant analysisTimestamp, String note) {
        Symbol tickerSymbol = new Symbol(ticker, InvestmentType.fromTicker(ticker));
        ConsensusDecision skipDecision = new ConsensusDecision(
            simulation.getSimulationId(),
            analysisTimestamp,
            strategyConfig.getConsensusMethod(),
            ConsensusActionParser.skip(ticker, note)
        );
        skipDecision.setSymbol(tickerSymbol);
        skipDecision.setQuantity(BigDecimal.ZERO);
        skipDecision.setInvestmentType(tickerSymbol.type());
        skipDecision.setExecutionOutcome(ExecutionOutcome.SKIPPED);
        skipDecision.setNotes(note);
        skipDecision.setExecuted(false);
        consensusDecisionRepository.save(skipDecision);
    }

    /**
     * Creates a "skipped" consensus decision when market data is not available.
     * This records the skip in the timeline so it's visible in the simulation viewer,
     * but allows the simulation to continue to the next evaluation period.
     *
     * @param simulation The simulation being executed
     * @param timestamp The timestamp when consensus was attempted
     * @param reason Human-readable explanation for why consensus was skipped
     */
    public void createSkippedConsensusDecision(Simulation simulation, Instant timestamp, String reason) {
        try {
            ConsensusDecision skippedDecision = new ConsensusDecision(
                simulation.getSimulationId(),
                timestamp,
                ConsensusMethod.ORCHESTRATOR_OVERRIDE, // Use orchestrator method as placeholder
                // Use the caller's actual reason. This previously always wrote the literal
                // "No market data available" regardless of why the round was skipped, so a
                // consensus pipeline that timed out on a slow LLM engine was recorded as missing
                // price data — pointing whoever read it at the wrong subsystem entirely. The
                // truthful reason survived only in the free-text notes.
                ConsensusActionParser.skip(null, reason)
            );

            skippedDecision.setExecutionOutcome(ExecutionOutcome.SKIPPED);
            skippedDecision.setNotes(reason);
            skippedDecision.setExecuted(false);

            ConsensusDecision saved = consensusDecisionRepository.save(skippedDecision);

            logger.info("Created skipped consensus decision {} for simulation {} at {}",
                saved.getId(),
                simulation.getSimulationId().value().toString().substring(0, 8),
                timestamp);

        } catch (Exception e) {
            logger.error("Failed to create skipped consensus decision: {}", e.getMessage(), e);
            // Don't fail the simulation if we can't save the skip record
        }
    }

    /**
     * Record a consensus outcome: save the orchestrator decision (if present) and fire
     * its event, dedupe-check, build and save the ConsensusDecision (linking agent and
     * orchestrator decision ids, aggregating LLM usage), and bill the usage to the
     * strategy owner's budget.
     *
     * @return the saved consensus decision, or {@code null} when a duplicate for the
     *         same simulation/symbol/timestamp window already exists (no trade should run)
     */
    public ConsensusDecision recordConsensusOutcome(
            String ticker, ConsensusOutcome outcome, Simulation simulation,
            StrategyConfig strategyConfig, Instant analysisTimestamp, UserId strategyOwnerId) {

        Symbol tickerSymbol = new Symbol(ticker, InvestmentType.fromTicker(ticker));
        List<LLMDecision> agentDecisions = outcome.agentDecisions();
        String simIdShort = simulation.getSimulationId().value().toString().substring(0, 8);

        // Save orchestrator decision and fire event (ORCHESTRATOR_OVERRIDE only)
        LLMDecision savedOrchestratorDecision = null;
        if (outcome.orchestratorDecision() != null) {
            savedOrchestratorDecision = llmDecisionRepository.save(outcome.orchestratorDecision());
            double orchestratorConfidence = savedOrchestratorDecision.getConfidenceScore() != null
                ? savedOrchestratorDecision.getConfidenceScore().value().doubleValue()
                : 0.0;
            LLMDecisionCreatedEvent orchestratorEvent = new LLMDecisionCreatedEvent(
                simulation.getSimulationId(),
                savedOrchestratorDecision.getId(),
                savedOrchestratorDecision.getRole(),
                savedOrchestratorDecision.getRecommendation().name(),
                savedOrchestratorDecision.getReasoning(),
                savedOrchestratorDecision.getRecommendedAction(),
                orchestratorConfidence,
                savedOrchestratorDecision.getDecisionTimestamp(),
                savedOrchestratorDecision.getIndicatorSnapshot(),
                savedOrchestratorDecision.getUiPayload()
            );
            llmDecisionEventPublisher.publish(orchestratorEvent);

            logger.info("[sim={}] Orchestrator decision for {}: {} (confidence: {})",
                simIdShort, ticker, savedOrchestratorDecision.getRecommendation(),
                String.format("%.2f", orchestratorConfidence));
        }

        // Duplicate check
        boolean exists = consensusDecisionRepository.existsBySimulationIdAndSymbolAndTimestampWindow(
            simulation.getSimulationId().value(), tickerSymbol.ticker(), analysisTimestamp, 60);
        if (exists) {
            logger.info("Skipping duplicate consensus for simulation {} ticker {} at {}",
                simulation.getSimulationId().value().toString().substring(0, 8), ticker, analysisTimestamp);
            return null;
        }

        // Create consensus decision using the generalized outcome
        ConsensusDecision consensusDecision = new ConsensusDecision(
            simulation.getSimulationId(), analysisTimestamp,
            strategyConfig.getConsensusMethod(), outcome.action());
        consensusDecision.setSymbol(tickerSymbol);
        if (outcome.quantity() != null) {
            consensusDecision.setQuantity(outcome.quantity());
        }
        consensusDecision.setInvestmentType(tickerSymbol.type());

        for (LLMDecision decision : agentDecisions) {
            consensusDecision.addLlmDecisionId(decision.getId());
        }
        if (savedOrchestratorDecision != null) {
            consensusDecision.addLlmDecisionId(savedOrchestratorDecision.getId());
        }
        consensusDecision.addNote(outcome.note());

        // Aggregate LLM usage
        com.quorumtrading.model.valueobject.LLMUsage aggregatedUsage = com.quorumtrading.model.valueobject.LLMUsage.ZERO;
        for (LLMDecision decision : agentDecisions) {
            if (decision.getLLMUsage() != null) {
                aggregatedUsage = aggregatedUsage.add(decision.getLLMUsage());
            }
        }
        if (savedOrchestratorDecision != null && savedOrchestratorDecision.getLLMUsage() != null) {
            aggregatedUsage = aggregatedUsage.add(savedOrchestratorDecision.getLLMUsage());
        }
        consensusDecision.setLLMUsage(aggregatedUsage);

        ConsensusDecision savedConsensus = consensusDecisionRepository.save(consensusDecision);
        logger.info("Consensus reached for simulation {} ticker {} (method: {}, id: {}): {}",
            simulation.getSimulationId().value().toString().substring(0, 8),
            ticker, strategyConfig.getConsensusMethod(), savedConsensus.getId(), outcome.note());

        // Record LLM usage to budget
        if (strategyOwnerId != null) {
            try {
                java.math.BigDecimal totalBilledCost = java.math.BigDecimal.ZERO;
                java.math.BigDecimal totalRawCost = java.math.BigDecimal.ZERO;
                int totalTokens = 0;
                for (LLMDecision decision : agentDecisions) {
                    if (decision.getLLMUsage() != null && decision.getLLMUsage().billedCost() != null) {
                        totalBilledCost = totalBilledCost.add(decision.getLLMUsage().billedCost());
                        totalRawCost = totalRawCost.add(decision.getLLMUsage().rawCost());
                        totalTokens += decision.getLLMUsage().totalTokens();
                    }
                }
                if (savedOrchestratorDecision != null &&
                    savedOrchestratorDecision.getLLMUsage() != null &&
                    savedOrchestratorDecision.getLLMUsage().billedCost() != null) {
                    totalBilledCost = totalBilledCost.add(savedOrchestratorDecision.getLLMUsage().billedCost());
                    totalRawCost = totalRawCost.add(savedOrchestratorDecision.getLLMUsage().rawCost());
                    totalTokens += savedOrchestratorDecision.getLLMUsage().totalTokens();
                }
                // Tokens, not cost: a free-engine simulation would otherwise leave no ledger row,
                // making its activity unreconstructable. Matches what chat already does.
                if (totalTokens > 0) {
                    recordLLMUsageUseCase.recordUsage(strategyOwnerId, totalBilledCost,
                        totalRawCost, totalTokens, com.quorumtrading.model.valueobject.UsageSource.SIMULATION);
                }
            } catch (Exception usageEx) {
                // The spend ledger is the only record of what an LLM call cost, so a failure here
                // is an error, not a warning — even though it must never fail the consensus.
                logger.error("Failed to record LLM usage for simulation {} user {}: {}",
                    simulation.getSimulationId().value(), strategyOwnerId, usageEx.getMessage(), usageEx);
            }
        }

        return savedConsensus;
    }
}
