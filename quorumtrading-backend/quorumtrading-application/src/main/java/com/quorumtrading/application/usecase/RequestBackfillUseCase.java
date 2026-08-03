package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.RequestBackfillCommand;
import com.quorumtrading.application.usecase.result.RequestBackfillResult;

/**
 * Use case for requesting backfill of missing market data.
 *
 * <p>This use case:</p>
 * <ul>
 *   <li>Analyzes current data availability for the requested range</li>
 *   <li>Identifies gaps in market data coverage</li>
 *   <li>Creates backfill jobs when gaps are found</li>
 *   <li>Returns job information if backfill is triggered</li>
 * </ul>
 *
 * <p><b>Smart Gap Detection:</b></p>
 * The use case internally calls AnalyzeDataAvailabilityUseCase to determine
 * if backfill is actually needed. If data is already complete, no backfill
 * job is created.
 */
public interface RequestBackfillUseCase {
    /**
     * Execute the backfill request.
     * Analyzes data availability and creates a backfill job if gaps exist.
     *
     * @param command the backfill command containing symbols, data types, and time range
     * @return RequestBackfillResult indicating whether backfill was triggered and job ID if applicable
     */
    RequestBackfillResult execute(RequestBackfillCommand command);
}
