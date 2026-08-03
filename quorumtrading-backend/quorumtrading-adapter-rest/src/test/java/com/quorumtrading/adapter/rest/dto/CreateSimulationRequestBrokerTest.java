package com.quorumtrading.adapter.rest.dto;

import com.quorumtrading.adapter.rest.dto.request.CreateSimulationRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The request carries a broker account.
 *
 * <p>It previously had no such field, and {@code SimulationResource} passed {@code null} for the
 * broker account regardless — so a {@code brokerAccountId} in the request body was silently
 * dropped by Jackson as an unknown property. Every REST-created simulation therefore ran
 * broker-less: no capital ring-fenced, no {@code SimulationPortfolio} ledger, and no way to reach
 * paper or live trading through the REST API at all.</p>
 */
class CreateSimulationRequestBrokerTest {

    private static CreateSimulationRequest request(String brokerAccountId, Double tradingLimit) {
        return new CreateSimulationRequest(
            "strategy-id",
            Instant.parse("2026-05-01T00:00:00Z"),
            Instant.parse("2026-06-01T00:00:00Z"),
            "probe",
            10_000.0,
            brokerAccountId,
            tradingLimit,
            false);
    }

    @Test
    void keepsTheBrokerAccountItWasGiven() {
        String accountId = "550e8400-e29b-41d4-a716-446655440000";

        assertThat(request(accountId, 2_500.0).brokerAccountId()).isEqualTo(accountId);
        assertThat(request(accountId, 2_500.0).tradingLimit()).isEqualTo(2_500.0);
    }

    /** Reserving more than the simulation itself holds would over-commit the account. */
    @Test
    void defaultsTheTradingLimitToTheSimulationCash() {
        assertThat(request("550e8400-e29b-41d4-a716-446655440000", null).tradingLimit())
            .isEqualTo(10_000.0);
    }

    @Test
    void leavesTheTradingLimitAloneForABrokerLessBacktest() {
        assertThat(request(null, null).brokerAccountId()).isNull();
        assertThat(request(null, null).tradingLimit()).isNull();
        assertThat(request("  ", null).tradingLimit()).isNull();
    }

    @Test
    void stillDefaultsInitialCash() {
        CreateSimulationRequest r = new CreateSimulationRequest(
            "strategy-id", null, Instant.parse("2026-06-01T00:00:00Z"), null, null, null, null, false);
        assertThat(r.initialCash()).isEqualTo(100_000.0);
    }
}
