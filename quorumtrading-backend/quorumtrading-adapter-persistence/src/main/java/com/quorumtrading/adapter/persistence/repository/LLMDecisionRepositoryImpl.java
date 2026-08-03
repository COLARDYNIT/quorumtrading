package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.LLMDecisionJpaEntity;
import com.quorumtrading.adapter.persistence.mapper.LLMDecisionMapper;
import com.quorumtrading.model.enums.AgentRole;
import com.quorumtrading.application.port.out.LLMDecisionRepository;
import com.quorumtrading.model.entity.LLMDecision;
import com.quorumtrading.model.valueobject.SimulationId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adapter implementation for LLMDecisionRepository.
 * Uses mapper to convert between domain entities and JPA entities,
 * maintaining hexagonal architecture boundaries.
 */
@ApplicationScoped
@Transactional(Transactional.TxType.REQUIRED)
public class LLMDecisionRepositoryImpl implements LLMDecisionRepository {

    @Inject
    LLMDecisionPanacheDAO panacheRepository;

    @Inject
    LLMDecisionMapper mapper;

    @Override
    public LLMDecision save(LLMDecision decision) {
        LLMDecisionJpaEntity entity = mapper.toJpaEntity(decision);
        panacheRepository.persist(entity);
        // Update domain entity with generated ID
        decision.setId(entity.id);
        return decision;
    }

    @Override
    public List<LLMDecision> findBySimulationId(
            UUID simulationId,
            Instant from,
            Instant to,
            String agentId,
            Integer limit,
            Long beforeLLMDecisionId,
            Long afterLLMDecisionId) {
        List<LLMDecisionJpaEntity> entities = panacheRepository.findBySimulationId(
                simulationId, from, to, agentId, limit, beforeLLMDecisionId, afterLLMDecisionId);
        return entities.stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<LLMDecision> getPreviousDecision(AgentRole agentRole, SimulationId simulationId, Instant beforeTimestamp) {
        Optional<LLMDecisionJpaEntity> entity = panacheRepository.findMostRecentDecision(
                agentRole.name(), simulationId.value(), beforeTimestamp);
        return entity.map(mapper::toDomain);
    }

    @Override
    public long countBySimulationIdAfter(UUID simulationId, Instant afterInclusive) {
        return panacheRepository.countBySimulationIdAfter(simulationId, afterInclusive);
    }

    @Override
    public long deleteBySimulationIdAfter(UUID simulationId, Instant afterInclusive) {
        return panacheRepository.deleteBySimulationIdAfter(simulationId, afterInclusive);
    }

    @Override
    public long countUncountedPromptTokensBySimulationId(UUID simulationId) {
        return panacheRepository.countUncountedPromptTokensBySimulationId(simulationId);
    }

    @Override
    public List<LLMDecision> findByIds(List<Long> llmDecisionIds) {
        if (llmDecisionIds == null || llmDecisionIds.isEmpty()) {
            return List.of();
        }

        List<LLMDecisionJpaEntity> entities = panacheRepository.list("id in ?1", llmDecisionIds);
        return entities.stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }
}
