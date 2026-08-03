package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.AccessDeniedException;
import com.quorumtrading.application.port.out.ChallengeRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.application.usecase.DeleteChallengeUseCase;
import com.quorumtrading.application.usecase.command.DeleteChallengeCommand;
import com.quorumtrading.model.entity.Challenge;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.User;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.logging.Logger;

/**
 * Service implementation for deleting challenges. Admin-only.
 * Detaches all simulations before deleting.
 * Framework-agnostic, uses constructor injection.
 */
public class DeleteChallengeService implements DeleteChallengeUseCase {

    private static final Logger LOG = Logger.getLogger(DeleteChallengeService.class.getName());

    private final ChallengeRepository challengeRepository;
    private final SimulationRepository simulationRepository;
    private final UserRepository userRepository;

    public DeleteChallengeService(
        ChallengeRepository challengeRepository,
        SimulationRepository simulationRepository,
        UserRepository userRepository
    ) {
        this.challengeRepository = challengeRepository;
        this.simulationRepository = simulationRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void execute(DeleteChallengeCommand command) {
        User user = userRepository.findById(command.requestingUserId())
            .orElseThrow(() -> new NoSuchElementException("User not found"));
        if (!user.isAdmin()) {
            throw new AccessDeniedException(command.requestingUserId(), "Challenge", command.challengeId().value().toString());
        }

        Challenge challenge = challengeRepository.findById(command.challengeId())
            .orElseThrow(() -> new NoSuchElementException("Challenge not found: " + command.challengeId()));

        // Detach all simulations from this challenge
        List<Simulation> entries = simulationRepository.findByChallengeId(command.challengeId());
        for (Simulation entry : entries) {
            simulationRepository.updateChallengeId(entry.getSimulationId(), null);
        }

        if (!challengeRepository.deleteById(command.challengeId())) {
            throw new NoSuchElementException("Challenge not found: " + command.challengeId());
        }

        LOG.info(String.format("Admin %s deleted challenge %s (%s), detached %d simulations",
            command.requestingUserId(), command.challengeId(), challenge.getName(), entries.size()));
    }
}
