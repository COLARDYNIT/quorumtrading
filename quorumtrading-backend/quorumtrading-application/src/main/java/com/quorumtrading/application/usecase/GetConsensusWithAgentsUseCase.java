package com.quorumtrading.application.usecase;

import com.quorumtrading.application.port.out.ConsensusDecisionRepository;
import com.quorumtrading.application.port.out.LLMDecisionRepository;
import com.quorumtrading.application.port.out.LlmParseFailureRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.service.AuthorizationService;
import com.quorumtrading.application.usecase.command.GetConsensusWithAgentsCommand;
import com.quorumtrading.application.usecase.dto.ConsensusWithAgentsResponse;
import com.quorumtrading.model.entity.ConsensusDecision;
import com.quorumtrading.model.entity.LLMDecision;
import com.quorumtrading.model.entity.LlmParseFailure;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Use case to retrieve consensus decisions with their associated agent LLM decisions.
 *
 * This use case aggregates consensus decisions with the individual agent recommendations
 * that contributed to each consensus. It supports filtering by simulation ID and ticker,
 * plus ID-based pagination for efficient navigation through consensus history.
 */
public class GetConsensusWithAgentsUseCase {

    private static final Logger logger = LoggerFactory.getLogger(GetConsensusWithAgentsUseCase.class);

    private final ConsensusDecisionRepository consensusRepository;
    private final LLMDecisionRepository llmDecisionRepository;
    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final AuthorizationService authorizationService;
    private final LlmParseFailureRepository parseFailureRepository;

    public GetConsensusWithAgentsUseCase(
            ConsensusDecisionRepository consensusRepository,
            LLMDecisionRepository llmDecisionRepository,
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            AuthorizationService authorizationService
    ) {
        this(consensusRepository, llmDecisionRepository, simulationRepository, strategyRepository, authorizationService, null);
    }

    public GetConsensusWithAgentsUseCase(
            ConsensusDecisionRepository consensusRepository,
            LLMDecisionRepository llmDecisionRepository,
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            AuthorizationService authorizationService,
            LlmParseFailureRepository parseFailureRepository
    ) {
        this.consensusRepository = consensusRepository;
        this.llmDecisionRepository = llmDecisionRepository;
        this.simulationRepository = simulationRepository;
        this.strategyRepository = strategyRepository;
        this.authorizationService = authorizationService;
        this.parseFailureRepository = parseFailureRepository;
    }

    /**
     * Execute the use case to retrieve consensus decisions with their agent decisions.
     *
     * @param command command containing query parameters
     * @return list of consensus decisions with their associated agent decisions
     */
    public List<ConsensusWithAgentsResponse> execute(GetConsensusWithAgentsCommand command) {
        logger.debug("Fetching consensus decisions for simulation: {}, ticker: {}, actions: {}, limit: {}",
                command.getSimulationId(), command.getTicker(), command.getActions(), command.getLimit());

        // Authorization check
        if (command.getRequestingUserId() != null) {
            SimulationId simulationId = new SimulationId(command.getSimulationId());
            Simulation simulation = simulationRepository.findById(simulationId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Simulation not found: " + command.getSimulationId()));
            Strategy strategy = strategyRepository.findById(simulation.getStrategyId())
                .orElseThrow(() -> new java.util.NoSuchElementException("Strategy not found"));
            authorizationService.requireView(command.getRequestingUserId(), simulation, strategy);
        }

        // 1. Fetch consensus decisions based on criteria
        List<ConsensusDecision> consensusDecisions = consensusRepository.findBySimulationIdAndTicker(
                command.getSimulationId(),
                command.getTicker(),
                command.getActions(),
                command.getLimit(),
                command.getBeforeConsensusId(),
                command.getAfterConsensusId()
        );

        if (consensusDecisions.isEmpty()) {
            logger.debug("No consensus decisions found for the given criteria");
            return List.of();
        }

        logger.debug("Found {} consensus decisions", consensusDecisions.size());

        // 2. Collect all LLM decision IDs from all consensus decisions
        List<Long> allLlmDecisionIds = consensusDecisions.stream()
                .flatMap(consensus -> consensus.getLlmDecisionIds().stream())
                .distinct()
                .collect(Collectors.toList());

        // 3. Fetch all related LLM decisions in bulk (efficient database query)
        List<LLMDecision> allLlmDecisions = fetchLlmDecisionsByIds(
                command.getSimulationId(),
                allLlmDecisionIds
        );

        // 4. Create a map for quick lookup: decisionId -> LLMDecision
        Map<Long, LLMDecision> llmDecisionMap = allLlmDecisions.stream()
                .collect(Collectors.toMap(
                        LLMDecision::getId,
                        decision -> decision
                ));

        // 5. When onlyParseFailures, fetch failures and filter to only decisions that had them
        Map<Instant, List<LlmParseFailure>> parseFailuresByTimestamp = Map.of();
        if (command.isOnlyParseFailures() && parseFailureRepository != null) {
            List<Instant> timestamps = consensusDecisions.stream()
                    .map(ConsensusDecision::getDecisionTimestamp)
                    .distinct()
                    .collect(Collectors.toList());
            List<LlmParseFailure> failures = parseFailureRepository.findBySimulationIdAndTimestamps(
                    command.getSimulationId(), timestamps);
            parseFailuresByTimestamp = failures.stream()
                    .collect(Collectors.groupingBy(LlmParseFailure::decisionTimestamp));
            logger.debug("Found {} parse failures across {} timestamps",
                    failures.size(), parseFailuresByTimestamp.size());
        }

        // 6. Build response objects by matching consensus decisions with their agent decisions
        List<ConsensusWithAgentsResponse> responses = new ArrayList<>();
        for (ConsensusDecision consensus : consensusDecisions) {
            List<LlmParseFailure> failures = parseFailuresByTimestamp.getOrDefault(
                    consensus.getDecisionTimestamp(), List.of());

            // When onlyParseFailures, skip decisions that had no failures
            if (command.isOnlyParseFailures() && failures.isEmpty()) {
                continue;
            }

            List<LLMDecision> agentDecisions = consensus.getLlmDecisionIds().stream()
                    .map(llmDecisionMap::get)
                    .filter(decision -> decision != null)
                    .collect(Collectors.toList());

            responses.add(new ConsensusWithAgentsResponse(consensus, agentDecisions, failures));
        }

        logger.debug("Successfully aggregated {} consensus decisions with their agent decisions", responses.size());
        return responses;
    }

    /**
     * Execute the use case to retrieve a single consensus decision with its agent decisions by ID.
     *
     * @param consensusId      the consensus decision ID
     * @param requestingUserId the user requesting the data (null for system/internal calls)
     * @return the consensus decision with its associated agent decisions, or empty if not found
     */
    public java.util.Optional<ConsensusWithAgentsResponse> executeById(Long consensusId, UserId requestingUserId) {
        logger.debug("Fetching consensus decision by ID: {}", consensusId);

        return consensusRepository.findById(consensusId)
                .map(consensus -> {
                    // Authorization check
                    Simulation simulation = simulationRepository.findById(consensus.getSimulationId())
                        .orElseThrow(() -> new java.util.NoSuchElementException("Simulation not found: " + consensus.getSimulationId()));
                    Strategy strategy = strategyRepository.findById(simulation.getStrategyId())
                        .orElseThrow(() -> new java.util.NoSuchElementException("Strategy not found"));
                    authorizationService.requireView(requestingUserId, simulation, strategy);

                    List<LLMDecision> agentDecisions = fetchLlmDecisionsByIds(
                            consensus.getSimulationId().value(),
                            consensus.getLlmDecisionIds()
                    );
                    return new ConsensusWithAgentsResponse(consensus, agentDecisions);
                });
    }

    /**
     * Fetch LLM decisions by their IDs using bulk fetch to avoid N+1 query problem.
     */
    private List<LLMDecision> fetchLlmDecisionsByIds(java.util.UUID simulationId, List<Long> decisionIds) {
        if (decisionIds.isEmpty()) {
            return List.of();
        }

        // Use bulk fetch method to get all decisions in a single query
        return llmDecisionRepository.findByIds(decisionIds);
    }
}
