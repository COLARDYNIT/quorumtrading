package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.query.GetSimulationChartDataQuery;
import com.quorumtrading.model.valueobject.ChartDataPoint;

import java.util.List;

/**
 * Use case for retrieving decimated chart data for simulation visualization.
 * <p>
 * This use case optimizes portfolio snapshot data for chart rendering by reducing
 * the number of data points while preserving the visual shape of the portfolio value over time.
 * </p>
 * <p><b>Performance Optimization:</b></p>
 * Instead of returning all portfolio snapshots (which can be thousands of points),
 * this use case applies time-bucket decimation to return only the requested number
 * of representative points (default: 500).
 * <p>
 * <b>Algorithm:</b> Time-bucket averaging - divides the time range into N buckets
 * and averages the values within each bucket.
 * </p>
 */
public interface GetSimulationChartDataUseCase {

    /**
     * Execute the chart data retrieval with decimation.
     *
     * @param query the query containing simulation ID and target number of points
     * @return list of decimated chart data points optimized for rendering
     */
    List<ChartDataPoint> execute(GetSimulationChartDataQuery query);
}
