package com.quorumtrading.adapter.mcp.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.adapter.mcp.dto.McpSimulationDto;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Three separate audits asked the same question of a simulation payload and could not answer it:
 * "am I trading real money, and is this run stuck?".
 *
 * <p>{@code LIVE} names both a lifecycle state and real-money trading, so a mock-broker run
 * reported {@code status:"LIVE"} with {@code tradingMode:"BACKTEST"} — and filtering by status LIVE
 * is what the live-trading walkthrough prescribed for "find your live runs". Separately, a
 * simulation is internally flagged paused from creation until it starts, so a healthy new run
 * announced {@code paused:true} with a null reason seconds after the create tool promised it starts
 * by itself; the obvious next move was to resume a run nobody had paused.</p>
 */
class SimulationProjectionHonestyTest {

    private static final UUID SIM_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID STRATEGY_UUID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private McpDtoMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        mapper = new McpDtoMapper();
        var field = McpDtoMapper.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(mapper, new ObjectMapper());
        UiLinks uiLinks = new UiLinks();
        uiLinks.frontendBaseUrl = "http://localhost:3000";
        uiLinks.frontendEnabled = true;
        var linksField = McpDtoMapper.class.getDeclaredField("uiLinks");
        linksField.setAccessible(true);
        linksField.set(mapper, uiLinks);
    }

    private Simulation freshSimulation() {
        return new Simulation(
                new SimulationId(SIM_UUID),
                "R7 projection probe",
                new StrategyId(STRATEGY_UUID),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-31T00:00:00Z"),
                Money.usd(new BigDecimal("10000")));
    }

    @Nested
    @DisplayName("realMoney answers the question status cannot")
    class RealMoney {

        @Test
        @DisplayName("a mock-broker run is not real money, whatever its lifecycle state says")
        void backtestIsNotRealMoney() {
            McpSimulationDto dto = mapper.toSimulationDto(freshSimulation(), "S", "BACKTEST");

            assertThat(dto.realMoney()).isFalse();
        }

        @Test
        @DisplayName("a paper run is not real money")
        void paperIsNotRealMoney() {
            assertThat(mapper.toSimulationDto(freshSimulation(), "S", "PAPER").realMoney()).isFalse();
        }

        @Test
        @DisplayName("only a LIVE trading mode reports real money")
        void liveTradingModeIsRealMoney() {
            assertThat(mapper.toSimulationDto(freshSimulation(), "S", "LIVE").realMoney()).isTrue();
        }

        @Test
        @DisplayName("an unresolved trading mode reports null, not false")
        void unresolvedTradingModeIsNull() {
            // "we did not look" and "no real money" are different answers to a question about money.
            assertThat(mapper.toSimulationDto(freshSimulation()).realMoney()).isNull();
        }
    }

    @Nested
    @DisplayName("paused means 'you must resume this', not 'not started yet'")
    class Paused {

        @Test
        @DisplayName("a freshly created run is not reported as paused")
        void freshRunIsNotPaused() {
            Simulation sim = freshSimulation();
            assertThat(sim.getStatus().name()).isEqualTo("PENDING");
            // The entity really does carry the flag; the projection is what must not repeat it.
            assertThat(sim.isPaused()).isTrue();

            McpSimulationDto dto = mapper.toSimulationDto(sim, "S", "BACKTEST");

            assertThat(dto.paused()).isFalse();
            assertThat(dto.pauseReason()).isNull();
        }

        @Test
        @DisplayName("a run paused while simulating is still reported as paused")
        void genuinelyPausedRunIsReported() {
            Simulation sim = freshSimulation();
            sim.start();
            sim.pause();

            assertThat(sim.getStatus().name()).isEqualTo("SIMULATING");
            assertThat(mapper.toSimulationDto(sim, "S", "BACKTEST").paused()).isTrue();
        }

        @Test
        @DisplayName("a running simulation is not reported as paused")
        void runningSimulationIsNotPaused() {
            Simulation sim = freshSimulation();
            sim.start();

            assertThat(mapper.toSimulationDto(sim, "S", "BACKTEST").paused()).isFalse();
        }
    }

    @Nested
    @DisplayName("list rows do not contradict the detail view")
    class ListDetailAgreement {

        @Test
        @DisplayName("twrPercent and mwrPercent are omitted, not null, when not computed")
        void unpopulatedRatesAreOmittedFromJson() throws Exception {
            McpSimulationDto listRow = mapper.toSimulationDto(
                    freshSimulation(), "S", "BACKTEST", List.of("BTC"), "10000", "0",
                    "10000", null, null, List.of());

            String json = new ObjectMapper().writeValueAsString(listRow);

            assertThat(json).doesNotContain("\"twrPercent\"").doesNotContain("\"mwrPercent\"");
            assertThat(json).contains("\"totalInvested\":\"10000\"");
        }

        @Test
        @DisplayName("a computed twrPercent is still serialised")
        void computedRateIsSerialised() throws Exception {
            McpSimulationDto detail = mapper.toSimulationDto(
                    freshSimulation(), "S", "BACKTEST", List.of("BTC"), "10294.74", "2.9474",
                    "10000", "2.9474", "3.1", List.of());

            assertThat(new ObjectMapper().writeValueAsString(detail))
                    .contains("\"twrPercent\":\"2.9474\"");
        }
    }
}
