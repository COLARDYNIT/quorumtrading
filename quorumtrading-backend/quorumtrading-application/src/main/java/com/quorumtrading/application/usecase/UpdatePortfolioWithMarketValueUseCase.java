package com.quorumtrading.application.usecase;

import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.valueobject.SimulationId;

import java.time.Instant;

public interface UpdatePortfolioWithMarketValueUseCase {

    PortfolioSnapshot updatePortfolioWithMarketValue(SimulationId simulationId, Instant portfolioUpdateTimestamp);
}
