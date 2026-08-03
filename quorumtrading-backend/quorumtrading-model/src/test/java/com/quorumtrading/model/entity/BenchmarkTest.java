package com.quorumtrading.model.entity;

import com.quorumtrading.model.valueobject.BenchmarkId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkTest {

    @Test
    void shouldCreateBacktestBenchmark() {
        BenchmarkId id = BenchmarkId.generate();
        Instant startDate = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant endDate = Instant.now();
        Instant createdAt = Instant.now();

        Benchmark benchmark = new Benchmark(id, "Test Backtest", startDate, endDate, createdAt);

        assertNotNull(benchmark);
        assertEquals(id, benchmark.getBenchmarkId());
        assertEquals("Test Backtest", benchmark.getName());
        assertEquals(startDate, benchmark.getStartDate());
        assertEquals(endDate, benchmark.getEndDate());
        assertEquals(createdAt, benchmark.getCreatedAt());
        assertTrue(benchmark.isBacktest());
        assertFalse(benchmark.isLive());
    }

    @Test
    void shouldCreateLiveBenchmark() {
        BenchmarkId id = BenchmarkId.generate();
        Instant startDate = Instant.now();
        Instant createdAt = Instant.now();

        Benchmark benchmark = new Benchmark(id, "Test Live", startDate, null, createdAt);

        assertNotNull(benchmark);
        assertEquals(id, benchmark.getBenchmarkId());
        assertEquals("Test Live", benchmark.getName());
        assertEquals(startDate, benchmark.getStartDate());
        assertNull(benchmark.getEndDate());
        assertEquals(createdAt, benchmark.getCreatedAt());
        assertFalse(benchmark.isBacktest());
        assertTrue(benchmark.isLive());
    }

    @Test
    void shouldAutoGenerateBacktestName() {
        BenchmarkId id = BenchmarkId.generate();
        Instant startDate = Instant.parse("2025-01-01T00:00:00Z");
        Instant endDate = Instant.parse("2025-01-31T23:59:59Z");
        Instant createdAt = Instant.now();

        Benchmark benchmark = new Benchmark(id, startDate, endDate, createdAt);

        assertNotNull(benchmark.getName());
        assertTrue(benchmark.getName().startsWith("Backtest: "));
        assertTrue(benchmark.getName().contains("Jan 1, 2025"));
        assertTrue(benchmark.getName().contains("Jan 31, 2025"));
    }

    @Test
    void shouldAutoGenerateLiveName() {
        BenchmarkId id = BenchmarkId.generate();
        Instant startDate = Instant.parse("2025-11-01T00:00:00Z");
        Instant createdAt = Instant.now();

        Benchmark benchmark = new Benchmark(id, startDate, null, createdAt);

        assertNotNull(benchmark.getName());
        assertTrue(benchmark.getName().startsWith("Live: Started "));
        assertTrue(benchmark.getName().contains("Nov 1, 2025"));
    }

    @Test
    void shouldAutoGenerateNameWhenBlank() {
        BenchmarkId id = BenchmarkId.generate();
        Instant startDate = Instant.parse("2025-11-01T00:00:00Z");
        Instant endDate = Instant.parse("2025-11-30T23:59:59Z");
        Instant createdAt = Instant.now();

        Benchmark benchmark = new Benchmark(id, "", startDate, endDate, createdAt);

        assertNotNull(benchmark.getName());
        assertFalse(benchmark.getName().isBlank());
        assertTrue(benchmark.getName().startsWith("Backtest: "));
    }

    @Test
    void shouldAutoGenerateNameWhenNull() {
        BenchmarkId id = BenchmarkId.generate();
        Instant startDate = Instant.parse("2025-11-01T00:00:00Z");
        Instant createdAt = Instant.now();

        Benchmark benchmark = new Benchmark(id, null, startDate, null, createdAt);

        assertNotNull(benchmark.getName());
        assertTrue(benchmark.getName().startsWith("Live: "));
    }

    @Test
    void shouldThrowExceptionWhenBenchmarkIdIsNull() {
        Instant startDate = Instant.now();
        Instant createdAt = Instant.now();

        assertThrows(IllegalArgumentException.class, () ->
            new Benchmark(null, "Test", startDate, null, createdAt)
        );
    }

    @Test
    void shouldThrowExceptionWhenStartDateIsNull() {
        BenchmarkId id = BenchmarkId.generate();
        Instant createdAt = Instant.now();

        assertThrows(IllegalArgumentException.class, () ->
            new Benchmark(id, "Test", null, null, createdAt)
        );
    }

    @Test
    void shouldThrowExceptionWhenCreatedAtIsNull() {
        BenchmarkId id = BenchmarkId.generate();
        Instant startDate = Instant.now();

        assertThrows(IllegalArgumentException.class, () ->
            new Benchmark(id, "Test", startDate, null, null)
        );
    }

    @Test
    void shouldThrowExceptionWhenEndDateBeforeStartDate() {
        BenchmarkId id = BenchmarkId.generate();
        Instant startDate = Instant.now();
        Instant endDate = startDate.minus(1, ChronoUnit.DAYS);
        Instant createdAt = Instant.now();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            new Benchmark(id, "Test", startDate, endDate, createdAt)
        );
        assertEquals("End date must be after start date", exception.getMessage());
    }

    @Test
    void shouldGenerateCorrectBacktestName() {
        Instant startDate = Instant.parse("2025-11-01T00:00:00Z");
        Instant endDate = Instant.parse("2025-11-30T23:59:59Z");

        String name = Benchmark.generateName(startDate, endDate);

        assertEquals("Backtest: Nov 1, 2025 - Nov 30, 2025", name);
    }

    @Test
    void shouldGenerateCorrectLiveName() {
        Instant startDate = Instant.parse("2025-11-10T00:00:00Z");

        String name = Benchmark.generateName(startDate, null);

        assertEquals("Live: Started Nov 10, 2025", name);
    }

    @Test
    void shouldConsiderEqualWhenSameBenchmarkId() {
        BenchmarkId id = BenchmarkId.generate();
        Instant startDate = Instant.now();
        Instant createdAt = Instant.now();

        Benchmark benchmark1 = new Benchmark(id, "Test 1", startDate, null, createdAt);
        Benchmark benchmark2 = new Benchmark(id, "Test 2", startDate.plus(1, ChronoUnit.DAYS), null, createdAt);

        assertEquals(benchmark1, benchmark2);
        assertEquals(benchmark1.hashCode(), benchmark2.hashCode());
    }

    @Test
    void shouldNotBeEqualWhenDifferentBenchmarkId() {
        Instant startDate = Instant.now();
        Instant createdAt = Instant.now();

        Benchmark benchmark1 = new Benchmark(BenchmarkId.generate(), "Test", startDate, null, createdAt);
        Benchmark benchmark2 = new Benchmark(BenchmarkId.generate(), "Test", startDate, null, createdAt);

        assertNotEquals(benchmark1, benchmark2);
    }

    @Test
    void shouldHaveCorrectToStringFormat() {
        BenchmarkId id = BenchmarkId.generate();
        Instant startDate = Instant.parse("2025-11-01T00:00:00Z");
        Instant endDate = Instant.parse("2025-11-30T23:59:59Z");
        Instant createdAt = Instant.parse("2025-11-01T00:00:00Z");

        Benchmark benchmark = new Benchmark(id, "Test Benchmark", startDate, endDate, createdAt);

        String toString = benchmark.toString();

        assertTrue(toString.contains("Benchmark{"));
        assertTrue(toString.contains("benchmarkId=" + id));
        assertTrue(toString.contains("name='Test Benchmark'"));
        assertTrue(toString.contains("startDate=" + startDate));
        assertTrue(toString.contains("endDate=" + endDate));
        assertTrue(toString.contains("createdAt=" + createdAt));
    }

    @Test
    void shouldRejectBacktestBenchmarkWithEndDateInTheFuture() {
        BenchmarkId id = BenchmarkId.generate();
        Instant startDate = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant endDate = Instant.now().plus(7, ChronoUnit.DAYS);
        Instant createdAt = Instant.now();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            new Benchmark(id, "Bad Benchmark", startDate, endDate, createdAt)
        );
        assertTrue(exception.getMessage().contains("Backtest cannot extend into the future"));
    }

    @Test
    void shouldAllowBacktestBenchmarkEndingToday() {
        BenchmarkId id = BenchmarkId.generate();
        Instant startDate = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant endOfToday = Instant.now().truncatedTo(ChronoUnit.DAYS)
            .plus(1, ChronoUnit.DAYS).minusSeconds(1);
        Instant createdAt = Instant.now();

        Benchmark benchmark = new Benchmark(id, "Ends Today", startDate, endOfToday, createdAt);
        assertNotNull(benchmark);
        assertTrue(benchmark.isBacktest());
    }

    @Test
    void shouldAllowReconstitutionWithInvalidDates() {
        // Reconstitute bypasses validation — existing DB entities may predate this rule
        BenchmarkId id = BenchmarkId.generate();
        Instant startDate = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant endDate = Instant.now().plus(7, ChronoUnit.DAYS);
        Instant createdAt = Instant.now();

        Benchmark benchmark = Benchmark.reconstitute(id, "Legacy", startDate, endDate, createdAt, null);
        assertNotNull(benchmark);
        assertEquals("Legacy", benchmark.getName());
    }

    // ========== fromSimulations tests ==========

    @Test
    void shouldCreateBenchmarkFromSimulationsWithDerivedDates() {
        Instant sim1Start = Instant.parse("2025-01-01T00:00:00Z");
        Instant sim1End = Instant.parse("2025-03-31T23:59:59Z");
        Instant sim2Start = Instant.parse("2025-02-01T00:00:00Z");
        Instant sim2End = Instant.parse("2025-06-30T23:59:59Z");
        Money capital = Money.usd(new BigDecimal("100000"));

        Simulation sim1 = new Simulation(SimulationId.generate(), "Sim 1", StrategyId.generate(),
            sim1Start, sim1End, capital);
        Simulation sim2 = new Simulation(SimulationId.generate(), "Sim 2", StrategyId.generate(),
            sim2Start, sim2End, capital);

        UserId owner = new UserId(UUID.randomUUID());
        Benchmark benchmark = Benchmark.fromSimulations(
            BenchmarkId.generate(), "Custom Name", List.of(sim1, sim2), owner);

        assertEquals("Custom Name", benchmark.getName());
        assertEquals(sim1Start, benchmark.getStartDate()); // Earliest start
        assertEquals(sim2End, benchmark.getEndDate());      // Latest end
        assertNotNull(benchmark.getCreatedAt());
        assertEquals(owner, benchmark.getOwnerId());
    }

    @Test
    void shouldSetEndDateNullWhenAnySimulationIsLive() {
        Instant sim1Start = Instant.parse("2025-01-01T00:00:00Z");
        Instant sim1End = Instant.parse("2025-03-31T23:59:59Z");
        Instant sim2Start = Instant.parse("2025-02-01T00:00:00Z");
        Money capital = Money.usd(new BigDecimal("100000"));

        Simulation sim1 = new Simulation(SimulationId.generate(), "Sim 1", StrategyId.generate(),
            sim1Start, sim1End, capital);
        Simulation sim2 = new Simulation(SimulationId.generate(), "Sim 2", StrategyId.generate(),
            sim2Start, null, capital); // Live sim (no end date)

        Benchmark benchmark = Benchmark.fromSimulations(
            BenchmarkId.generate(), null, List.of(sim1, sim2), new UserId(UUID.randomUUID()));

        assertEquals(sim1Start, benchmark.getStartDate()); // Earliest start
        assertNull(benchmark.getEndDate());                // Null because of live sim
        assertTrue(benchmark.isLive());
    }

    @Test
    void fromSimulations_shouldAutoGenerateNameWhenNull() {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = Instant.parse("2025-03-31T23:59:59Z");
        Money capital = Money.usd(new BigDecimal("100000"));

        Simulation sim = new Simulation(SimulationId.generate(), "Sim", StrategyId.generate(),
            start, end, capital);

        Benchmark benchmark = Benchmark.fromSimulations(
            BenchmarkId.generate(), null, List.of(sim), new UserId(UUID.randomUUID()));

        assertNotNull(benchmark.getName());
        assertTrue(benchmark.getName().startsWith("Backtest: "));
    }

    @Test
    void shouldRejectEmptySimulationList() {
        assertThrows(IllegalArgumentException.class, () ->
            Benchmark.fromSimulations(
                BenchmarkId.generate(), "Name", List.of(), new UserId(UUID.randomUUID()))
        );
    }

    @Test
    void shouldRejectNullSimulationList() {
        assertThrows(IllegalArgumentException.class, () ->
            Benchmark.fromSimulations(
                BenchmarkId.generate(), "Name", null, new UserId(UUID.randomUUID()))
        );
    }

    @Test
    void shouldAllowCrossDateRangesViaReconstitute() {
        // fromSimulations uses reconstitute() to skip validateBacktestDates
        // This test verifies that a benchmark spanning past to future is allowed
        Instant pastStart = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant futureEnd = Instant.now().plus(30, ChronoUnit.DAYS);
        Money capital = Money.usd(new BigDecimal("100000"));

        // Create sims via reconstitute to bypass individual sim date validation
        Simulation sim1 = Simulation.reconstitute(SimulationId.generate(), "Past Sim",
            StrategyId.generate(), pastStart, Instant.now().minus(1, ChronoUnit.DAYS),
            capital, null, null, null);
        Simulation sim2 = Simulation.reconstitute(SimulationId.generate(), "Future Sim",
            StrategyId.generate(), Instant.now(), futureEnd,
            capital, null, null, null);

        // This would fail with the normal constructor due to validateBacktestDates
        // but fromSimulations uses reconstitute() so it works
        Benchmark benchmark = Benchmark.fromSimulations(
            BenchmarkId.generate(), "Cross Range", List.of(sim1, sim2), new UserId(UUID.randomUUID()));

        assertEquals(pastStart, benchmark.getStartDate());
        assertEquals(futureEnd, benchmark.getEndDate());
    }
}
