package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.AccessDeniedException;
import com.quorumtrading.application.port.out.ChallengeRepository;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.application.usecase.CreateChallengeUseCase;
import com.quorumtrading.application.usecase.command.CreateChallengeCommand;
import com.quorumtrading.model.entity.Challenge;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.valueobject.ChallengeId;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.logging.Logger;

/**
 * Service implementation for creating challenges. Admin-only.
 * Framework-agnostic, uses constructor injection.
 */
public class CreateChallengeService implements CreateChallengeUseCase {

    private static final Logger LOG = Logger.getLogger(CreateChallengeService.class.getName());

    private final ChallengeRepository challengeRepository;
    private final UserRepository userRepository;

    public CreateChallengeService(ChallengeRepository challengeRepository, UserRepository userRepository) {
        this.challengeRepository = challengeRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Challenge execute(CreateChallengeCommand command) {
        User user = userRepository.findById(command.requestingUserId())
            .orElseThrow(() -> new NoSuchElementException("User not found"));
        if (!user.isAdmin()) {
            throw new AccessDeniedException(command.requestingUserId(), "Challenge", "create");
        }

        Challenge challenge = new Challenge(
            ChallengeId.generate(),
            command.name(),
            command.description(),
            command.ticker(),
            command.initialCash(),
            command.simulationStartDate(),
            command.simulationEndDate(),
            command.participationOpen(),
            command.participationClose(),
            Instant.now(),
            command.requestingUserId(),
            command.strategiesPublic()
        );

        Challenge saved = challengeRepository.save(challenge);
        LOG.info(String.format("Admin %s created challenge %s (%s) for ticker %s",
            command.requestingUserId(), saved.getChallengeId(), saved.getName(), saved.getTicker()));
        return saved;
    }
}
