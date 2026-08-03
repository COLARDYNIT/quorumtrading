package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.DeletedAccountHashRepository;
import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.application.port.out.PaymentTransactionRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.application.usecase.DeleteUserAccountUseCase;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.UserId;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Service implementation for GDPR-compliant account deletion.
 * <p>
 * Deletes all user data in the correct order to respect foreign key constraints:
 * <ol>
 *   <li>Strategies (cascades to simulations, decisions, snapshots via DB constraints)</li>
 *   <li>Market research configurations (cascades to executions, agent decisions via DB constraints)</li>
 *   <li>Payment transactions (no FK constraint, explicit cleanup)</li>
 *   <li>User entity (cascades to broker accounts, API tokens, budget, OAuth providers via DB constraints)</li>
 * </ol>
 * </p>
 * Framework-agnostic, uses constructor injection.
 */
public class DeleteUserAccountService implements DeleteUserAccountUseCase {

    private static final Logger LOG = Logger.getLogger(DeleteUserAccountService.class);

    private final UserRepository userRepository;
    private final StrategyRepository strategyRepository;
    private final MarketResearchRepository marketResearchRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final DeletedAccountHashRepository deletedAccountHashRepository;

    public DeleteUserAccountService(
            UserRepository userRepository,
            StrategyRepository strategyRepository,
            MarketResearchRepository marketResearchRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            DeletedAccountHashRepository deletedAccountHashRepository) {
        this.userRepository = userRepository;
        this.strategyRepository = strategyRepository;
        this.marketResearchRepository = marketResearchRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.deletedAccountHashRepository = deletedAccountHashRepository;
    }

    @Override
    public void execute(UserId userId) {
        // Verify user exists and capture email hash before deletion
        var user = userRepository.findById(userId)
            .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        LOG.infof("GDPR account deletion initiated for user %s", userId);

        // Store email hash for anti-abuse detection before deleting user data
        String emailHash = sha256(user.getEmail());
        deletedAccountHashRepository.save(emailHash, user.getCreatedAt());

        // 1. Delete strategies (cascades to simulations, decisions, snapshots, scheduled_simulations, simulation_portfolios)
        List<Strategy> strategies = strategyRepository.findByOwnerId(userId);
        for (Strategy strategy : strategies) {
            strategyRepository.deleteById(strategy.getId());
        }
        LOG.infof("Deleted %d strategies for user %s", strategies.size(), userId);

        // 2. Delete market research (cascades to research_executions, research_agent_decisions)
        List<MarketResearch> researchList = marketResearchRepository.findByOwnerId(userId);
        for (MarketResearch research : researchList) {
            marketResearchRepository.delete(research.getId());
        }
        LOG.infof("Deleted %d market research configurations for user %s", researchList.size(), userId);

        // 3. Delete payment transactions (no FK constraint, needs explicit cleanup)
        int deletedPayments = paymentTransactionRepository.deleteByUserId(userId);
        LOG.infof("Deleted %d payment transactions for user %s", deletedPayments, userId);

        // 4. Delete user entity (cascades to: broker_accounts, api_tokens,
        //    user_budgets, user_oauth_providers via ON DELETE CASCADE)
        boolean deleted = userRepository.deleteById(userId);
        if (!deleted) {
            throw new NoSuchElementException("User not found during deletion: " + userId);
        }

        LOG.infof("GDPR account deletion completed for user %s", userId);
    }

    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.toLowerCase().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
