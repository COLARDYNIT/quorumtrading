package com.quorumtrading.adapter.persistence.mapper;

import com.quorumtrading.adapter.persistence.entity.SimulationJpaEntity;
import com.quorumtrading.adapter.persistence.entity.StrategyJpaEntity;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.BenchmarkId;
import com.quorumtrading.model.valueobject.ChallengeId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mapper between Simulation models and SimulationJpaEntity.
 *
 * PHASE 3 UPDATE:
 * This mapper now supports TWO Simulation representations:
 * 1. Port Simulation (com.quorumtrading.application.port.out.Simulation) - Simple DTO for API
 * 2. Entity Simulation (com.quorumtrading.model.entity.Simulation) - Rich domain entity
 *
 * ARCHITECTURAL NOTE:
 * - Domain Simulation references StrategyId (UUID wrapper)
 * - Database simulations table references strategy_id (UUID foreign key)
 *
 * Mapping approach:
 * - When converting domain → JPA: Look up the Strategy by UUID (stored in name field) to get its UUID database ID
 * - When converting JPA → domain: Direct UUID mapping
 */
@ApplicationScoped
public class SimulationMapper {

    @Inject
    StrategyMapper strategyMapper;

    @Inject
    EntityManager entityManager;

    /**
     * Convert domain Simulation to JPA entity.
     *
     * Maps domain StrategyId (UUID) to database strategy_id (UUID) by looking up
     * the Strategy with matching UUID in the name field.
     *
     * @param simulation the domain simulation
     * @return the JPA entity
     * @throws IllegalStateException if the referenced strategy cannot be found in database
     */
    public SimulationJpaEntity toJpaEntity(Simulation simulation) {
        return toDomainEntityJpa(simulation);
    }

    /**
     * Convert JPA entity to domain Simulation.
     *
     * Maps:
     * - id (UUID) → SimulationId
     * - strategy_id (UUID) → StrategyId
     * - initialPortfolio JSONB → Symbol and Money
     * - dates and status
     */
    public Simulation toDomain(SimulationJpaEntity entity) {
        return toDomainEntity(entity);
    }

    /**
     * Find the database UUID for a Strategy by its UUID.
     *
     * Now that database uses UUID primary keys, this is a direct mapping.
     * We just check if the strategy exists in the database.
     *
     * @param strategyId the domain StrategyId (UUID wrapper)
     * @return the database UUID
     * @throws IllegalStateException if strategy not found
     */
    private UUID findStrategyDbId(StrategyId strategyId) {
        UUID uuid = strategyId.value();

        StrategyJpaEntity strategy = entityManager.find(StrategyJpaEntity.class, uuid);

        if (strategy == null) {
            throw new IllegalStateException(
                "Cannot map StrategyId to database UUID: Strategy with UUID " +
                uuid + " not found in database. " +
                "This indicates either: 1) Strategy hasn't been persisted yet, or " +
                "2) Data inconsistency between domain and database."
            );
        }

        return strategy.id;
    }


    // ==================== Domain Entity Mapping ====================

    /**
     * Convert domain entity Simulation to JPA entity.
     *
     * This method maps the rich domain entity (com.quorumtrading.model.entity.Simulation)
     * to the JPA persistence entity with proper handling of:
     * - SimulationStatus enum → String
     * - Symbol and Money → initialPortfolio JSONB
     * - Results Map
     * - Timestamps
     *
     * @param simulation the domain entity simulation
     * @return the JPA entity
     * @throws IllegalStateException if the referenced strategy cannot be found in database
     */
    private SimulationJpaEntity toDomainEntityJpa(Simulation simulation) {
        SimulationJpaEntity entity = new SimulationJpaEntity();

        // Map SimulationId (UUID wrapper) to UUID
        if (simulation.getSimulationId() != null) {
            entity.setId(simulation.getSimulationId().value());
        }
        // else @PrePersist will generate UUID

        // Map name
        entity.setName(simulation.getName());

        // Map StrategyId (UUID wrapper) to UUID database ID
        UUID strategyDbId = findStrategyDbId(simulation.getStrategyId());
        entity.setStrategyId(strategyDbId);

        // Map dates
        entity.setStartDate(simulation.getStartDate());
        entity.setEndDate(simulation.getEndDate());

        // Map status enum to String
        entity.setStatus(simulation.getStatus().name());

        // Map paused state
        entity.setPaused(simulation.isPaused());

        // Map progress (tracked during simulation execution)
        entity.setProgress(simulation.getProgress());

        // Map timestamps
        entity.setStartedAt(simulation.getStartedAt());
        entity.setCompletedAt(simulation.getCompletedAt());
        entity.setFailureReason(simulation.getFailureReason());

        // Map benchmarkId (nullable)
        if (simulation.getBenchmarkId() != null) {
            entity.setBenchmarkId(simulation.getBenchmarkId().value());
        }

        // Map challengeId (nullable)
        if (simulation.getChallengeId() != null) {
            entity.setChallengeId(simulation.getChallengeId().value());
        }

        // Map Symbol and Money to initialPortfolio JSONB
        Map<String, Object> initialPortfolio = new HashMap<>();
        initialPortfolio.put("initialCash", simulation.getInitialCash().amount().doubleValue());
        initialPortfolio.put("currency", simulation.getInitialCash().currency().name());
        entity.setInitialPortfolio(initialPortfolio);

        // Configuration can be empty for now
        entity.setConfiguration(new HashMap<>());

        // Map external pause fields
        entity.setPausedForExternalReason(simulation.isPausedForExternalReason());
        entity.setExternalPauseReason(simulation.getExternalPauseReason());

        // Map broker account and trading limit (nullable during migration)
        if (simulation.getBrokerAccountId() != null) {
            entity.setBrokerAccountId(simulation.getBrokerAccountId().value());
        }
        if (simulation.getTradingLimit() != null) {
            entity.setTradingLimitAmount(simulation.getTradingLimit().amount());
            entity.setTradingLimitCurrency(simulation.getTradingLimit().currency().name());
        }

        // Map backfill tracking fields
        entity.setBackfillJobId(simulation.getBackfillJobId());
        entity.setSkippedEvaluationCount(simulation.getSkippedEvaluationCount());
        entity.setTotalSkippedEvaluationCount(simulation.getTotalSkippedEvaluationCount());
        entity.setTotalEvaluationCount(simulation.getTotalEvaluationCount());

        // Map distributed locking fields
        entity.setVersion(simulation.getVersion());
        entity.setLockedBy(simulation.getLockedBy());
        entity.setLockedAt(simulation.getLockedAt());

        // Map archived LLM cost fields
        entity.setArchivedLlmCostUsd(simulation.getArchivedLLMCostUsd());
        entity.setArchivedLlmPromptTokens(simulation.getArchivedLLMPromptTokens());
        entity.setArchivedLlmCompletionTokens(simulation.getArchivedLLMCompletionTokens());
        entity.setArchivedLlmTotalTokens(simulation.getArchivedLLMTotalTokens());
        entity.setArchivedLlmDecisionCount(simulation.getArchivedLLMDecisionCount());

        // Map notification controls (V114)
        if (simulation.getOwnerUserId() != null) {
            entity.setOwnerUserId(simulation.getOwnerUserId().value());
        }
        entity.setNotifyDecisions(simulation.isNotifyDecisions());

        return entity;
    }

    /**
     * Convert JPA entity to domain entity Simulation.
     *
     * Maps JPA persistence entity to rich domain entity with:
     * - String status → SimulationStatus enum
     * - initialPortfolio JSONB → Symbol and Money
     * - Progress calculation from timestamps
     * - Results Map
     *
     * @param entity the JPA entity
     * @return the domain entity simulation
     */
    private Simulation toDomainEntity(SimulationJpaEntity entity) {
        SimulationId id = new SimulationId(entity.getId());

        // Convert UUID strategy_id to StrategyId
        StrategyId strategyId = new StrategyId(entity.getStrategyId());

        // Extract Money from initialPortfolio JSONB
        // Symbol/tickers are now in strategy config, not simulation
        Map<String, Object> portfolio = entity.getInitialPortfolio();
        Money initialCash;

        if (portfolio != null && !portfolio.isEmpty()) {
            BigDecimal cashAmount = portfolio.get("initialCash") instanceof Double
                ? BigDecimal.valueOf((Double) portfolio.get("initialCash"))
                : new BigDecimal(portfolio.get("initialCash").toString());
            Money.Currency currency = Money.Currency.valueOf(
                (String) portfolio.getOrDefault("currency", "USD")
            );
            initialCash = new Money(cashAmount, currency);
        } else {
            // Fallback defaults
            initialCash = new Money(BigDecimal.valueOf(100000), Money.Currency.USD);
        }

        // Map benchmarkId (nullable)
        BenchmarkId benchmarkId = null;
        if (entity.getBenchmarkId() != null) {
            benchmarkId = new BenchmarkId(entity.getBenchmarkId());
        }

        // Map challengeId (nullable)
        ChallengeId challengeId = null;
        if (entity.getChallengeId() != null) {
            challengeId = new ChallengeId(entity.getChallengeId());
        }

        // Map brokerAccountId (nullable)
        BrokerAccountId brokerAccountId = null;
        if (entity.getBrokerAccountId() != null) {
            brokerAccountId = new BrokerAccountId(entity.getBrokerAccountId());
        }

        // Map tradingLimit (nullable)
        Money tradingLimit = null;
        if (entity.getTradingLimitAmount() != null && entity.getTradingLimitCurrency() != null) {
            Money.Currency currency = Money.Currency.valueOf(entity.getTradingLimitCurrency());
            tradingLimit = new Money(entity.getTradingLimitAmount(), currency);
        }

        // Reconstitute the full persisted state directly — reconstitution restores
        // already-validated state and must NOT re-run transition validation.
        // (A force-completed LIVE simulation has status COMPLETED with endDate null
        // or in the future; routing that through setStatus() made it unreadable.)
        return Simulation.reconstitution()
            .simulationId(id)
            .name(entity.getName())
            .strategyId(strategyId)
            .startDate(entity.getStartDate())
            .endDate(entity.getEndDate())
            .initialCash(initialCash)
            .benchmarkId(benchmarkId)
            .challengeId(challengeId)
            .brokerAccountId(brokerAccountId)
            .tradingLimit(tradingLimit)
            .status(entity.getStatus() != null ? SimulationStatus.valueOf(entity.getStatus()) : null)
            .progress(entity.getProgress())
            .paused(entity.isPaused())
            .startedAt(entity.getStartedAt())
            .completedAt(entity.getCompletedAt())
            .failureReason(entity.getFailureReason())
            .createdAt(entity.getCreatedAt())
            .externalPause(entity.isPausedForExternalReason(), entity.getExternalPauseReason())
            .backfillJobId(entity.getBackfillJobId())
            .skippedEvaluationCount(entity.getSkippedEvaluationCount())
            .totalSkippedEvaluationCount(entity.getTotalSkippedEvaluationCount())
            .totalEvaluationCount(entity.getTotalEvaluationCount())
            .version(entity.getVersion() != null ? entity.getVersion() : 0L)
            .lockedBy(entity.getLockedBy())
            .lockedAt(entity.getLockedAt())
            .archivedLLMCostUsd(entity.getArchivedLlmCostUsd())
            .archivedLLMPromptTokens(entity.getArchivedLlmPromptTokens())
            .archivedLLMCompletionTokens(entity.getArchivedLlmCompletionTokens())
            .archivedLLMTotalTokens(entity.getArchivedLlmTotalTokens())
            .archivedLLMDecisionCount(entity.getArchivedLlmDecisionCount())
            .ownerUserId(entity.getOwnerUserId() != null ? new UserId(entity.getOwnerUserId()) : null)
            .notifyDecisions(entity.isNotifyDecisions())
            .build();
    }
}
