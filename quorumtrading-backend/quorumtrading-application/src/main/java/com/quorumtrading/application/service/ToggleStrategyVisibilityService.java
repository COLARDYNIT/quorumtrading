package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.AccessDeniedException;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.application.usecase.ToggleStrategyVisibilityUseCase;
import com.quorumtrading.application.usecase.command.ToggleStrategyVisibilityCommand;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.entity.User;

import java.util.NoSuchElementException;
import java.util.logging.Logger;

/**
 * Service implementation for toggling strategy public/private visibility.
 * Only admins who own the strategy can change visibility.
 * Framework-agnostic, uses constructor injection.
 */
public class ToggleStrategyVisibilityService implements ToggleStrategyVisibilityUseCase {

    private static final Logger LOG = Logger.getLogger(ToggleStrategyVisibilityService.class.getName());

    private final StrategyRepository strategyRepository;
    private final UserRepository userRepository;

    public ToggleStrategyVisibilityService(StrategyRepository strategyRepository, UserRepository userRepository) {
        this.strategyRepository = strategyRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void execute(ToggleStrategyVisibilityCommand command) {
        User user = userRepository.findById(command.requestingUserId())
            .orElseThrow(() -> new NoSuchElementException("User not found"));
        if (!user.isAdmin()) {
            throw new AccessDeniedException(command.requestingUserId(), "Strategy", command.strategyId().value().toString());
        }

        Strategy strategy = strategyRepository.findById(command.strategyId())
            .orElseThrow(() -> new NoSuchElementException("Strategy not found: " + command.strategyId()));
        if (!strategy.getOwnerId().equals(command.requestingUserId())) {
            throw new AccessDeniedException(command.requestingUserId(), "Strategy", command.strategyId().value().toString());
        }

        if (command.makePublic()) {
            strategy.makePublic();
        } else {
            strategy.makePrivate();
        }
        strategyRepository.save(strategy);
        LOG.info(String.format("Admin %s set strategy %s visibility to public=%s",
            command.requestingUserId(), command.strategyId(), command.makePublic()));
    }
}
