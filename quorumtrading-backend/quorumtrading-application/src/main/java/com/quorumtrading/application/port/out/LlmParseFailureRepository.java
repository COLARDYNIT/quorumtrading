package com.quorumtrading.application.port.out;

import com.quorumtrading.application.usecase.dto.ModelReliabilityStats;
import com.quorumtrading.model.entity.LlmParseFailure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Port for persisting and querying LLM parse failures.
 */
public interface LlmParseFailureRepository {

    void save(LlmParseFailure failure);

    List<LlmParseFailure> findBySimulationIdAndTimestamps(UUID simulationId, List<Instant> timestamps);

    ModelReliabilityStats getModelReliabilityStats(int days);
}
