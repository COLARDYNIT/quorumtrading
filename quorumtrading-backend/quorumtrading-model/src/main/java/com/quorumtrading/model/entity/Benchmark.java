package com.quorumtrading.model.entity;

import com.quorumtrading.model.valueobject.BenchmarkId;
import com.quorumtrading.model.valueobject.UserId;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

/**
 * Domain entity representing a benchmark that groups multiple strategy simulations for comparison.
 * A benchmark can be a historical backtest period or a live trading competition.
 *
 * <p><b>Aggregate Root</b> - Controls access to associated simulations.</p>
 *
 * <p><b>Aggregate Boundary:</b></p>
 * <ul>
 *   <li><b>Root:</b> Benchmark</li>
 *   <li><b>External References:</b> Simulation (by SimulationId)</li>
 * </ul>
 *
 * <p><b>Invariants Enforced:</b></p>
 * <ul>
 *   <li>End date must be after start date (if provided)</li>
 *   <li>Name is auto-generated if not provided</li>
 *   <li>Created timestamp is immutable</li>
 * </ul>
 *
 * <p><b>Benchmark Types:</b></p>
 * <ul>
 *   <li><b>Backtest:</b> Has both start and end dates (historical period)</li>
 *   <li><b>Live:</b> Has start date only (ongoing competition)</li>
 * </ul>
 *
 * @see Simulation
 */
public class Benchmark implements AggregateRoot {

    private BenchmarkId benchmarkId;
    private String name;
    private Instant startDate;
    private Instant endDate;
    private Instant createdAt;
    private UserId ownerId;

    /** No-arg constructor for reconstitution only. */
    private Benchmark() {}

    /**
     * Creates a new Benchmark with explicit name and owner.
     *
     * @param benchmarkId Unique identifier
     * @param name Display name for the benchmark
     * @param startDate Competition start date
     * @param endDate Competition end date (null for live/ongoing)
     * @param createdAt Timestamp when benchmark was created
     * @param ownerId Owner of this benchmark
     * @throws IllegalArgumentException if dates are invalid
     */
    public Benchmark(
        BenchmarkId benchmarkId,
        String name,
        Instant startDate,
        Instant endDate,
        Instant createdAt,
        UserId ownerId
    ) {
        if (benchmarkId == null) {
            throw new IllegalArgumentException("BenchmarkId cannot be null");
        }
        if (startDate == null) {
            throw new IllegalArgumentException("Start date cannot be null");
        }
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must be after start date");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Created timestamp cannot be null");
        }
        validateBacktestDates(startDate, endDate);

        this.benchmarkId = benchmarkId;
        this.name = (name == null || name.isBlank()) ? generateName(startDate, endDate) : name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.createdAt = createdAt;
        this.ownerId = ownerId;
    }

    /**
     * Backward-compatible constructor without ownerId.
     */
    public Benchmark(
        BenchmarkId benchmarkId,
        String name,
        Instant startDate,
        Instant endDate,
        Instant createdAt
    ) {
        this(benchmarkId, name, startDate, endDate, createdAt, null);
    }

    /**
     * Creates a new Benchmark with auto-generated name.
     */
    public Benchmark(
        BenchmarkId benchmarkId,
        Instant startDate,
        Instant endDate,
        Instant createdAt
    ) {
        this(benchmarkId, null, startDate, endDate, createdAt, null);
    }

    /**
     * Reconstitute a Benchmark from persistence. Skips date validation since the entity
     * was already validated at creation time and may predate current rules.
     */
    public static Benchmark reconstitute(
        BenchmarkId benchmarkId,
        String name,
        Instant startDate,
        Instant endDate,
        Instant createdAt,
        UserId ownerId
    ) {
        Benchmark b = new Benchmark();
        b.benchmarkId = benchmarkId;
        b.name = (name == null || name.isBlank()) ? generateName(startDate, endDate) : name;
        b.startDate = startDate;
        b.endDate = endDate;
        b.createdAt = createdAt;
        b.ownerId = ownerId;
        return b;
    }

    /**
     * Creates a new Benchmark composed from existing simulations.
     * Derives date range from the simulation set:
     * - startDate = earliest simulation start
     * - endDate = latest simulation end (null if any simulation has no end date)
     *
     * Uses reconstitute() internally to skip validateBacktestDates, since the derived
     * date range from individually-valid simulations may span today.
     *
     * @param benchmarkId Unique identifier for the new benchmark
     * @param name Display name (null for auto-generated)
     * @param simulations The simulations to derive dates from (must not be empty)
     * @param ownerId Owner of this benchmark
     * @return A new Benchmark with dates derived from the simulations
     * @throws IllegalArgumentException if simulations is empty
     */
    public static Benchmark fromSimulations(
        BenchmarkId benchmarkId,
        String name,
        Collection<Simulation> simulations,
        UserId ownerId
    ) {
        if (simulations == null || simulations.isEmpty()) {
            throw new IllegalArgumentException("At least one simulation is required to compose a benchmark");
        }

        Instant startDate = null;
        Instant endDate = null;
        boolean hasLiveSimulation = false;

        for (Simulation sim : simulations) {
            if (startDate == null || sim.getStartDate().isBefore(startDate)) {
                startDate = sim.getStartDate();
            }
            if (sim.getEndDate() == null) {
                hasLiveSimulation = true;
            } else if (!hasLiveSimulation && (endDate == null || sim.getEndDate().isAfter(endDate))) {
                endDate = sim.getEndDate();
            }
        }

        if (hasLiveSimulation) {
            endDate = null;
        }

        return reconstitute(
            benchmarkId,
            (name == null || name.isBlank()) ? generateName(startDate, endDate) : name,
            startDate,
            endDate,
            Instant.now(),
            ownerId
        );
    }

    /**
     * Validates that a backtest benchmark does not extend into the future.
     * StartDate before today (yesterday or earlier) with endDate after today → rejected.
     */
    private static void validateBacktestDates(Instant startDate, Instant endDate) {
        if (BacktestWindow.extendsIntoTheFuture(startDate, endDate, Instant.now(), ZoneOffset.UTC)) {
            throw new IllegalArgumentException(BacktestWindow.rejectionMessage(startDate, endDate));
        }
    }

    // Getters
    public BenchmarkId getBenchmarkId() {
        return benchmarkId;
    }

    public String getName() {
        return name;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UserId getOwnerId() {
        return ownerId;
    }

    // Business methods

    /**
     * Checks if this is a backtest benchmark (has end date).
     *
     * @return true if end date is set
     */
    public boolean isBacktest() {
        return endDate != null;
    }

    /**
     * Checks if this is a live benchmark (no end date).
     *
     * @return true if end date is null
     */
    public boolean isLive() {
        return endDate == null;
    }

    /**
     * Auto-generates benchmark name based on dates.
     * Format:
     * - Backtest: "Backtest: MMM d, yyyy - MMM d, yyyy"
     * - Live: "Live: Started MMM d, yyyy"
     *
     * @param startDate Start date
     * @param endDate End date (null for live)
     * @return Generated name
     */
    public static String generateName(Instant startDate, Instant endDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
            .withZone(ZoneId.of("UTC"));

        if (endDate != null) {
            return String.format("Backtest: %s - %s",
                formatter.format(startDate),
                formatter.format(endDate));
        }
        return String.format("Live: Started %s", formatter.format(startDate));
    }

    @Override
    public String toString() {
        return "Benchmark{" +
                "benchmarkId=" + benchmarkId +
                ", name='" + name + '\'' +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", createdAt=" + createdAt +
                ", ownerId=" + ownerId +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Benchmark that = (Benchmark) o;
        return benchmarkId.equals(that.benchmarkId);
    }

    @Override
    public int hashCode() {
        return benchmarkId.hashCode();
    }
}
