package com.quorumtrading.adapter.persistence.mapper;

import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.port.out.UserRepository;
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
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round trip for {@link SimulationMapper}, which unlike the other mappers is not a pure function:
 * it resolves {@code StrategyId} against the strategies table, so the strategy has to exist before
 * a simulation can be mapped at all.
 *
 * <p>That makes this an integration test rather than part of {@link MapperRoundTripTest} — and it
 * has to be a top-level class, because CDI ignores {@code @Transactional} on the methods of a
 * {@code @Nested} inner class and Quarkus fails the build rather than silently running without a
 * transaction.</p>
 */
@QuarkusTest
@DisplayName("SimulationMapper — round trip")
class SimulationMapperRoundTripIntegrationTest {

    @Inject SimulationMapper simulationMapper;
    @Inject StrategyRepository strategyRepository;
    @Inject UserRepository userRepository;



    /** A strategy needs a real owner row: orchestrator_strategies.owner_id is a foreign key. */
    private StrategyId persistedStrategy() {
        UserId ownerId = UserId.generate();
        userRepository.save(User.create(ownerId,
                "mapper-" + ownerId.value() + "@test.local", "Mapper fixture", null,
                OAuthProvider.GOOGLE));
        Strategy strategy = new Strategy(StrategyId.generate(), "Mapper fixture",
                "---\ncrypto_tickers:\n  - BTC\n---\nBuy.", ownerId);
        return strategyRepository.save(strategy).getId();
    }

    @Test
    @Transactional
    @DisplayName("dates, cash and identity survive the round trip")
    void shouldRoundTrip() {
        SimulationId id = new SimulationId(UUID.randomUUID());
        StrategyId strategyId = persistedStrategy();
        Simulation sim = Simulation.reconstitute(id, "Backtest", strategyId,
                Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-08T00:00:00Z"),
                Money.usd(new BigDecimal("10000.00")), null, null, null);

        Simulation back = simulationMapper.toDomain(simulationMapper.toJpaEntity(sim));

        assertThat(back.getSimulationId()).isEqualTo(id);
        assertThat(back.getName()).isEqualTo("Backtest");
        assertThat(back.getStrategyId()).isEqualTo(strategyId);
        assertThat(back.getStartDate()).isEqualTo(Instant.parse("2026-06-01T00:00:00Z"));
        assertThat(back.getEndDate()).isEqualTo(Instant.parse("2026-06-08T00:00:00Z"));
        assertThat(back.getInitialCash().amount()).isEqualByComparingTo("10000.00");
    }

    @Test
    @Transactional
    @DisplayName("the failure reason survives — it is the only record of why a run died")
    void shouldCarryTheFailureReason() {
        Simulation sim = Simulation.reconstitute(new SimulationId(UUID.randomUUID()), "S",
                persistedStrategy(), Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-08T00:00:00Z"),
                Money.usd(new BigDecimal("1000")), null, null, null);
        sim.fail("Market-data backfill failed: no provider returned data for this range.");

        Simulation back = simulationMapper.toDomain(simulationMapper.toJpaEntity(sim));

        assertThat(back.getFailureReason())
                .isEqualTo("Market-data backfill failed: no provider returned data for this range.");
    }

    @Test
    @Transactional
    @DisplayName("a LIVE simulation with no end date round trips with the null intact")
    void shouldPreserveANullEndDate() {
        Simulation live = Simulation.reconstitute(new SimulationId(UUID.randomUUID()), "Live",
                persistedStrategy(), Instant.parse("2026-06-01T00:00:00Z"), null,
                Money.usd(new BigDecimal("5000")), null, null, null);

        Simulation back = simulationMapper.toDomain(simulationMapper.toJpaEntity(live));

        assertThat(back.getEndDate())
                .as("a null end date is what makes a simulation LIVE rather than a backtest")
                .isNull();
    }
}
