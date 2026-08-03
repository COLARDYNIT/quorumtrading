package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.ChallengeRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.CreateSimulationUseCase;
import com.quorumtrading.application.usecase.JoinChallengeUseCase;
import com.quorumtrading.application.usecase.command.CreateSimulationCommand;
import com.quorumtrading.application.usecase.command.JoinChallengeCommand;
import com.quorumtrading.model.entity.Challenge;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.SimulationId;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Service implementation for joining a challenge with a strategy.
 * Creates a new simulation with the challenge's constraints and links it.
 * Framework-agnostic, uses constructor injection.
 */
public class JoinChallengeService implements JoinChallengeUseCase {

    private static final Logger LOG = Logger.getLogger(JoinChallengeService.class.getName());

    private final ChallengeRepository challengeRepository;
    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final CreateSimulationUseCase createSimulationUseCase;
    private final AuthorizationService authorizationService;

    public JoinChallengeService(
        ChallengeRepository challengeRepository,
        SimulationRepository simulationRepository,
        StrategyRepository strategyRepository,
        CreateSimulationUseCase createSimulationUseCase,
        AuthorizationService authorizationService
    ) {
        this.challengeRepository = challengeRepository;
        this.simulationRepository = simulationRepository;
        this.strategyRepository = strategyRepository;
        this.createSimulationUseCase = createSimulationUseCase;
        this.authorizationService = authorizationService;
    }

    @Override
    public SimulationId execute(JoinChallengeCommand command) {
        Challenge challenge = challengeRepository.findById(command.challengeId())
            .orElseThrow(() -> new NoSuchElementException("Challenge not found: " + command.challengeId()));

        if (!challenge.isAcceptingEntries()) {
            throw new IllegalStateException("Challenge is no longer accepting entries: " + challenge.getName());
        }

        Strategy strategy = strategyRepository.findById(command.strategyId())
            .orElseThrow(() -> new NoSuchElementException("Strategy not found: " + command.strategyId()));

        // Verify user owns this strategy
        authorizationService.requireView(command.requestingUserId(), strategy);

        // Validate strategy complies with challenge requirements (domain-level check)
        var configParser = new com.quorumtrading.model.orchestration.StrategyConfigParser();
        try {
            var config = configParser.parse(strategy.getContent());
            var compliance = challenge.checkCompliance(config);
            if (!compliance.compliant()) {
                throw new IllegalArgumentException(
                    "Strategy does not meet challenge requirements: " + String.join("; ", compliance.violations()));
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            LOG.warning(String.format("Could not parse strategy %s to validate compliance: %s",
                command.strategyId(), e.getMessage()));
        }

        // Replace-entry logic: detach old entry if user already has one
        Optional<Simulation> existingEntry = simulationRepository
            .findByOwnerIdAndChallengeId(command.requestingUserId(), command.challengeId());
        if (existingEntry.isPresent()) {
            simulationRepository.updateChallengeId(existingEntry.get().getSimulationId(), null);
            LOG.info(String.format("Detached previous entry %s from challenge %s for user %s",
                existingEntry.get().getSimulationId(), command.challengeId(), command.requestingUserId()));
        }

        // Create a new simulation with the challenge's constraints
        String simName = strategy.getName() + " — " + challenge.getName();
        CreateSimulationCommand createCommand = new CreateSimulationCommand(
            simName,
            command.strategyId(),
            challenge.getSimulationStartDate(),
            challenge.getSimulationEndDate(),
            challenge.getInitialCash(),
            null, // no broker account (mock backtest)
            null, // no trading limit
            null, // no mock broker config (use defaults)
            command.requestingUserId()
        );

        SimulationId simulationId = createSimulationUseCase.execute(createCommand);

        // Link the simulation to the challenge
        simulationRepository.updateChallengeId(simulationId, command.challengeId());

        LOG.info(String.format("User %s joined challenge %s with strategy %s, created simulation %s",
            command.requestingUserId(), command.challengeId(), command.strategyId(), simulationId));

        return simulationId;
    }
}
