package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.MockBrokerConfig;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.UserId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Command for creating a benchmark with multiple simulations.
 * <p>
 * A benchmark aggregates multiple simulations running the same time period
 * with different strategies, allowing for performance comparison and ranking.
 * </p>
 * <p>
 * The command supports both historical backtesting (with endDate) and live
 * simulation mode (endDate = null).
 * </p>
 *
 * <p><b>Broker Configuration (mutually exclusive):</b></p>
 * <ul>
 *   <li><b>Mock broker:</b> When mockConfig is provided, a separate mock broker account
 *       is created for each strategy with the specified initial capital and cost preset</li>
 *   <li><b>Paper/Real broker:</b> When brokerAccountId is provided, all simulations share
 *       the same broker account with capital reserved per simulation (capitalPerStrategy)</li>
 *   <li><b>Legacy mode:</b> When neither is provided, uses default initial cash without broker accounts</li>
 * </ul>
 *
 * @param strategyIds list of strategy UUIDs to create simulations for (must not be empty)
 * @param startDate when the benchmark/simulations start (required)
 * @param endDate when the benchmark/simulations end (nullable for live mode)
 * @param name optional custom name for the benchmark (nullable, will be auto-generated if null)
 * @param mockConfig optional mock broker configuration (when provided, creates mock accounts per strategy)
 * @param userId the user creating the benchmark (required when mockConfig or brokerAccountId is provided)
 * @param brokerAccountId optional existing broker account ID (for paper/real trading)
 * @param capitalPerStrategy optional capital to reserve per simulation (required when brokerAccountId is provided)
 */
public record CreateBenchmarkCommand(
    List<UUID> strategyIds,
    Instant startDate,
    Instant endDate,
    String name,
    MockBrokerConfig mockConfig,
    UserId userId,
    BrokerAccountId brokerAccountId,
    Money capitalPerStrategy
) {
    /**
     * Legacy constructor for backward compatibility (without brokerAccountId support).
     */
    public CreateBenchmarkCommand(
        List<UUID> strategyIds,
        Instant startDate,
        Instant endDate,
        String name,
        MockBrokerConfig mockConfig,
        UserId userId
    ) {
        this(strategyIds, startDate, endDate, name, mockConfig, userId, null, null);
    }

    /**
     * Compact constructor with validation.
     *
     * @throws IllegalArgumentException if validation fails
     */
    public CreateBenchmarkCommand {
        if (strategyIds == null || strategyIds.isEmpty()) {
            throw new IllegalArgumentException("Strategy IDs are required and cannot be empty");
        }
        if (startDate == null) {
            throw new IllegalArgumentException("Start date is required");
        }
        if (endDate != null && !endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("End date must be after start date");
        }
        // Tiingo provides 30+ years of daily stock data; earliest reliable data starts ~1993.
        // Alpaca IEX feed covers ~5.5 years; Tiingo fills the gap for older backtests.
        Instant dataAvailabilityCutoff = Instant.parse("1993-01-29T00:00:00Z");
        if (startDate.isBefore(dataAvailabilityCutoff)) {
            throw new IllegalArgumentException(
                "Start date cannot be before January 29, 1993. " +
                "Historical market data is not available before this date. " +
                "Please choose a start date of January 29, 1993 or later."
            );
        }
        if (mockConfig != null && userId == null) {
            throw new IllegalArgumentException("UserId is required when mockConfig is provided");
        }
        if (mockConfig != null && brokerAccountId != null) {
            throw new IllegalArgumentException("mockConfig and brokerAccountId are mutually exclusive");
        }
        if (brokerAccountId != null) {
            if (capitalPerStrategy == null) {
                throw new IllegalArgumentException("capitalPerStrategy is required when brokerAccountId is provided");
            }
            if (capitalPerStrategy.amount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("capitalPerStrategy must be greater than zero");
            }
            if (userId == null) {
                throw new IllegalArgumentException("UserId is required when brokerAccountId is provided");
            }
        }
    }

    public boolean usesMockBroker() {
        return mockConfig != null;
    }

    public boolean usesExistingBrokerAccount() {
        return brokerAccountId != null;
    }

    public boolean isLiveMode() {
        return endDate == null;
    }

    public int getStrategyCount() {
        return strategyIds.size();
    }

    public Money getTotalCapitalRequired() {
        if (capitalPerStrategy == null) {
            return null;
        }
        BigDecimal total = capitalPerStrategy.amount().multiply(BigDecimal.valueOf(strategyIds.size()));
        return new Money(total, capitalPerStrategy.currency());
    }
}
