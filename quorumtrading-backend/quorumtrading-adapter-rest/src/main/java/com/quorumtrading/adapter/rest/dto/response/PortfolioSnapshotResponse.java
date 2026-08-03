package com.quorumtrading.adapter.rest.dto.response;

import java.util.Map;

/**
 * Portfolio snapshot response DTO matching frontend expectations.
 * Maps from application layer PortfolioSnapshot.
 */
public class PortfolioSnapshotResponse {

    public String id;
    public String simulationId;
    public String timestamp;
    public double cashBalance;
    public Map<String, PositionResponse> positions;  // Strongly typed positions map
    public double totalValue;

    public PortfolioSnapshotResponse() {}

    public PortfolioSnapshotResponse(String id, String simulationId, String timestamp,
                                     double cashBalance, Map<String, PositionResponse> positions,
                                     double totalValue) {
        this.id = id;
        this.simulationId = simulationId;
        this.timestamp = timestamp;
        this.cashBalance = cashBalance;
        this.positions = positions;
        this.totalValue = totalValue;
    }
}
