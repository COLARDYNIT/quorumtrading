package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.MockBrokerConfig;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;

import java.time.Instant;

/**
 * Command for creating a new simulation.
 *
 * <p><b>Broker Account Options (mutually exclusive):</b></p>
 * <ul>
 *   <li><b>brokerAccountId + tradingLimit:</b> Use existing broker account</li>
 *   <li><b>mockBrokerConfig:</b> Create inline mock broker with custom costs</li>
 *   <li><b>Neither:</b> Use legacy global broker routing (backward compatible)</li>
 * </ul>
 *
 * @param name             optional name for the simulation (null for auto-generated)
 * @param strategyId       ID of the strategy to use for this simulation
 * @param startDate        simulation start date
 * @param endDate          simulation end date
 * @param initialCash      initial cash amount for the portfolio (used by legacy routing)
 * @param brokerAccountId  optional broker account ID to use for trading (nullable)
 * @param tradingLimit     optional trading limit for the broker account (nullable)
 * @param mockBrokerConfig optional inline mock broker config (creates new mock account, nullable)
 * @param requestingUserId ID of the user creating the simulation (for authorization, nullable for system-created)
 * @param confirmLive      acknowledges that this simulation will trade REAL money. Required when
 *                         {@code brokerAccountId} names a live account (ALPACA_LIVE, SAXO_LIVE),
 *                         mirroring {@link CreateInvestmentPlanCommand#confirmLive()}. Without it,
 *                         the path that opens a real position immediately was gated only by prose
 *                         asking the agent to check with the user, while the slower monthly
 *                         investment-plan path was gated in code — the rails were inverted
 *                         relative to risk.
 */
public record CreateSimulationCommand(
    String name,
    StrategyId strategyId,
    Instant startDate,
    Instant endDate,
    Money initialCash,
    BrokerAccountId brokerAccountId,
    Money tradingLimit,
    MockBrokerConfig mockBrokerConfig,
    UserId requestingUserId,
    boolean confirmLive
) {
    /**
     * Backward-compatible constructor for callers that never attach a live account — internal
     * paths (benchmarks, challenges, DCA execution) which either use a mock broker or have already
     * taken the user's acknowledgement upstream.
     */
    public CreateSimulationCommand(
        String name,
        StrategyId strategyId,
        Instant startDate,
        Instant endDate,
        Money initialCash,
        BrokerAccountId brokerAccountId,
        Money tradingLimit,
        MockBrokerConfig mockBrokerConfig,
        UserId requestingUserId
    ) {
        this(name, strategyId, startDate, endDate, initialCash, brokerAccountId, tradingLimit,
            mockBrokerConfig, requestingUserId, false);
    }
    /**
     * Backward-compatible constructor without broker account fields.
     * Uses legacy global broker routing.
     */
    public CreateSimulationCommand(
        String name,
        StrategyId strategyId,
        Instant startDate,
        Instant endDate,
        Money initialCash
    ) {
        this(name, strategyId, startDate, endDate, initialCash, null, null, null, null);
    }

    /**
     * Constructor with broker account ID and trading limit.
     * Uses per-account broker routing.
     */
    public CreateSimulationCommand(
        String name,
        StrategyId strategyId,
        Instant startDate,
        Instant endDate,
        Money initialCash,
        BrokerAccountId brokerAccountId,
        Money tradingLimit
    ) {
        this(name, strategyId, startDate, endDate, initialCash, brokerAccountId, tradingLimit, null, null);
    }
}
