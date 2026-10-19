package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.enums.OAuthProvider;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The chart query decodes its native-query rows by column index, and the index shifts by one
 * when the positions column is left out. Total value and cash are deliberately different
 * here, so reading either from the wrong column fails the test instead of passing by accident.
 */
@QuarkusTest
class PortfolioSnapshotProjectionIntegrationTest {

    private static final BigDecimal TOTAL = new BigDecimal("12345.67");
    private static final BigDecimal CASH = new BigDecimal("2345.67");

    @Inject PortfolioRepository portfolioRepository;
    @Inject SimulationRepository simulationRepository;
    @Inject StrategyRepository strategyRepository;
    @Inject UserRepository userRepository;

    @Test
    void totalAndCashLandInTheirOwnFieldsWithPositions() {
        SimulationId simId = simulationWithOneSnapshot();

        List<PortfolioSnapshot> snapshots =
            portfolioRepository.findBySimulationId(simId, null, null, null, null, false);

        assertTotalAndCash(snapshots);
    }

    @Test
    void totalAndCashLandInTheirOwnFieldsWithoutPositions() {
        SimulationId simId = simulationWithOneSnapshot();

        List<PortfolioSnapshot> snapshots =
            portfolioRepository.findBySimulationId(simId, null, null, null, "daily", true);

        assertTotalAndCash(snapshots);
    }

    private static void assertTotalAndCash(List<PortfolioSnapshot> snapshots) {
        assertThat(snapshots).singleElement().satisfies(s -> {
            assertThat(s.getTotalValue().amount()).isEqualByComparingTo(TOTAL);
            assertThat(s.getCashAvailable().amount()).isEqualByComparingTo(CASH);
        });
    }

    // ----- fixtures -----

    private SimulationId simulationWithOneSnapshot() {
        UserId userId = UserId.generate();
        userRepository.save(User.create(userId,
            "snapshot-projection-" + UUID.randomUUID() + "@example.com",
            "Snapshot Projection", null, OAuthProvider.GOOGLE));

        Strategy strategy = strategyRepository.save(new Strategy(
            new StrategyId(UUID.randomUUID()), "Snapshot Projection Strategy",
            "## Test\nSimple test strategy", userId));

        SimulationId simId = new SimulationId(UUID.randomUUID());
        Simulation sim = new Simulation(simId, "Snapshot Projection Simulation", strategy.getId(),
            Instant.now().minus(30, ChronoUnit.DAYS), Instant.now(), Money.usd(new BigDecimal("10000.00")));
        sim.setOwnerUserId(userId);
        simulationRepository.save(sim);

        portfolioRepository.save(new PortfolioSnapshot(simId, Instant.parse("2026-03-04T21:00:00Z"),
            Map.of(), Money.usd(TOTAL), Money.usd(CASH)));
        return simId;
    }
}
