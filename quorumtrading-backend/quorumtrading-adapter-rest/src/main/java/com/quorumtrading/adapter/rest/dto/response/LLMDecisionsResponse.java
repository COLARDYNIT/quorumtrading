package com.quorumtrading.adapter.rest.dto.response;

import java.util.List;
import java.util.UUID;

public class LLMDecisionsResponse {

    public List<LLMDecisionResponse> decisions;
    public int total;
    public UUID simulationId;
    public Integer limit;

    public LLMDecisionsResponse() {}

    public LLMDecisionsResponse(List<LLMDecisionResponse> decisions, int total,
                                UUID simulationId, Integer limit) {
        this.decisions = decisions;
        this.total = total;
        this.simulationId = simulationId;
        this.limit = limit;
    }
}
