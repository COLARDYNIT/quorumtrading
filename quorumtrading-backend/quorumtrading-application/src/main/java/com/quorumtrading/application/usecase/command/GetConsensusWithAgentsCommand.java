package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.UserId;

import java.util.List;
import java.util.UUID;

/**
 * Command to retrieve consensus decisions with their associated agent LLM decisions.
 * Supports filtering by simulation ID, ticker symbol, actions, and pagination.
 */
public class GetConsensusWithAgentsCommand {

    private final UUID simulationId;
    private final String ticker;
    private final List<String> actions;
    private final Integer limit;
    private final Long beforeConsensusId;
    private final Long afterConsensusId;
    private final UserId requestingUserId;
    private final boolean onlyParseFailures;

    public GetConsensusWithAgentsCommand(
            UUID simulationId,
            String ticker,
            List<String> actions,
            Integer limit,
            Long beforeConsensusId,
            Long afterConsensusId,
            UserId requestingUserId
    ) {
        this(simulationId, ticker, actions, limit, beforeConsensusId, afterConsensusId, requestingUserId, false);
    }

    public GetConsensusWithAgentsCommand(
            UUID simulationId,
            String ticker,
            List<String> actions,
            Integer limit,
            Long beforeConsensusId,
            Long afterConsensusId,
            UserId requestingUserId,
            boolean onlyParseFailures
    ) {
        this.simulationId = simulationId;
        this.ticker = ticker;
        this.actions = actions;
        this.limit = limit;
        this.beforeConsensusId = beforeConsensusId;
        this.afterConsensusId = afterConsensusId;
        this.requestingUserId = requestingUserId;
        this.onlyParseFailures = onlyParseFailures;
    }

    /** Backward-compatible constructor for internal/system callers. */
    public GetConsensusWithAgentsCommand(
            UUID simulationId,
            String ticker,
            List<String> actions,
            Integer limit,
            Long beforeConsensusId,
            Long afterConsensusId
    ) {
        this(simulationId, ticker, actions, limit, beforeConsensusId, afterConsensusId, null, false);
    }

    // Getters
    public UUID getSimulationId() {
        return simulationId;
    }

    public String getTicker() {
        return ticker;
    }

    public List<String> getActions() {
        return actions;
    }

    public Integer getLimit() {
        return limit;
    }

    public Long getBeforeConsensusId() {
        return beforeConsensusId;
    }

    public Long getAfterConsensusId() {
        return afterConsensusId;
    }

    public UserId getRequestingUserId() {
        return requestingUserId;
    }

    public boolean isOnlyParseFailures() {
        return onlyParseFailures;
    }
}
