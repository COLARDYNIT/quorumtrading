package com.quorumtrading.adapter.rest.dto.response;

import java.util.List;
import java.util.UUID;

public class ConsensusDecisionsResponse {

    public List<ConsensusDecisionResponse> decisions;
    public int total;
    public UUID simulationId;
    public Integer limit;

    public ConsensusDecisionsResponse() {}

    public ConsensusDecisionsResponse(List<ConsensusDecisionResponse> decisions, int total,
                                      UUID simulationId, Integer limit) {
        this.decisions = decisions;
        this.total = total;
        this.simulationId = simulationId;
        this.limit = limit;
    }
}
