package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.CloneStrategyUseCase;
import com.quorumtrading.application.usecase.command.CloneStrategyCommand;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.StrategyId;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Service implementation for cloning a public strategy into the caller's account.
 * Framework-agnostic, uses constructor injection.
 */
public class CloneStrategyService implements CloneStrategyUseCase {

    private static final Logger LOG = Logger.getLogger(CloneStrategyService.class.getName());

    private final StrategyRepository strategyRepository;

    public CloneStrategyService(StrategyRepository strategyRepository) {
        this.strategyRepository = strategyRepository;
    }

    @Override
    public StrategyId execute(CloneStrategyCommand command) {
        Strategy source = strategyRepository.findById(command.sourceStrategyId())
            .orElseThrow(() -> new NoSuchElementException("Strategy not found: " + command.sourceStrategyId()));
        if (!source.isPublicVisible()) {
            throw new IllegalStateException("Strategy is not public");
        }
        if (!source.isActive()) {
            throw new IllegalStateException("Cannot clone an inactive strategy");
        }

        StrategyId cloneId = new StrategyId(UUID.randomUUID());
        Strategy clone = source.deriveChildForUser(cloneId, command.requestingUserId());
        Strategy saved = strategyRepository.save(clone);
        LOG.info(String.format("User %s cloned public strategy %s -> %s",
            command.requestingUserId(), command.sourceStrategyId(), saved.getId()));
        return saved.getId();
    }
}
