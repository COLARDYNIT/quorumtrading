package com.quorumtrading.adapter.rest.dto.response;

import java.util.List;
import java.util.UUID;

public class PortfolioSnapshotsResponse {

    public List<PortfolioSnapshotResponse> snapshots;
    public int total;
    public UUID simulationId;
    public Integer limit;

    public PortfolioSnapshotsResponse() {}

    public PortfolioSnapshotsResponse(List<PortfolioSnapshotResponse> snapshots, int total,
                                      UUID simulationId, Integer limit) {
        this.snapshots = snapshots;
        this.total = total;
        this.simulationId = simulationId;
        this.limit = limit;
    }
}
