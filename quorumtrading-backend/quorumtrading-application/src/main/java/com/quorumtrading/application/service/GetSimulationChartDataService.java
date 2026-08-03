package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.GetSimulationChartDataUseCase;
import com.quorumtrading.application.usecase.query.GetSimulationChartDataQuery;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.ChartDataPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Service that decimates portfolio snapshots into chart-optimized data points.
 * <p>
 * <b>Decimation Algorithm: Time-Bucket Averaging</b>
 * </p>
 * <ol>
 *   <li>Fetches all portfolio snapshots for the simulation (sorted by timestamp)</li>
 *   <li>Divides the time range into N buckets (where N = targetPoints)</li>
 *   <li>For each bucket, averages the portfolio values of all snapshots in that bucket</li>
 *   <li>Returns one data point per bucket</li>
 * </ol>
 * <p>
 * <b>Performance:</b> Reduces thousands of data points to 500 (default),
 * making chart rendering fast while preserving the overall shape of the data.
 * </p>
 */
public class GetSimulationChartDataService implements GetSimulationChartDataUseCase {

    private static final Logger logger = LoggerFactory.getLogger(GetSimulationChartDataService.class);

    private final PortfolioRepository portfolioRepository;
    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final AuthorizationService authorizationService;

    public GetSimulationChartDataService(
            PortfolioRepository portfolioRepository,
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            AuthorizationService authorizationService) {
        this.portfolioRepository = portfolioRepository;
        this.simulationRepository = simulationRepository;
        this.strategyRepository = strategyRepository;
        this.authorizationService = authorizationService;
    }

    @Override
    public List<ChartDataPoint> execute(GetSimulationChartDataQuery query) {
        logger.debug("Fetching chart data for simulation: {} with {} target points",
            query.simulationId().value(), query.targetPoints());

        // Authorization check
        Simulation simulation = simulationRepository.findById(query.simulationId())
            .orElseThrow(() -> new java.util.NoSuchElementException("Simulation not found: " + query.simulationId()));
        Strategy strategy = strategyRepository.findById(simulation.getStrategyId())
            .orElseThrow(() -> new java.util.NoSuchElementException("Strategy not found"));
        authorizationService.requireView(query.requestingUserId(), simulation, strategy);

        // Fetch all snapshots for this simulation
        List<PortfolioSnapshot> snapshots = portfolioRepository.findAllBySimulationId(query.simulationId());

        if (snapshots.isEmpty()) {
            logger.warn("No portfolio snapshots found for simulation: {}", query.simulationId().value());
            return List.of();
        }

        // If we have fewer snapshots than target points, return all without decimation
        if (snapshots.size() <= query.targetPoints()) {
            logger.debug("Snapshot count ({}) <= target points ({}), returning all points",
                snapshots.size(), query.targetPoints());
            return convertToChartPoints(snapshots);
        }

        // Apply time-bucket decimation
        logger.debug("Decimating {} snapshots to {} points using time-bucket averaging",
            snapshots.size(), query.targetPoints());
        return decimateWithTimeBuckets(snapshots, query.targetPoints());
    }

    /**
     * Converts portfolio snapshots to chart data points without decimation.
     */
    private List<ChartDataPoint> convertToChartPoints(List<PortfolioSnapshot> snapshots) {
        return snapshots.stream()
            .map(snapshot -> ChartDataPoint.from(
                snapshot.getTimestamp(),
                snapshot.getTotalValue(),
                snapshot.getCashAvailable()
            ))
            .toList();
    }

    /**
     * Decimates snapshots using time-bucket averaging.
     * <p>
     * Divides the time range into N buckets and averages values within each bucket.
     * </p>
     *
     * @param snapshots    All snapshots (must be sorted by timestamp)
     * @param targetPoints Number of buckets/points to generate
     * @return Decimated list of chart points
     */
    private List<ChartDataPoint> decimateWithTimeBuckets(List<PortfolioSnapshot> snapshots, int targetPoints) {
        int totalSnapshots = snapshots.size();
        int bucketSize = totalSnapshots / targetPoints;

        // Ensure we have at least 1 snapshot per bucket
        if (bucketSize < 1) {
            bucketSize = 1;
        }

        List<ChartDataPoint> decimatedPoints = new ArrayList<>();

        for (int i = 0; i < totalSnapshots; i += bucketSize) {
            int bucketEnd = Math.min(i + bucketSize, totalSnapshots);
            List<PortfolioSnapshot> bucket = snapshots.subList(i, bucketEnd);

            // Average the values in this bucket
            ChartDataPoint averagedPoint = averageBucket(bucket);
            decimatedPoints.add(averagedPoint);
        }

        logger.debug("Decimation complete: {} snapshots -> {} chart points",
            totalSnapshots, decimatedPoints.size());

        return decimatedPoints;
    }

    /**
     * Averages all snapshots in a bucket into a single chart data point.
     * Uses the middle timestamp of the bucket and averages the portfolio/cash values.
     */
    private ChartDataPoint averageBucket(List<PortfolioSnapshot> bucket) {
        if (bucket.isEmpty()) {
            throw new IllegalArgumentException("Bucket cannot be empty");
        }

        // Use the middle snapshot's timestamp (or first if only one)
        Instant timestamp = bucket.get(bucket.size() / 2).getTimestamp();

        // Average portfolio values
        BigDecimal avgPortfolioValue = bucket.stream()
            .map(s -> s.getTotalValue().amount())
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(bucket.size()), 2, RoundingMode.HALF_UP);

        // Average cash values
        BigDecimal avgCash = bucket.stream()
            .map(s -> s.getCashAvailable().amount())
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(bucket.size()), 2, RoundingMode.HALF_UP);

        return new ChartDataPoint(timestamp, avgPortfolioValue, avgCash);
    }
}
