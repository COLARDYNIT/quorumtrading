package com.quorumtrading.model.entity;

import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.ContributionId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ContributionTest {

    private Simulation liveSim() {
        // Start of today at 00:00 UTC — the boundary Simulation.start() uses to classify LIVE vs
        // backtest. This helper used to build the SYSTEM zone's midnight, because that is what
        // start() read at the time; both now agree on UTC, so the status no longer depends on
        // which side of local midnight the suite runs on, or on where it runs.
        Instant start = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Simulation sim = new Simulation(
                new SimulationId(UUID.randomUUID()),
                "Test sim",
                new StrategyId(UUID.randomUUID()),
                start,
                null,                 // endDate null → LIVE
                Money.usd(new BigDecimal("1000")));
        sim.start();
        return sim;
    }

    @Test
    void positiveAmountAccepted() {
        Contribution c = new Contribution(
                ContributionId.generate(),
                new SimulationId(UUID.randomUUID()),
                Money.usd(new BigDecimal("200")),
                Instant.now(), null, null, Instant.now());
        assertEquals(new BigDecimal("200"), c.amount().amount());
    }

    @Test
    void zeroAmountRejected() {
        SimulationId sid = new SimulationId(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> new Contribution(
                ContributionId.generate(), sid,
                Money.usd(BigDecimal.ZERO),
                Instant.now(), null, null, Instant.now()));
    }

    @Test
    void buildContributionRequiresLiveSimulation() {
        Instant past = Instant.now().minus(30, ChronoUnit.DAYS);
        Simulation backtest = new Simulation(
                new SimulationId(UUID.randomUUID()),
                "Backtest",
                new StrategyId(UUID.randomUUID()),
                past,
                Instant.now().minus(1, ChronoUnit.DAYS),  // endDate in past → backtest
                Money.usd(new BigDecimal("1000")));
        backtest.start(); // transitions to SIMULATING
        assertFalse(backtest.canAcceptContribution());
        assertThrows(IllegalStateException.class, () -> backtest.buildContribution(
                Money.usd(new BigDecimal("200")), Instant.now(), null, null));
    }

    @Test
    void buildContributionProducesValidEntity() {
        Simulation sim = liveSim();
        assertTrue(sim.canAcceptContribution());
        Contribution c = sim.buildContribution(
                Money.usd(new BigDecimal("200")),
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID());
        assertEquals(sim.getSimulationId(), c.simulationId());
        assertEquals(0, c.amount().amount().compareTo(new BigDecimal("200")));
        assertNotNull(c.sourcePlanId());
    }

    @Test
    void simulationPortfolioRecordContributionBumpsBothCashFields() {
        SimulationPortfolio portfolio = SimulationPortfolio.initialize(
                new SimulationId(UUID.randomUUID()),
                new BrokerAccountId(UUID.randomUUID()),
                Money.usd(new BigDecimal("1000")));
        BigDecimal originalReserved = portfolio.getCashReserved().amount();
        portfolio.recordContribution(Money.usd(new BigDecimal("200")));
        assertEquals(0, portfolio.getCashAvailable().amount().compareTo(new BigDecimal("1200")));
        assertEquals(0, portfolio.getCashReserved().amount().compareTo(originalReserved.add(new BigDecimal("200"))));
    }

    @Test
    void simulationPortfolioRecordContributionRejectsNegative() {
        SimulationPortfolio portfolio = SimulationPortfolio.initialize(
                new SimulationId(UUID.randomUUID()),
                new BrokerAccountId(UUID.randomUUID()),
                Money.usd(new BigDecimal("1000")));
        assertThrows(IllegalArgumentException.class,
                () -> portfolio.recordContribution(Money.usd(new BigDecimal("-50"))));
    }
}
