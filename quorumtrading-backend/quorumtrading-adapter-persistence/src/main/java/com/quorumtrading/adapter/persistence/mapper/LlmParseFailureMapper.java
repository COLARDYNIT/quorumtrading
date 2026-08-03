package com.quorumtrading.adapter.persistence.mapper;

import com.quorumtrading.adapter.persistence.entity.LlmParseFailureJpaEntity;
import com.quorumtrading.model.entity.LlmParseFailure;
import com.quorumtrading.model.valueobject.SimulationId;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class LlmParseFailureMapper {

    public LlmParseFailureJpaEntity toJpaEntity(LlmParseFailure failure) {
        LlmParseFailureJpaEntity entity = new LlmParseFailureJpaEntity();
        entity.simulationId = failure.simulationId() != null ? failure.simulationId().value() : null;
        entity.decisionTimestamp = failure.decisionTimestamp();
        entity.ticker = failure.ticker();
        entity.agentRole = failure.agentRole();
        entity.requestedModel = failure.requestedModel();
        entity.actualModel = failure.actualModel();
        entity.temperature = failure.temperature();
        entity.executionTimeMs = failure.executionTimeMs();
        entity.prompt = failure.prompt();
        entity.rawResponse = failure.rawResponse();
        entity.errorMessage = failure.errorMessage();
        entity.errorType = failure.errorType();
        return entity;
    }

    public LlmParseFailure toDomain(LlmParseFailureJpaEntity entity) {
        return new LlmParseFailure(
                entity.simulationId != null ? new SimulationId(entity.simulationId) : null,
                entity.decisionTimestamp,
                entity.ticker,
                entity.agentRole,
                entity.requestedModel,
                entity.actualModel,
                entity.temperature,
                entity.executionTimeMs != null ? entity.executionTimeMs : 0,
                entity.prompt,
                entity.rawResponse,
                entity.errorMessage,
                entity.errorType
        );
    }
}
