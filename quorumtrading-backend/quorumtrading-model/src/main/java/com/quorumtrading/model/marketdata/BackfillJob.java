package com.quorumtrading.model.marketdata;

import com.quorumtrading.model.enums.BackfillStatus;
import com.quorumtrading.model.enums.Granularity;
import com.quorumtrading.model.valueobject.SimulationId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain entity representing a request to backfill missing market data.
 * Tracks the progress and status of backfill operations for identified data gaps.
 *
 * This is a domain entity following DDD principles:
 * - Encapsulates state management logic (startProcessing, updateProgress, etc.)
 * - Contains business rules for state transitions
 * - No infrastructure concerns (JPA annotations belong in adapter layer)
 */
public class BackfillJob {
    private final UUID id;
    private final List<MarketDataGap> gaps;
    private BackfillStatus status;
    private Integer progressPercentage;
    private Instant startedAt;
    private Instant completedAt;
    private String errorMessage;
    private Integer totalRecordsExpected;
    private Integer recordsProcessed;
    private SimulationId linkedSimulationId;
    private Instant retryAfter;
    private Granularity granularity;

    /**
     * Creates a new backfill request for the specified gaps.
     * Initial status is PENDING with 0% progress.
     *
     * @param gaps List of market data gaps to backfill
     */
    public BackfillJob(List<MarketDataGap> gaps) {
        this.id = UUID.randomUUID();
        this.gaps = List.copyOf(gaps);
        this.status = BackfillStatus.PENDING;
        this.progressPercentage = 0;
        this.recordsProcessed = 0;
        this.granularity = Granularity.ONE_DAY;
    }

    /**
     * Creates a new backfill request linked to a simulation.
     * When this backfill completes, the simulation will be notified.
     *
     * @param gaps List of market data gaps to backfill
     * @param linkedSimulationId The simulation waiting for this backfill
     */
    public BackfillJob(List<MarketDataGap> gaps, SimulationId linkedSimulationId) {
        this(gaps);
        this.linkedSimulationId = linkedSimulationId;
    }

    /**
     * Creates a new backfill request linked to a simulation with specific granularity.
     *
     * @param gaps List of market data gaps to backfill
     * @param linkedSimulationId The simulation waiting for this backfill
     * @param granularity The bar granularity to use for backfilling
     */
    public BackfillJob(List<MarketDataGap> gaps, SimulationId linkedSimulationId, Granularity granularity) {
        this(gaps);
        this.linkedSimulationId = linkedSimulationId;
        this.granularity = granularity != null ? granularity : Granularity.ONE_DAY;
    }

    /**
     * Reconstitutes a BackfillJob from persisted state.
     * Used by the persistence adapter to restore domain entities from storage.
     *
     * This is a static factory method to support proper persistence round-trips
     * while maintaining encapsulation of domain logic.
     *
     * @param id The unique identifier
     * @param gaps List of market data gaps
     * @param status Current status
     * @param progressPercentage Progress percentage (0-100)
     * @param startedAt Timestamp when processing started
     * @param completedAt Timestamp when processing completed
     * @param errorMessage Error message if failed
     * @param totalRecordsExpected Total number of records expected
     * @param recordsProcessed Number of records processed so far
     * @param linkedSimulationId Optional linked simulation ID
     * @param retryAfter Optional instant when the job should be retried
     * @return Reconstituted BackfillJob with all persisted state
     */
    public static BackfillJob reconstitute(
            UUID id,
            List<MarketDataGap> gaps,
            BackfillStatus status,
            Integer progressPercentage,
            Instant startedAt,
            Instant completedAt,
            String errorMessage,
            Integer totalRecordsExpected,
            Integer recordsProcessed,
            SimulationId linkedSimulationId,
            Instant retryAfter,
            Granularity granularity) {
        return new BackfillJob(
            id,
            gaps,
            status,
            progressPercentage,
            startedAt,
            completedAt,
            errorMessage,
            totalRecordsExpected,
            recordsProcessed,
            linkedSimulationId,
            retryAfter,
            granularity
        );
    }

    /**
     * Private constructor for reconstitution from persisted state.
     * Used only by the reconstitute factory method to restore entities from storage.
     *
     * @param id The unique identifier
     * @param gaps List of market data gaps
     * @param status Current status
     * @param progressPercentage Progress percentage (0-100)
     * @param startedAt Timestamp when processing started
     * @param completedAt Timestamp when processing completed
     * @param errorMessage Error message if failed
     * @param totalRecordsExpected Total number of records expected
     * @param recordsProcessed Number of records processed so far
     * @param linkedSimulationId Optional linked simulation ID
     * @param retryAfter Optional instant when the job should be retried
     */
    private BackfillJob(
            UUID id,
            List<MarketDataGap> gaps,
            BackfillStatus status,
            Integer progressPercentage,
            Instant startedAt,
            Instant completedAt,
            String errorMessage,
            Integer totalRecordsExpected,
            Integer recordsProcessed,
            SimulationId linkedSimulationId,
            Instant retryAfter,
            Granularity granularity) {
        this.id = id;
        this.gaps = List.copyOf(gaps);
        this.status = status;
        this.progressPercentage = progressPercentage;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.errorMessage = errorMessage;
        this.totalRecordsExpected = totalRecordsExpected;
        this.recordsProcessed = recordsProcessed;
        this.linkedSimulationId = linkedSimulationId;
        this.retryAfter = retryAfter;
        this.granularity = granularity != null ? granularity : Granularity.ONE_DAY;
    }

    /**
     * Starts processing the backfill request.
     * Transitions from PENDING to IN_PROGRESS status.
     *
     * @throws IllegalStateException if request is not in PENDING status
     */
    public void startProcessing() {
        if (status != BackfillStatus.PENDING && status != BackfillStatus.RETRY_PENDING) {
            throw new IllegalStateException("Can only start pending or retry-pending requests");
        }
        this.status = BackfillStatus.IN_PROGRESS;
        this.retryAfter = null;
        if (this.startedAt == null) {
            this.startedAt = Instant.now();
        }
    }

    /**
     * Updates the progress of the backfill request.
     * Calculates percentage based on records processed vs total expected.
     *
     * @param recordsProcessed Number of records processed so far
     * @param totalRecords Total number of records to process
     */
    public void updateProgress(int recordsProcessed, int totalRecords) {
        this.recordsProcessed = recordsProcessed;
        this.totalRecordsExpected = totalRecords;
        this.progressPercentage = (recordsProcessed * 100) / totalRecords;
    }

    /**
     * Marks the backfill request as completed.
     * Sets status to COMPLETED, records completion time, and sets progress to 100%.
     */
    public void markCompleted() {
        this.status = BackfillStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.progressPercentage = 100;
    }

    /**
     * Marks the backfill request as failed.
     * Sets status to FAILED, records completion time, and stores error message.
     *
     * @param error Error message describing the failure
     */
    public void markFailed(String error) {
        this.status = BackfillStatus.FAILED;
        this.completedAt = Instant.now();
        this.errorMessage = error;
    }

    /**
     * Schedules the job for retry at a future time.
     * Transitions to RETRY_PENDING status and stores the retry timestamp.
     *
     * @param retryAfter the instant after which this job should be retried
     */
    public void scheduleRetry(Instant retryAfter) {
        this.status = BackfillStatus.RETRY_PENDING;
        this.retryAfter = retryAfter;
    }

    /**
     * Checks whether this job is ready to be retried.
     *
     * @return true if the retry time has passed and the job can be re-processed
     */
    public boolean isReadyForRetry() {
        return retryAfter != null && Instant.now().isAfter(retryAfter);
    }

    public Instant getRetryAfter() {
        return retryAfter;
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public List<MarketDataGap> getGaps() {
        return gaps;
    }

    public BackfillStatus getStatus() {
        return status;
    }

    public Integer getProgressPercentage() {
        return progressPercentage;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Integer getTotalRecordsExpected() {
        return totalRecordsExpected;
    }

    public Integer getRecordsProcessed() {
        return recordsProcessed;
    }

    /**
     * Returns the linked simulation ID if present.
     * When this backfill completes, the simulation will be notified.
     *
     * @return Optional containing the linked simulation ID, or empty if not linked
     */
    public Optional<SimulationId> getLinkedSimulationId() {
        return Optional.ofNullable(linkedSimulationId);
    }

    public Granularity getGranularity() {
        return granularity != null ? granularity : Granularity.ONE_DAY;
    }
}
