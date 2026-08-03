package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.PortfolioRepository.DailyPortfolioValue;
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
 * The multi-simulation chart query used to hand callers a {@code List<Object[]>} whose column
 * order was documented only in a javadoc line, leaving the REST layer to cast {@code row[0]},
 * {@code row[1]}, {@code row[2]} by position — so reordering the projection would have failed
 * at runtime, in production, as a ClassCastException.
 *
 * <p>This runs the real native query against the Flyway-migrated {@code quorumtrading_test}
 * schema, which is the only place the decoding can actually be proven: Hibernate's runtime
 * types for a TIMESTAMPTZ and a uuid column are exactly what a mock cannot tell you.</p>
 */
@QuarkusTest
class DailyPortfolioValueProjectionIntegrationTest {

    @Inject PortfolioRepository portfolioRepository;
    @Inject SimulationRepository simulationRepository;
    @Inject StrategyRepository strategyRepository;
    @Inject UserRepository userRepository;

    @Test
    void eachColumnLandsInItsOwnTypedField() {
        UserId userId = createUser();
        SimulationId simId = createSimulation(userId);
        Instant when = Instant.parse("2026-03-04T21:00:00Z");
        portfolioRepository.save(snapshot(simId, when, new BigDecimal("12345.67")));

        List<DailyPortfolioValue> points =
            portfolioRepository.findDailyTotalValueBySimulationIds(List.of(simId.value()));

        assertThat(points).hasSize(1);
        DailyPortfolioValue point = points.get(0);
        assertThat(point.simulationId()).isEqualTo(simId.value());
        assertThat(point.timestamp()).isEqualTo(when);
        assertThat(point.totalValue()).isEqualByComparingTo(new BigDecimal("12345.67"));
    }

    @Test
    void pointsFromSeveralSimulationsStayAttributedToTheRightOne() {
        UserId userId = createUser();
        SimulationId first = createSimulation(userId);
        SimulationId second = createSimulation(userId);
        Instant when = Instant.parse("2026-03-05T21:00:00Z");
        portfolioRepository.save(snapshot(first, when, new BigDecimal("1000.00")));
        portfolioRepository.save(snapshot(second, when, new BigDecimal("2000.00")));

        List<DailyPortfolioValue> points = portfolioRepository
            .findDailyTotalValueBySimulationIds(List.of(first.value(), second.value()));

        assertThat(points).hasSize(2);
        assertThat(points).filteredOn(p -> p.simulationId().equals(first.value()))
            .singleElement()
            .satisfies(p -> assertThat(p.totalValue()).isEqualByComparingTo(new BigDecimal("1000.00")));
        assertThat(points).filteredOn(p -> p.simulationId().equals(second.value()))
            .singleElement()
            .satisfies(p -> assertThat(p.totalValue()).isEqualByComparingTo(new BigDecimal("2000.00")));
    }

    @Test
    void oneDayYieldsOnePointPerSimulation() {
        UserId userId = createUser();
        SimulationId simId = createSimulation(userId);
        Instant morning = Instant.parse("2026-03-06T09:00:00Z");
        portfolioRepository.save(snapshot(simId, morning, new BigDecimal("100.00")));
        portfolioRepository.save(snapshot(simId, morning.plus(6, ChronoUnit.HOURS), new BigDecimal("150.00")));

        List<DailyPortfolioValue> points =
            portfolioRepository.findDailyTotalValueBySimulationIds(List.of(simId.value()));

        // Last snapshot of the day wins — that is what the DISTINCT ON ordering selects.
        assertThat(points).singleElement()
            .satisfies(p -> assertThat(p.totalValue()).isEqualByComparingTo(new BigDecimal("150.00")));
    }

    @Test
    void noSimulationsMeansNoQueryAndNoPoints() {
        assertThat(portfolioRepository.findDailyTotalValueBySimulationIds(List.of())).isEmpty();
    }

    // ----- fixtures -----

    private PortfolioSnapshot snapshot(SimulationId simId, Instant at, BigDecimal totalValue) {
        Money value = Money.usd(totalValue);
        return new PortfolioSnapshot(simId, at, Map.of(), value, value);
    }

    private UserId createUser() {
        UserId userId = UserId.generate();
        userRepository.save(User.create(userId,
            "daily-value-" + UUID.randomUUID() + "@example.com",
            "Daily Value Projection", null, OAuthProvider.GOOGLE));
        return userId;
    }

    private SimulationId createSimulation(UserId userId) {
        Strategy strategy = strategyRepository.save(new Strategy(
            new StrategyId(UUID.randomUUID()), "Chart Data Strategy",
            "## Test\nSimple test strategy", userId));

        SimulationId simId = new SimulationId(UUID.randomUUID());
        Simulation sim = new Simulation(simId, "Chart Data Simulation", strategy.getId(),
            Instant.now().minus(30, ChronoUnit.DAYS), Instant.now(), Money.usd(new BigDecimal("10000.00")));
        sim.setOwnerUserId(userId);
        simulationRepository.save(sim);
        return simId;
    }
}
