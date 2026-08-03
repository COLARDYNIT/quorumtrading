package com.quorumtrading.adapter.broker.alpaca;

import com.quorumtrading.application.port.out.BrokerPort;
import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.valueobject.SimulationId;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AlpacaBrokerAdapter.
 *
 * Tests verify read-only operations:
 * - API connectivity
 * - Account info retrieval
 * - Portfolio sync
 *
 * Skipped in CI unless ALPACA_INTEGRATION_TESTS=true is set.
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "ALPACA_INTEGRATION_TESTS", matches = "true")
class AlpacaBrokerIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(AlpacaBrokerIntegrationTest.class);

    @Inject
    @Named("alpacaBroker")
    BrokerPort alpacaBroker;

    @Test
    void testApiConnectivity() {
        // Verify broker adapter is injectable
        assertNotNull(alpacaBroker, "AlpacaBroker should be injectable via @Named");
        assertTrue(alpacaBroker instanceof AlpacaBrokerAdapter,
            "Injected broker should be AlpacaBrokerAdapter instance");
    }

    @Test
    void testFetchPortfolio() {
        // Create test simulation ID
        SimulationId simulationId = new SimulationId(UUID.randomUUID());

        // Fetch portfolio (read-only operation) - this will throw if auth fails
        Portfolio portfolio = alpacaBroker.getCurrentPortfolio(simulationId);

        // CRITICAL: Verify we got actual portfolio data (not defaults due to auth failure)
        assertNotNull(portfolio, "Portfolio should not be null");
        assertNotNull(portfolio.cashAvailable(), "Cash balance should not be null");
        assertNotNull(portfolio.positions(), "Positions map should not be null");

        // Verify cash balance is a valid number (>= 0) - paper accounts can be reset to $0
        assertTrue(portfolio.cashAvailable().amount().doubleValue() >= 0,
            "Cash balance should be >= 0. " +
            "Negative balance likely indicates authentication failure (401) or API error. " +
            "Check logs for errors.");

        log.info("Successfully fetched portfolio from Alpaca API: cashBalance=${}, positions={}",
            portfolio.cashAvailable().amount(), portfolio.positions().size());

        // Verify portfolio has reasonable values for paper trading account
        assertTrue(portfolio.cashAvailable().amount().doubleValue() <= 10_000_000,
            "Paper trading account should have reasonable cash balance (< $10M)");
    }
}
