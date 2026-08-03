package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.ConsensusDecisionJpaEntity;
import com.quorumtrading.adapter.persistence.mapper.ConsensusDecisionMapper;
import com.quorumtrading.application.port.out.ConsensusDecisionRepository;
import com.quorumtrading.model.entity.ConsensusDecision;
import com.quorumtrading.model.enums.ExecutionOutcome;
import com.quorumtrading.model.valueobject.SimulationId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adapter implementation for ConsensusDecisionRepository.
 * Uses mapper to convert between domain entities and JPA entities,
 * maintaining hexagonal architecture boundaries.
 */
@ApplicationScoped
@Transactional(Transactional.TxType.REQUIRED)
public class ConsensusDecisionRepositoryImpl implements ConsensusDecisionRepository {

    @Inject
    ConsensusDecisionPanacheDAO panacheRepository;

    @Inject
    ConsensusDecisionMapper mapper;

    @Override
    public ConsensusDecision save(ConsensusDecision decision) {
        ConsensusDecisionJpaEntity entity = mapper.toJpaEntity(decision);

        // Check if entity is new or existing (detached)
        if (entity.id == null) {
            // New entity - set wall-clock creation time and persist
            if (entity.createdAt == null) {
                entity.createdAt = Instant.now();
            }
            panacheRepository.persist(entity);
            // Update domain entity with generated ID
            decision.setId(entity.id);
        } else {
            // Existing entity (detached) - preserve original createdAt from database
            if (entity.createdAt == null) {
                ConsensusDecisionJpaEntity existing = panacheRepository.findById(entity.id);
                entity.createdAt = existing != null ? existing.createdAt : Instant.now();
            }
            // Use merge to reattach and update
            ConsensusDecisionJpaEntity merged = panacheRepository.getEntityManager().merge(entity);
            // Force immediate database write to persist execution_price and other updated fields
            panacheRepository.getEntityManager().flush();
            // Update domain entity with latest state
            decision.setId(merged.id);
        }

        return decision;
    }

    @Override
    public List<ConsensusDecision> findBySimulationId(UUID simulationId, Instant from, Instant to, Boolean executed, ExecutionOutcome executionOutcome, List<String> actions, Integer limit) {
        List<ConsensusDecisionJpaEntity> entities = panacheRepository.findBySimulationId(simulationId, from, to, executed, executionOutcome, actions, limit);
        return entities.stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<ConsensusDecision> findLatestBySimulationId(SimulationId simulationId) {
        return panacheRepository.findLatestBySimulationId(simulationId.value())
                .map(mapper::toDomain);
    }

    @Override
    public List<ConsensusDecision> findBySimulationIdAndTicker(
            UUID simulationId,
            String ticker,
            List<String> actions,
            Integer limit,
            Long beforeConsensusId,
            Long afterConsensusId
    ) {
        List<ConsensusDecisionJpaEntity> entities = panacheRepository.findBySimulationIdAndTicker(
                simulationId,
                ticker,
                actions,
                limit,
                beforeConsensusId,
                afterConsensusId
        );
        return entities.stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsBySimulationIdAndSymbolAndTimestampWindow(
            UUID simulationId,
            String symbol,
            Instant timestamp,
            int windowSeconds
    ) {
        return panacheRepository.existsBySimulationIdAndSymbolAndTimestampWindow(
                simulationId,
                symbol,
                timestamp,
                windowSeconds
        );
    }

    @Override
    public Optional<ConsensusDecision> findById(Long id) {
        return panacheRepository.findByIdOptional(id)
                .map(mapper::toDomain);
    }

    @Override
    public AggregatedLLMCost sumCostBySimulationIdAfter(UUID simulationId, Instant afterInclusive) {
        Object[] row = panacheRepository.sumCostBySimulationIdAfter(simulationId, afterInclusive);
        return new AggregatedLLMCost(
            ((Number) row[0]).intValue(),
            ((Number) row[1]).intValue(),
            ((Number) row[2]).intValue(),
            row[3] instanceof BigDecimal bd ? bd : new BigDecimal(row[3].toString()),
            ((Number) row[4]).intValue()
        );
    }

    @Override
    public long countBySimulationIdAfter(UUID simulationId, Instant afterInclusive) {
        return panacheRepository.countBySimulationIdAfter(simulationId, afterInclusive);
    }

    @Override
    public long countExecutedTradesBySimulationId(UUID simulationId) {
        return panacheRepository.countExecutedTradesBySimulationId(simulationId);
    }

    @Override
    public long countNotExecutedBySimulationId(UUID simulationId) {
        return panacheRepository.countNotExecutedBySimulationId(simulationId);
    }


    @Override
    public long deleteBySimulationIdAfter(UUID simulationId, Instant afterInclusive) {
        return panacheRepository.deleteBySimulationIdAfter(simulationId, afterInclusive);
    }

    @Override
    public AggregatedLLMCost sumCostBySimulationId(UUID simulationId) {
        Object[] row = panacheRepository.sumCostBySimulationId(simulationId);
        return new AggregatedLLMCost(
            ((Number) row[0]).intValue(),
            ((Number) row[1]).intValue(),
            ((Number) row[2]).intValue(),
            row[3] instanceof BigDecimal bd ? bd : new BigDecimal(row[3].toString()),
            ((Number) row[4]).intValue()
        );
    }
}
